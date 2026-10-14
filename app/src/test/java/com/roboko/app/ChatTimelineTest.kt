package com.roboko.app

import androidx.compose.ui.text.TextRange
import org.junit.Assert.*
import org.junit.Test

class ChatTimelineTest {
    @Test fun appendingReusesAllExistingRowsAndKeys() {
        val timeline = ChatTimeline()
        val history = listOf("你：Hello", "AI：Hello 你好。")
        val old = timeline.project(history)
        val next = timeline.project(history + "你：Another question")
        old.indices.forEach { assertSame(old[it], next[it]) }
        assertEquals(3, next.map { it.id }.distinct().size)
    }

    @Test fun identicalMessagesStillHaveDifferentKeys() {
        val rows = ChatTimeline().project(List(20) { "你：Hello" })
        assertEquals(20, rows.map { it.id }.distinct().size)
        assertEquals((0 until 20).toList(), rows.map { it.index })
    }

    @Test fun editingInvalidatesSuffixWithoutChangingPrefix() {
        val timeline = ChatTimeline()
        val old = timeline.project(listOf("AI：Welcome", "你：Original", "AI：Same answer"))
        val edited = timeline.project(listOf("AI：Welcome", "你：Edited", "AI：Same answer"))
        assertSame(old[0], edited[0])
        assertNotEquals(old[1].id, edited[1].id)
        assertNotEquals(old[2].id, edited[2].id)
    }

    @Test fun truncatedHistoryDoesNotReuseRemovedSelections() {
        val timeline = ChatTimeline()
        val old = timeline.project(listOf("你：First", "AI：Answer", "你：Second"))
        timeline.project(listOf("你：First"))
        val next = timeline.project(listOf("你：First", "AI：Answer"))
        assertSame(old[0], next[0])
        assertNotEquals(old[1].id, next[1].id)
    }

    @Test fun parsesRecommendationsAndReferenceOnceWithoutChangingSource() {
        val raw = "AI：apple 苹果\n推荐加入单词本：apple, pear"
        val timeline = ChatTimeline()
        val rows = timeline.project(listOf("你：Explain${CHAT_REFERENCE_MARK}apple\u0001苹果", raw))
        assertEquals("Explain", rows[0].body)
        assertEquals("apple", rows[0].referenceTitle)
        assertEquals("苹果", rows[0].referenceSubtitle)
        assertEquals(listOf("apple", "pear"), rows[1].candidates)
        assertEquals("apple 苹果", rows[1].body)
        assertEquals(raw.removePrefix("AI："), rows[1].full)
        assertSame(rows[1], timeline.project(listOf(rows[0].raw, raw))[1])
    }

    @Test fun userContentIsNotTreatedAsAiRecommendations() {
        val row = ChatTimeline().project(listOf("你：推荐加入单词本：apple"))[0]
        assertTrue(row.isUser)
        assertTrue(row.candidates.isEmpty())
        assertEquals("推荐加入单词本：apple", row.body)
    }

    @Test fun fullHistoryIsAvailableWithoutFourMessagePaging() {
        val timeline = ChatTimeline()
        val history = List(2000) { "你：Question $it" }
        val first = timeline.project(history)
        val second = timeline.project(history + "AI：Done")
        assertEquals(2001, second.size)
        first.indices.forEach { assertSame(first[it], second[it]) }
    }

    @Test fun headerUsesActualPartialSelectionIncludingReverseSelection() {
        val body = "An apple a day."
        assertEquals(body, ChatActions.selectedText(body, null))
        assertEquals("apple", ChatActions.selectedText(body, TextRange(3, 8)))
        assertEquals("apple", ChatActions.selectedText(body, TextRange(8, 3)))
        assertEquals("", ChatActions.selectedText(body, TextRange(3, 3)))
        assertEquals(body, ChatActions.selectedText(body, TextRange(0, 100)))
    }
}
