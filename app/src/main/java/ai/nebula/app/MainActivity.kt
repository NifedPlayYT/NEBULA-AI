package ai.nebula.app

import android.os.Bundle
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF0B0C13)
private val Card = Color(0xFF141624)
private val Field = Color(0xFF1D2030)
private val Purple = Color(0xFFAA91FF)
private val Yellow = Color(0xFFFFD990)
private val Muted = Color(0xFF989BAD)
private val Border = Color(0xFF292D40)

@Composable private fun rememberPortraitSheet(): ImageBitmap? {
 val context = LocalContext.current
 return remember(context) {
  runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.nebula_portraits)?.asImageBitmap() }.getOrNull()
 }
}

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
  window.statusBarColor = android.graphics.Color.rgb(16, 16, 24)
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  val p = getSharedPreferences("nebula", MODE_PRIVATE)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme(background=Bg, surface=Card, primary=Purple, secondary=Yellow, onBackground=Color.White, onSurface=Color.White)) {
    Nebula(p.getString("base","https://api.orcarouter.ai/v1")!!, p.getString("model","deepseek/deepseek-v4.1-flash-free")!!, p.getString("key","")!!, p.getBoolean("clean_mode",true), p.getString("chats","[]")!!, p.getString("custom_people","[]")!!, { b,m,k,clean -> p.edit().putString("base",b).putString("model",m).putString("key",k).putBoolean("clean_mode",clean).apply() }, { json -> p.edit().putString("chats",json).apply() }, { json -> p.edit().putString("custom_people",json).apply() })
   }
  }
 }
}
private data class Person(val name:String,val creator:String,val bio:String,val category:String,val initials:String)
private data class Line(val user:Boolean,val text:String)
private data class ChatSession(val id:String,val person:Person,val lines:List<Line>,val updatedAt:Long)
private val people = listOf(
 Person("Рин Итоши","@nebula","Холодный и расчётливый футболист. Не любит пустых разговоров.","Аниме","РИ"),
 Person("Астра","@nebula","Спокойная, внимательная, с тонким чувством юмора.","ОС","А"),
 Person("Кай","@nebula","Ироничный напарник, который любит приключения.","Игры","К"),
 Person("Мира","@nebula","Загадочная собеседница со своей историей.","Фэнтези","М")
)

@Composable private fun Nebula(base0:String, model0:String, key0:String, clean0:Boolean, chatsJson0:String, customPeopleJson0:String, save:(String,String,String,Boolean)->Unit, saveChats:(String)->Unit, savePeople:(String)->Unit) {
 var page by remember { mutableStateOf("home") }
 var chatReturnPage by remember { mutableStateOf("home") }
 var settingsReturnPage by remember { mutableStateOf("home") }
 var selected by remember { mutableStateOf(people[0]) }
 val customPeople = remember { mutableStateListOf<Person>().apply { addAll(decodePeople(customPeopleJson0)) } }
 val allPeople = people + customPeople.toList()
 var category by remember { mutableStateOf("Для вас") }
 var search by remember { mutableStateOf("") }
 var base by remember { mutableStateOf(base0) }
 var model by remember { mutableStateOf(model0) }
 var apiKey by remember { mutableStateOf(key0) }
 var cleanModeState by remember { mutableStateOf(clean0) }
 var name by remember { mutableStateOf("") }
 var bio by remember { mutableStateOf("") }
 var input by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf("") }
 val lines = remember { mutableStateListOf<Line>() }
 val sessions = remember { mutableStateListOf<ChatSession>().apply { addAll(decodeChats(chatsJson0)) } }
 var currentChatId by remember { mutableStateOf("") }
 val persistCurrentChat = {
  if(currentChatId.isNotBlank()) {
   val item=ChatSession(currentChatId,selected,lines.takeLast(120),System.currentTimeMillis())
   val index=sessions.indexOfFirst{it.id==currentChatId}
   if(index>=0) sessions[index]=item else sessions.add(0,item)
   sessions.sortByDescending{it.updatedAt}
   while(sessions.size>30) sessions.removeAt(sessions.lastIndex)
   saveChats(encodeChats(sessions.toList()))
  }
 }
 val scope = rememberCoroutineScope()
 val openPerson: (Person)->Unit = { person ->
  chatReturnPage=page
  selected=person
  lines.clear()
  error=""
  input=""
  currentChatId=System.currentTimeMillis().toString()
  lines.add(Line(false,"Привет. Я " + person.name + ". О чём поговорим?"))
  persistCurrentChat()
  page="chat"
 }
 Column(Modifier.fillMaxSize().background(Bg)) {
  AnimatedContent(
   targetState=page,
   modifier=Modifier.weight(1f),
   label="nebula-page-transition",
   transitionSpec={
    (fadeIn(tween(180)) + slideInHorizontally(tween(220)){it/18}) togetherWith
     (fadeOut(tween(120)) + slideOutHorizontally(tween(160)){-it/20})
   }
  ) { currentPage ->
   when(currentPage) {
    "home" -> Home(allPeople,category,{category=it},search,{search=it},onCreate={page="create"},onOpen=openPerson)
    "create" -> CreatePersonScreen(name,{name=it},bio,{bio=it},onBack={page="home"},onCreate={
     val created=Person(name.ifBlank{"Новый персонаж"},"@you",bio,"ОС","✦")
     customPeople.add(created)
     savePeople(encodePeople(customPeople.toList()))
     name=""
     bio=""
     openPerson(created)
    })
    "settings" -> SettingsScreen(base,{base=it},model,{model=it},apiKey,{apiKey=it},cleanModeState,{cleanModeState=it},{save(base,model,apiKey,cleanModeState);page=settingsReturnPage},{page=settingsReturnPage})
    "profile" -> ProfileScreen(sessions.size,allPeople.size,onCreate={page="create"},onChats={page="chats"},onSettings={settingsReturnPage="profile";page="settings"})
    "chats" -> ChatsScreen(sessions.toList(),onOpen={session ->
     chatReturnPage="chats"
     currentChatId=session.id
     selected=session.person
     lines.clear()
     lines.addAll(session.lines)
     input=""
     error=""
     page="chat"
    },onDelete={id -> sessions.removeAll{it.id==id};saveChats(encodeChats(sessions.toList()))},onCatalog={page="home"})
    "chat" -> {
     val avatarIndex=allPeople.indexOfFirst{it.name==selected.name}.coerceAtLeast(0)
     ChatScreen(selected,avatarIndex,lines.toList(),busy,error,input,{input=it},
      onBack={page=chatReturnPage},
      onSettings={settingsReturnPage="chat";page="settings"},
      onSend={sent ->
       input=""
       lines.add(Line(true,sent))
       lines.add(Line(false,""))
       val replyIndex=lines.lastIndex
       busy=true
       error=""
       persistCurrentChat()
       scope.launch {
        try {
         askAiStream(base,model,apiKey,selected.name,selected.bio,cleanModeState,lines.dropLast(1).toList()) { chunk ->
          if(replyIndex < lines.size) lines[replyIndex]=Line(false,lines[replyIndex].text+chunk)
         }
         if(replyIndex < lines.size && lines[replyIndex].text.isBlank()) error="Модель вернула пустой ответ."
        } catch(e:Exception) {
         error=e.message?:"Ошибка API"
         if(replyIndex < lines.size && lines[replyIndex].text.isBlank()) lines.removeAt(replyIndex)
        } finally {
         persistCurrentChat()
         busy=false
        }
       }
      })
    }
    else -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Страница не найдена",color=Muted)}
   }
  }
  if(page=="home" || page=="chats" || page=="profile") {
   BottomTabBar(
    current=page,
    onSelect={ page=it }
   )
  }
 }
}

@Composable private fun BottomTabBar(current:String,onSelect:(String)->Unit) {
 Surface(color=Color(0xFF11131E),tonalElevation=0.dp,shadowElevation=16.dp,modifier=Modifier.fillMaxWidth().navigationBarsPadding()) {
  Column {
   Divider(color=Color(0xFF24283A),thickness=0.8.dp)
   Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
    BottomTab("chats","Чаты",Icons.Outlined.Forum,current=="chats",Modifier.weight(1f)){onSelect("chats")}
    BottomTab("home","Каталог",Icons.Outlined.Explore,current=="home",Modifier.weight(1f)){onSelect("home")}
    BottomTab("profile","Профиль",Icons.Outlined.Person,current=="profile",Modifier.weight(1f)){onSelect("profile")}
   }
  }
 }
}
@Composable private fun BottomTab(key:String,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
 Column(modifier=modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).clickable{onClick()}.padding(vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
  Box(contentAlignment=Alignment.Center) {
   if(selected) Box(Modifier.size(width=58.dp,height=31.dp).background(Color(0xFF302747),RoundedCornerShape(16.dp)))
   Icon(icon,contentDescription=label,tint=if(selected)Color(0xFFD0C1FF) else Muted,modifier=Modifier.size(21.dp))
  }
  Spacer(Modifier.height(3.dp))
  Text(label,fontSize=10.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=if(selected)Color(0xFFD0C1FF) else Muted)
 }
}

private fun encodePeople(items:List<Person>):String = JSONArray().apply {
 items.forEach { put(JSONObject().put("name",it.name).put("creator",it.creator).put("bio",it.bio).put("category",it.category).put("initials",it.initials)) }
}.toString()

private fun decodePeople(json:String):List<Person> = try {
 val array=JSONArray(json)
 (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { Person(it.optString("name","Новый персонаж"),it.optString("creator","@you"),it.optString("bio",""),it.optString("category","ОС"),it.optString("initials","✦")) } }
} catch (_:Exception) { emptyList() }

private fun encodeChats(chats:List<ChatSession>):String = JSONArray().apply {
 chats.forEach { chat ->
  put(JSONObject().put("id",chat.id).put("updatedAt",chat.updatedAt)
   .put("person",JSONObject().put("name",chat.person.name).put("creator",chat.person.creator).put("bio",chat.person.bio).put("category",chat.person.category).put("initials",chat.person.initials))
   .put("lines",JSONArray().apply { chat.lines.forEach { put(JSONObject().put("user",it.user).put("text",it.text)) } }))
 }
}.toString()

private fun decodeChats(json:String):List<ChatSession> = try {
 val array=JSONArray(json)
 (0 until array.length()).mapNotNull { i ->
  val obj=array.optJSONObject(i) ?: return@mapNotNull null
  val p=obj.optJSONObject("person") ?: return@mapNotNull null
  val person=Person(p.optString("name","Персонаж"),p.optString("creator","@nebula"),p.optString("bio",""),p.optString("category","ОС"),p.optString("initials","✦"))
  val raw=obj.optJSONArray("lines") ?: JSONArray()
  val chatLines=(0 until raw.length()).mapNotNull { n -> raw.optJSONObject(n)?.let { Line(it.optBoolean("user"),it.optString("text")) } }
  ChatSession(obj.optString("id",i.toString()),person,chatLines,obj.optLong("updatedAt"))
 }
} catch (_:Exception) { emptyList() }

@Composable private fun SettingsScreen(base:String,onBase:(String)->Unit,model:String,onModel:(String)->Unit,key:String,onKey:(String)->Unit,clean:Boolean,onClean:(Boolean)->Unit,onSave:()->Unit,onBack:()->Unit) {
 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp)) {
  PageHeader("Настройки","Подключение и поведение NEBULA",onBack)
  Spacer(Modifier.height(18.dp))
  Text("СТИЛЬ ОБЩЕНИЯ",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
  Surface(color=Card,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=9.dp)) {
   Column(Modifier.padding(17.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(42.dp).background(Color(0xFF302747),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) { Icon(Icons.Outlined.AutoAwesome,null,tint=Purple) }
     Spacer(Modifier.width(12.dp))
     Column(Modifier.weight(1f)) {
      Text("Чистый режим",fontWeight=FontWeight.Bold,fontSize=15.sp)
      Text("Более мягкая и нейтральная манера речи",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
     }
     Switch(checked=clean,onCheckedChange=onClean)
    }
    Divider(color=Border,modifier=Modifier.padding(vertical=13.dp))
    Text(if(clean) "Включён: без мата и грубых выражений." else "Выключен: более свободная разговорная речь, сленг и эмоциональность. Общие правила безопасности продолжают действовать.",fontSize=12.sp,color=Muted,lineHeight=18.sp)
   }
  }
  Text("ПОДКЛЮЧЕНИЕ AI",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,modifier=Modifier.padding(top=25.dp,bottom=9.dp))
  Surface(color=Card,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(16.dp)) {
    Text("Адрес API",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFE8E6F2))
    Spacer(Modifier.height(7.dp))
    FieldInput(base,onBase,"https://api.orcarouter.ai/v1")
    Text("Модель",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFE8E6F2),modifier=Modifier.padding(top=14.dp))
    Spacer(Modifier.height(7.dp))
    FieldInput(model,onModel,"Название модели")
    Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
     AssistChip(onClick={onModel("deepseek/deepseek-v4.1-flash-free")},label={Text("DeepSeek Free",fontSize=11.sp)},colors=AssistChipDefaults.assistChipColors(containerColor=Field, labelColor=Color.White),border=BorderStroke(1.dp,Border))
     AssistChip(onClick={onModel("orcarouter/free")},label={Text("OrcaRouter Free",fontSize=11.sp)},colors=AssistChipDefaults.assistChipColors(containerColor=Field, labelColor=Color.White),border=BorderStroke(1.dp,Border))
    }
    var revealKey by remember { mutableStateOf(false) }
    Text("API-ключ",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFE8E6F2),modifier=Modifier.padding(top=14.dp,bottom=7.dp))
    OutlinedTextField(value=key,onValueChange=onKey,placeholder={Text("API-ключ провайдера",color=Muted)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),visualTransformation=if(revealKey)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={revealKey=!revealKey}){Icon(if(revealKey)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(revealKey)"Скрыть ключ" else "Показать ключ",tint=Muted)}},colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Field,unfocusedContainerColor=Field,focusedBorderColor=Purple,unfocusedBorderColor=Border,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedPlaceholderColor=Muted,unfocusedPlaceholderColor=Muted))
   }
  }
  Surface(color=Color(0xFF1A1B29),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=13.dp)) {
   Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top) {
    Icon(Icons.Outlined.Lock,null,tint=Yellow,modifier=Modifier.padding(top=1.dp))
    Spacer(Modifier.width(10.dp))
    Text("Ключ хранится в настройках этого устройства. Не вставляй личный API-ключ в исходный код или публичные сборки.",fontSize=11.sp,color=Muted,lineHeight=17.sp)
   }
  }
  Button(onClick=onSave,modifier=Modifier.fillMaxWidth().padding(top=19.dp).height(54.dp),shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple,contentColor=Color(0xFF171321))) {
   Text("Сохранить настройки",fontWeight=FontWeight.ExtraBold,fontSize=14.sp)
  }
  Spacer(Modifier.height(24.dp))
 }
}

@Composable private fun PageHeader(title:String,subtitle:String,onBack:(()->Unit)?=null,action:(()->Unit)?=null) {
 Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
  if(onBack!=null) {
   IconButton(onClick=onBack,modifier=Modifier.size(42.dp).background(Card,CircleShape)) { Icon(Icons.Outlined.ArrowBack,null,tint=Color.White) }
   Spacer(Modifier.width(12.dp))
  } else {
   BrandMark(38)
   Spacer(Modifier.width(11.dp))
  }
  Column(Modifier.weight(1f)) {
   Text(title,fontSize=21.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.4).sp)
   Text(subtitle,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
  }
  if(action!=null) IconButton(onClick=action,modifier=Modifier.size(42.dp).background(Card,CircleShape)) { Icon(Icons.Outlined.Add,null,tint=Purple) }
 }
}

@Composable private fun TypingIndicator() {
 val transition=rememberInfiniteTransition(label="typing-dots")
 Row(Modifier.background(Color(0xF0191C2A),RoundedCornerShape(18.dp)).border(1.dp,Border,RoundedCornerShape(18.dp)).padding(horizontal=15.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
  repeat(3) { index ->
   val alpha by transition.animateFloat(initialValue=0.25f,targetValue=1f,animationSpec=infiniteRepeatable(animation=tween(520,delayMillis=index*150),repeatMode=RepeatMode.Reverse),label="typing-dot-" + index)
   Box(Modifier.size(6.dp).background(Purple.copy(alpha=alpha),CircleShape))
  }
  Spacer(Modifier.width(3.dp))
  Text("печатает",color=Muted,fontSize=12.sp)
 }
}

@Composable private fun RichMessageText(text:String,user:Boolean) {
 val styled=remember(text,user) {
  buildAnnotatedString {
   val rows=text.replace("\r\n","\n").split("\n")
   rows.forEachIndexed { index,raw ->
    val isDialogue=raw.startsWith("- ")
    if(isDialogue) withStyle(SpanStyle(color=if(user)Color.White else Purple,fontWeight=FontWeight.Bold)){append("— ")}
    val content=if(isDialogue)raw.removePrefix("- ") else raw
    val action=Regex("\\*([^*\\n]+)\\*")
    var cursor=0
    action.findAll(content).forEach { match ->
     append(content.substring(cursor,match.range.first))
     withStyle(SpanStyle(color=if(user)Color.White.copy(alpha=.78f) else Muted,fontStyle=FontStyle.Italic,fontSize=14.sp)){append(match.groupValues[1])}
     cursor=match.range.last+1
    }
    append(content.substring(cursor))
    if(index<rows.lastIndex) append("\n")
   }
  }
 }
 Text(styled,modifier=Modifier.padding(horizontal=15.dp,vertical=12.dp),fontSize=15.sp,lineHeight=22.sp,color=if(user)Color.White else Color(0xFFF1F0F8))
}

@Composable private fun Home(peopleList:List<Person>,category:String,onCategory:(String)->Unit,search:String,onSearch:(String)->Unit,onCreate:()->Unit,onOpen:(Person)->Unit) {
 val categories=listOf("Для вас","ОС","Аниме","Игры","Фэнтези","Реализм")
 val shown=peopleList.filter{(category=="Для вас"||it.category==category)&&(it.name.contains(search,true)||it.bio.contains(search,true))}
 Column(Modifier.fillMaxSize().background(Bg)) {
  Column(Modifier.padding(horizontal=19.dp)) {
   PageHeader("NEBULA AI","ТВОЯ ВСЕЛЕННАЯ ИСТОРИЙ",action=onCreate)
   Text("Найди своего",fontSize=29.sp,fontWeight=FontWeight.Black,color=Color.White,modifier=Modifier.padding(top=17.dp))
   Text("собеседника",fontSize=29.sp,fontWeight=FontWeight.Black,color=Purple)
   Text("Персонажи, истории и диалоги — в одном месте.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=5.dp,bottom=16.dp))
   Surface(color=Color(0xFF171A28),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
    Row(Modifier.padding(horizontal=15.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
     Icon(Icons.Outlined.Search,null,tint=Muted,modifier=Modifier.size(20.dp))
     Spacer(Modifier.width(10.dp))
     BasicTextField(search,onSearch,modifier=Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=14.sp),decorationBox={inner->if(search.isEmpty())Text("Поиск персонажей",color=Muted,fontSize=14.sp);inner()})
     if(search.isNotEmpty()) IconButton(onClick={onSearch("")},modifier=Modifier.size(24.dp)){Icon(Icons.Outlined.Close,null,tint=Muted,modifier=Modifier.size(17.dp))}
    }
   }
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start=19.dp,end=19.dp,top=15.dp,bottom=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
   categories.forEach { item ->
    val active=category==item
    Surface(color=if(active)Purple else Color(0xFF171A28),shape=RoundedCornerShape(30.dp),border=if(active)null else BorderStroke(1.dp,Border),modifier=Modifier.clickable{onCategory(item)}) {
     Text(item,fontSize=12.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium,color=if(active)Color(0xFF171321) else Muted,modifier=Modifier.padding(horizontal=14.dp,vertical=9.dp))
    }
   }
  }
  if(shown.isEmpty()) {
   Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
     Icon(Icons.Outlined.SearchOff,null,tint=Muted,modifier=Modifier.size(34.dp))
     Text("Ничего не найдено",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
     Text("Попробуй изменить запрос или категорию",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))
    }
   }
  } else {
   Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
     Text("ПОПУЛЯРНЫЕ ПЕРСОНАЖИ",fontSize=10.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp,modifier=Modifier.weight(1f))
     Text(shown.size.toString() + " персонажа",fontSize=10.sp,color=Muted)
    }
    shown.chunked(2).forEach { rowPeople ->
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(11.dp)) {
      rowPeople.forEach { person ->
       val personIndex=peopleList.indexOf(person).coerceAtLeast(0)
       PersonCard(person,personIndex,Modifier.weight(1f),onOpen)
      }
      if(rowPeople.size==1) Spacer(Modifier.weight(1f))
     }
    }
    Surface(color=Color(0xFF171A28),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=3.dp,bottom=20.dp).clickable{onCreate()}) {
     Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(42.dp).background(Color(0xFF302747),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Add,null,tint=Purple,modifier=Modifier.size(23.dp))}
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)){Text("Создать своего персонажа",fontWeight=FontWeight.Bold,fontSize=14.sp);Text("Задай ему имя и характер",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=3.dp))}
      Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
     }
    }
   }
  }
 }
}

@Composable private fun PersonCard(p:Person,index:Int,modifier:Modifier=Modifier,onOpen:(Person)->Unit) {
 Surface(color=Card,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Border),modifier=modifier.clickable{onOpen(p)}) {
  Box(Modifier.fillMaxWidth().height(224.dp)) {
   val image=rememberPortraitSheet()
   val tileWidth=(image?.width?:2)/2
   val tileHeight=(image?.height?:2)/2
   Canvas(Modifier.fillMaxSize()) {
    drawRect(Brush.linearGradient(personColors(index)))
    if(index in 0..1 && image!=null) {
     drawImage(image,srcOffset=IntOffset(index*tileWidth,0),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(size.width.toInt(),size.height.toInt()))
    }
    drawRect(Brush.verticalGradient(listOf(Color(0x0010121B),Color(0x2010121B),Color(0xF20B0C13))))
   }
   if(index !in 0..1) {
    Box(Modifier.align(Alignment.Center).size(84.dp).background(Color.White.copy(alpha=.08f),CircleShape),contentAlignment=Alignment.Center) {
     Text(p.initials.ifBlank{"✦"},fontSize=31.sp,fontWeight=FontWeight.Black,color=Color.White.copy(alpha=.88f))
    }
   }
   Surface(color=Color(0xB9151723),shape=RoundedCornerShape(30.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),modifier=Modifier.align(Alignment.TopStart).padding(10.dp)) {
    Text(p.category.uppercase(),fontSize=8.sp,color=Color.White,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
   }
   Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(29.dp).background(Color(0xAA11131E),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Yellow,modifier=Modifier.size(15.dp))}
   Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal=12.dp,vertical=13.dp)) {
    Text(p.name,fontSize=16.sp,fontWeight=FontWeight.ExtraBold,color=Color.White,maxLines=1)
    Text(p.bio.ifBlank{"Новая история начинается здесь."},color=Color.White.copy(alpha=.75f),fontSize=10.sp,lineHeight=14.sp,maxLines=2,modifier=Modifier.padding(top=4.dp))
    Row(Modifier.fillMaxWidth().padding(top=9.dp),verticalAlignment=Alignment.CenterVertically) {
     Text("Открыть диалог",fontSize=10.sp,color=Color(0xFFD7CCFF),fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
     Icon(Icons.Outlined.ArrowForward,null,tint=Color(0xFFD7CCFF),modifier=Modifier.size(16.dp))
    }
   }
  }
 }
}

private fun personColors(index:Int):List<Color> = when(((index%4)+4)%4) {
 0 -> listOf(Color(0xFF40345F),Color(0xFF191C31))
 1 -> listOf(Color(0xFF31505A),Color(0xFF171D2E))
 2 -> listOf(Color(0xFF633F55),Color(0xFF21192D))
 else -> listOf(Color(0xFF3A4B75),Color(0xFF191D32))
}

@Composable private fun ProfileScreen(chatCount:Int,personCount:Int,onCreate:()->Unit,onChats:()->Unit,onSettings:()->Unit) {
 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal=19.dp,vertical=12.dp)) {
  PageHeader("Профиль","ТВОЁ ПРОСТРАНСТВО NEBULA")
  Surface(color=Card,shape=RoundedCornerShape(25.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=13.dp)) {
   Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF33294F),Color(0xFF181B2B)))).padding(22.dp)) {
    Column(horizontalAlignment=Alignment.Start) {
     Box(Modifier.size(62.dp).background(Brush.linearGradient(listOf(Color(0xFFBBA7FF),Color(0xFF7659D2))),RoundedCornerShape(21.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Color.White,modifier=Modifier.size(31.dp))}
     Text("Гость NEBULA",fontSize=21.sp,fontWeight=FontWeight.ExtraBold,color=Color.White,modifier=Modifier.padding(top=15.dp))
     Text("Твоя личная вселенная диалогов",fontSize=12.sp,color=Color.White.copy(alpha=.72f),modifier=Modifier.padding(top=4.dp))
     Text("Локальный профиль · данные на устройстве",fontSize=10.sp,color=Color.White.copy(alpha=.52f),modifier=Modifier.padding(top=12.dp))
    }
   }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(top=13.dp)) {
   ProfileStat("СОХРАНЁННЫЕ ЧАТЫ",chatCount.toString(),Modifier.weight(1f))
   ProfileStat("ПЕРСОНАЖИ",personCount.toString(),Modifier.weight(1f))
  }
  Text("БЫСТРЫЙ ДОСТУП",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp,modifier=Modifier.padding(top=25.dp,bottom=3.dp))
  ProfileAction("Мои чаты","Продолжить сохранённые диалоги",Icons.Outlined.Forum,onChats)
  ProfileAction("Создать персонажа","Придумать нового собеседника",Icons.Outlined.AddCircleOutline,onCreate)
  ProfileAction("Настройки AI","Модель, API и стиль общения",Icons.Outlined.Tune,onSettings)
  Surface(color=Color(0xFF141724),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=20.dp,bottom=18.dp)) {
   Row(Modifier.padding(15.dp),verticalAlignment=Alignment.Top) {
    Icon(Icons.Outlined.Info,null,tint=Muted)
    Spacer(Modifier.width(10.dp))
    Text("NEBULA сохраняет историю диалогов и созданных персонажей локально. Для ответов AI нужен действующий API-провайдер.",fontSize=11.sp,color=Muted,lineHeight=17.sp)
   }
  }
 }
}

@Composable private fun ChatsScreen(sessions:List<ChatSession>,onOpen:(ChatSession)->Unit,onDelete:(String)->Unit,onCatalog:()->Unit) {
 Column(Modifier.fillMaxSize().background(Bg)) {
  Column(Modifier.padding(horizontal=19.dp)) {
   PageHeader("Мои чаты","ТВОИ СОХРАНЁННЫЕ ИСТОРИИ")
   Text("Продолжи разговор",fontSize=25.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(top=15.dp))
   Text("Все диалоги остаются на этом устройстве.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=15.dp))
  }
  if(sessions.isEmpty()) {
   Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
     Box(Modifier.size(66.dp).background(Color(0xFF252039),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Forum,null,tint=Purple,modifier=Modifier.size(29.dp))}
     Text("Здесь пока пусто",fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.padding(top=14.dp))
     Text("Открой персонажа и начни первый диалог.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=5.dp))
     Button(onClick=onCatalog,modifier=Modifier.padding(top=17.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple,contentColor=Color(0xFF171321))){Text("Открыть каталог",fontWeight=FontWeight.Bold)}
    }
   }
  } else {
   LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=14.dp),contentPadding=PaddingValues(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
    items(sessions.sortedByDescending{it.updatedAt}) { session ->
     Surface(color=Color(0xFF151826),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
      Row(Modifier.clickable{onOpen(session)}.padding(start=12.dp,end=5.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
       val idx=people.indexOfFirst{it.name==session.person.name}.let{if(it>=0)it else 2}
       PortraitAvatar(idx,48,session.person.initials)
       Spacer(Modifier.width(12.dp))
       Column(Modifier.weight(1f)) {
        Text(session.person.name,fontWeight=FontWeight.Bold,fontSize=14.sp)
        Text(session.lines.lastOrNull()?.text?.ifBlank{"Диалог начат"}?:"Диалог начат",color=Muted,fontSize=11.sp,maxLines=1,modifier=Modifier.padding(top=4.dp))
        Text(session.person.category.uppercase(),color=Purple,fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(top=5.dp))
       }
       IconButton(onClick={onDelete(session.id)}){Icon(Icons.Outlined.DeleteOutline,"Удалить чат",tint=Muted,modifier=Modifier.size(19.dp))}
      }
     }
    }
   }
  }
 }
}

@Composable private fun CreatePersonScreen(name:String,onName:(String)->Unit,bio:String,onBio:(String)->Unit,onBack:()->Unit,onCreate:()->Unit) {
 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp)) {
  PageHeader("Новый персонаж","СОЗДАЙ СОБСТВЕННЫЙ ОБРАЗ",onBack)
  Surface(color=Card,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=17.dp)) {
   Column(Modifier.padding(18.dp)) {
    Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.linearGradient(listOf(Color(0xFF453765),Color(0xFF1B2035))),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center) {
     Icon(Icons.Outlined.AutoAwesome,null,tint=Color(0xFFD3C7FF),modifier=Modifier.size(46.dp))
    }
    Text("Имя персонажа",fontWeight=FontWeight.Bold,fontSize=13.sp,modifier=Modifier.padding(top=18.dp,bottom=7.dp))
    FieldInput(name,onName,"Например, Акира")
    Text("Характер и описание",fontWeight=FontWeight.Bold,fontSize=13.sp,modifier=Modifier.padding(top=16.dp,bottom=7.dp))
    FieldInput(bio,onBio,"Расскажи о характере, интересах и манере общения",true)
    Button(onClick=onCreate,enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=19.dp).height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple,contentColor=Color(0xFF171321))) {
     Text("Создать персонажа",fontWeight=FontWeight.ExtraBold)
    }
   }
  }
  Spacer(Modifier.height(24.dp))
 }
}

@Composable private fun ProfileStat(label:String,value:String,modifier:Modifier=Modifier) {
 Surface(color=Card,shape=RoundedCornerShape(19.dp),border=BorderStroke(1.dp,Border),modifier=modifier) {
  Column(Modifier.padding(15.dp)) {
   Text(value,fontSize=26.sp,fontWeight=FontWeight.Black,color=Purple)
   Text(label,fontSize=9.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(top=4.dp))
  }
 }
}
@Composable private fun ProfileAction(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
 Surface(color=Card,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=8.dp).clickable{onClick()}) {
  Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
   Box(Modifier.size(40.dp).background(Color(0xFF29243C),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=Purple,modifier=Modifier.size(21.dp))}
   Spacer(Modifier.width(12.dp))
   Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.Bold,fontSize=13.sp); Text(subtitle,fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=3.dp)) }
   Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
  }
 }
}

@Composable private fun ChatScreen(person:Person,avatarIndex:Int,lines:List<Line>,busy:Boolean,error:String,input:String,onInput:(String)->Unit,onBack:()->Unit,onSettings:()->Unit,onSend:(String)->Unit) {
 val listState=rememberLazyListState()
 val backdrop=rememberPortraitSheet()
 var followOutput by remember { mutableStateOf(true) }
 LaunchedEffect(listState) {
  snapshotFlow {
   val info=listState.layoutInfo
   val lastVisible=info.visibleItemsInfo.lastOrNull()?.index ?: -1
   lastVisible >= info.totalItemsCount-2
  }.collect { followOutput=it }
 }
 LaunchedEffect(lines.size,lines.lastOrNull()?.text?.length,busy,error) {
  if(lines.isNotEmpty() && followOutput) {
   val target=lines.lastIndex+(if(busy)1 else 0)+(if(error.isNotBlank())1 else 0)
   listState.scrollToItem(target.coerceAtLeast(0))
  }
 }
 Box(Modifier.fillMaxSize().background(Bg)) {
  Canvas(Modifier.fillMaxSize()) {
   drawRect(Brush.linearGradient(personColors(avatarIndex)))
   if(avatarIndex in 0..1 && backdrop!=null) {
    val tileWidth=backdrop.width/2
    val tileHeight=backdrop.height/2
    drawImage(backdrop,srcOffset=IntOffset(avatarIndex*tileWidth,0),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(size.width.toInt(),(size.height*.72f).toInt()),alpha=.32f)
   }
   drawRect(Brush.verticalGradient(listOf(Color(0xA6090A12),Color(0xDD0B0C13),Color(0xFF0B0C13))))
  }
  Column(Modifier.fillMaxSize().imePadding()) {
   Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
    IconButton(onClick=onBack,modifier=Modifier.size(42.dp).background(Color(0xCC171A28),CircleShape)){Icon(Icons.Outlined.ArrowBack,null,tint=Color.White)}
    Spacer(Modifier.width(10.dp))
    PortraitAvatar(avatarIndex,42,person.initials)
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
     Text(person.name,fontWeight=FontWeight.Bold,fontSize=15.sp,color=Color.White,maxLines=1)
     Text(if(busy)"Печатает…" else "Персонаж NEBULA",fontSize=10.sp,color=if(busy)Yellow else Muted)
    }
    IconButton(onClick=onSettings,modifier=Modifier.size(42.dp).background(Color(0xCC171A28),CircleShape)){Icon(Icons.Outlined.Tune,null,tint=Color.White)}
   }
   AnimatedVisibility(visible=lines.size<=1,enter=fadeIn(tween(180)),exit=fadeOut(tween(140))) {
    Surface(color=Color(0xD9171A28),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.09f)),modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp).padding(bottom=9.dp)) {
     Column(Modifier.padding(horizontal=15.dp,vertical=12.dp)) {
      Text("ЗНАКОМСТВО",fontSize=9.sp,color=Yellow,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
      Text(person.bio.ifBlank{"Новая история начинается здесь."},fontSize=12.sp,lineHeight=17.sp,color=Color.White.copy(alpha=.82f),modifier=Modifier.padding(top=5.dp))
     }
    }
   }
   LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth().padding(horizontal=13.dp),contentPadding=PaddingValues(top=8.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
    items(lines) { line ->
     Row(Modifier.fillMaxWidth(),horizontalArrangement=if(line.user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
      if(!line.user) { PortraitAvatar(avatarIndex,30,person.initials); Spacer(Modifier.width(7.dp)) }
      Surface(color=if(line.user)Color(0xFF6650A2) else Color(0xF01A1D2B),shape=RoundedCornerShape(topStart=if(line.user)19.dp else 7.dp,topEnd=if(line.user)7.dp else 19.dp,bottomStart=19.dp,bottomEnd=19.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.08f)),modifier=Modifier.widthIn(max=310.dp)) {
       RichMessageText(line.text,line.user)
      }
     }
    }
    if(busy) item { Row(verticalAlignment=Alignment.CenterVertically){PortraitAvatar(avatarIndex,30,person.initials);Spacer(Modifier.width(7.dp));TypingIndicator()} }
    if(error.isNotBlank()) item { Text(error,color=Color(0xFFFFB5B5),fontSize=11.sp,modifier=Modifier.padding(start=38.dp)) }
   }
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.Bottom) {
    Surface(color=Color(0xF0191C2A),shape=RoundedCornerShape(25.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.10f)),modifier=Modifier.weight(1f)) {
     Row(Modifier.padding(start=15.dp,end=6.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.Bottom) {
      Icon(Icons.Outlined.AutoAwesome,null,tint=Purple,modifier=Modifier.padding(bottom=10.dp).size(18.dp))
      Spacer(Modifier.width(9.dp))
      BasicTextField(input,onInput,modifier=Modifier.weight(1f).padding(vertical=9.dp),singleLine=false,maxLines=4,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=14.sp,lineHeight=19.sp),decorationBox={inner->if(input.isEmpty())Text("Напиши сообщение…",color=Muted,fontSize=13.sp);inner()})
      Spacer(Modifier.width(5.dp))
      IconButton(enabled=input.isNotBlank()&&!busy,onClick={onSend(input.trim())},modifier=Modifier.size(40.dp).background(if(input.isNotBlank()&&!busy)Purple else Color(0xFF343747),CircleShape)) {
       Icon(Icons.Outlined.Send,"Отправить",tint=if(input.isNotBlank()&&!busy)Color(0xFF171321) else Muted,modifier=Modifier.size(18.dp))
      }
     }
    }
   }
  }
 }
}

@Composable private fun BrandMark(size:Int) {
 Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color(0xFFBBA7FF),Color(0xFF7659D2))),RoundedCornerShape((size/3).dp)),contentAlignment=Alignment.Center) {
  Icon(Icons.Outlined.AutoAwesome,null,tint=Color.White,modifier=Modifier.size((size*.58f).dp))
 }
}

@Composable private fun PortraitAvatar(index:Int,size:Int,initials:String="✦") {
 val image=rememberPortraitSheet()
 val tileWidth=(image?.width?:2)/2
 val tileHeight=(image?.height?:2)/2
 Box(Modifier.size(size.dp).clip(RoundedCornerShape((size/3).dp)).background(Brush.linearGradient(personColors(index)))) {
  if(index in 0..1 && image!=null) {
   Canvas(Modifier.fillMaxSize()) {
    drawImage(image,srcOffset=IntOffset(index*tileWidth,0),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(size.width.toInt(),size.height.toInt()))
   }
  } else {
   Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
    Text(initials.ifBlank{"✦"},fontSize=(size*.36f).sp,fontWeight=FontWeight.Bold,color=Color.White)
   }
  }
 }
}

@Composable private fun FieldInput(value:String,onChange:(String)->Unit,hint:String,multi:Boolean=false) {
 OutlinedTextField(value,onChange,placeholder={Text(hint,color=Muted,fontSize=13.sp)},modifier=Modifier.fillMaxWidth(),minLines=if(multi)4 else 1,maxLines=if(multi)6 else 1,singleLine=!multi,shape=RoundedCornerShape(16.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Field,unfocusedContainerColor=Field,focusedBorderColor=Purple,unfocusedBorderColor=Border,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedPlaceholderColor=Muted,unfocusedPlaceholderColor=Muted))
}

private suspend fun askAiStream(base:String,model:String,key:String,character:String,personality:String,cleanMode:Boolean,history:List<Line>,onChunk:(String)->Unit)=withContext(Dispatchers.IO){
 require(base.isNotBlank()&&model.isNotBlank()&&key.isNotBlank()){"Открой настройки API и укажи адрес, модель и ключ."}
 val msgs=JSONArray().put(JSONObject().put("role","system").put("content","Ты персонаж по имени $character. Режим общения: ${if(cleanMode) "ЧИСТЫЙ — не используй мат, грубые оскорбления и резкие выражения; говори мягко и уважительно." else "СВОБОДНЫЙ — говори естественно и живо; допустимы разговорный язык, сленг, умеренная ненормативная лексика и яркие эмоциональные реакции, если они уместны. Не смягчай каждую реплику искусственно и не превращай ответы в стерильные формулировки. Сохраняй индивидуальный голос персонажа."}. Не создавай сексуальный или эротический контент. Соблюдай общие правила безопасности независимо от режима. Твоя личность и характер: ${personality.ifBlank { "Самостоятельно поддерживай естественный характер и последовательный стиль общения." }}. Строго придерживайся этой личности в каждом ответе. Сохраняй характер и отвечай естественно. Пиши быстро и по делу: обычно 1–4 коротких предложения. Не повторяй вопрос и не добавляй лишние вступления. В ролевой сцене оформляй действия строго между одиночными звёздочками, например *сел на пол*; каждое действие должно быть отдельным фрагментом. Реплики персонажа в диалоге начинай с новой строки и тире, например: - Привет, как дела? Не используй Markdown-заголовки и лишние списки. В ролевой сцене описывай только нужные действия и реплики, не растягивай ответ без причины. Если пользователь просит подробности — отвечай подробнее."))
 history.takeLast(16).forEach{msgs.put(JSONObject().put("role",if(it.user)"user" else "assistant").put("content",it.text))}
 val body=JSONObject().put("model",model).put("messages",msgs).put("temperature",if(cleanMode)0.72 else 0.86).put("max_tokens",if(cleanMode)450 else 650).put("stream",true).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 val endpoint=(base.trim().trimEnd('/')+"/chat/completions").toHttpUrlOrNull() ?: throw IllegalStateException("Некорректный адрес API. Укажи полный URL, например https://api.example.com/v1")
  val req=Request.Builder().url(endpoint).header("Authorization","Bearer ${key.trim()}").header("Accept","text/event-stream").post(body).build()
 val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).callTimeout(100,TimeUnit.SECONDS).build()
 client.newCall(req).execute().use{res->
  if(!res.isSuccessful){
   res.body?.close()
   val message=when(res.code){
    401 -> "API-ключ не принят. Проверь ключ в настройках."
    403 -> "Провайдер запретил запрос. Проверь доступ к модели и баланс."
    404 -> "Не найден адрес API или модель. Проверь настройки подключения."
    408 -> "Провайдер не успел ответить. Попробуй ещё раз."
    429 -> "Слишком много запросов или достигнут лимит провайдера. Подожди немного и повтори."
    in 500..599 -> "На стороне AI-провайдера временная ошибка (${res.code}). Попробуй позже."
    else -> "Ошибка API ${res.code}. Проверь адрес, модель и настройки провайдера."
   }
   throw IllegalStateException(message)
  }
  val source=res.body?.source()?:throw IllegalStateException("Пустой ответ API")
  while(!source.exhausted()){
   val line=source.readUtf8Line()?:break
   if(!line.startsWith("data:"))continue
   val data=line.removePrefix("data:").trim()
   if(data=="[DONE]")break
   try{
    val event=JSONObject(data)
    event.optJSONObject("error")?.let { throw IllegalStateException(it.optString("message","Провайдер вернул ошибку генерации.")) }
    val delta=event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
    if(delta.isNotEmpty())withContext(Dispatchers.Main){onChunk(delta)}
   }catch(e:org.json.JSONException){}
  }
 }
}
