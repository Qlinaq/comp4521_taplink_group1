package com.taplink.app.data.support

import com.taplink.app.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SupportAssistantTest {
    private val noonHkt: Long = LocalDate.of(2026, 11, 23)
        .atTime(12, 0).atZone(ZoneId.of("Asia/Hong_Kong")).toInstant().toEpochMilli()

    @Test fun quotaStopsAtTheDailyLimit() {
        val quota = SupportQuota(InMemoryUsageStore(), dailyLimit = 2) { noonHkt }
        assertEquals(2, quota.remaining())
        assertTrue(quota.tryConsume())
        assertTrue(quota.tryConsume())
        assertFalse(quota.tryConsume())
        assertEquals(0, quota.remaining())
    }

    @Test fun quotaResetsAtHongKongMidnight() {
        val store = InMemoryUsageStore()
        val firstDay = SupportQuota(store, dailyLimit = 1) { noonHkt }
        assertTrue(firstDay.tryConsume())
        assertFalse(firstDay.tryConsume())
        // 00:30 the next Hong Kong day.
        val nextDay = SupportQuota(store, dailyLimit = 1) { noonHkt + 12 * 60 * 60 * 1000L }
        assertEquals(1, nextDay.remaining())
        assertTrue(nextDay.tryConsume())
    }

    @Test fun historyKeepsRecentTurnsAndDropsALeadingAssistantTurn() {
        val turns = (1..12).map { SupportTurn(fromUser = it % 2 == 1, text = "turn $it") }
        val kept = SupportPrompt.history(turns)
        assertTrue(kept.size <= SupportPrompt.MAX_TURNS)
        assertEquals(listOf("turn 5", "turn 6", "turn 7", "turn 8", "turn 9", "turn 10", "turn 11", "turn 12"), kept.map { it.text })
        assertTrue(kept.first().fromUser)
    }

    @Test fun historyTruncatesLongTurnsAndEmptyInput() {
        val long = "x".repeat(5_000)
        assertEquals(SupportPrompt.MAX_TURN_CHARS, SupportPrompt.history(listOf(SupportTurn(true, long))).single().text.length)
        assertTrue(SupportPrompt.history(listOf(SupportTurn(true, "  "))).isEmpty())
    }

    @Test fun systemPromptCarriesTheHandoffAndLanguageRules() {
        val prompt = SupportPrompt.system(SupportFacts(language = AppLanguage.TRADITIONAL_CHINESE, cardName = "Daily", balance = "HK$120.00"))
        assertTrue(prompt.contains("2520 1314"))
        assertTrue(prompt.contains("Traditional Chinese (Hong Kong)"))
        assertTrue(prompt.contains("simulated"))
        assertTrue(prompt.contains("password"))
        assertTrue(prompt.contains("Daily"))
        assertTrue(prompt.contains("HK$120.00"))
        assertTrue(SupportPrompt.system(SupportFacts(language = AppLanguage.ENGLISH)).contains("English"))
    }

    @Test fun faqMatcherUnderstandsBothLanguagesAndFallsBack() {
        assertEquals(FaqTopic.TOP_UP, FaqMatcher.match("點樣增值？"))
        assertEquals(FaqTopic.TOP_UP, FaqMatcher.match("How do I top up"))
        assertEquals(FaqTopic.PASSWORD, FaqMatcher.match("I forgot my password"))
        assertEquals(FaqTopic.ACCESSIBILITY, FaqMatcher.match("如何放大字體"))
        assertEquals(FaqTopic.REFUND, FaqMatcher.match("我想退款"))
        assertEquals(FaqTopic.UNKNOWN, FaqMatcher.match("今日天氣如何"))
    }

    @Test fun clientReportsMissingKeyWithoutCallingTheNetwork() = kotlinx.coroutines.test.runTest {
        val client = DeepSeekSupport("", "deepseek-flash", SupportQuota(InMemoryUsageStore()) { noonHkt })
        assertFalse(client.isConfigured)
        val result = client.answer(SupportFacts(), listOf(SupportTurn(true, "hello")))
        assertEquals(AssistantResult.Failure(FailureKind.NOT_CONFIGURED), result)
    }

    @Test fun clientKeepsHistoryWithinTheCharacterBudget() {
        val turns = (1..50).map { SupportTurn(true, "a".repeat(500)) }
        val kept = SupportPrompt.history(turns)
        assertTrue(kept.sumOf { it.text.length } <= SupportPrompt.MAX_TOTAL_CHARS)
        assertTrue(kept.isNotEmpty())
    }
}
