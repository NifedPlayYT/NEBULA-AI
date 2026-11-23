package ai.nebula.app.data

data class Character(
    val name: String,
    val creator: String,
    val bio: String,
    val category: String,
    val initials: String
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
    Character("Рин Итоши", "@nebula", "Холодный и расчётливый футболист. Не любит пустых разговоров.", "Аниме", "РИ"),
    Character("Астра", "@nebula", "Спокойная, внимательная, с тонким чувством юмора.", "ОС", "А"),
    Character("Кай", "@nebula", "Ироничный напарник, который любит приключения.", "Игры", "К"),
    Character("Мира", "@nebula", "Загадочная собеседница со своей историей.", "Фэнтези", "М")
)
