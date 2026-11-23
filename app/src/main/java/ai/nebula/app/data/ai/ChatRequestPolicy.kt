package ai.nebula.app.data.ai

import ai.nebula.app.data.ChatMessage

/**
 * Keeps the newest useful dialogue while bounding request size.
 * The result preserves chronological order; oversized newest messages keep their tail.
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

            val remaining = maxCharacters - used
            if (remaining <= 0) break

            if (message.text.length > remaining) {
                selected.add(message.copy(text = message.text.takeLast(remaining)))
                break
            }

            selected.add(message)
            used += message.text.length
        }
        return selected.asReversed()
    }
}
