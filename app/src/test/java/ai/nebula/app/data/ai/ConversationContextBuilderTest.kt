package ai.nebula.app.data.ai

import ai.nebula.app.data.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBuilderTest {
    @Test
    fun keepsOlderContextAndLeavesRecentTurnsOut() {
        val history = listOf(
            ChatMessage(true, "Мы договорились отправиться в северный город."),
            ChatMessage(false, "Я подготовлю карту."),
            ChatMessage(true, "А теперь обсудим погоду."),
            ChatMessage(false, "Сегодня солнечно.")
        )

        val context = ConversationContextBuilder.build(history, recentMessageCount = 2)

        assertTrue(context.contains("северный город"))
        assertTrue(context.contains("подготовлю карту"))
        assertTrue(!context.contains("погоду"))
    }

    @Test
    fun emptyOrRecentOnlyHistoryHasNoContext() {
        assertEquals("", ConversationContextBuilder.build(emptyList()))
        assertEquals("", ConversationContextBuilder.build(listOf(ChatMessage(true, "Привет"))))
    }

    @Test
    fun contextRespectsCharacterLimit() {
        val history = (1..12).map { ChatMessage(true, "Факт номер $it " + "x".repeat(300)) }
        val context = ConversationContextBuilder.build(history, recentMessageCount = 2, maxCharacters = 500)
        assertTrue(context.length <= 500)
    }
}
