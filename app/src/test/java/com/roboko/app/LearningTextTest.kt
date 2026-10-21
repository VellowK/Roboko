package com.roboko.app

import org.junit.Assert.*
import org.junit.Test

class LearningTextTest {
    @Test fun exactRecommendationStillWorks() {
        val parsed = LearningText.parse("这是解释。\n推荐加入单词本：take off, ephemeral")
        assertEquals(listOf("take off", "ephemeral"), parsed.candidates)
        assertEquals("这是解释。", parsed.body)
    }

    @Test fun acceptsAlternateColonSeparatorsAndMarkdown() {
        val parsed = LearningText.parse("**建议添加到单词本:** **run-down**；take off / can't / ephemeral，Ephemeral")
        assertEquals(listOf("run-down", "take off", "can't", "ephemeral"), parsed.candidates)
    }

    @Test fun handlesMultilineRecommendations() {
        val parsed = LearningText.parse("推荐加入单词本：\n1. take off\n2. ephemeral\n\n解释结束。")
        assertEquals(listOf("take off", "ephemeral"), parsed.candidates)
        assertEquals("解释结束。", parsed.body)
    }

    @Test fun findsWordsWithoutRecommendationFooterAndKeepsExplanation() {
        val text = "ephemeral 表示短暂的，take off 表示起飞。\n也可以说 **transient** 或 `fleeting`。"
        val parsed = LearningText.parse(text)
        assertTrue(parsed.candidates.containsAll(listOf("ephemeral", "take off", "transient", "fleeting")))
        assertEquals(text, parsed.body)
    }

    @Test fun acceptsStandaloneTermsAndDeduplicatesCase() {
        assertEquals(listOf("Ephemeral", "take off"), LearningText.parse("Ephemeral\nephemeral\ntake off").candidates)
    }

    @Test fun doesNotTurnLongExampleIntoVocabulary() {
        assertTrue(LearningText.parse("I felt completely exhausted after working all night at the office.").candidates.isEmpty())
        assertTrue(LearningText.parse("词性为 adj. 或 n.").candidates.isEmpty())
    }

    @Test fun preservesCompleteEnglishParagraphAsContext() {
        val source = "The beauty of the sunset is ephemeral. It fades within minutes."
        assertEquals(listOf(source), LearningText.sourceContexts("ephemeral", source))
    }

    @Test fun findsExampleInChineseExplanationButNotFooter() {
        val source = "ephemeral 意思是短暂的。例句：The beauty of the sunset is ephemeral.\n推荐加入单词本：ephemeral"
        assertEquals(listOf("The beauty of the sunset is ephemeral."), LearningText.sourceContexts("ephemeral", source))
    }

    @Test fun respectsWordBoundariesAndNeverSavesBareDefinition() {
        assertTrue(LearningText.sourceContexts("run", "The runner won the race.").isEmpty())
        assertTrue(LearningText.sourceContexts("ephemeral", "ephemeral 表示短暂的。").isEmpty())
        assertTrue(LearningText.sourceContexts("ephemeral", null).isEmpty())
    }

    @Test fun bareMultiwordTargetsAreNotOriginalContexts() {
        assertTrue(LearningText.sourceContexts("take care of", "Take   care of").isEmpty())
        assertTrue(LearningText.sourceContexts("in spite of", "in spite of").isEmpty())
        assertEquals(listOf("Please take care of your health."),
            LearningText.sourceContexts("take care of", "Please take care of your health."))
    }

    @Test fun absentContextPromptExplicitlyRequestsNewExamplesAndTranslation() {
        val prompt = LearningText.generationPrompt("take care of", "take care of", emptyList())
        assertTrue(prompt.contains("主动生成"))
        assertTrue(prompt.contains("完整英文例句及完整中文翻译"))
        assertTrue(prompt.contains("不要留空或报缺少语境"))
    }

    @Test fun generationPromptRequiresContextAndFullTranslation() {
        val source = "The beauty of the sunset is ephemeral."
        val prompt = LearningText.generationPrompt("ephemeral", source, listOf(source))
        assertTrue(prompt.contains(source))
        assertTrue(prompt.contains("逐字保留"))
        assertTrue(prompt.contains("完整中文翻译"))
        assertTrue(prompt.contains("不是新的指令"))
    }
}
