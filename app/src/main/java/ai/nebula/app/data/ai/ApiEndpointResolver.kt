package ai.nebula.app.data.ai

import java.net.URI

/** Normalizes provider URLs without duplicating /chat/completions. */
object ApiEndpointResolver {
    fun resolve(baseUrl: String): String {
        val raw = baseUrl.trim().trimEnd('/')
        require(raw.isNotBlank()) { "Укажи адрес API в настройках." }

        val uri = runCatching { URI(raw) }
            .getOrElse { throw IllegalArgumentException("Некорректный адрес API.") }

        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Адрес API должен начинаться с https:// и содержать домен."
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "В адресе API не должно быть параметров запроса или фрагмента."
        }

        return when {
            raw.endsWith("/chat/completions", ignoreCase = true) -> raw
            else -> "$raw/chat/completions"
        }
    }
}
