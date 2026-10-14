package com.roboko.app

import androidx.compose.runtime.Immutable
import java.util.Locale

/** Immutable, lightweight row data: scrolling does not need the full meaning/context graph. */
@Immutable
internal data class LibraryEntry(val id: String, val displayForm: String, val label: String, val progress: Double)

internal object LearningLibrary {
    fun active(items: List<LearningItem>): List<LearningItem> = items.filter { it.deletedAt == null }

    fun recent(items: List<LearningItem>, limit: Int = 3): List<LearningItem> =
        active(items).sortedByDescending { it.createdAt }.take(limit)

    fun sorted(items: List<LearningItem>, query: String, sort: LibrarySort, descending: Boolean): List<LearningItem> {
        val filtered = active(items).let { active ->
            if (query.isBlank()) active else active.filter { item ->
                item.headword.contains(query, true) || item.meanings.any { meaning ->
                    meaning.label.contains(query, true) || meaning.chineseDefinition.contains(query, true) ||
                        meaning.contexts.any { it.english.contains(query, true) || it.chinese.contains(query, true) }
                }
            }
        }
        val sorted = when (sort) {
            LibrarySort.ADDED -> filtered.sortedBy { it.createdAt }
            LibrarySort.ALPHABETICAL -> filtered.sortedBy { it.headword.lowercase(Locale.ROOT) }
            LibrarySort.MASTERY -> filtered.sortedBy(::progress)
        }
        return if (descending) sorted.asReversed() else sorted
    }

    fun entry(item: LearningItem) = LibraryEntry(item.id, item.displayForm, item.meanings.firstOrNull()?.label ?: "待整理", progress(item))

    private fun progress(item: LearningItem) = if (item.meanings.isEmpty()) 0.0 else item.meanings.sumOf { it.progress } / item.meanings.size
}

/** Lists are read-only snapshots; no mutable collection escapes the projection. */
@Immutable
internal data class LibraryPage(
    val entries: List<LibraryEntry> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.ADDED,
    val descending: Boolean = true
)

/** Rebuild search fields/sort keys only after data changes, never while measuring a row. */
internal class LibraryProjection {
    private data class Indexed(val entry: LibraryEntry, val added: java.time.Instant, val alphabetical: String, val fields: List<String>)
    private var source: List<LearningItem>? = null
    private var orders: Map<LibrarySort, List<Indexed>> = emptyMap()
    private var total = 0

    fun project(items: List<LearningItem>, query: String, sort: LibrarySort, descending: Boolean): LibraryPage {
        if (source !== items) {
            val indexed = LearningLibrary.active(items).map { item ->
                val fields = buildList {
                    add(item.headword)
                    item.meanings.forEach { meaning ->
                        add(meaning.label); add(meaning.chineseDefinition)
                        meaning.contexts.forEach { add(it.english); add(it.chinese) }
                    }
                }.map { it.lowercase(Locale.ROOT) }
                Indexed(LearningLibrary.entry(item), item.createdAt, item.headword.lowercase(Locale.ROOT), fields)
            }
            orders = mapOf(
                LibrarySort.ADDED to indexed.sortedBy { it.added },
                LibrarySort.ALPHABETICAL to indexed.sortedBy { it.alphabetical },
                LibrarySort.MASTERY to indexed.sortedBy { it.entry.progress }
            )
            total = indexed.size
            source = items
        }
        val ordered = orders.getValue(sort).let { if (descending) it.asReversed() else it }
        val needle = query.lowercase(Locale.ROOT)
        val matches = if (query.isBlank()) ordered else ordered.filter { row -> row.fields.any { it.contains(needle) } }
        return LibraryPage(matches.map { it.entry }, total, query, sort, descending)
    }
}
