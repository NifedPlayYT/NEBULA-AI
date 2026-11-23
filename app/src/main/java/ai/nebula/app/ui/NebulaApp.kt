package ai.nebula.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.nebula.app.data.AppPage
import ai.nebula.app.data.AppSettings
import ai.nebula.app.data.Character
import ai.nebula.app.data.ChatMessage
import ai.nebula.app.data.ChatSession
import ai.nebula.app.data.NebulaRepository
import ai.nebula.app.data.ai.ChatCompletionClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Ink = Color(0xFF090B12)
private val Panel = Color(0xFF151827)
private val PanelLift = Color(0xFF1D2031)
private val Violet = Color(0xFFB9A3FF)
private val SoftGold = Color(0xFFF1D5A5)
private val MutedText = Color(0xFF969AAF)
private val Hairline = Color(0xFF2B2F43)

@Composable
fun NebulaApp(repository: NebulaRepository) {
    val scope = rememberCoroutineScope()
    val ai = remember { ChatCompletionClient() }
    var settings by remember { mutableStateOf(repository.loadSettings()) }
    var characters by remember { mutableStateOf(repository.loadCharacters()) }
    var sessions by remember { mutableStateOf(repository.loadSessions()) }
    var page by remember { mutableStateOf(AppPage.DISCOVER) }
    var chatReturnPage by remember { mutableStateOf(AppPage.DISCOVER) }
    var settingsReturnPage by remember { mutableStateOf(AppPage.PROFILE) }
    var selectedCharacter by remember { mutableStateOf<Character?>(null) }
    var currentChatId by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var messageInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var chatError by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var chatSearch by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var newName by remember { mutableStateOf("") }
    var newBio by remember { mutableStateOf("") }
    var newGreeting by remember { mutableStateOf("") }
    var newInstructions by remember { mutableStateOf("") }

    fun saveSettings(next: AppSettings) {
        settings = next
        repository.saveSettings(next)
    }

    fun saveCurrentChat() {
        val character = selectedCharacter ?: return
        if (currentChatId.isBlank()) return
        val session = ChatSession(currentChatId, character, messages, System.currentTimeMillis())
        sessions = (listOf(session) + sessions.filterNot { it.id == session.id })
            .sortedByDescending { it.updatedAt }.take(30)
        repository.saveSessions(sessions)
    }

    fun openCharacter(character: Character) {
        chatReturnPage = if (page == AppPage.CREATE) AppPage.DISCOVER else page
        selectedCharacter = character
        currentChatId = System.currentTimeMillis().toString()
        messages = listOf(ChatMessage(false, character.greeting.ifBlank { "Привет. Я " + character.name + ". О чём поговорим?" }))
        messageInput = ""
        chatError = ""
        page = AppPage.CHAT
        saveCurrentChat()
    }

    fun openSession(session: ChatSession) {
        chatReturnPage = AppPage.CHATS
        selectedCharacter = session.character
        currentChatId = session.id
        messages = session.messages
        messageInput = ""
        chatError = ""
        page = AppPage.CHAT
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        val character = selectedCharacter
        if (text.isBlank() || sending || character == null) return
        val history = messages.filterNot { !it.isUser && it.text.isBlank() } + ChatMessage(true, text)
        messages = history + ChatMessage(false, "")
        messageInput = ""
        chatError = ""
        sending = true
        saveCurrentChat()
        generationJob = scope.launch {
            try {
                ai.stream(settings, character, history) { chunk ->
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && !messages[lastIndex].isUser) {
                        val updated = messages.toMutableList()
                        updated[lastIndex] = updated[lastIndex].copy(text = updated[lastIndex].text + chunk)
                        messages = updated
                    }
                }
                if (messages.lastOrNull()?.text.isNullOrBlank()) {
                    chatError = "Модель вернула пустой ответ. Попробуй ещё раз."
                    messages = messages.dropLast(1)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                chatError = exception.message ?: "Не удалось получить ответ от AI."
                if (messages.lastOrNull()?.let { !it.isUser && it.text.isBlank() } == true) {
                    messages = messages.dropLast(1)
                }
            } finally {
                if (messages.lastOrNull()?.let { !it.isUser && it.text.isBlank() } == true) {
                    messages = messages.dropLast(1)
                }
                generationJob = null
                sending = false
                saveCurrentChat()
            }
        }
    }

    fun stopGeneration() {
        chatError = "Генерация остановлена."
        generationJob?.cancel()
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101321), Ink, Ink)))) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 24 }) togetherWith fadeOut(tween(120))
            },
            label = "nebula-route"
        ) { currentPage ->
            when (currentPage) {
                AppPage.DISCOVER -> DiscoverScreen(
                    characters, search, { search = it }, category, { category = it },
                    ::openCharacter, { page = AppPage.CREATE }
                )
                AppPage.CHATS -> ChatsScreen(
                    sessions, chatSearch, { chatSearch = it }, ::openSession,
                    { id -> sessions = sessions.filterNot { it.id == id }; repository.saveSessions(sessions) },
                    { page = AppPage.DISCOVER }
                )
                AppPage.CREATE -> CreateScreen(
                    newName, { newName = it }, newBio, { newBio = it },
                    newGreeting, { newGreeting = it }, newInstructions, { newInstructions = it },
                    { page = AppPage.DISCOVER },
                    {
                        val safeName = newName.trim()
                        if (safeName.isNotEmpty()) {
                            val created = Character(
                                safeName, "@you", newBio.trim().ifBlank { "Новый персонаж NEBULA." },
                                "Мой персонаж", safeName.take(2).uppercase(),
                                greeting = newGreeting.trim(),
                                instructions = newInstructions.trim()
                            )
                            characters = characters + created
                            repository.saveCustomCharacters(characters)
                            newName = ""
                            newBio = ""
                            newGreeting = ""
                            newInstructions = ""
                            openCharacter(created)
                        }
                    }
                )
                AppPage.PROFILE -> ProfileScreen(
                    sessions.size, characters.size, settings.cleanMode,
                    { settingsReturnPage = AppPage.PROFILE; page = AppPage.SETTINGS },
                    { page = AppPage.CHATS }, { page = AppPage.CREATE }
                )
                AppPage.SETTINGS -> SettingsScreen(
                    settings, ::saveSettings, { page = settingsReturnPage }
                )
                AppPage.CHAT -> {
                    val character = selectedCharacter
                    if (character != null) ConversationScreen(
                        character, messages, messageInput, { messageInput = it },
                        sending, chatError, { page = chatReturnPage },
                        { settingsReturnPage = AppPage.CHAT; page = AppPage.SETTINGS },
                        ::sendMessage, ::stopGeneration
                    )
                }
            }
        }

        if (page in listOf(AppPage.DISCOVER, AppPage.CHATS, AppPage.CREATE, AppPage.PROFILE)) {
            BottomDock(page) { page = it }
        }
    }
}

@Composable
private fun DiscoverScreen(
    characters: List<Character>, search: String, onSearch: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    onOpen: (Character) -> Unit, onCreate: () -> Unit
) {
    val categories = listOf("Все", "Аниме", "ОС", "Игры", "Фэнтези", "Мой персонаж")
    val filtered = characters.filter {
        (category == "Все" || it.category == category) &&
            (it.name.contains(search, true) || it.bio.contains(search, true))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(34)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("NEBULA", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("ТВОЁ ПРОСТРАНСТВО", color = MutedText, fontSize = 8.sp, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFF192A2A), shape = CircleShape) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Color(0xFF78D7A9), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("ONLINE", color = Color(0xFF9BE5BE), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF4C4267))
        ) {
            Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF40345C), Color(0xFF24263D), Color(0xFF181B2B)))).padding(20.dp)) {
                Column {
                    Text("ТВОЯ ВСЕЛЕННАЯ", color = SoftGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Найди своего\nсобеседника.", color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
                    Text("Персонажи, истории и разговоры — в одном месте.", color = Color(0xFFD0CDE0), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    Button(
                        onClick = onCreate, modifier = Modifier.padding(top = 17.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1D6FF), contentColor = Color(0xFF241B35))
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Создать персонажа", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).size(70.dp).border(1.dp, Color.White.copy(alpha = .12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFFC6B3FF), modifier = Modifier.size(29.dp))
                }
            }
        }
        Text("ОТКРОЙ НОВОЕ", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
        SearchField(search, onSearch)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 13.dp, bottom = 13.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEach { item ->
                val active = item == category
                Surface(
                    modifier = Modifier.clickable { onCategory(item) },
                    color = if (active) Color(0xFFDCCFFF) else Panel,
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, if (active) Color(0xFFE6DCFF) else Hairline)
                ) {
                    Text(item, color = if (active) Color(0xFF241B35) else MutedText, fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Персонажи", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${filtered.size} В КАТАЛОГЕ", color = MutedText, fontSize = 8.sp, letterSpacing = 1.sp)
        }
        if (filtered.isEmpty()) EmptyState("Ничего не найдено", "Попробуй изменить запрос или категорию.")
        else filtered.chunked(2).forEach { rowCharacters ->
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                rowCharacters.forEach { CharacterCard(it, Modifier.weight(1f)) { onOpen(it) } }
                if (rowCharacters.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CharacterCard(character: Character, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = Panel, shape = RoundedCornerShape(21.dp), border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(characterGradient(character.name))),
                contentAlignment = Alignment.Center
            ) {
                Text(character.initials, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Surface(Modifier.align(Alignment.TopStart).padding(7.dp), color = Color.Black.copy(alpha = .25f), shape = RoundedCornerShape(8.dp)) {
                    Text(character.category.uppercase(), color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                }
            }
            Text(character.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 11.dp))
            Text(character.bio, color = MutedText, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ОТКРЫТЬ", color = Violet, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Violet, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun ChatsScreen(
    sessions: List<ChatSession>, search: String, onSearch: (String) -> Unit,
    onOpen: (ChatSession) -> Unit, onDelete: (String) -> Unit, onDiscover: () -> Unit
) {
    val filteredSessions = sessions.filter { session ->
        session.character.name.contains(search, ignoreCase = true) ||
            session.messages.lastOrNull()?.text.orEmpty().contains(search, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeading("Твои чаты", "ИСТОРИЯ ДИАЛОГОВ")
        SearchField(search, onSearch, placeholder = "Поиск по персонажу или сообщениям…")
        if (sessions.isEmpty()) {
            EmptyState("Пока нет диалогов", "Открой персонажа в каталоге, чтобы начать первый разговор.")
            Button(onClick = onDiscover, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(15.dp)) {
                Text("Открыть каталог")
            }
        } else if (filteredSessions.isEmpty()) {
            EmptyState("Ничего не найдено", "Попробуй изменить поисковый запрос.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredSessions, key = { it.id }) { session ->
                    Surface(color = Panel, shape = RoundedCornerShape(19.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth().clickable { onOpen(session) }) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            CharacterAvatar(session.character, 48)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(session.character.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(session.messages.lastOrNull()?.text?.ifBlank { "Новый диалог" } ?: "Новый диалог",
                                    color = MutedText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                            IconButton(onClick = { onDelete(session.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, "Удалить чат", tint = MutedText, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateScreen(
    name: String, onName: (String) -> Unit, bio: String, onBio: (String) -> Unit,
    greeting: String, onGreeting: (String) -> Unit,
    instructions: String, onInstructions: (String) -> Unit,
    onBack: () -> Unit, onCreate: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Назад", tint = Color.White) }
            Column {
                Text("Студия персонажей", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("СОЗДАЙ СВОЕГО СОБЕСЕДНИКА", color = MutedText, fontSize = 8.sp, letterSpacing = 1.2.sp)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("НОВЫЙ ПЕРСОНАЖ", color = SoftGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Box(Modifier.fillMaxWidth().padding(top = 16.dp).height(130.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF45375F), Color(0xFF22283E)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFFCDBDFF), modifier = Modifier.size(44.dp))
                }
                Text("Имя", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
                AppTextField(name, onName, "Например, Акира")
                Text("Характер и описание", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 15.dp, bottom = 7.dp))
                AppTextField(bio, onBio, "Опиши характер, интересы и манеру общения…", minLines = 4)
                Text("Первое сообщение", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 15.dp, bottom = 7.dp))
                AppTextField(greeting, onGreeting, "С чего персонаж начнёт диалог?", minLines = 2)
                Text("Дополнительные инструкции персонажу", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 15.dp, bottom = 7.dp))
                AppTextField(instructions, onInstructions, "Манера речи, цели, привычки, особенности поведения…", minLines = 4)
                Text("Эти настройки сохраняются в карточке персонажа и применяются в новых ответах. Они не могут отменять правила безопасности.", color = MutedText, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onCreate, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 19.dp).height(50.dp),
                    shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF211A32))) {
                    Text("Создать персонажа", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    chatsCount: Int, charactersCount: Int, cleanMode: Boolean,
    onSettings: () -> Unit, onChats: () -> Unit, onCreate: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenHeading("Профиль", "ТВОЯ NEBULA")
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandBadge(58)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("NEBULA USER", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Персональное пространство", color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatCard("ДИАЛОГИ", chatsCount.toString(), Modifier.weight(1f))
                    StatCard("ПЕРСОНАЖИ", charactersCount.toString(), Modifier.weight(1f))
                    StatCard("РЕЖИМ", if (cleanMode) "Чистый" else "Свободный", Modifier.weight(1f))
                }
            }
        }
        Text("УПРАВЛЕНИЕ", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 24.dp, bottom = 9.dp))
        ActionRow("Настройки AI", "Провайдер, модель и стиль ответов", Icons.Outlined.Tune, onSettings)
        ActionRow("История диалогов", "Продолжить сохранённый разговор", Icons.Outlined.Forum, onChats)
        ActionRow("Создать персонажа", "Добавить нового собеседника", Icons.Outlined.Add, onCreate)
        Text("NEBULA AI  •  VERSION 0.2", color = MutedText, fontSize = 8.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 25.dp, bottom = 20.dp))
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    var showKey by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Назад", tint = Color.White) }
            Column {
                Text("Настройки AI", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("ПОДКЛЮЧЕНИЕ И ПОВЕДЕНИЕ", color = MutedText, fontSize = 8.sp, letterSpacing = 1.3.sp)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ПОДКЛЮЧЕНИЕ ПРОВАЙДЕРА", color = SoftGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                SettingsLabel("API base URL")
                AppTextField(settings.apiBaseUrl, { onSettingsChange(settings.copy(apiBaseUrl = it)) }, "https://api.example.com/v1")
                SettingsLabel("Модель")
                AppTextField(settings.model, { onSettingsChange(settings.copy(model = it)) }, "Название модели")
                SettingsLabel("API-ключ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        AppTextField(settings.apiKey, { onSettingsChange(settings.copy(apiKey = it)) }, "Вставь API-ключ",
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation())
                    }
                    IconButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "СКРЫТЬ" else "ПОКАЗАТЬ", color = Violet, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("СТИЛЬ ОБЩЕНИЯ", color = SoftGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Чистый режим", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (settings.cleanMode) "Спокойная и вежливая манера речи." else "Более свободная и эмоциональная манера речи.",
                            color = MutedText, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Switch(checked = settings.cleanMode, onCheckedChange = { onSettingsChange(settings.copy(cleanMode = it)) })
                }
                Text("ПРОМПТ ДЛЯ СВОБОДНОГО РЕЖИМА", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
                AppTextField(settings.customPrompt, { onSettingsChange(settings.copy(customPrompt = it)) },
                    "Твоя инструкция включится только при выключенном Clean Mode…", minLines = 5)
                Text("При включённом Clean Mode этот промпт не отправляется. В обоих режимах сохраняются базовые требования безопасности.",
                    color = MutedText, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Text("Изменения сохраняются автоматически на этом устройстве.", color = MutedText, fontSize = 9.sp, modifier = Modifier.padding(top = 13.dp, bottom = 22.dp))
    }
}

@Composable
private fun ConversationScreen(
    character: Character, messages: List<ChatMessage>, input: String, onInput: (String) -> Unit,
    sending: Boolean, error: String, onBack: () -> Unit, onSettings: () -> Unit,
    onSend: (String) -> Unit, onStop: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(color = Color(0xF7131724), border = BorderStroke(0.5.dp, Hairline)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !sending, onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Назад", tint = if (sending) MutedText else Color.White) }
                CharacterAvatar(character, 39)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(character.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("AI-ПЕРСОНАЖ  •  NEBULA", color = MutedText, fontSize = 7.sp, letterSpacing = 1.sp)
                }
                IconButton(enabled = !sending, onClick = onSettings) { Icon(Icons.Outlined.Settings, "Настройки", tint = if (sending) MutedText.copy(alpha = .45f) else MutedText) }
            }
        }
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages.filter { it.isUser || it.text.isNotBlank() }) { message -> MessageBubble(character, message) }
            if (sending && messages.lastOrNull()?.let { !it.isUser && it.text.isBlank() } == true) item { TypingRow(character) }
            if (error.isNotBlank()) item {
                Surface(color = Color(0xFF3A222A), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(error, color = Color(0xFFFFCACA), fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
        Surface(color = Color(0xFF131725), shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), border = BorderStroke(1.dp, Hairline)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = input, onValueChange = onInput,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 11.dp),
                    minLines = 1, maxLines = 5,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 19.sp),
                    keyboardOptions = KeyboardOptions.Default,
                    decorationBox = { inner -> if (input.isEmpty()) Text("Написать сообщение…", color = MutedText, fontSize = 13.sp); inner() }
                )
                IconButton(
                    enabled = sending || input.isNotBlank(),
                    onClick = { if (sending) onStop() else onSend(input) },
                    modifier = Modifier.size(43.dp).clip(CircleShape)
                        .background(if (sending) Color(0xFF6A3947) else if (input.isNotBlank()) Violet else PanelLift)
                ) {
                    if (sending) {
                        Icon(Icons.Outlined.StopCircle, "Остановить генерацию", tint = Color.White)
                    } else {
                        Icon(Icons.Outlined.Send, "Отправить", tint = if (input.isNotBlank()) Color(0xFF211A32) else MutedText)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(character: Character, message: ChatMessage) {
    val clipboardManager = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!message.isUser) {
            CharacterAvatar(character, 29)
            Spacer(Modifier.width(7.dp))
        }
        Surface(
            color = if (message.isUser) Color(0xFF554477) else Color(0xFF1A1E2D),
            shape = RoundedCornerShape(topStart = if (message.isUser) 18.dp else 6.dp, topEnd = if (message.isUser) 6.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(message.text, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
        IconButton(
            onClick = { clipboardManager.setText(AnnotatedString(message.text)) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Outlined.ContentCopy, "Копировать сообщение", tint = MutedText, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun TypingRow(character: Character) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CharacterAvatar(character, 29)
        Spacer(Modifier.width(8.dp))
        Surface(color = PanelLift, shape = RoundedCornerShape(15.dp)) {
            Text("Печатает…", color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun BottomDock(current: AppPage, onSelect: (AppPage) -> Unit) {
    Surface(
        color = Color(0xF6131623), shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, Color(0xFF343147)), shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp).navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            DockItem("Чаты", Icons.Outlined.Forum, current == AppPage.CHATS, Modifier.weight(1f)) { onSelect(AppPage.CHATS) }
            DockItem("Каталог", Icons.Outlined.Explore, current == AppPage.DISCOVER, Modifier.weight(1f)) { onSelect(AppPage.DISCOVER) }
            DockItem("Создать", Icons.Outlined.Add, current == AppPage.CREATE, Modifier.weight(1f)) { onSelect(AppPage.CREATE) }
            DockItem("Профиль", Icons.Outlined.PersonOutline, current == AppPage.PROFILE, Modifier.weight(1f)) { onSelect(AppPage.PROFILE) }
        }
    }
}

@Composable
private fun DockItem(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.height(33.dp).width(67.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) Brush.linearGradient(listOf(Color(0xFFD9CEFF), Color(0xFFA58DE9))) else Brush.linearGradient(listOf(Panel, Panel))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (selected) Color(0xFF251D37) else MutedText, modifier = Modifier.size(19.dp))
        }
        Text(label, color = if (selected) Color(0xFFE0D5FF) else MutedText, fontSize = 8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SearchField(
    value: String, onValue: (String) -> Unit,
    placeholder: String = "Поиск персонажа…"
) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Hairline)) {
        BasicTextField(
            value = value, onValueChange = onValue,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = MutedText, fontSize = 12.sp); inner() }
        )
    }
}

@Composable
private fun AppTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    minLines: Int = 1, visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 11.sp, color = MutedText) },
        minLines = minLines, maxLines = if (minLines > 1) 7 else 1, singleLine = minLines == 1,
        visualTransformation = visualTransformation, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PanelLift, unfocusedContainerColor = PanelLift,
            focusedBorderColor = Violet, unfocusedBorderColor = Hairline,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Violet
        )
    )
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 20.dp, bottom = 17.dp)) {
        Text(subtitle, color = SoftGold, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun SettingsLabel(label: String) {
    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 13.dp, bottom = 6.dp))
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Hairline),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF29263D)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Violet, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(subtitle, color = MutedText, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", color = MutedText, fontSize = 22.sp)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Surface(color = PanelLift, shape = RoundedCornerShape(13.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = MutedText, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandBadge(48)
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
        Text(subtitle, color = MutedText, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BrandBadge(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp))
        .background(Brush.linearGradient(listOf(Color(0xFFD0C2FF), Color(0xFF745BC4)))),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size((size * .52f).dp))
    }
}

@Composable
private fun CharacterAvatar(character: Character, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp))
        .background(Brush.linearGradient(characterGradient(character.name))), contentAlignment = Alignment.Center) {
        Text(character.initials.take(2), color = Color.White, fontSize = (size * .31f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun characterGradient(name: String): List<Color> {
    val palette = listOf(
        listOf(Color(0xFF62518D), Color(0xFF302C4B)),
        listOf(Color(0xFF3B7181), Color(0xFF233747)),
        listOf(Color(0xFF9B6B68), Color(0xFF48334B)),
        listOf(Color(0xFF557B6C), Color(0xFF263F43))
    )
    val hash = if (name.hashCode() == Int.MIN_VALUE) 0 else kotlin.math.abs(name.hashCode())
    return palette[hash % palette.size]
}
