package ai.nebula.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JSON keys and preference names intentionally match the 0.1.x app.
 * Existing settings, characters and chats therefore survive a normal update.
 */
class NebulaRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("nebula", Context.MODE_PRIVATE)

    fun loadSettings() = AppSettings(
        apiBaseUrl = preferences.getString("base", "https://api.orcarouter.ai/v1")
            ?: "https://api.orcarouter.ai/v1",
        model = preferences.getString("model", "deepseek/deepseek-v4.1-flash-free")
            ?: "deepseek/deepseek-v4.1-flash-free",
        apiKey = preferences.getString("key", "") ?: "",
        cleanMode = preferences.getBoolean("clean_mode", true),
        customPrompt = preferences.getString("system_prompt", "") ?: ""
    )

    fun saveSettings(settings: AppSettings) {
        preferences.edit()
            .putString("base", settings.apiBaseUrl)
            .putString("model", settings.model)
            .putString("key", settings.apiKey)
            .putBoolean("clean_mode", settings.cleanMode)
            .putString("system_prompt", settings.customPrompt)
            .apply()
    }

    fun loadCharacters(): List<Character> {
        val json = preferences.getString("custom_people", "[]") ?: "[]"
        return defaultCharacters() + decodeCharacters(json)
    }

    fun saveCustomCharacters(allCharacters: List<Character>) {
        val defaultNames = defaultCharacters().map { it.name }.toSet()
        val custom = allCharacters.filterNot { it.name in defaultNames }
        preferences.edit().putString("custom_people", encodeCharacters(custom)).apply()
    }

    fun loadSessions(): List<ChatSession> {
        val json = preferences.getString("chats", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val person = item.optJSONObject("person") ?: continue
                    val character = Character(
                        name = person.optString("name", "Персонаж"),
                        creator = person.optString("creator", "@nebula"),
                        bio = person.optString("bio", ""),
                        category = person.optString("category", "ОС"),
                        initials = person.optString("initials", "✦"),
                        greeting = person.optString("greeting", ""),
                        instructions = person.optString("instructions", "")
                    )
                    val rawMessages = item.optJSONArray("lines") ?: JSONArray()
                    val messages = buildList {
                        for (messageIndex in 0 until rawMessages.length()) {
                            val message = rawMessages.optJSONObject(messageIndex) ?: continue
                            add(ChatMessage(message.optBoolean("user"), message.optString("text", "")))
                        }
                    }
                    add(
                        ChatSession(
                            id = item.optString("id", index.toString()),
                            character = character,
                            messages = messages,
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }.take(30)
        }.getOrDefault(emptyList())
    }

    fun saveSessions(sessions: List<ChatSession>) {
        val array = JSONArray()
        sessions.sortedByDescending { it.updatedAt }.take(30).forEach { session ->
            val person = JSONObject()
                .put("name", session.character.name)
                .put("creator", session.character.creator)
                .put("bio", session.character.bio)
                .put("category", session.character.category)
                .put("initials", session.character.initials)
                .put("greeting", session.character.greeting)
                .put("instructions", session.character.instructions)
            val lines = JSONArray()
            session.messages.takeLast(160).forEach { message ->
                lines.put(JSONObject().put("user", message.isUser).put("text", message.text))
            }
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("updatedAt", session.updatedAt)
                    .put("person", person)
                    .put("lines", lines)
            )
        }
        preferences.edit().putString("chats", array.toString()).apply()
    }

    private fun encodeCharacters(characters: List<Character>): String {
        val array = JSONArray()
        characters.forEach { person ->
            array.put(
                JSONObject()
                    .put("name", person.name)
                    .put("creator", person.creator)
                    .put("bio", person.bio)
                    .put("category", person.category)
                    .put("initials", person.initials)
                    .put("greeting", person.greeting)
                    .put("instructions", person.instructions)
            )
        }
        return array.toString()
    }

    private fun decodeCharacters(json: String): List<Character> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    Character(
                        name = item.optString("name", "Новый персонаж"),
                        creator = item.optString("creator", "@you"),
                        bio = item.optString("bio", ""),
                        category = item.optString("category", "ОС"),
                        initials = item.optString("initials", "✦"),
                        greeting = item.optString("greeting", ""),
                        instructions = item.optString("instructions", "")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
