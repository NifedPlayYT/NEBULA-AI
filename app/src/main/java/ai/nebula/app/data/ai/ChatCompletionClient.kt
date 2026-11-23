package ai.nebula.app.data.ai

import ai.nebula.app.data.AppSettings
import ai.nebula.app.data.Character
import ai.nebula.app.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatCompletionClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun stream(
        settings: AppSettings,
        character: Character,
        history: List<ChatMessage>,
        onChunk: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(settings.model.isNotBlank()) { "Укажи название модели в настройках." }
        require(settings.apiKey.isNotBlank()) { "Добавь API-ключ в настройках." }

        val endpoint = ApiEndpointResolver.resolve(settings.apiBaseUrl)
        val requestHistory = ChatRequestPolicy.selectHistory(history)
        val continuity = ConversationContextBuilder.build(history)
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "system")
                .put("content", SystemPromptBuilder.build(settings, character, continuity))
        )
        requestHistory.forEach { message ->
            messages.put(
                JSONObject()
                    .put("role", if (message.isUser) "user" else "assistant")
                    .put("content", message.text)
            )
        }

        val payload = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", messages)
            .put("temperature", if (settings.cleanMode) 0.72 else 0.86)
            .put("max_tokens", 700)
            .put("stream", true)
            .toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer " + settings.apiKey.trim())
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string().orEmpty()
                throw IOException(mapHttpError(response.code, details))
            }
            val source = response.body?.source() ?: throw IOException("Провайдер вернул пустой ответ.")
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue

                val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                val providerError = event.optJSONObject("error")
                if (providerError != null) {
                    throw IOException(providerError.optString("message", "Ошибка генерации у провайдера."))
                }
                val delta = event.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()
                if (delta.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) { onChunk(delta) }
                }
            }
        }
    }

    private fun mapHttpError(code: Int, details: String): String {
        val message = when (code) {
            400 -> "Провайдер отклонил запрос. Проверь модель и параметры подключения."
            401 -> "API-ключ не принят. Проверь ключ в настройках."
            403 -> "Нет доступа к модели или провайдер отклонил запрос."
            404 -> "Не найден API-адрес или модель. Проверь настройки."
            408 -> "Провайдер не успел ответить. Попробуй ещё раз."
            429 -> "Достигнут лимит запросов. Подожди немного и повтори."
            in 500..599 -> "Временная ошибка AI-провайдера (" + code + ")."
            else -> "Ошибка API " + code + "."
        }
        return message + details.takeIf { it.isNotBlank() }?.let { " Подробности: " + it.take(240) }.orEmpty()
    }
}
