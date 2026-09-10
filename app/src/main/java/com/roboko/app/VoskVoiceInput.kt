package com.roboko.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.max

/**
 * Vosk 离线语音识别（中文 / 英文 / 中英双引擎）。
 *
 * - 自带 AudioRecord 采集（Vosk 的 SpeechService 只支持单个识别器，无法按需喂多路）。
 * - 双引擎模式下，同一路音频同时喂给中英两个 Recognizer，按段用置信度择优。
 * - 任一侧触发端点后两个识别器同时 reset()，保证分段对齐、同一段音频不重复计入。
 * - 已结算片段会累加，停顿不会清空前文。
 * - 模型以 zip 打包在 assets，首次使用时解压到内部存储。
 * - 所有回调切到主线程。
 */
class VoskVoiceInput(private val context: Context) : SttController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    private var cnModel: Model? = null
    private var enModel: Model? = null
    private var cnRec: Recognizer? = null
    private var enRec: Recognizer? = null

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    private val committed = StringBuilder()
    @Volatile private var lastSegment = ""

    private var requested: SttEngine = SttEngine.VOSK_BOTH
    private var loaded: SttEngine? = null
    private var pendingReady: (() -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null

    @Volatile override var isReady = false; private set
    @Volatile override var isLoading = false; private set
    @Volatile var isListening = false; private set

    /** 设置要使用的语种模式（VOSK_CN / VOSK_EN / VOSK_BOTH），需在 prepare 前调用。 */
    fun setEngine(engine: SttEngine) { requested = engine }

    override fun prepare(onReady: () -> Unit, onError: (String) -> Unit) {
        val target = requested
        if (isReady && loaded == target) { main.post(onReady); return }
        pendingReady = onReady
        pendingError = onError
        if (isLoading) return
        isLoading = true
        closeRecognizers()
        val needCn = target == SttEngine.VOSK_CN || target == SttEngine.VOSK_BOTH
        val needEn = target == SttEngine.VOSK_EN || target == SttEngine.VOSK_BOTH
        scope.launch {
            try {
                if (needCn && cnRec == null) {
                    val m = Model(ensureModelExtracted(CN_DIR, CN_ZIP))
                    cnModel = m
                    cnRec = Recognizer(m, SAMPLE_RATE.toFloat()).apply { setWords(true); setPartialWords(true) }
                }
                if (needEn && enRec == null) {
                    val m = Model(ensureModelExtracted(EN_DIR, EN_ZIP))
                    enModel = m
                    enRec = Recognizer(m, SAMPLE_RATE.toFloat()).apply { setWords(true); setPartialWords(true) }
                }
                loaded = target
                isReady = true
                isLoading = false
                main.post { pendingReady?.invoke() }
            } catch (e: Exception) {
                isLoading = false
                isReady = false
                val message = e.message ?: "离线语音模型加载失败"
                main.post { pendingError?.invoke(message) }
            }
        }
    }

    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit): Boolean {
        if (!isReady || (cnRec == null && enRec == null)) return false
        if (isListening) return false
        committed.setLength(0)
        lastSegment = ""
        try { cnRec?.reset() } catch (_: Exception) {}
        try { enRec?.reset() } catch (_: Exception) {}
        return try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, CHUNK * 4))
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onError("无法初始化录音设备")
                return false
            }
            audioRecord = record
            running = true
            isListening = true
            worker = Thread {
                try {
                    record.startRecording()
                    val buf = ByteArray(CHUNK)
                    while (running) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        processChunk(buf, n, onPartial)
                    }
                } catch (e: Exception) {
                    val message = e.message ?: "录音中断"
                    main.post { onError(message) }
                } finally {
                    try {
                        val cnHyp = cnRec?.let { parse(it.finalResult, "text", "result") }
                        val enHyp = enRec?.let { parse(it.finalResult, "text", "result") }
                        pick(cnHyp, enHyp)?.let { commit(it.text) }
                    } catch (_: Exception) {}
                    try { record.stop() } catch (_: Exception) {}
                    try { record.release() } catch (_: Exception) {}
                    audioRecord = null
                    running = false
                    isListening = false
                    val full = committed.toString()
                    main.post { onFinal(full) }
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
            closeRecognizers()
            loaded = null
            isReady = false
        }
    }

    // ---------------------------------------------------------------- 识别核心

    private fun processChunk(buf: ByteArray, n: Int, onPartial: (String) -> Unit) {
        val cn = cnRec
        val en = enRec
        val cnDone = cn?.let { try { it.acceptWaveForm(buf, n) } catch (_: Exception) { false } } ?: false
        val enDone = en?.let { try { it.acceptWaveForm(buf, n) } catch (_: Exception) { false } } ?: false
        if (cnDone || enDone) {
            val cnHyp = if (cnDone) parse(cn!!.result, "text", "result") else null
            val enHyp = if (enDone) parse(en!!.result, "text", "result") else null
            pick(cnHyp, enHyp)?.let { commit(it.text) }
            // 在静音边界让两个识别器同时重新开始，确保分段对齐、不重复计入同一段音频
            try { cn?.reset() } catch (_: Exception) {}
            try { en?.reset() } catch (_: Exception) {}
            val full = committed.toString()
            main.post { onPartial(full) }
        } else {
            val cnHyp = cn?.let { parse(it.partialResult, "partial", "partial_result") }
            val enHyp = en?.let { parse(it.partialResult, "partial", "partial_result") }
            val live = pick(cnHyp, enHyp)?.text
            val full = committed.toString() + (if (live.isNullOrBlank()) "" else joinTail(live))
            main.post { onPartial(full) }
        }
    }

    private fun commit(segment: String) {
        if (segment.isBlank() || segment == lastSegment) return
        committed.append(joinTail(segment))
        lastSegment = segment
    }

    private class Hyp(val text: String, val conf: Double)

    /** 解析 Vosk 的 JSON 结果，取出文本与平均置信度。 */
    private fun parse(json: String?, textKey: String, wordsKey: String): Hyp? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val text = o.optString(textKey, "")
            if (text.isBlank()) return null
            val words = o.optJSONArray(wordsKey)
            var sum = 0.0
            var count = 0
            if (words != null) {
                for (i in 0 until words.length()) {
                    val w = words.optJSONObject(i) ?: continue
                    sum += w.optDouble("conf", 0.5)
                    count++
                }
            }
            Hyp(text, if (count > 0) sum / count else 0.5)
        } catch (_: Exception) { null }
    }

    /** 置信度择优。日志便于实测后调参。 */
    private fun pick(cn: Hyp?, en: Hyp?): Hyp? {
        if (cn == null) return en
        if (en == null) return cn
        Log.d(TAG, "cn(" + cn.conf + ")=" + cn.text + "  |  en(" + en.conf + ")=" + en.text)
        return if (en.conf > cn.conf) en else cn
    }

    // ---------------------------------------------------------------- 资源与工具

    private fun closeRecognizers() {
        try { cnRec?.close() } catch (_: Exception) {}
        try { enRec?.close() } catch (_: Exception) {}
        cnRec = null; enRec = null
        try { cnModel?.close() } catch (_: Exception) {}
        try { enModel?.close() } catch (_: Exception) {}
        cnModel = null; enModel = null
    }

    /** 中文之间不插空格，英文之间插空格。 */
    private fun joinTail(segment: String): String {
        if (committed.isEmpty() || segment.isEmpty()) return segment
        return if (isCjk(committed.last()) || isCjk(segment.first())) segment else " $segment"
    }

    private fun isCjk(c: Char) = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF

    /** 解压 assets 中的模型 zip 到内部存储；已解压则复用。 */
    private fun ensureModelExtracted(dirName: String, zipName: String): String {
        val modelRoot = File(context.filesDir, dirName)
        if (File(modelRoot, "final.mdl").exists() || File(modelRoot, "am/final.mdl").exists()) return modelRoot.absolutePath
        modelRoot.deleteRecursively()
        val base = context.filesDir
        val basePath = base.canonicalPath
        context.assets.open(zipName).use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(base, entry.name)
                    // 防止 zip-slip：只允许写入 filesDir 之内
                    if (!out.canonicalPath.startsWith(basePath)) { entry = zis.nextEntry; continue }
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return modelRoot.absolutePath
    }

    companion object {
        private const val TAG = "VoskVoiceInput"
        private const val CN_DIR = "vosk-model-small-cn-0.3"
        private const val CN_ZIP = "vosk-model-small-cn-0.3.zip"
        private const val EN_DIR = "vosk-model-small-en-us-0.15"
        private const val EN_ZIP = "vosk-model-small-en-us-0.15.zip"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK = 3200
    }
}
