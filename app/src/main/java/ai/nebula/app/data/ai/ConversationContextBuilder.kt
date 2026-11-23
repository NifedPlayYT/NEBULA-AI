package ai.nebula.app.data.ai

import ai.nebula.app.data.ChatMessage

/**
 * Builds a compact continuity note from older turns.
 *
 * Recent turns are still sent as ordinary chat messages. This note preserves
 * earlier details when a conversation becomes longer than the provider window.
 */
object ConversationContextBuilder {
    fun build(
        history: List<ChatMessage>,
        recentMessageCount: Int = 10,
        maxCharacters: Int = 1_800
    ): String {
        require(recentMessageCount >= 0)
        require(maxCharacters > 0)

        val older = history
            .dropLast(recentMessageCount.coerceAtMost(history.size))
            .filter { it.text.isNotBlank() }
            .takeLast(18)

        if (older.isEmpty()) return ""

        val entries = older.map { message ->
            val role = if (message.isUser) "Пользователь" else "Персонаж"
            val clean = message.text
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(320)
            "$role: $clean"
        }

        val selected = mutableListOf<String>()
        var used = 0
        for (entry in entries.asReversed()) {
            val cost = entry.length + 1
            if (used + cost > maxCharacters) break
            selected.add(entry)
            used += cost
        }

        return selected.asReversed().joinToString("\n")
    }
}
