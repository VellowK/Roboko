package com.roboko.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemCodecTest {
    private val source = "The beauty of the sunset is ephemeral. It fades within minutes."
    private val payload = """{
        "headword":"ephemeral", "displayForm":"ephemeral", "phoneticUs":"/ɪˈfemərəl/", "phoneticUk":"",
        "meanings":[{"label":"短暂的", "partOfSpeech":"adjective", "englishDefinition":"lasting a short time",
        "chineseDefinition":"持续很短时间的", "contexts":[
        {"english":"The beauty of the sunset is ephemeral. It fades within minutes.","chinese":"日落的美是短暂的，几分钟内就消逝了。","representative":true},
        {"english":"Fashion trends are ephemeral.","chinese":"时尚潮流是短暂的。","representative":true}]}], "categories":["形容词"]
    }"""

    @Test fun contextsAndTranslationsSurvivePersistentRoundTrip() {
        val item = ItemCodec.decodeGenerated(payload, "ephemeral", listOf(source))
        assertEquals(item, ItemCodec.decode(ItemCodec.encode(item)))
        assertEquals(2, item.meanings.single().contexts.size)
        assertEquals(source, item.meanings.single().contexts.first().english)
        assertEquals("日落的美是短暂的，几分钟内就消逝了。", item.meanings.single().contexts.first().chinese)
        assertEquals(1, item.meanings.single().contexts.count { it.representative })
        assertNull(item.phoneticUk)
    }

    @Test fun acceptsFencedJsonAndAssignsRepresentativeWhenOmitted() {
        val json = JSONObject(payload)
        val contexts = json.getJSONArray("meanings").getJSONObject(0).getJSONArray("contexts")
        for (i in 0 until contexts.length()) contexts.getJSONObject(i).remove("representative")
        val item = ItemCodec.decodeGenerated("```json\n$json\n```", "ephemeral")
        assertTrue(item.meanings.single().contexts.first().representative)
        assertEquals(1, item.meanings.single().contexts.count { it.representative })
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsMissingContexts() {
        val json = JSONObject(payload)
        json.getJSONArray("meanings").getJSONObject(0).remove("contexts")
        ItemCodec.decodeGenerated(json.toString(), "ephemeral")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsMissingFullTranslation() {
        val json = JSONObject(payload)
        json.getJSONArray("meanings").getJSONObject(0).getJSONArray("contexts").getJSONObject(0).put("chinese", " ")
        ItemCodec.decodeGenerated(json.toString(), "ephemeral")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsReplacingSourceWithInventedSentence() {
        ItemCodec.decodeGenerated(payload, "ephemeral", listOf("This ephemeral moment matters to me."))
    }

    @Test fun wholeMessageAdditionValidatesAfterHeadwordExtraction() {
        val full = "ephemeral 表示短暂的。例句：The beauty of the sunset is ephemeral. It fades within minutes."
        val item = ItemCodec.decodeGenerated(payload, full)
        val required = LearningText.sourceContexts(item.headword, full)
        assertFalse(required.isEmpty())
        // 混合中文讲解中的例句按句提取，英文段落则整体保存；让模型输出同样提取出的原文。
        val json = JSONObject(payload)
        json.getJSONArray("meanings").getJSONObject(0).getJSONArray("contexts").getJSONObject(0).put("english", required.first())
        ItemCodec.validateSourceContexts(ItemCodec.decodeGenerated(json.toString(), full), required)
    }

    @Test fun invalidStoredRecordFailsSafely() {
        assertNull(ItemCodec.decode("not json"))
    }
}
