package com.roboko.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal object ItemCodec {
    fun encode(item: LearningItem): String {
        val meanings = JSONArray().apply {
            item.meanings.forEach { meaning ->
                put(JSONObject().put("id", meaning.id).put("order", meaning.index).put("label", meaning.label)
                    .put("partOfSpeech", meaning.partOfSpeech).put("englishDefinition", meaning.englishDefinition)
                    .put("chineseDefinition", meaning.chineseDefinition).put("progress", meaning.progress)
                    .put("contexts", JSONArray().apply {
                        meaning.contexts.forEach { context ->
                            put(JSONObject().put("id", context.id).put("english", context.english).put("chinese", context.chinese)
                                .put("createdAt", context.createdAt.toString()).put("representative", context.representative))
                        }
                    }))
            }
        }
        return JSONObject().put("id", item.id).put("headword", item.headword).put("displayForm", item.displayForm)
            .put("phoneticUs", item.phoneticUs).put("phoneticUk", item.phoneticUk).put("createdAt", item.createdAt.toString())
            .put("deletedAt", item.deletedAt?.toString()).put("meanings", meanings).put("categories", JSONArray(item.categories)).toString()
    }

    fun decode(payload: String): LearningItem? = try {
        val o = JSONObject(payload)
        val meaningsJson = o.getJSONArray("meanings")
        val meanings = (0 until meaningsJson.length()).map { m ->
            val x = meaningsJson.getJSONObject(m)
            val contexts = x.getJSONArray("contexts")
            MeaningBranch(x.getString("id"), x.getInt("order"), x.getString("label"), x.getString("partOfSpeech"),
                x.getString("englishDefinition"), x.getString("chineseDefinition"), x.getDouble("progress"),
                (0 until contexts.length()).map { c ->
                    val y = contexts.getJSONObject(c)
                    ContextEntry(y.getString("id"), y.getString("english"), y.getString("chinese"),
                        Instant.parse(y.getString("createdAt")), y.getBoolean("representative"))
                })
        }
        val cats = o.getJSONArray("categories")
        LearningItem(o.getString("id"), o.getString("headword"), o.optString("displayForm", o.getString("headword")),
            o.nullableString("phoneticUs"), o.nullableString("phoneticUk"), Instant.parse(o.getString("createdAt")),
            o.nullableString("deletedAt")?.let(Instant::parse), meanings, (0 until cats.length()).map { cats.getString(it) })
    } catch (_: Exception) { null }

    fun decodeGenerated(payload: String, fallbackWord: String, requiredContexts: List<String> = emptyList()): LearningItem {
        val start = payload.indexOf('{')
        val end = payload.lastIndexOf('}')
        require(start >= 0 && end >= start) { "AI 未返回有效词条 JSON，请重试" }
        val o = JSONObject(payload.substring(start, end + 1))
        val word = o.optString("headword", fallbackWord).trim().ifBlank { fallbackWord }
        val meaningsJson = o.optJSONArray("meanings") ?: throw IllegalArgumentException("AI 未返回词义")
        val now = Instant.now()
        val meanings = (0 until meaningsJson.length()).map { index ->
            val m = meaningsJson.getJSONObject(index)
            val contextsJson = m.optJSONArray("contexts") ?: throw IllegalArgumentException("AI 未返回英文语境和中文翻译，请重新整理")
            val contexts = (0 until contextsJson.length()).map { c ->
                val x = contextsJson.getJSONObject(c)
                val english = x.optString("english", "").trim()
                val chinese = x.optString("chinese", "").trim()
                require(english.isNotBlank() && chinese.isNotBlank()) { "AI 返回的语境或完整中文翻译为空，请重新整理" }
                ContextEntry(UUID.randomUUID().toString(), english, chinese, now, x.optBoolean("representative", false))
            }
            require(contexts.isNotEmpty()) { "AI 未返回英文语境和中文翻译，请重新整理" }
            val representative = contexts.indexOfFirst { it.representative }.coerceAtLeast(0)
            MeaningBranch(UUID.randomUUID().toString(), index + 1, m.optString("label", "主要含义"),
                m.optString("partOfSpeech", "phrase"), m.optString("englishDefinition", ""), m.optString("chineseDefinition", ""),
                0.0, contexts.mapIndexed { c, context -> context.copy(representative = c == representative) })
        }
        require(meanings.isNotEmpty()) { "AI 未返回有效词义" }
        val categories = o.optJSONArray("categories") ?: JSONArray().put("待整理")
        return LearningItem(UUID.randomUUID().toString(), word, o.optString("displayForm", word),
            o.nullableString("phoneticUs"), o.nullableString("phoneticUk"), now, null, meanings,
            (0 until categories.length()).map { categories.getString(it) }).also { validateSourceContexts(it, requiredContexts) }
    }

    fun validateSourceContexts(item: LearningItem, requiredContexts: List<String>) {
        val returnedContexts = item.meanings.flatMap { it.contexts }.map { it.english }
        require(requiredContexts.all { source -> returnedContexts.any { it == source } }) {
            "AI 未保留原始英文语境，请重新整理；原文尚未保存"
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotBlank() }
}
