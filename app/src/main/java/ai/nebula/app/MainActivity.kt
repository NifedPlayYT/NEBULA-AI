package ai.nebula.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Night = Color(0xFF0B0A12)
private val Panel = Color(0xFF171522)
private val Violet = Color(0xFF9B72FF)
private val Muted = Color(0xFFAAA5BA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nebula", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Violet, onBackground = Color.White, onSurface = Color.White)) {
                NebulaApp(
                    prefs.getString("base", "https://api.openai.com/v1") ?: "",
                    prefs.getString("model", "gpt-4o-mini") ?: "",
                    prefs.getString("key", "") ?: ""
                ) { base, model, key ->
                    prefs.edit().putString("base", base).putString("model", model).putString("key", key).apply()
                }
            }
        }
    }
}

private data class ChatLine(val user: Boolean, val text: String)

@Composable
private fun NebulaApp(initialBase: String, initialModel: String, initialKey: String, save: (String, String, String) -> Unit) {
    var page by remember { mutableStateOf("home") }
    var character by remember { mutableStateOf("Астра") }
    var base by remember { mutableStateOf(initialBase) }
    var model by remember { mutableStateOf(initialModel) }
    var key by remember { mutableStateOf(initialKey) }
    var settings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatLine>() }

    Column(Modifier.fillMaxSize().background(Night).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEBULA", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("AI CHARACTER UNIVERSE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Text("⚙", color = Violet, fontSize = 26.sp, modifier = Modifier.clickable { settings = true }.padding(8.dp))
        }
        Spacer(Modifier.height(24.dp))
        when (page) {
            "home" -> {
                Text("ТВОЯ ВСЕЛЕННАЯ", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Выбери персонажа для общения", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Все", "ОС", "Аниме", "Игры").forEach {
                        Surface(color = if (it == "Все") Violet else Panel, shape = RoundedCornerShape(18.dp)) {
                            Text(it, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                CharacterCard("Астра", "Спокойная, наблюдательная, с тонким чувством юмора.", "ОС") {
                    character = "Астра"; messages.clear(); messages.add(ChatLine(false, "Привет! Я Астра. О чём поговорим?")); page = "chat"
                }
                CharacterCard("Рэй", "Энергичный собеседник, любит приключения и идеи.", "Фэнтези") {
                    character = "Рэй"; messages.clear(); messages.add(ChatLine(false, "Привет! Я Рэй. Придумаем приключение?")); page = "chat"
                }
                OutlinedButton(onClick = { page = "create" }, modifier = Modifier.fillMaxWidth()) { Text("+ Создать персонажа") }
            }
            "create" -> {
                var name by remember { mutableStateOf("") }
                var description by remember { mutableStateOf("") }
                Text("НОВЫЙ ПЕРСОНАЖ", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                NebulaField(name, { name = it }, "Имя персонажа")
                Spacer(Modifier.height(12.dp))
                NebulaField(description, { description = it }, "Характер и описание", true)
                Spacer(Modifier.height(18.dp))
                Button(onClick = {
                    character = name.ifBlank { "Новый персонаж" }
                    messages.clear()
                    messages.add(ChatLine(false, "Привет! Я " + character + ". Давай познакомимся."))
                    page = "chat"
                }, modifier = Modifier.fillMaxWidth()) { Text("Начать общение") }
                TextButton(onClick = { page = "home" }) { Text("Назад") }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Violet, fontSize = 30.sp, modifier = Modifier.clickable { page = "home" }.padding(end = 12.dp))
                    Column {
                        Text(character, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Персонаж NEBULA", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages) { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.user) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (line.user) Color(0xFF392B61) else Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 300.dp)) {
                                Text(line.text, modifier = Modifier.padding(13.dp))
                            }
                        }
                    }
                    if (busy) item { Text(character + " печатает…", color = Muted) }
                    if (error.isNotBlank()) item { Text(error, color = Color(0xFFFF8B8B), fontSize = 12.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).background(Panel, RoundedCornerShape(22.dp)).padding(14.dp)) {
                        BasicTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                            decorationBox = { inner -> if (input.isEmpty()) Text("Написать сообщение…", color = Muted); inner() })
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = input.isNotBlank() && !busy, onClick = {
                        val sent = input.trim(); input = ""; messages.add(ChatLine(true, sent)); error = ""; busy = true
                        scope.launch {
                            try { messages.add(ChatLine(false, requestAi(base, model, key, character, messages.toList()))) }
                            catch (e: Exception) { error = e.message ?: "Не удалось получить ответ. Проверь API и интернет." }
                            finally { busy = false }
                        }
                    }) { Text("➤") }
                }
            }
        }
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Настройки AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("API URL", color = Muted); NebulaField(base, { base = it }, "https://provider.example/v1")
                    Text("Модель", color = Muted); NebulaField(model, { model = it }, "Название модели")
                    Text("API-ключ", color = Muted); NebulaField(key, { key = it }, "Ключ провайдера")
                    Text("Ключ хранится на устройстве. Не публикуй APK с личным ключом.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { save(base, model, key); settings = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { settings = false }) { Text("Отмена") } })
    }
}

@Composable
private fun CharacterCard(name: String, description: String, tag: String, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).background(Color(0xFF33264F), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text(name.take(1), color = Violet, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 12.sp)
                Text(tag.uppercase(), color = Violet, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Text("›", color = Violet, fontSize = 28.sp)
        }
    }
}

@Composable
private fun NebulaField(value: String, onChange: (String) -> Unit, hint: String, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, placeholder = { Text(hint) }, modifier = Modifier.fillMaxWidth(),
        minLines = if (multiline) 3 else 1, maxLines = if (multiline) 5 else 1)
}

private suspend fun requestAi(base: String, model: String, key: String, character: String, history: List<ChatLine>): String =
    withContext(Dispatchers.IO) {
        require(base.isNotBlank() && model.isNotBlank() && key.isNotBlank()) { "Открой ⚙ и укажи API URL, модель и ключ." }
        val payload = JSONArray()
        payload.put(JSONObject().put("role", "system").put("content", "Ты персонаж по имени " + character + ". Общайся естественно и сохраняй характер."))
        history.takeLast(20).forEach { line ->
            payload.put(JSONObject().put("role", if (line.user) "user" else "assistant").put("content", line.text))
        }
        val json = JSONObject().put("model", model).put("messages", payload).put("temperature", 0.85).toString()
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(base.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + key).post(body).build()
        val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("API вернул " + response.code + ": " + raw.take(200))
            JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }
