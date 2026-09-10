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
        "mimo", "https://api.mimo.mi.com/v1", "", "mimo-v2.5-asr",
        "https://mimo.mi.com/", SttApiFormat.CHAT_AUDIO
    ),
    SttCloudConfig(
        "groq", "https://api.groq.com/openai/v1", "", "whisper-large-v3-turbo",
        "https://console.groq.com/keys", SttApiFormat.OPENAI_TRANSCRIPTION
    ),
    SttCloudConfig(
        "openai", "https://api.openai.com/v1", "", "whisper-1",
        "https://platform.openai.com/api-keys", SttApiFormat.OPENAI_TRANSCRIPTION
    ),
    // 火山走的是私有 WebSocket 协议（大模型流式语音识别），不兼容以上两种接口，
    // 这里仅用于展示申请入口，实际接入待后续实现。
    SttCloudConfig("volcano", "", "", "", "https://console.volcengine.com/speech/app", SttApiFormat.OPENAI_TRANSCRIPTION)
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
            val wav = File(context.cacheDir, "stt-upload.wav")
            if (wav.exists()) wav.delete()
            val out = RandomAccessFile(wav, "rw")
            out.setLength(0)
            out.write(ByteArray(44))   // 先占位 WAV 头，收尾时回填长度
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
            worker = Thread {
                val buf = ByteArray(CHUNK)
                var pcmBytes = 0L
                try {
                    record.startRecording()
                    while (running) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        out.write(buf, 0, n)
                        pcmBytes += n
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
                    if (pcmBytes < SAMPLE_RATE) {   // 少于约 0.5 秒视为没说话
                        main.post { onError("录音太短，请再试一次"); onFinal("") }
                        return@Thread
                    }
                    scope.launch {
                        val result = runCatching { transcribe(wav, config) }
                        main.post {
                            val text = result.getOrNull()
                            if (!text.isNullOrBlank()) onFinal(text)
                            else {
                                onError(result.exceptionOrNull()?.message ?: "没有识别到内容")
                                onFinal("")
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

    private fun transcribe(wav: File, config: SttCloudConfig): String = when (config.format) {
        SttApiFormat.OPENAI_TRANSCRIPTION -> transcribeMultipart(wav, config)
        SttApiFormat.CHAT_AUDIO -> transcribeChatAudio(wav, config)
    }

    /** OpenAI 风格：multipart 上传到 /audio/transcriptions。 */
    private fun transcribeMultipart(wav: File, config: SttCloudConfig): String {
        val boundary = "----RobokoBoundary" + System.currentTimeMillis()
        val url = URL(config.baseUrl.trimEnd('/') + "/audio/transcriptions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
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
            if (code !in 200..299) throw IllegalStateException("转写失败（HTTP $code）${body.take(160)}")
            return JSONObject(body).optString("text", "").trim()
        } finally {
            conn.disconnect()
            try { wav.delete() } catch (_: Exception) {}
        }
    }

    /** 对话式音频输入：base64 音频放进 messages 打到 /chat/completions（小米 MiMo ASR）。 */
    private fun transcribeChatAudio(wav: File, config: SttCloudConfig): String {
        val base64Audio = Base64.encodeToString(wav.readBytes(), Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().put(JSONObject().apply {
                    put("type", "input_audio")
                    put("input_audio", JSONObject().put("data", "data:audio/wav;base64,$base64Audio"))
                }))
            }))
            put("asr_options", JSONObject().put("language", "auto"))
        }
        val conn = (URL(config.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 120000
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
            if (code !in 200..299) throw IllegalStateException("转写失败（HTTP $code）${body.take(160)}")
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
        val wav = File(context.cacheDir, "stt-test.wav")
        try {
            writeSilentWav(wav, 1200)
            val text = transcribe(wav, config)
            if (text.isBlank()) "✓ 配置可用：服务已正常响应（静音音频无文字属正常）"
            else "✓ 配置可用，识别到：$text"
        } catch (e: Exception) {
            friendlyError(e.message ?: "测试失败")
        } finally {
            try { wav.delete() } catch (_: Exception) {}
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

    private fun friendlyError(raw: String): String = when {
        raw.contains("401") -> "✗ API Key 无效或未授权（HTTP 401）"
        raw.contains("403") -> "✗ 无权限或余额不足（HTTP 403）"
        raw.contains("404") -> "✗ 接口地址不正确，应填到 /v1 为止（HTTP 404）"
        raw.contains("429") -> "✗ 请求过于频繁，稍后再试（HTTP 429）"
        raw.contains("Unable to resolve host") || raw.contains("Failed to connect") || raw.contains("timeout") -> "✗ 网络不通，请检查网络或代理"
        else -> "✗ $raw"
    }

    companion object {
        private const val TAG = "CloudVoiceInput"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK = 3200
    }
}
