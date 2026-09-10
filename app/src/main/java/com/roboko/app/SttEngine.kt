package com.roboko.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent

/** 语音识别引擎。SYSTEM 为 Android 官方 API，其余为 Vosk 离线模型。 */
enum class SttEngine(val label: String, val detail: String) {
    SYSTEM("系统识别（默认）", "调用 Android 官方语音识别，支持中英混说，需要设备语音服务"),
    VOSK_CN("Vosk 离线 · 中文", "完全离线，仅中文，不依赖系统语音服务"),
    VOSK_EN("Vosk 离线 · 英文", "完全离线，仅英文，不依赖系统语音服务"),
    VOSK_BOTH("Vosk 离线 · 中英双引擎", "离线同时跑中英模型，按段择优；流畅混说效果有限")
}

/** 语音识别控制器统一接口，供语音按钮调用。 */
interface SttController {
    /** 引擎是否已就绪可用（系统识别恒为可用或不可用）。 */
    val isReady: Boolean
    /** 是否正在加载（Vosk 需要加载模型）。 */
    val isLoading: Boolean
    /** 准备引擎，就绪或失败后回调。重复调用安全。 */
    fun prepare(onReady: () -> Unit, onError: (String) -> Unit)
    /** 开始识别，返回是否成功启动。 */
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit): Boolean
    /** 停止识别，收尾后回调 onFinal。 */
    fun stop()
    /** 释放资源。 */
    fun shutdown()
}

/**
 * Android 官方 SpeechRecognizer 封装。
 *
 * - 每次识别都用全新实例，规避 ERROR_CLIENT(5) / ERROR_RECOGNIZER_BUSY(8) 卡死。
 * - 保留最近一次 partial 结果，最终结果为空时兜底，避免已识别文字被丢弃。
 * - 回调本身就在主线程。
 */
class SystemVoiceInput(private val context: Context) : SttController {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var lastText = ""

    override val isReady: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)
    override val isLoading: Boolean get() = false

    override fun prepare(onReady: () -> Unit, onError: (String) -> Unit) {
        if (isReady) main.post(onReady) else main.post { onError("当前设备没有可用的系统语音识别服务，请在设置中改用 Vosk 离线识别") }
    }

    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("当前设备没有可用的系统语音识别服务")
            return false
        }
        dispose()
        lastText = ""
        return try {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.takeIf { it.isNotBlank() } ?: lastText
                    if (best.isNotBlank()) onFinal(best) else onError("没有识别到内容，请再试一次")
                    lastText = ""
                    dispose()
                }

                override fun onError(error: Int) {
                    if (lastText.isNotBlank() && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        onFinal(lastText); lastText = ""; dispose(); return
                    }
                    lastText = ""
                    onError(errorText(error))
                    dispose()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { lastText = it; onPartial(it) }
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            true
        } catch (e: Exception) {
            onError(e.message ?: "无法启动系统语音识别")
            dispose()
            false
        }
    }

    override fun stop() { try { recognizer?.stopListening() } catch (_: Exception) {} }

    override fun shutdown() { dispose() }

    private fun dispose() {
        val r = recognizer
        recognizer = null
        r?.let { old -> main.post { try { old.destroy() } catch (_: Exception) {} } }
    }

    private fun errorText(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到内容，请再试一次"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络异常"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，请重试"
        SpeechRecognizer.ERROR_CLIENT -> "系统识别器异常，可在设置中改用 Vosk 离线识别"
        else -> "语音识别失败（错误码 $error）"
    }
}
