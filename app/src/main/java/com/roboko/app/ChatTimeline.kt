package com.roboko.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

internal const val CHAT_REFERENCE_MARK = "\u0002REF\u0002"

/** Read-only projection, computed off the UI thread and reused when a message is unchanged. */
@Immutable
internal data class ChatMessage(
    val id: Long,
    val index: Int,
    val raw: String,
    val full: String,
    val body: String,
    val isUser: Boolean,
    val referenceTitle: String?,
    val referenceSubtitle: String?,
    val candidates: List<String>
)

/** One collector owns this cache. IDs survive appends; edited suffixes get fresh IDs. */
internal class ChatTimeline {
    private var previous = emptyList<ChatMessage>()
    private var nextId = 0L

    fun project(messages: List<String>): List<ChatMessage> {
        var prefixUnchanged = true
        val result = messages.mapIndexed { index, raw ->
            val old = previous.getOrNull(index)
            prefixUnchanged = prefixUnchanged && old?.raw == raw
            if (prefixUnchanged) old!! else {
                val user = raw.startsWith("你：")
                val full = raw.substringBefore(CHAT_REFERENCE_MARK).removePrefix("你：").removePrefix("AI：")
                val reference = if (raw.contains(CHAT_REFERENCE_MARK)) raw.substringAfter(CHAT_REFERENCE_MARK) else null
                val parsed = if (user) ParsedLearningText(full, emptyList()) else LearningText.parse(full)
                ChatMessage(nextId++, index, raw, full, parsed.body.ifBlank { full }, user,
                    reference?.substringBefore('\u0001'), reference?.substringAfter('\u0001', ""), parsed.candidates.toList())
            }
        }
        previous = result
        return result
    }
}

@Immutable
internal data class ChatSelection(val messageId: Long, val range: TextRange? = null)

internal object ChatActions {
    /** Null means the whole message. A collapsed range must not silently act on the whole message. */
    fun selectedText(body: String, range: TextRange?): String {
        if (range == null) return body
        return body.substring(range.min.coerceIn(0, body.length), range.max.coerceIn(0, body.length))
    }
}
