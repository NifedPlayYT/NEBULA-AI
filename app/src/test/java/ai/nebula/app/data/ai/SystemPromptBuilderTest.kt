package ai.nebula.app.data.ai

import ai.nebula.app.data.AppSettings
import ai.nebula.app.data.Character
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptBuilderTest {
    private val character = Character("Астра", "@test", "Спокойный собеседник", "ОС", "А")

    @Test
    fun cleanModeUsesPoliteStyle() {
        val prompt = SystemPromptBuilder.build(AppSettings(cleanMode = true), character)
        assertTrue(prompt.contains("STYLE: CLEAN"))
        assertFalse(prompt.contains("STYLE: EXPRESSIVE"))
    }

    @Test
    fun expressiveModeUsesSeparateStyleWithoutRemovingSafetyRules() {
        val prompt = SystemPromptBuilder.build(AppSettings(cleanMode = false), character)
        assertTrue(prompt.contains("STYLE: EXPRESSIVE"))
        assertTrue(prompt.contains("Do not produce sexual or erotic content"))
    }

    @Test
    fun customPromptIsIncludedAndBounded() {
        val custom = "Говори короткими фразами и сохраняй характер."
        val prompt = SystemPromptBuilder.build(AppSettings(customPrompt = custom), character)
        assertTrue(prompt.contains(custom))
        assertTrue(prompt.contains("does not override safety requirements"))
    }
}
