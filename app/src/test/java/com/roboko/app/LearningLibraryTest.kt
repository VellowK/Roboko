package com.roboko.app

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LearningLibraryTest {
    private fun item(id: String, added: String, progress: Double = 0.0, deleted: Boolean = false): LearningItem {
        val instant = Instant.parse(added)
        val context = ContextEntry("context-$id", "A useful example for $id.", "用于测试搜索的完整语境。", instant, true)
        return LearningItem(id, id, id, null, null, instant, if (deleted) instant else null,
            listOf(MeaningBranch("meaning-$id", 1, "含义", "noun", "definition", "释义", progress, listOf(context))), emptyList())
    }

    @Test fun recentUsesCreationTimeNotInputOrder() {
        val old = item("old", "2020-01-01T00:00:00Z")
        val newest = item("new", "2026-01-03T00:00:00Z")
        val middle = item("middle", "2026-01-02T00:00:00Z")
        assertEquals(listOf("new", "middle", "old"), LearningLibrary.recent(listOf(old, newest, middle)).map { it.id })
        assertEquals(listOf("new"), LearningLibrary.recent(listOf(old, newest), 1).map { it.id })
    }

    @Test fun reviewingAnOldItemDoesNotMakeItRecent() {
        val old = item("old", "2020-01-01T00:00:00Z", 5.5)
        val newest = item("new", "2026-01-03T00:00:00Z")
        assertEquals("new", LearningLibrary.recent(listOf(old, newest)).first().id)
    }

    @Test fun countAndRecentExcludeDeletedItems() {
        val kept = item("kept", "2026-01-01T00:00:00Z")
        val removed = item("removed", "2026-01-04T00:00:00Z", deleted = true)
        assertEquals(1, LearningLibrary.active(listOf(kept, removed)).size)
        assertEquals(listOf(kept), LearningLibrary.recent(listOf(removed, kept)))
    }

    @Test fun searchMatchesContextWithoutChangingTotal() {
        val items = listOf(item("apple", "2026-01-01T00:00:00Z"), item("zebra", "2026-01-02T00:00:00Z"))
        assertEquals(listOf("apple"), LearningLibrary.sorted(items, "APPLE", LibrarySort.ADDED, true).map { it.id })
        assertEquals(2, LearningLibrary.sorted(items, "完整语境", LibrarySort.ADDED, true).size)
        assertEquals(2, LearningLibrary.active(items).size)
    }

    @Test fun allSortOrdersUseSameRowsAndSupportDirection() {
        val items = listOf(item("zebra", "2026-01-01T00:00:00Z", 5.0), item("apple", "2026-01-02T00:00:00Z", 1.0))
        assertEquals(listOf("apple", "zebra"), LearningLibrary.sorted(items, "", LibrarySort.ADDED, true).map { it.id })
        assertEquals(listOf("apple", "zebra"), LearningLibrary.sorted(items, "", LibrarySort.ALPHABETICAL, false).map { it.id })
        assertEquals(listOf("zebra", "apple"), LearningLibrary.sorted(items, "", LibrarySort.MASTERY, true).map { it.id })
    }

    @Test fun projectionMatchesExistingSortAndSearchContracts() {
        val items = listOf(item("zebra", "2026-01-01T00:00:00Z", 5.0), item("apple", "2026-01-02T00:00:00Z", 1.0), item("deleted", "2026-01-03T00:00:00Z", deleted = true))
        val projection = LibraryProjection()
        for (sort in LibrarySort.entries) for (descending in listOf(true, false)) for (query in listOf("", "APPLE", "完整语境", "missing")) {
            val page = projection.project(items, query, sort, descending)
            assertEquals(LearningLibrary.sorted(items, query, sort, descending).map { it.id }, page.entries.map { it.id })
            assertEquals(2, page.total)
            assertEquals(query, page.query)
            assertEquals(sort, page.sort)
            assertEquals(descending, page.descending)
        }
    }

    @Test fun projectionReusesRowsAcrossSearchAndSorting() {
        val items = listOf(item("apple", "2026-01-01T00:00:00Z"), item("zebra", "2026-01-02T00:00:00Z"))
        val projection = LibraryProjection()
        val all = projection.project(items, "", LibrarySort.ADDED, false)
        val filtered = projection.project(items, "apple", LibrarySort.ALPHABETICAL, true)
        assertSame(all.entries[0], filtered.entries[0])
    }

    @Test fun projectionInvalidatesProgressAndDeletedItemsAfterDataChanges() {
        val original = listOf(item("apple", "2026-01-01T00:00:00Z"), item("zebra", "2026-01-02T00:00:00Z"))
        val projection = LibraryProjection()
        val old = projection.project(original, "", LibrarySort.MASTERY, true)
        val changed = listOf(item("apple", "2026-01-01T00:00:00Z", 4.0), item("zebra", "2026-01-02T00:00:00Z", deleted = true))
        val next = projection.project(changed, "", LibrarySort.MASTERY, true)
        assertEquals(1, next.total)
        assertEquals(4.0, next.entries.single().progress, 0.0)
        assertEquals(2, old.total)
        assertEquals(0.0, old.entries.first().progress, 0.0)
    }

    @Test fun projectionKeepsFullLargeLibraryAndSearchesContexts() {
        val items = List(2000) { item("word-$it", "2026-01-01T00:00:00Z") }
        val projection = LibraryProjection()
        val all = projection.project(items, "", LibrarySort.ADDED, true)
        val filtered = projection.project(items, "example for word-1999.", LibrarySort.ADDED, true)
        assertEquals(2000, all.entries.size)
        assertEquals(2000, filtered.total)
        assertEquals("word-1999", filtered.entries.single().id)
    }

    @Test fun lightweightRowsHandleMissingMeaningsWithoutNan() {
        val empty = item("empty", "2026-01-01T00:00:00Z").copy(meanings = emptyList())
        assertEquals(0.0, LearningLibrary.entry(empty).progress, 0.0)
        assertEquals("待整理", LearningLibrary.entry(empty).label)
    }
}
