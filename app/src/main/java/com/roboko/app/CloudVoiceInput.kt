package com.roboko.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/** 云端 STT 接口协议。 */
enum class SttApiFormat(val label: String) {
    /** OpenAI 风格：POST /audio/transcriptions，multipart 上传，Authorization: Bearer */
    OPENAI_TRANSCRIPTION("OpenAI 转写接口"),
    /** 对话式音频输入：POST /chat/completions，base64 音频放进 messages（MiMo ASR 用这个） */
    CHAT_AUDIO("对话式音频接口")
}

/** 云端 STT 配置。 */
data class SttCloudConfig(
    val presetId: String = "siliconflow",
    val baseUrl: String = "https://api.siliconflow.cn/v1",
    val apiKey: String = "",
    val model: String = "FunAudioLLM/SenseVoiceSmall",
    /** 该服务申请 API Key 的官方地址。 */
    val keyUrl: String = "https://cloud.siliconflow.cn/account/ak",
    val format: SttApiFormat = SttApiFormat.OPENAI_TRANSCRIPTION
) {
    fun isComplete() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** 云端 STT 预设。 */
val sttPresets = listOf(
    SttCloudConfig(
        "siliconflow", "https://api.siliconflow.cn/v1", "", "FunAudioLLM/SenseVoiceSmall",
        "https://cloud.siliconflow.cn/account/ak", SttApiFormat.OPENAI_TRANSCRIPTION
    ),
    SttCloudConfig(
        "mimo", "https://api.xiaomimimo.com/v1", "", "mimo-v2.5-asr",
        "https://mimo.mi.com/", SttApiFormat.CHAT_AUDIO
    ),
    SttCloudConfig(
        "openai", "https://api.openai.com/v1", "", "whisper-1",
        "https://platform.openai.com/api-keys", SttApiFormat.OPENAI_TRANSCRIPTION
    )
)

/**
 * 云端语音识别。
 *
 * 录音写入 16kHz 单声道 PCM16 的 WAV，停止后一次性上传转写。支持两种接口协议：
 * - OPENAI_TRANSCRIPTION：/audio/transcriptions，multipart 上传（硅基流动 SenseVoice、Groq、OpenAI）
 * - CHAT_AUDIO：/chat/completions，base64 音频放进 messages（小米 MiMo ASR）
 *
 * 注意：均为非流式——录音过程中不出字，停止后返回整段文本。
 */
class CloudVoiceInput(private val context: Context) : SttController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    var config: SttCloudConfig = SttCloudConfig()

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    @Volatile override var isReady = false; private set
    override val isLoading: Boolean get() = false
    @Volatile var isListening = false; private set

    override fun prepare(onReady: () -> Unit, onError: (String) -> Unit) {
        isReady = config.isComplete()
        if (isReady) main.post(onReady)
        else main.post { onError("请先在 设置 → 语音识别 → 云端识别设置 中填写接口地址与 API Key") }
    }

    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit): Boolean {
        if (!config.isComplete()) {
            onError("请先在 设置 → 语音识别 → 云端识别设置 中填写接口地址与 API Key")
            return false
        }
        if (isListening) return false
        return try {
            val wav = File(context.cacheDir, "stt-rec.wav")
            if (wav.exists()) wav.delete()
            val partialFile = File(context.cacheDir, "stt-partial.wav")
            val out = RandomAccessFile(wav, "rw")
            out.setLength(0)
            out.write(ByteArray(44))   // 先占位 WAV 头，每次发送前回填长度
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, CHUNK * 4))
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                out.close(); record.release()
                onError("无法初始化录音设备")
                return false
            }
            audioRecord = record
            running = true
            isListening = true
            val lastPartialText = AtomicReference("")
            val partialInFlight = AtomicBoolean(false)
            worker = Thread {
                val buf = ByteArray(CHUNK)
                var pcmBytes = 0L
                var peak = 0
                var lastPartialAt = 0L
                try {
                    record.startRecording()
                    while (running) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        out.seek(pcmBytes + 44)     // 始终追加到音频末尾
                        out.write(buf, 0, n)
                        pcmBytes += n
                        // 记录峰值音量，便于判断「麦克风没录到声音」还是「服务没返回文字」
                        var i = 0
                        while (i + 1 < n) {
                            val lo = buf[i].toInt() and 0xFF
                            val hi = buf[i + 1].toInt()
                            val s = ((hi shl 8) or lo).toShort().toInt()
                            val a = if (s < 0) -s else s
                            if (a > peak) peak = a
                            i += 2
                        }
                        // 分块伪流式：每隔一段时间把已录音频送一次，实现边录边出字
                        val now = System.currentTimeMillis()
                        if (pcmBytes >= MIN_BYTES_FOR_PARTIAL &&
                            now - lastPartialAt >= PARTIAL_INTERVAL_MS &&
                            partialInFlight.compareAndSet(false, true)
                        ) {
                            lastPartialAt = now
                            val copied = try {
                                writeWavHeader(out, pcmBytes)
                                out.seek(pcmBytes + 44)
                                wav.inputStream().use { input -> partialFile.outputStream().use { input.copyTo(it) } }
                                true
                            } catch (_: Exception) { false }
                            if (!copied) {
                                partialInFlight.set(false)
                            } else {
                                scope.launch {
                                    try {
                                        val text = transcribe(partialFile, config, PARTIAL_TIMEOUT_MS)
                                        if (text.isNotBlank()) {
                                            lastPartialText.set(text)
                                            main.post { onPartial(text) }
                                        }
                                    } catch (_: Exception) {
                                        // 分块失败不打扰用户，停止时仍会用完整音频重试
                                    } finally {
                                        partialInFlight.set(false)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "录音异常", e)
                    main.post { onError(e.message ?: "录音中断") }
                } finally {
                    try { record.stop() } catch (_: Exception) {}
                    try { record.release() } catch (_: Exception) {}
                    audioRecord = null
                    running = false
                    isListening = false
                    try {
                        writeWavHeader(out, pcmBytes)
                        out.close()
                    } catch (_: Exception) {}
                    if (pcmBytes < SAMPLE_RATE / 2) {   // 少于约 0.5 秒视为没说话
                        main.post { onError("录音太短，请再试一次"); onFinal("") }
                        return@Thread
                    }
                    scope.launch {
                        val result = runCatching { transcribe(wav, config, FINAL_TIMEOUT_MS) }
                        main.post {
                            val text = result.getOrNull()
                            val fallback = lastPartialText.get()
                            when {
                                !text.isNullOrBlank() -> onFinal(text)
                                // 整段请求失败时，用最后一次分块结果兜底，避免已识别内容白费
                                fallback.isNotBlank() -> onFinal(fallback)
                                else -> {
                                    val seconds = pcmBytes / 2.0 / SAMPLE_RATE
                                    val detail = result.exceptionOrNull()?.let { friendlyError(it.message ?: "识别失败") }
                                        ?: "服务返回了空文本。已录制 %.1f 秒，峰值音量 %d/32767%s".format(
                                            seconds, peak,
                                            if (peak < 300) "（几乎没有声音，请检查麦克风权限或换个环境）" else "（录音正常，可能是音频格式或模型问题）"
                                        )
                                    onError(detail)
                                    onFinal("")
                                }
                            }
                        }
                    }
                }
            }.also { it.start() }
            true
        } catch (e: Exception) {
            isListening = false
            onError(e.message ?: "无法启动录音，请检查麦克风权限")
            false
        }
    }

    override fun stop() { running = false }

    override fun shutdown() {
        running = false
        scope.launch {
            try { worker?.join(1500) } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
        }
    }

    // ---------------------------------------------------------------- 上传转写

    private fun transcribe(wav: File, config: SttCloudConfig, readTimeoutMs: Int = 120000): String = when (config.format) {
        SttApiFormat.OPENAI_TRANSCRIPTION -> transcribeMultipart(wav, config, readTimeoutMs)
        SttApiFormat.CHAT_AUDIO -> transcribeChatAudio(wav, config, readTimeoutMs)
    }

    /** OpenAI 风格：multipart 上传到 /audio/transcriptions。 */
    private fun transcribeMultipart(wav: File, config: SttCloudConfig, readTimeoutMs: Int): String {
        val boundary = "----RobokoBoundary" + System.currentTimeMillis()
        val url = URL(config.baseUrl.trimEnd('/') + "/audio/transcriptions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            conn.outputStream.use { os ->
                fun writeText(s: String) = os.write(s.toByteArray(Charsets.UTF_8))
                writeText("--$boundary\r\n")
                writeText("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                writeText("${config.model}\r\n")
                writeText("--$boundary\r\n")
                writeText("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
                writeText("Content-Type: audio/wav\r\n\r\n")
                wav.inputStream().use { it.copyTo(os) }
                writeText("\r\n--$boundary--\r\n")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code|${body.take(300)}")
            return JSONObject(body).optString("text", "").trim()
        } finally {
            conn.disconnect()
            try { wav.delete() } catch (_: Exception) {}
        }
    }

    /** 对话式音频输入：base64 音频放进 messages 打到 /chat/completions（小米 MiMo ASR）。 */
    private fun transcribeChatAudio(wav: File, config: SttCloudConfig, readTimeoutMs: Int): String {
        val base64Audio = Base64.encodeToString(wav.readBytes(), Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().put(JSONObject().apply {
                    put("type", "input_audio")
                    put("input_audio", JSONObject().apply {
                        put("data", "data:audio/wav;base64,$base64Audio")
                        put("format", "wav")
                    })
                }))
            }))
            put("asr_options", JSONObject().put("language", "auto"))
        }
        val conn = (URL(config.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("api-key", config.apiKey)                    // MiMo 采用该鉴权头
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")   // 兼容其他实现
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code|${body.take(300)}")
            val content = JSONObject(body).optJSONArray("choices")
                ?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
            return when (content) {
                is String -> content.trim()
                is JSONArray -> (0 until content.length()).joinToString("") { i ->
                    content.optJSONObject(i)?.optString("text", "") ?: ""
                }.trim()
                else -> ""
            }
        } finally {
            conn.disconnect()
            try { wav.delete() } catch (_: Exception) {}
        }
    }

    /** 回填 WAV 头的两个长度字段。 */
    private fun writeWavHeader(out: RandomAccessFile, pcmBytes: Long) {
        val dataLen = pcmBytes.toInt()
        out.seek(0)
        fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        fun le32(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
        val byteRate = SAMPLE_RATE * 2
        out.write("RIFF".toByteArray())
        out.write(le32(36 + dataLen))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(1))              // PCM
        out.write(le16(1))              // mono
        out.write(le32(SAMPLE_RATE))
        out.write(le32(byteRate))
        out.write(le16(2))              // block align
        out.write(le16(16))             // bits per sample
        out.write("data".toByteArray())
        out.write(le32(dataLen))
    }

    /**
     * 用一段静音音频实测当前配置，走的是与真实录音完全相同的上传路径。
     * 返回可直接展示给用户的中文结论。
     */
    suspend fun test(config: SttCloudConfig): String = withContext(Dispatchers.IO) {
        if (!config.isComplete()) return@withContext "✗ 请先填写接口地址、API Key 和模型名称"
        // 先探测域名能否连通，用来区分「手机网络到不了」和「接口/配置有问题」
        probeReachable(config)?.let { return@withContext it }
        val wav = File(context.cacheDir, "stt-test.wav")
        try {
            writeSilentWav(wav, 1200)
            // 测试用较短超时，避免界面长时间停在「测试中」
            val text = transcribe(wav, config, TEST_TIMEOUT_MS)
            if (text.isBlank()) "✓ 配置可用：服务已正常响应（静音音频无文字属正常）"
            else "✓ 配置可用，识别到：$text"
        } catch (e: Exception) {
            val raw = e.message ?: "测试失败"
            // 部分服务对纯静音音频会报「无语音内容」，这属于服务正常响应，配置本身没问题
            if (isNoSpeechError(raw)) "✓ 配置可用：服务已正常响应（测试音频为静音，提示无语音内容属正常）"
            else friendlyError(raw)
        } finally {
            try { wav.delete() } catch (_: Exception) {}
        }
    }

    /** 轻量连通性探测：返回 null 表示可达，否则返回给用户看的说明。任何 HTTP 状态码都算可达。 */
    private fun probeReachable(config: SttCloudConfig): String? {
        val probeUrl = config.baseUrl.trimEnd('/') + "/models"
        return try {
            val c = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                setRequestProperty("api-key", config.apiKey)
            }
            try { c.responseCode } finally { c.disconnect() }
            null
        } catch (e: Exception) {
            "✗ 连不上 ${config.baseUrl}\n原因：${e.message ?: "未知错误"}\n请确认手机网络能访问该域名（可尝试切换 WiFi / 移动数据）"
        }
    }

    /** 生成一段静音 WAV，用于连通性与鉴权自检。 */
    private fun writeSilentWav(file: File, millis: Int) {
        val pcm = ByteArray(SAMPLE_RATE * millis / 1000 * 2)
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            out.write(ByteArray(44))
            out.write(pcm)
            writeWavHeader(out, pcm.size.toLong())
        }
    }

    private fun isNoSpeechError(raw: String): Boolean {
        val s = raw.lowercase()
        return s.contains("no speech") || s.contains("no valid speech") || s.contains("silence") ||
            s.contains("too short") || s.contains("empty audio") || s.contains("静音") || s.contains("无语音")
    }

    /** 把原始错误翻译成结论，并保留服务端返回的原文，便于定位。 */
    private fun friendlyError(raw: String): String {
        val code = raw.substringAfter("HTTP ", "").substringBefore("|").trim()
        val body = raw.substringAfter("|", "").trim()
        val head = when {
            code == "401" -> "API Key 无效或未授权（HTTP 401）"
            code == "403" -> "无权限、账号未实名或余额不足（HTTP 403）"
            code == "404" -> "接口地址不正确，应填到 /v1 为止（HTTP 404）"
            code == "429" -> "请求过于频繁，稍后再试（HTTP 429）"
            code.startsWith("5") -> "服务端错误（HTTP $code），稍后重试"
            raw.contains("Unable to resolve host") -> "域名解析失败，请检查手机网络"
            raw.contains("Failed to connect") || raw.contains("connect timed out") -> "建立连接超时，手机网络可能无法访问该域名"
            raw.contains("Read timed out") || raw.contains("timed out") -> "服务器响应超时（网络能连通但没及时返回）"
            code.isNotBlank() -> "服务返回 HTTP $code"
            else -> raw
        }
        return "✗ $head" + if (body.isNotBlank()) "\n服务返回：$body" else ""
    }

    companion object {
        private const val TAG = "CloudVoiceInput"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK = 3200
        private const val TEST_TIMEOUT_MS = 30000
        private const val PARTIAL_TIMEOUT_MS = 20000
        private const val FINAL_TIMEOUT_MS = 60000
        /** 分块伪流式：每隔多久把已录音频送一次。 */
        private const val PARTIAL_INTERVAL_MS = 1500L
        /** 至少录到约 1.2 秒才开始分块，太短识别不出东西。 */
        private const val MIN_BYTES_FOR_PARTIAL = SAMPLE_RATE * 2 * 6 / 5
    }
}
