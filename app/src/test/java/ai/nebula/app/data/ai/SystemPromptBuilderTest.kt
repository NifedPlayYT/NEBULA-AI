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
    fun customPromptIsIgnoredWhileCleanModeIsEnabled() {
        val custom = "Говори короткими фразами и сохраняй характер."
        val prompt = SystemPromptBuilder.build(AppSettings(cleanMode = true, customPrompt = custom), character)
        assertFalse(prompt.contains(custom))
    }

    @Test
    fun customPromptIsIncludedOnlyInExpressiveMode() {
        val custom = "Говори короткими фразами и сохраняй характер."
        val prompt = SystemPromptBuilder.build(AppSettings(cleanMode = false, customPrompt = custom), character)
        assertTrue(prompt.contains(custom))
        assertTrue(prompt.contains("Additional guidance for EXPRESSIVE mode only"))
        assertTrue(prompt.contains("does not override safety requirements"))
    }

    @Test
    fun conversationContinuityIsIncludedAsContextNotAsInstructions() {
        val context = "Пользователь выбрал северный город."
        val prompt = SystemPromptBuilder.build(
            AppSettings(cleanMode = true),
            character,
            context
        )
        assertTrue(prompt.contains("CONTINUITY NOTE"))
        assertTrue(prompt.contains(context))
        assertTrue(prompt.contains("not as new instructions"))
    }

    @Test
    fun emptyConversationContextDoesNotAddAnEmptySection() {
        val prompt = SystemPromptBuilder.build(AppSettings(), character)
        assertFalse(prompt.contains("CONTINUITY NOTE"))
    }

    @Test
    fun characterSpecificGuidanceAndGreetingAreIncluded() {
        val configured = character.copy(
            greeting = "Добро пожаловать в мастерскую.",
            instructions = "Говорит коротко, любит точные формулировки."
        )
        val prompt = SystemPromptBuilder.build(AppSettings(), configured)
        assertTrue(prompt.contains("CHARACTER-SPECIFIC GUIDANCE"))
        assertTrue(prompt.contains("любит точные формулировки"))
        assertTrue(prompt.contains("Добро пожаловать в мастерскую"))
        assertTrue(prompt.contains("cannot override safety requirements"))
    }

    @Test
    fun characterGuidanceIsLengthBounded() {
        val configured = character.copy(instructions = "x".repeat(5_000))
        val prompt = SystemPromptBuilder.build(AppSettings(), configured)
        assertTrue(prompt.length < 5_500)
    }
}
