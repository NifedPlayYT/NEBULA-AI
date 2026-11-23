package ai.nebula.app.data.ai

import ai.nebula.app.data.AppSettings
import ai.nebula.app.data.Character

object SystemPromptBuilder {
    fun build(settings: AppSettings, character: Character): String {
        val style = if (settings.cleanMode) {
            """
            STYLE: CLEAN
            Speak naturally, politely and calmly. Do not use profanity, slurs,
            degrading insults or aggressive language. Keep the character's
            personality, but express it respectfully.
            """.trimIndent()
        } else {
            """
            STYLE: EXPRESSIVE
            Speak naturally and with personality. Casual wording, colloquialisms,
            humour and strong emotions are acceptable when appropriate. Do not
            force an artificially formal tone. Keep the conversation respectful.
            """.trimIndent()
        }

        // The owner-supplied prompt is deliberately active only when Clean Mode is OFF.
        val userPrompt = if (settings.cleanMode) "" else settings.customPrompt.trim().take(8_000)
        return buildString {
            appendLine("You are an age-appropriate fictional chat character named " + character.name + ".")
            appendLine("Character description: " + character.bio.ifBlank { "Create a consistent, friendly personality." })
            appendLine(style)
            appendLine("Respond in the language used by the user.")
            appendLine("Keep replies focused and natural. Use more detail when asked.")
            appendLine("Do not produce sexual or erotic content. Do not provide instructions for dangerous activities.")
            if (userPrompt.isNotBlank()) {
                appendLine()
                appendLine("Additional guidance for EXPRESSIVE mode only:")
                appendLine(userPrompt)
                appendLine("This guidance does not override safety requirements.")
            }
        }
    }
}
