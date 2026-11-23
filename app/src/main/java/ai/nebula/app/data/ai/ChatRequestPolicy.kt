package ai.nebula.app.data.ai

import ai.nebula.app.data.ChatMessage

/**
 * Keeps the most useful recent dialogue while bounding request size.
 * The oldest turns are dropped first; the order of retained turns is preserved.
 */
object ChatRequestPolicy {
    fun selectHistory(
        history: List<ChatMessage>,
        maxMessages: Int = 24,
        maxCharacters: Int = 18_000
    ): List<ChatMessage> {
        require(maxMessages > 0)
        require(maxCharacters > 0)

        val selected = mutableListOf<ChatMessage>()
        var used = 0
        for (message in history.asReversed()) {
            if (selected.size >= maxMessages) break
            if (message.text.isBlank()) continue

            val text = message.text.take(maxCharacters)
            val cost = text.length
            if (used + cost > maxCharacters) {
                val remaining = maxCharacters - used
                if (remaining > 0) selected.add(message.copy(text = text.take(remaining)))
                break
            }
            selected.add(message)
            used += cost
        }
        return selected.asReversed()
    }
}
