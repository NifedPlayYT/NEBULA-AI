package ai.nebula.app.data

data class Character(
    val name: String,
    val creator: String,
    val bio: String,
    val category: String,
    val initials: String,
    val greeting: String = "",
    val instructions: String = ""
)

data class ChatMessage(val isUser: Boolean, val text: String)

data class ChatSession(
    val id: String,
    val character: Character,
    val messages: List<ChatMessage>,
    val updatedAt: Long
)

data class AppSettings(
    val apiBaseUrl: String = "https://api.orcarouter.ai/v1",
    val model: String = "deepseek/deepseek-v4.1-flash-free",
    val apiKey: String = "",
    val cleanMode: Boolean = true,
    val customPrompt: String = ""
)

enum class AppPage { DISCOVER, CHATS, CREATE, PROFILE, SETTINGS, CHAT }

fun defaultCharacters(): List<Character> = listOf(
    Character("Рин Итоши", "@nebula", "Холодный и расчётливый футболист. Не любит пустых разговоров.", "Аниме", "РИ",
        "Что тебе нужно?", "Держится сдержанно, говорит прямо и не тратит слова зря."),
    Character("Астра", "@nebula", "Спокойная, внимательная, с тонким чувством юмора.", "ОС", "А",
        "Привет. Рада знакомству. О чём поговорим?", "Внимательно слушает, задаёт уместные вопросы и запоминает детали беседы."),
    Character("Кай", "@nebula", "Ироничный напарник, который любит приключения.", "Игры", "К",
        "Ну что, какое приключение сегодня?", "Шутит, предлагает идеи и сохраняет бодрый настрой."),
    Character("Мира", "@nebula", "Загадочная собеседница со своей историей.", "Фэнтези", "М",
        "Ты наконец пришёл. У меня есть одна история.", "Говорит образно, любит загадки и постепенно раскрывает свой мир.")
)
