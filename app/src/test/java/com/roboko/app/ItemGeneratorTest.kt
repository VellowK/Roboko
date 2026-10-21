package com.roboko.app

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemGeneratorTest {
    private val original = "The beauty of the sunset is ephemeral. It fades within minutes."
    private val translation = "日落的美是短暂的，几分钟内就消逝了。"
    private val payload = """{"headword":"ephemeral","meanings":[{"label":"短暂的","contexts":[
        {"english":"Fashion trends are ephemeral.","chinese":"时尚潮流是短暂的。","representative":true}]}]}"""
    private val repair = """{"translations":[{"sourceIndex":0,"meaningIndex":0,"chinese":"日落的美是短暂的，几分钟内就消逝了。"}]}"""

    @Test fun noSourceUsesGeneratedExampleInOneRequest() {
        var calls = 0
        val item = ItemGenerator.generate("ephemeral", null) { prompt ->
            calls++
            assertTrue(prompt.contains("主动生成"))
            payload
        }
        assertEquals(1, calls)
        assertEquals("Fashion trends are ephemeral.", item.meanings.single().contexts.single().english)
    }

    @Test fun recommendationOnlySourceDoesNotRequireAnOriginal() {
        var calls = 0
        ItemGenerator.generate("ephemeral", "推荐加入单词本：ephemeral, transient") { calls++; payload }
        assertEquals(1, calls)
    }

    @Test fun multiwordTargetIsNotMistakenForItsOwnOriginalContext() {
        var calls = 0
        val item = ItemGenerator.generate("take care of", null) {
            calls++
            payload.replace("ephemeral", "take care of")
        }
        assertEquals(1, calls)
        assertNotEquals("take care of", item.meanings.single().contexts.single().english)
    }

    @Test fun alreadyPreservedSourceNeedsNoExtraRequest() {
        var calls = 0
        val json = JSONObject(payload)
        json.getJSONArray("meanings").getJSONObject(0).getJSONArray("contexts").getJSONObject(0)
            .put("english", original).put("chinese", translation)
        val item = ItemGenerator.generate("ephemeral", original) { calls++; "```json\n$json\n```" }
        assertEquals(1, calls)
        assertEquals(original, item.meanings.single().contexts.single().english)
    }

    @Test fun rewrittenOrOmittedSourceIsPreservedLocallyWithFullTranslation() {
        var calls = 0
        val item = ItemGenerator.generate("ephemeral", original) { prompt ->
            calls++
            if (calls == 1) payload else {
                assertTrue(prompt.contains("sourceIndex"))
                assertTrue(prompt.contains(original))
                // No English field is needed: the application, not the model, owns the source text.
                "```json\n$repair\n```"
            }
        }
        val contexts = item.meanings.single().contexts
        assertEquals(2, calls)
        assertEquals(original, contexts.first().english)
        assertEquals(translation, contexts.first().chinese)
        assertTrue(contexts.first().representative)
        assertEquals(1, contexts.count { it.representative })
        assertTrue(contexts.any { it.english == "Fashion trends are ephemeral." })
        assertEquals(item, ItemCodec.decode(ItemCodec.encode(item)))
    }

    @Test fun wholeMessageExtractsHeadwordBeforeRepairingOriginal() {
        val source = "ephemeral 表示短暂的。例句：$original"
        var calls = 0
        val item = ItemGenerator.generate(source, source) { calls++; if (calls == 1) payload else repair }
        assertEquals("ephemeral", item.headword)
        assertEquals(original, item.meanings.single().contexts.first().english)
        assertEquals(2, calls)
    }

    @Test fun multipleSourcesAreMatchedByIndexNotResponseOrderAndCorrectMeaning() {
        val first = "This ephemeral moment matters to me."
        val second = "Their ephemeral fame soon faded away."
        val source = "第一个例句：$first\n第二个例句：$second"
        val json = JSONObject(payload)
        val meanings = json.getJSONArray("meanings")
        meanings.put(JSONObject(meanings.getJSONObject(0).toString()).put("label", "转瞬即逝的"))
        var calls = 0
        val item = ItemGenerator.generate("ephemeral", source) {
            calls++
            if (calls == 1) json.toString() else """{"translations":[
                {"sourceIndex":1,"meaningIndex":1,"chinese":"他们短暂的名气很快就消散了。"},
                {"sourceIndex":0,"meaningIndex":0,"chinese":"这短暂的一刻对我很重要。"}]}"""
        }
        assertEquals(first, item.meanings[0].contexts.first().english)
        assertEquals(second, item.meanings[1].contexts.first().english)
        assertEquals("他们短暂的名气很快就消散了。", item.meanings[1].contexts.first().chinese)
        assertTrue(item.meanings.all { meaning -> meaning.contexts.count { it.representative } == 1 })
    }

    @Test fun missingGeneratedExampleTriggersOneAutomaticRetry() {
        val invalid = JSONObject(payload)
        invalid.getJSONArray("meanings").getJSONObject(0).remove("contexts")
        var calls = 0
        val item = ItemGenerator.generate("ephemeral", null) { prompt ->
            calls++
            if (calls == 1) invalid.toString() else {
                assertTrue(prompt.contains("重新生成完整 JSON"))
                payload
            }
        }
        assertEquals(2, calls)
        assertFalse(item.meanings.single().contexts.single().chinese.isBlank())
    }

    @Test fun missingGeneratedTranslationTriggersOneAutomaticRetry() {
        val invalid = JSONObject(payload)
        invalid.getJSONArray("meanings").getJSONObject(0).getJSONArray("contexts").getJSONObject(0).put("chinese", "")
        var calls = 0
        ItemGenerator.generate("ephemeral", "ephemeral 表示短暂的。") { calls++; if (calls == 1) invalid.toString() else payload }
        assertEquals(2, calls)
    }

    @Test fun invalidJsonRetryIsBounded() {
        var calls = 0
        try {
            ItemGenerator.generate("ephemeral", null) { calls++; "not json" }
            fail("Invalid data must not be saved")
        } catch (_: IllegalArgumentException) {
            assertEquals(2, calls)
        }
    }

    @Test fun networkFailureDoesNotTriggerAnotherPaidRequest() {
        var calls = 0
        try {
            ItemGenerator.generate("ephemeral", null) { calls++; throw IOException("offline") }
            fail("Network failure must propagate")
        } catch (_: IOException) {
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationDoesNotRetryOrReturnAnItem() {
        var calls = 0
        try {
            ItemGenerator.generate("ephemeral", null) { calls++; throw CancellationException("cancelled") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
    }

    @Test fun repairRejectsWrongSourceOrMeaningIndexAndBlankTranslation() {
        for (invalid in listOf(
            repair.replace("\"sourceIndex\":0", "\"sourceIndex\":1"),
            repair.replace("\"meaningIndex\":0", "\"meaningIndex\":-1"),
            repair.replace(translation, " ")
        )) {
            var calls = 0
            try {
                ItemGenerator.generate("ephemeral", original) { calls++; if (calls == 1) payload else invalid }
                fail("Incomplete translations must not be saved")
            } catch (_: IllegalArgumentException) {
                assertEquals(2, calls)
            }
        }
    }

    @Test fun repairRejectsMissingOrDuplicateTranslations() {
        val duplicate = JSONObject(repair)
        val entries = duplicate.getJSONArray("translations")
        entries.put(JSONObject(entries.getJSONObject(0).toString()))
        for (invalid in listOf(JSONObject().put("translations", JSONArray()).toString(), duplicate.toString())) {
            var calls = 0
            try {
                ItemGenerator.generate("ephemeral", original) { calls++; if (calls == 1) payload else invalid }
                fail("Each original must have exactly one full translation")
            } catch (_: IllegalArgumentException) {
                assertEquals(2, calls)
            }
        }
    }
}
