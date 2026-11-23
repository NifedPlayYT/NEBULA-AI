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

private val Bg = Color(0xFF101018)
private val Card = Color(0xFF1B1B29)
private val Field = Color(0xFF252536)
private val Purple = Color(0xFF9B7BFF)
private val Yellow = Color(0xFFFFD27A)
private val Muted = Color(0xFFAAA9BD)
private val Border = Color(0xFF303047)

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
 var selected by remember { mutableStateOf(people[0]) }
 val customPeople = remember { mutableStateListOf<Person>().apply { addAll(decodePeople(customPeopleJson0)) } }
 val allPeople = people + customPeople
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
 val persistCurrentChat = { if(currentChatId.isNotBlank()){ val item=ChatSession(currentChatId,selected,lines.takeLast(120),System.currentTimeMillis()); val index=sessions.indexOfFirst{it.id==currentChatId}; if(index>=0)sessions[index]=item else sessions.add(0,item); sessions.sortByDescending{it.updatedAt}; while(sessions.size>30)sessions.removeAt(sessions.lastIndex); saveChats(encodeChats(sessions.toList())) } }
 val scope = rememberCoroutineScope()
 Column(Modifier.fillMaxSize().background(Bg)) {
  Row(Modifier.fillMaxWidth().background(Card).padding(horizontal=18.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
   BrandMark(34)
   Spacer(Modifier.width(10.dp))
   Column(Modifier.weight(1f)) {
    Text("NEBULA",fontSize=21.sp,fontWeight=FontWeight.Black,letterSpacing=1.6.sp)
    Text("ТВОЯ ВСЕЛЕННАЯ ИСТОРИЙ",fontSize=9.sp,color=Muted,letterSpacing=1.2.sp)
   }
   IconButton(onClick={page="settings"}) { Icon(Icons.Outlined.Settings,null,tint=Muted) }
  }
   AnimatedContent(
    targetState=page,
    modifier=Modifier.weight(1f),
    label="nebula-page-transition",
    transitionSpec={
     (fadeIn(tween(190)) + slideInHorizontally(tween(220)){it/12}) togetherWith
      (fadeOut(tween(130)) + slideOutHorizontally(tween(170)){-it/16})
    }
   ) { currentPage -> when(currentPage) {
    "home" -> Home(allPeople,category,{category=it},search,{search=it},onCreate={page="create"}) { person ->
     selected=person; lines.clear(); error=""; currentChatId=System.currentTimeMillis().toString()
     lines.add(Line(false,"Привет. Я ${person.name}. О чём поговорим?")); persistCurrentChat(); page="chat"
    }
    "create" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
     Text("‹  НАЗАД",color=Purple,modifier=Modifier.clickable{page="home"})
     Spacer(Modifier.height(22.dp)); Text("НОВЫЙ ПЕРСОНАЖ",fontSize=24.sp,fontWeight=FontWeight.Black)
     Text("Создай собеседника со своим характером.",color=Muted,modifier=Modifier.padding(vertical=8.dp))
     Text("ИМЯ",fontSize=11.sp,color=Muted); FieldInput(name,{name=it},"Например, Акира")
     Spacer(Modifier.height(15.dp)); Text("ХАРАКТЕР И ОПИСАНИЕ",fontSize=11.sp,color=Muted)
     FieldInput(bio,{bio=it},"Опиши личность и манеру общения",true)
     Spacer(Modifier.height(22.dp))
     Button(onClick={val created=Person(name.ifBlank{"Новый персонаж"},"@you",bio,"ОС","✦");customPeople.add(created);savePeople(encodePeople(customPeople.toList()));selected=created;lines.clear();currentChatId=System.currentTimeMillis().toString();lines.add(Line(false,"Привет! Я ${selected.name}."));persistCurrentChat();name="";bio="";page="chat"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Text("Создать персонажа")}
    }
    "settings" -> SettingsScreen(base,{base=it},model,{model=it},apiKey,{apiKey=it},cleanModeState,{cleanModeState=it},{save(base,model,apiKey,cleanModeState);page="home"},{page="home"})
    "profile" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
     Text("ТВОЙ ПРОФИЛЬ",fontSize=25.sp,fontWeight=FontWeight.Black)
     Text("Твоя личная вселенная NEBULA",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=4.dp,bottom=18.dp))
     Surface(color=Card,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
       Box(Modifier.size(82.dp).background(Brush.linearGradient(listOf(Color(0xFFB79CFF),Color(0xFF6241C7))),CircleShape),contentAlignment=Alignment.Center) { Text("✦",fontSize=38.sp,color=Color.White,fontWeight=FontWeight.Black) }
       Text("Гость NEBULA",fontSize=20.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=12.dp))
       Text("Локальный профиль · данные на этом устройстве",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
      }
     }
     Spacer(Modifier.height(16.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      ProfileStat("ЧАТЫ",sessions.size.toString(),Modifier.weight(1f))
      ProfileStat("ПЕРСОНАЖИ",allPeople.size.toString(),Modifier.weight(1f))
     }
     Spacer(Modifier.height(18.dp))
     Text("БЫСТРЫЕ ДЕЙСТВИЯ",fontSize=11.sp,color=Purple,fontWeight=FontWeight.Bold)
     ProfileAction("Создать персонажа","Задай имя и характер",Icons.Outlined.AddCircle){page="create"}
     ProfileAction("Мои чаты","Вернуться к сохранённым диалогам",Icons.Outlined.Forum){page="chats"}
     ProfileAction("Настройки AI","Модель, API и стиль общения",Icons.Outlined.Settings){page="settings"}
     Spacer(Modifier.height(18.dp))
     Text("NEBULA AI",fontSize=11.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
     Text("Твои диалоги и персонажи хранятся локально на устройстве. Для работы AI нужен действующий API-провайдер.",fontSize=12.sp,color=Muted,lineHeight=18.sp,modifier=Modifier.padding(top=7.dp,bottom=18.dp))
    }
    "chat" -> {
     val avatarIndex=allPeople.indexOfFirst{it.name==selected.name}.coerceAtLeast(0)
     val chatListState=rememberLazyListState()
     val backdrop=rememberPortraitSheet()
     val tileW=(backdrop?.width ?: 2)/2; val tileH=(backdrop?.height ?: 2)/2; val tile=(avatarIndex%2)
     LaunchedEffect(lines.size,lines.lastOrNull()?.text,busy,error) {
      if(lines.isNotEmpty()) chatListState.animateScrollToItem(lines.lastIndex)
     }
     Box(Modifier.fillMaxSize()) {
      Canvas(Modifier.fillMaxSize()) {
       if(backdrop != null) drawImage(backdrop,srcOffset=IntOffset((tile%2)*tileW,(tile/2)*tileH),srcSize=IntSize(tileW,tileH),dstSize=IntSize(size.width.toInt(),size.height.toInt()),alpha=0.46f)
       drawRect(Brush.verticalGradient(0f to Color(0xAA08080D),0.34f to Color(0xB0101018),0.72f to Color(0xD8101018),1f to Color(0xF5101018)))
      }
      Column(Modifier.fillMaxSize().imePadding()) {
       Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Surface(color=Color(0xAA17171F),shape=CircleShape,border=BorderStroke(1.dp,Color.White.copy(alpha=.12f))) {
         IconButton(onClick={page="home"}){Icon(Icons.Outlined.ArrowBack,null,tint=Color.White)}
        }
        Spacer(Modifier.width(11.dp))
        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
         PortraitAvatar(avatarIndex,43)
         Spacer(Modifier.width(10.dp))
         Column {
          Text(selected.name,fontWeight=FontWeight.Bold,fontSize=17.sp,color=Color.White)
          Text(if(busy) "Печатает…" else "Персонаж NEBULA",fontSize=11.sp,color=if(busy)Yellow else Color.White.copy(alpha=.68f))
         }
        }
        Surface(color=Color(0xAA17171F),shape=CircleShape,border=BorderStroke(1.dp,Color.White.copy(alpha=.12f))) {
         IconButton(onClick={page="settings"}){Icon(Icons.Outlined.Tune,null,tint=Color.White)}
        }
       }
       Surface(color=Color(0xB92A2928),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.08f)),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(top=8.dp,bottom=12.dp)) {
        Column(Modifier.padding(horizontal=18.dp,vertical=15.dp)) {
         Text("ЗНАКОМСТВО",fontSize=10.sp,color=Yellow,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
         Text(selected.name,fontSize=19.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp))
         Text(selected.bio.ifBlank{"Новая история начинается здесь."},fontSize=13.sp,lineHeight=19.sp,color=Color.White.copy(alpha=.78f),modifier=Modifier.padding(top=4.dp))
        }
       }
       LazyColumn(state=chatListState,modifier=Modifier.weight(1f).fillMaxWidth().padding(horizontal=13.dp),contentPadding=PaddingValues(top=6.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        items(lines){line->AnimatedVisibility(visible=true,enter=fadeIn(tween(190))+slideInVertically(tween(220)){it/8}) {
         Row(Modifier.fillMaxWidth(),horizontalArrangement=if(line.user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
          if(!line.user){PortraitAvatar(avatarIndex,34);Spacer(Modifier.width(8.dp))}
          Surface(color=if(line.user)Color(0xE66F55B9) else Color(0xE62B2A29),shape=RoundedCornerShape(topStart=if(line.user)20.dp else 7.dp,topEnd=if(line.user)7.dp else 20.dp,bottomStart=20.dp,bottomEnd=20.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=if(line.user) .10f else .09f)),modifier=Modifier.widthIn(max=310.dp)) {
           RichMessageText(line.text,line.user)
          }
         }
        }}
        if(busy)item { Row(verticalAlignment=Alignment.CenterVertically){PortraitAvatar(avatarIndex,30);Spacer(Modifier.width(8.dp));TypingIndicator()} }
        if(error.isNotBlank())item { Text(error,color=Color(0xFFFFAAAA),fontSize=12.sp,modifier=Modifier.padding(start=42.dp)) }
       }
       Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
        Surface(color=Color(0xE62A2929),shape=RoundedCornerShape(30.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.14f)),modifier=Modifier.weight(1f)) {
         Row(Modifier.padding(start=14.dp,end=7.dp,top=7.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically) {
          Icon(Icons.Outlined.AutoAwesome,null,tint=Color.White.copy(alpha=.8f),modifier=Modifier.size(21.dp))
          Spacer(Modifier.width(10.dp))
          BasicTextField(input,{input=it},modifier=Modifier.weight(1f).padding(vertical=10.dp),singleLine=false,maxLines=4,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=15.sp),decorationBox={inner->if(input.isEmpty())Text("Сообщение ${selected.name}…",color=Color.White.copy(alpha=.55f),fontSize=14.sp);inner()})
          Spacer(Modifier.width(6.dp))
          IconButton(enabled=input.isNotBlank()&&!busy,onClick={
           val sent=input.trim();input="";lines.add(Line(true,sent));lines.add(Line(false,""));val replyIndex=lines.lastIndex;busy=true;error="";persistCurrentChat()
           scope.launch{try{askAiStream(base,model,apiKey,selected.name,selected.bio,cleanModeState,lines.dropLast(1).toList()){chunk->lines[replyIndex]=Line(false,lines[replyIndex].text+chunk)};if(lines[replyIndex].text.isBlank())error="Модель вернула пустой ответ."}catch(e:Exception){error=e.message?:"Ошибка API";if(lines[replyIndex].text.isBlank())lines.removeAt(replyIndex)}finally{persistCurrentChat();busy=false}}
          },modifier=Modifier.size(42.dp).background(if(input.isNotBlank()&&!busy)Purple else Color(0xFF57515F),CircleShape)) {
           Icon(Icons.Outlined.Send,"Отправить",tint=Color.White,modifier=Modifier.size(19.dp))
          }
         }
        }
       }
      }
     }
    }
    "chats" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
      Text("МОИ ЧАТЫ",fontSize=24.sp,fontWeight=FontWeight.Black); Text("Твои сохранённые диалоги",color=Muted,modifier=Modifier.padding(top=4.dp,bottom=16.dp))
      if(sessions.isEmpty()) Text("Пока здесь пусто. Открой персонажа, чтобы начать диалог.",color=Muted)
      sessions.sortedByDescending{it.updatedAt}.forEach { session ->
       Surface(color=Card,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)) {
        Row(Modifier.clickable { currentChatId=session.id; selected=session.person; lines.clear(); lines.addAll(session.lines); error=""; page="chat" }.padding(start=15.dp,end=7.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
         Column(Modifier.weight(1f).padding(vertical=5.dp)) { Text(session.person.name,fontWeight=FontWeight.Bold,fontSize=16.sp); Text(session.lines.lastOrNull()?.text?.ifBlank { "Диалог начат" } ?: "Диалог начат",color=Muted,fontSize=12.sp,maxLines=2,modifier=Modifier.padding(top=5.dp)) }
         IconButton(onClick={sessions.removeAll{it.id==session.id};saveChats(encodeChats(sessions.toList()))}) { Icon(Icons.Outlined.DeleteOutline,"Удалить чат",tint=Muted) }
        }
       }
      }
      Button(onClick={page="home"},modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Text("К персонажам")}
     }
     else -> Column(Modifier.padding(22.dp)){Text("Страница не найдена",color=Muted)}
   }
   }
   if(page != "chat") BottomTabBar(
    current = when(page) { "chats" -> "chats"; "profile", "settings", "create" -> "profile"; else -> "home" },
    onSelect = { page = it }
   )
  }

}

@Composable private fun BottomTabBar(current:String,onSelect:(String)->Unit) {
 Surface(color=Color(0xFF171722),tonalElevation=8.dp,shadowElevation=12.dp) {
  Column {
   Divider(color=Color(0xFF2A2939),thickness=0.7.dp)
   Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp).padding(horizontal=9.dp),verticalAlignment=Alignment.CenterVertically) {
    BottomTab("chats","Чаты",Icons.Outlined.Forum,current=="chats",Modifier.weight(1f)){onSelect("chats")}
    BottomTab("home","Каталог",Icons.Outlined.GridView,current=="home",Modifier.weight(1f)){onSelect("home")}
    BottomTab("profile","Профиль",Icons.Outlined.Person,current=="profile",Modifier.weight(1f)){onSelect("profile")}
   }
  }
 }
}
@Composable private fun BottomTab(key:String,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
 Column(modifier=modifier.fillMaxHeight().clickable{onClick()}.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
  Box(contentAlignment=Alignment.Center) {
   if(selected) Box(Modifier.size(width=54.dp,height=29.dp).background(Color(0xFF352A52),RoundedCornerShape(15.dp)))
   Icon(icon,contentDescription=label,tint=if(selected)Color(0xFFC7B4FF) else Muted,modifier=Modifier.size(22.dp))
  }
  Spacer(Modifier.height(3.dp))
  Text(label,fontSize=10.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=if(selected)Color(0xFFC7B4FF) else Muted)
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

@Composable private fun SettingsScreen(base:String,onBase:(String)->Unit,model:String,onModel:(String)->Unit,key:String,onKey:(String)->Unit,clean:Boolean,onClean:(Boolean)->Unit,onSave:()->Unit,onBack:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,null,tint=Color.White)};Text("Настройки",fontSize=24.sp,fontWeight=FontWeight.Black)}
  Text("ПЕРСОНАЛИЗАЦИЯ",fontSize=11.sp,color=Purple,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=20.dp,bottom=10.dp))
  Surface(color=Card,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Shield,null,tint=Purple);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Чистый режим",fontWeight=FontWeight.Bold);Text("Управляет манерой речи и уровнем разговорной лексики.",fontSize=12.sp,color=Muted)};Switch(checked=clean,onCheckedChange=onClean)};Text(if(clean) "Чистый режим: без мата и грубых выражений." else "Свободный режим: естественная разговорная речь, сленг и эмоциональные реакции. Общие правила безопасности сохраняются.",fontSize=11.sp,color=Muted)}}
  Text("ПОДКЛЮЧЕНИЕ AI",fontSize=11.sp,color=Purple,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=8.dp))
  Text("Адрес API",fontSize=12.sp,color=Muted);FieldInput(base,onBase,"https://api.orcarouter.ai/v1")
  Spacer(Modifier.height(12.dp));Text("Модель",fontSize=12.sp,color=Muted);FieldInput(model,onModel,"Название модели")
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){TextButton(onClick={onModel("deepseek/deepseek-v4.1-flash-free")}){Text("DeepSeek Free",fontSize=11.sp)};TextButton(onClick={onModel("orcarouter/free")}){Text("OrcaRouter Free",fontSize=11.sp)}}
  var revealKey by remember { mutableStateOf(false) }
  Text("API-ключ",fontSize=12.sp,color=Muted)
  OutlinedTextField(value=key,onValueChange=onKey,placeholder={Text("API-ключ провайдера",color=Muted)},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=if(revealKey) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={revealKey=!revealKey}){Icon(if(revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(revealKey) "Скрыть ключ" else "Показать ключ",tint=Muted)}},colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Purple,unfocusedBorderColor=Color(0xFF555555),focusedTextColor=Color.White,unfocusedTextColor=Color.White))
  Surface(color=Field,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Text("Не публикуй APK с личным API-ключом. Запросы могут тарифицироваться провайдером.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(14.dp))}
  Spacer(Modifier.height(22.dp));Button(onClick=onSave,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(15.dp)){Text("Сохранить настройки",fontWeight=FontWeight.Bold)};Spacer(Modifier.height(18.dp))
 }
}
@Composable private fun TypingIndicator(){
 val transition=rememberInfiniteTransition(label="typing-dots")
 Row(Modifier.background(Card,RoundedCornerShape(14.dp)).padding(horizontal=13.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
  repeat(3){index->
   val alpha by transition.animateFloat(initialValue=0.28f,targetValue=1f,animationSpec=infiniteRepeatable(animation=tween(520,delayMillis=index*150),repeatMode=RepeatMode.Reverse),label="typing-dot-$index")
   Box(Modifier.size(6.dp).background(Purple.copy(alpha=alpha),CircleShape))
  }
  Spacer(Modifier.width(3.dp));Text("печатает",color=Muted,fontSize=12.sp)
 }
}

@Composable private fun RichMessageText(text:String,user:Boolean){
 val styled=remember(text,user){
  buildAnnotatedString {
   text.replace("\r\n","\n").split("\n").forEachIndexed{index,raw->
    val isDialogue=raw.startsWith("- ")
    if(isDialogue) withStyle(SpanStyle(color=if(user)Color.White.copy(alpha=.9f) else Purple,fontWeight=FontWeight.Bold)){append("— ")}
    val content=if(isDialogue)raw.removePrefix("- ") else raw
    val action=Regex("\\*([^*\\n]+)\\*")
    var cursor=0
    action.findAll(content).forEach{m->
     append(content.substring(cursor,m.range.first))
     withStyle(SpanStyle(color=if(user)Color.White.copy(alpha=.78f) else Muted,fontStyle=FontStyle.Italic,fontSize=14.sp)){append(m.groupValues[1])}
     cursor=m.range.last+1
    }
    append(content.substring(cursor))
    if(index<text.replace("\r\n","\n").split("\n").lastIndex)append("\n")
   }
  }
 }
 Text(styled,modifier=Modifier.padding(14.dp),fontSize=16.sp,lineHeight=23.sp,color=if(user)Color.White else Color(0xFFF2F0FA))
}

@Composable private fun NavItem(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(horizontal=20.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Muted);Spacer(Modifier.width(18.dp));Text(label,fontSize=16.sp)}
}
@Composable private fun Home(peopleList:List<Person>,category:String,onCategory:(String)->Unit,search:String,onSearch:(String)->Unit,onCreate:()->Unit,onOpen:(Person)->Unit){
 val categories=listOf("Для вас","ОС","Аниме","Игры","Фэнтези","Реализм")
 val shown=peopleList.filter{(category=="Для вас"||it.category==category)&&(it.name.contains(search,true)||it.bio.contains(search,true))}
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth().padding(start=18.dp,end=12.dp,top=14.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
   Surface(color=Field,shape=RoundedCornerShape(28.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.weight(1f)) {
    Row(Modifier.padding(horizontal=15.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
     Icon(Icons.Outlined.Search,null,tint=Muted,modifier=Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
     BasicTextField(search,onSearch,modifier=Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=15.sp),decorationBox={inner->if(search.isEmpty())Text("Найти персонажа…",color=Muted,fontSize=15.sp);inner()})
    }
   }
   TextButton(onClick={onCategory("Для вас")}) { Text("Все",fontSize=15.sp,color=Color.White,fontWeight=FontWeight.SemiBold); Icon(Icons.Outlined.ExpandMore,null,tint=Color.White) }
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(22.dp),verticalAlignment=Alignment.CenterVertically) {
   categories.forEach { item ->
    Column(Modifier.clickable{onCategory(item)}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
     Text(item,fontSize=15.sp,fontWeight=if(category==item)FontWeight.Bold else FontWeight.Medium,color=if(category==item)Color.White else Muted)
     Spacer(Modifier.height(7.dp))
     Box(Modifier.width(if(category==item)25.dp else 0.dp).height(4.dp).background(if(category==item)Yellow else Color.Transparent,CircleShape))
    }
   }
  }
  if(shown.isEmpty()) {
   Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Ничего не найдено.",color=Muted)}
  } else {
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    shown.chunked(2).forEachIndexed { rowIndex,rowPeople ->
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      rowPeople.forEach { person ->
       val personIndex=peopleList.indexOf(person).coerceAtLeast(0)
       PersonCard(person,personIndex,Modifier.weight(1f),onOpen)
      }
      if(rowPeople.size==1) Spacer(Modifier.weight(1f))
     }
    }
    Surface(color=Card,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=5.dp,bottom=18.dp).clickable{onCreate()}) {
     Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.AddCircleOutline,null,tint=Purple,modifier=Modifier.size(26.dp)); Spacer(Modifier.width(11.dp)); Column { Text("Создать персонажа",fontWeight=FontWeight.Bold); Text("Придумай своего собеседника",fontSize=11.sp,color=Muted) } }
    }
   }
  }
 }
}
@Composable private fun ProfileStat(label:String,value:String,modifier:Modifier=Modifier) {
 Surface(color=Card,shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border),modifier=modifier) {
  Column(Modifier.padding(15.dp)) { Text(value,fontSize=24.sp,fontWeight=FontWeight.Black,color=Purple); Text(label,fontSize=10.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp) }
 }
}
@Composable private fun ProfileAction(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
 Surface(color=Card,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=8.dp).clickable{onClick()}) {
  Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
   Icon(icon,null,tint=Purple,modifier=Modifier.size(23.dp)); Spacer(Modifier.width(13.dp))
   Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.Bold); Text(subtitle,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=2.dp)) }
   Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
  }
 }
}
@Composable private fun Heading(text:String){Text(text,fontSize=17.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(horizontal=22.dp,vertical=12.dp))}
@Composable private fun PersonCard(p:Person,index:Int,modifier:Modifier=Modifier,onOpen:(Person)->Unit){
 Surface(color=Card,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Color(0xFF292936)),modifier=modifier.clickable{onOpen(p)}) {
  Box(Modifier.fillMaxWidth().height(238.dp)) {
   val image=rememberPortraitSheet()
   val tileWidth=(image?.width ?: 2)/2; val tileHeight=(image?.height ?: 2)/2; val tile=((index%2)+2)%2
   Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))) {
    if(image != null) drawImage(image,srcOffset=IntOffset((tile%2)*tileWidth,(tile/2)*tileHeight),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(size.width.toInt(),size.height.toInt())) else drawRect(Color(0xFF302744))
    drawRect(Brush.verticalGradient(0f to Color.Transparent,0.42f to Color(0x22000000),1f to Color(0xE8000000)))
   }
   Surface(color=Color(0xAA15151D),shape=RoundedCornerShape(20.dp),modifier=Modifier.align(Alignment.TopStart).padding(9.dp)) {
    Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.ChatBubble,null,tint=Color.White,modifier=Modifier.size(12.dp)); Spacer(Modifier.width(5.dp)); Text("NEBULA",fontSize=9.sp,color=Color.White,fontWeight=FontWeight.Bold) }
   }
   Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal=12.dp,vertical=13.dp)) {
    Text(p.name,fontSize=17.sp,fontWeight=FontWeight.ExtraBold,color=Color.White,maxLines=2,lineHeight=19.sp)
    Text(p.bio,color=Color.White.copy(alpha=.78f),fontSize=11.sp,lineHeight=14.sp,maxLines=2,modifier=Modifier.padding(top=4.dp))
   }
  }
 }
}

@Composable private fun BrandMark(size:Int){
 Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color(0xFFB79CFF),Color(0xFF7651E8))),RoundedCornerShape((size/3).dp)),contentAlignment=Alignment.Center){
  Icon(Icons.Outlined.AutoAwesome,null,tint=Color.White,modifier=Modifier.size((size*.62f).dp))
 }
}

@Composable private fun PortraitAvatar(index:Int,size:Int){
 val image = rememberPortraitSheet()
 val tileWidth = (image?.width ?: 2) / 2
 val tileHeight = (image?.height ?: 2) / 2
 val tile = ((index % 2) + 2) % 2
 Canvas(Modifier.size(size.dp).clip(RoundedCornerShape(16.dp))) {
  if(image != null) drawImage(
   image = image,
   srcOffset = IntOffset((tile % 2) * tileWidth, (tile / 2) * tileHeight),
   srcSize = IntSize(tileWidth, tileHeight),
   dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
  )
 }
}
@Composable private fun FieldInput(value:String,onChange:(String)->Unit,hint:String,multi:Boolean=false){
 OutlinedTextField(value,onChange,placeholder={Text(hint,color=Muted)},modifier=Modifier.fillMaxWidth(),minLines=if(multi)4 else 1,maxLines=if(multi)6 else 1,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Purple,unfocusedBorderColor=Color(0xFF555555),focusedTextColor=Color.White,unfocusedTextColor=Color.White))
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
