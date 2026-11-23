package ai.nebula.app.data.ai

import ai.nebula.app.data.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestPolicyTest {
    @Test
    fun retainsNewestMessagesInOriginalOrder() {
        val history = (1..5).map { ChatMessage(it % 2 == 1, "message-$it") }
        val result = ChatRequestPolicy.selectHistory(history, maxMessages = 3)
        assertEquals(listOf("message-3", "message-4", "message-5"), result.map { it.text })
    }

    @Test
    fun enforcesCharacterBudget() {
        val history = listOf(
            ChatMessage(true, "a".repeat(100)),
            ChatMessage(false, "b".repeat(100))
        )
        val result = ChatRequestPolicy.selectHistory(history, maxCharacters = 120)
        assertTrue(result.sumOf { it.text.length } <= 120)
        assertEquals("b".repeat(100), result.last().text)
    }

    @Test
    fun oversizedNewestMessageKeepsItsEnding() {
        val history = listOf(ChatMessage(true, "BEGIN-" + "x".repeat(100) + "-END"))
        val result = ChatRequestPolicy.selectHistory(history, maxCharacters = 20)
        assertEquals(20, result.single().text.length)
        assertTrue(result.single().text.endsWith("-END"))
    }
}
