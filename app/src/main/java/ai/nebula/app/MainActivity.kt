package ai.nebula.app

import android.os.Bundle
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.flow.collect
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
  chatReturnPage=if(page=="create") "home" else page
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
  if(page=="home" || page=="chats" || page=="create" || page=="profile") {
   BottomTabBar(
    current=page,
    onSelect={ page=it }
   )
  }
 }
}

@Composable private fun BottomTabBar(current:String,onSelect:(String)->Unit) {
 val pulse=rememberInfiniteTransition(label="dock-glow")
 val glow by pulse.animateFloat(initialValue=.14f,targetValue=.32f,animationSpec=infiniteRepeatable(animation=tween(1800),repeatMode=RepeatMode.Reverse),label="dock-pulse")
 Surface(
  color=Color(0xF7111420),
  shape=RoundedCornerShape(27.dp),
  border=BorderStroke(1.dp,Color(0xFF3A3550)),
  shadowElevation=20.dp,
  modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp).navigationBarsPadding().padding(top=7.dp,bottom=8.dp)
 ) {
  Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1B1C2C),Color(0xFF111420)))).padding(horizontal=10.dp,vertical=9.dp)) {
   Row(Modifier.fillMaxWidth().padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically) {
    BrandMark(19)
    Spacer(Modifier.width(7.dp))
    Text("NEBULA",fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=1.7.sp,color=Color(0xFFDBD2F7))
    Spacer(Modifier.weight(1f))
    Box(Modifier.size(5.dp).background(Color(0xFF8CE0B6).copy(alpha=glow+0.45f),CircleShape))
    Spacer(Modifier.width(5.dp))
    Text("ТВОЁ ПРОСТРАНСТВО",fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=Muted)
   }
   Spacer(Modifier.height(8.dp))
   Row(Modifier.fillMaxWidth().height(60.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
    BottomTab("chats","Чаты",Icons.Outlined.Forum,current=="chats",Modifier.weight(1f)){onSelect("chats")}
    BottomTab("home","Каталог",Icons.Outlined.Explore,current=="home",Modifier.weight(1f)){onSelect("home")}
    BottomTab("create","Создать",Icons.Outlined.AddCircleOutline,current=="create",Modifier.weight(1f)){onSelect("create")}
    BottomTab("profile","Профиль",Icons.Outlined.PersonOutline,current=="profile",Modifier.weight(1f)){onSelect("profile")}
   }
  }
 }
}
@Composable private fun BottomTab(key:String,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
 val tint by animateColorAsState(if(selected)Color(0xFF211A32) else Color(0xFF9A9CB0),animationSpec=spring(),label="tab-tint-"+key)
 val pillWidth by animateDpAsState(if(selected)82.dp else 42.dp,animationSpec=spring(dampingRatio=.72f,stiffness=420f),label="tab-width-"+key)
 Column(modifier=modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).clickable{onClick()}.padding(vertical=1.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
  Box(Modifier.fillMaxWidth().height(38.dp),contentAlignment=Alignment.Center) {
   if(selected) {
    Box(Modifier.width(pillWidth+8.dp).height(36.dp).background(Brush.linearGradient(listOf(Color(0xFFD9CBFF),Color(0xFF9D83E6),Color(0xFFBDAAFF))),RoundedCornerShape(14.dp)).border(1.dp,Color.White.copy(alpha=.38f),RoundedCornerShape(14.dp)))
    Box(Modifier.width(pillWidth).height(28.dp).background(Color.White.copy(alpha=.13f),RoundedCornerShape(11.dp)))
   }
   Icon(icon,contentDescription=label,tint=tint,modifier=Modifier.size(if(selected)21.dp else 20.dp))
  }
  Text(label,fontSize=9.sp,fontWeight=if(selected)FontWeight.ExtraBold else FontWeight.Medium,color=if(selected)Color(0xFFE0D5FF) else Muted,letterSpacing=.1.sp,maxLines=1)
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
 var revealKey by remember { mutableStateOf(false) }
 val pulse=rememberInfiniteTransition(label="settings-pulse")
 val ringAlpha by pulse.animateFloat(initialValue=.12f,targetValue=.28f,animationSpec=infiniteRepeatable(animation=tween(1900),repeatMode=RepeatMode.Reverse),label="settings-ring")
 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=11.dp)) {
  PageHeader("Настройки","ЦЕНТР УПРАВЛЕНИЯ NEBULA",onBack)
  Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(27.dp),border=BorderStroke(1.dp,Color(0xFF48405F)),modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {
   Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF40345F),Color(0xFF23253B),Color(0xFF151A28)))).padding(17.dp)) {
    Canvas(Modifier.matchParentSize()) {
     drawCircle(Color(0x227E6CC6).copy(alpha=ringAlpha+.1f),radius=size.width*.42f,center=androidx.compose.ui.geometry.Offset(size.width*.97f,size.height*.08f))
     drawCircle(Color(0x228F7DD0),radius=size.width*.22f,center=androidx.compose.ui.geometry.Offset(size.width*.98f,size.height*.1f))
    }
    Column {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(43.dp).background(Color(0x55D4C8FF),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Tune,null,tint=Color(0xFFE4D9FF),modifier=Modifier.size(22.dp))}
      Spacer(Modifier.width(11.dp))
      Column(Modifier.weight(1f)) {
       Text("CONTROL ROOM",fontSize=8.sp,fontWeight=FontWeight.Black,color=Yellow,letterSpacing=1.6.sp)
       Text("Твоя конфигурация",fontSize=20.sp,fontWeight=FontWeight.ExtraBold,color=Color.White,modifier=Modifier.padding(top=3.dp))
      }
      Surface(color=Color(0x337FD6B2),shape=RoundedCornerShape(9.dp),border=BorderStroke(1.dp,Color(0x557FD6B2))) {
       Text("LOCAL",fontSize=8.sp,color=Color(0xFFB8E9D1),fontWeight=FontWeight.Black,letterSpacing=1.sp,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
      }
     }
     Text("Настрой голос общения и подключение AI-провайдера в одном месте.",fontSize=11.sp,lineHeight=17.sp,color=Color.White.copy(alpha=.77f),modifier=Modifier.padding(top=14.dp))
     Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      Surface(color=Color(0x33121120),shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.1f)),modifier=Modifier.weight(1f)) {
       Column(Modifier.padding(10.dp)) { Text("СТИЛЬ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text(if(clean)"Чистый" else "Свободный",fontSize=11.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp)) }
      }
      Surface(color=Color(0x33121120),shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.1f)),modifier=Modifier.weight(1f)) {
       Column(Modifier.padding(10.dp)) { Text("МОДЕЛЬ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text(model.substringAfterLast("/").take(18),fontSize=10.sp,color=Color.White,fontWeight=FontWeight.Bold,maxLines=1,modifier=Modifier.padding(top=4.dp)) }
      }
     }
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=23.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("01",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Black)
   Spacer(Modifier.width(9.dp))
   Text("МАНЕРА ОБЩЕНИЯ",fontSize=9.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.5.sp)
   Spacer(Modifier.weight(1f))
   Text("PERSONALITY",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
  }
  Surface(color=Card,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().animateContentSize()) {
   Column(Modifier.padding(16.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(47.dp).background(Brush.linearGradient(listOf(Color(0xFF6D5799),Color(0xFF302747))),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Color(0xFFE1D6FF),modifier=Modifier.size(23.dp))}
     Spacer(Modifier.width(12.dp))
     Column(Modifier.weight(1f)) {
      Text("Чистый режим",fontWeight=FontWeight.ExtraBold,fontSize=14.sp)
      Text("Контроль над тоном ответов",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
     }
     Switch(checked=clean,onCheckedChange=onClean)
    }
    Divider(color=Border,modifier=Modifier.padding(vertical=14.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Surface(color=if(clean)Color(0xFF302747) else Field,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,if(clean)Color(0xFF77619F) else Border),modifier=Modifier.weight(1f)) {
      Column(Modifier.padding(11.dp)) {
       Icon(Icons.Outlined.FavoriteBorder,null,tint=if(clean)Purple else Muted,modifier=Modifier.size(17.dp))
       Text("Мягкий тон",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=7.dp))
       Text("Без мата и грубости",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
      }
     }
     Surface(color=if(!clean)Color(0xFF302747) else Field,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,if(!clean)Color(0xFF77619F) else Border),modifier=Modifier.weight(1f)) {
      Column(Modifier.padding(11.dp)) {
       Icon(Icons.Outlined.Forum,null,tint=if(!clean)Purple else Muted,modifier=Modifier.size(17.dp))
       Text("Свободный стиль",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=7.dp))
       Text("Сленг и эмоции",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
      }
     }
    }
    Text(if(clean)"Сейчас включён спокойный стиль речи. Общие правила безопасности действуют в любом режиме." else "Сейчас включён более разговорный стиль. Общие правила безопасности действуют в любом режиме.",fontSize=10.sp,color=Muted,lineHeight=16.sp,modifier=Modifier.padding(top=12.dp))
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=22.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("02",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Black)
   Spacer(Modifier.width(9.dp))
   Text("ПОДКЛЮЧЕНИЕ AI",fontSize=9.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.5.sp)
   Spacer(Modifier.weight(1f))
   Text("API GATEWAY",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
  }
  Surface(color=Card,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(16.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
     Icon(Icons.Outlined.CloudQueue,null,tint=Purple,modifier=Modifier.size(20.dp))
     Spacer(Modifier.width(8.dp))
     Text("Endpoint",fontSize=13.sp,fontWeight=FontWeight.ExtraBold)
     Spacer(Modifier.weight(1f))
     Text("BASE URL",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
    }
    Spacer(Modifier.height(9.dp))
    FieldInput(base,onBase,"https://api.example.com/v1")
    Text("MODEL ID",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.2.sp,modifier=Modifier.padding(top=16.dp,bottom=7.dp))
    FieldInput(model,onModel,"Название модели")
    Text("БЫСТРЫЙ ВЫБОР",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(top=14.dp,bottom=8.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Surface(color=Field,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.weight(1f).clickable{onModel("deepseek/deepseek-v4.1-flash-free")}) {
      Column(Modifier.padding(10.dp)) {
       Icon(Icons.Outlined.Bolt,null,tint=Yellow,modifier=Modifier.size(17.dp))
       Text("DeepSeek Free",fontSize=9.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp))
       Text("Быстрый ответ",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
      }
     }
     Surface(color=Field,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.weight(1f).clickable{onModel("orcarouter/free")}) {
      Column(Modifier.padding(10.dp)) {
       Icon(Icons.Outlined.Hub,null,tint=Purple,modifier=Modifier.size(17.dp))
       Text("OrcaRouter Free",fontSize=9.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp))
       Text("Маршрутизация моделей",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
      }
     }
    }
    Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically) {
     Text("API-ключ",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Color(0xFFE8E6F2))
     Spacer(Modifier.weight(1f))
     Text(if(key.isBlank())"НЕ УКАЗАН" else "СОХРАНИТСЯ ЛОКАЛЬНО",fontSize=7.sp,color=if(key.isBlank())Yellow else Color(0xFF93DAB8),fontWeight=FontWeight.Black,letterSpacing=.7.sp)
    }
    OutlinedTextField(value=key,onValueChange=onKey,placeholder={Text("Вставь ключ провайдера",color=Muted,fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(15.dp),visualTransformation=if(revealKey)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={revealKey=!revealKey}){Icon(if(revealKey)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(revealKey)"Скрыть ключ" else "Показать ключ",tint=Muted)}},colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Field,unfocusedContainerColor=Field,focusedBorderColor=Purple,unfocusedBorderColor=Border,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedPlaceholderColor=Muted,unfocusedPlaceholderColor=Muted))
    Surface(color=Color(0xFF1A1D2A),shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {
     Row(Modifier.padding(12.dp),verticalAlignment=Alignment.Top) {
      Icon(Icons.Outlined.Lock,null,tint=Yellow,modifier=Modifier.size(17.dp))
      Spacer(Modifier.width(9.dp))
      Text("Ключ сохраняется в настройках устройства. Не публикуй его и не добавляй в исходный код.",fontSize=9.sp,color=Muted,lineHeight=15.sp)
     }
    }
   }
  }
  Button(onClick=onSave,modifier=Modifier.fillMaxWidth().padding(top=18.dp).height(56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFCDBEFF),contentColor=Color(0xFF211A32))) {
   Icon(Icons.Outlined.CheckCircle,null,modifier=Modifier.size(18.dp))
   Spacer(Modifier.width(8.dp))
   Text("Сохранить конфигурацию",fontWeight=FontWeight.ExtraBold,fontSize=13.sp)
  }
  Text("NEBULA AI  •  LOCAL CONFIGURATION",fontSize=8.sp,color=Color(0xFF707287),fontWeight=FontWeight.Bold,letterSpacing=1.3.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=13.dp,bottom=20.dp))
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
 val ambience=rememberInfiniteTransition(label="catalog-ambience")
 val orbAlpha by ambience.animateFloat(initialValue=.12f,targetValue=.27f,animationSpec=infiniteRepeatable(animation=tween(2200),repeatMode=RepeatMode.Reverse),label="catalog-orb")
 Column(Modifier.fillMaxSize().background(Bg)) {
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
   Column(Modifier.padding(horizontal=17.dp)) {
    PageHeader("NEBULA AI","ТВОЯ ВСЕЛЕННАЯ ДИАЛОГОВ",action=onCreate)
    Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(29.dp),border=BorderStroke(1.dp,Color(0xFF4A4164)),modifier=Modifier.fillMaxWidth().padding(top=15.dp)) {
     Box(Modifier.fillMaxWidth().height(232.dp).background(Brush.linearGradient(listOf(Color(0xFF3B3158),Color(0xFF25263D),Color(0xFF151B2B))))) {
      Canvas(Modifier.matchParentSize()) {
       drawCircle(Color(0x337E6CC6).copy(alpha=orbAlpha+0.12f),radius=size.width*.46f,center=androidx.compose.ui.geometry.Offset(size.width*.93f,size.height*.16f))
       drawCircle(Color(0x1E9A8AE8),radius=size.width*.28f,center=androidx.compose.ui.geometry.Offset(size.width*.91f,size.height*.19f))
       drawCircle(Color(0x18E5D9FF),radius=size.width*.13f,center=androidx.compose.ui.geometry.Offset(size.width*.91f,size.height*.19f))
       drawCircle(Color(0x338A7BD0),radius=size.width*.17f,center=androidx.compose.ui.geometry.Offset(size.width*.02f,size.height*.98f))
      }
      Column(Modifier.fillMaxSize().padding(18.dp)) {
       Row(verticalAlignment=Alignment.CenterVertically) {
        Surface(color=Color(0x55E7DAFF),shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.19f))) {
         Text("PERSONA DISCOVERY",fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=1.25.sp,color=Color(0xFFE6DCFF),modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment=Alignment.CenterVertically) {
         Box(Modifier.size(6.dp).background(Color(0xFF8FE0B8),CircleShape))
         Spacer(Modifier.width(5.dp))
         Text("ONLINE",fontSize=8.sp,color=Color(0xFFC4E9D5),fontWeight=FontWeight.Bold,letterSpacing=1.sp)
        }
       }
       Spacer(Modifier.height(17.dp))
       Text("НЕ ПРОСТО ЧАТ.",fontSize=9.sp,fontWeight=FontWeight.Black,color=Yellow,letterSpacing=2.sp)
       Text("Твоя следующая",fontSize=29.sp,fontWeight=FontWeight.Black,color=Color.White,letterSpacing=(-1.1).sp)
       Text("история начинается здесь.",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color(0xFFD5C7FF),lineHeight=27.sp,letterSpacing=(-.7).sp)
       Spacer(Modifier.weight(1f))
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
         Text("\${peopleList.size} ПЕРСОНАЖЕЙ В КАТАЛОГЕ",fontSize=8.sp,color=Color.White.copy(alpha=.68f),fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
         Text("Выбери собеседника",fontSize=12.sp,color=Color.White,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=3.dp))
        }
        Button(onClick=onCreate,shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=13.dp,vertical=9.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD4C7FF),contentColor=Color(0xFF211A32))) {
         Icon(Icons.Outlined.Add,null,modifier=Modifier.size(15.dp))
         Spacer(Modifier.width(5.dp))
         Text("Создать",fontSize=10.sp,fontWeight=FontWeight.ExtraBold)
        }
       }
      }
     }
    }
    Surface(color=Color(0xFF171A27),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=13.dp)) {
     Row(Modifier.padding(horizontal=13.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
      Icon(Icons.Outlined.Search,null,tint=Purple,modifier=Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      BasicTextField(search,onSearch,modifier=Modifier.weight(1f).padding(vertical=13.dp),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=13.sp),decorationBox={inner->if(search.isEmpty())Text("Найти персонажа, жанр или настроение…",color=Muted,fontSize=11.sp);inner()})
      if(search.isNotBlank()) IconButton(onClick={onSearch("")},modifier=Modifier.size(30.dp)){Icon(Icons.Outlined.Close,null,tint=Muted,modifier=Modifier.size(16.dp))}
     }
    }
   }
   Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=19.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
     Text("НАВИГАЦИЯ ПО МИРАМ",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.7.sp)
     Text("Выбери настроение",fontSize=19.sp,fontWeight=FontWeight.ExtraBold,color=Color.White,modifier=Modifier.padding(top=3.dp))
    }
    Text("\${categories.size} КАТЕГОРИЙ",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
   }
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start=17.dp,end=17.dp,bottom=15.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    categories.forEach { item ->
     val active=category==item
     val container by animateColorAsState(if(active)Color(0xFFD6C9FF) else Color(0xFF171A29),label="category-"+item)
     Surface(color=container,shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,if(active)Color.White.copy(alpha=.28f) else Border),modifier=Modifier.clickable{onCategory(item)}) {
      Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
       if(active) Box(Modifier.size(5.dp).background(Color(0xFF5E4B8B),CircleShape))
       if(active) Spacer(Modifier.width(6.dp))
       Text(item,fontSize=11.sp,fontWeight=if(active)FontWeight.ExtraBold else FontWeight.SemiBold,color=if(active)Color(0xFF241B35) else Muted)
      }
     }
    }
   }
   Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
     Text(if(category=="Для вас")"ПОДБОРКА NEBULA" else category.uppercase(),fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.8.sp)
     Text("Персонажи",fontSize=23.sp,color=Color.White,fontWeight=FontWeight.Black,modifier=Modifier.padding(top=3.dp))
    }
    Surface(color=Color(0xFF232338),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Border)) {
     Text("\${shown.size} НАЙДЕНО",fontSize=8.sp,color=Color(0xFFD1C5F7),fontWeight=FontWeight.Bold,letterSpacing=.7.sp,modifier=Modifier.padding(horizontal=9.dp,vertical=7.dp))
    }
   }
   if(shown.isEmpty()) {
    Box(Modifier.fillMaxWidth().height(190.dp),contentAlignment=Alignment.Center) {
     Column(horizontalAlignment=Alignment.CenterHorizontally) {
      Icon(Icons.Outlined.SearchOff,null,tint=Muted,modifier=Modifier.size(34.dp))
      Text("Ничего не найдено",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
      Text("Измени запрос или выбери другую категорию",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))
     }
    }
   } else {
    Column(Modifier.padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     shown.chunked(2).forEach { rowPeople ->
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(11.dp)) {
       rowPeople.forEach { person ->
        val personIndex=peopleList.indexOf(person).coerceAtLeast(0)
        PersonCard(person,personIndex,Modifier.weight(1f),onOpen)
       }
       if(rowPeople.size==1) Spacer(Modifier.weight(1f))
      }
     }
    }
   }
   Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Color(0xFF3A3550)),modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp).padding(top=17.dp,bottom=22.dp)) {
    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Color(0xFF9079D3),Color(0xFF4C3B73))),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Color.White,modifier=Modifier.size(24.dp))}
     Spacer(Modifier.width(12.dp))
     Column(Modifier.weight(1f)) {
      Text("ТВОЯ ЛИЧНАЯ ВСЕЛЕННАЯ",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.sp)
      Text("Твой персонаж — твои правила",fontWeight=FontWeight.ExtraBold,fontSize=12.sp,color=Color.White,modifier=Modifier.padding(top=3.dp))
      Text("Собери образ, характер и собственную историю",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
     }
     IconButton(onClick=onCreate){Icon(Icons.Outlined.ArrowForward,null,tint=Purple)}
    }
   }
  }
 }
}


@Composable private fun PersonCard(p:Person,index:Int,modifier:Modifier=Modifier,onOpen:(Person)->Unit) {
 val image=rememberPortraitSheet()
 val tileWidth=(image?.width?:2)/2
 val tileHeight=(image?.height?:2)/2
 val shimmer=rememberInfiniteTransition(label="person-card-"+index)
 val glow by shimmer.animateFloat(initialValue=.10f,targetValue=.24f,animationSpec=infiniteRepeatable(animation=tween(2100,delayMillis=index*120),repeatMode=RepeatMode.Reverse),label="person-card-glow")
 Surface(color=Card,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Color(0xFF403A58)),modifier=modifier.animateContentSize().clickable{onOpen(p)}) {
  Column(Modifier.fillMaxWidth()) {
   Box(Modifier.fillMaxWidth().height(206.dp).background(Brush.linearGradient(personColors(index)))) {
    Canvas(Modifier.fillMaxSize()) {
     drawRect(Brush.linearGradient(personColors(index)))
     drawCircle(Color.White.copy(alpha=glow),radius=size.width*.33f,center=androidx.compose.ui.geometry.Offset(size.width*.82f,size.height*.16f))
     drawCircle(Color(0x228F7DCE),radius=size.width*.22f,center=androidx.compose.ui.geometry.Offset(size.width*.13f,size.height*.8f))
     if(index in 0..1 && image!=null) {
      drawImage(image,srcOffset=IntOffset(index*tileWidth,0),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(size.width.toInt(),size.height.toInt()),alpha=.9f)
     } else {
      drawCircle(Color.White.copy(alpha=.11f),radius=size.width*.25f,center=androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.47f))
      drawCircle(Color.White.copy(alpha=.09f),radius=size.width*.18f,center=androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.47f))
     }
     drawRect(Brush.verticalGradient(listOf(Color(0x0010121B),Color(0x5510121B),Color(0xDD080A12))))
    }
    if(index !in 0..1) {
     Box(Modifier.align(Alignment.Center).size(74.dp).background(Color.White.copy(alpha=.075f),RoundedCornerShape(24.dp)).border(1.dp,Color.White.copy(alpha=.19f),RoundedCornerShape(24.dp)),contentAlignment=Alignment.Center) {
      Text(p.initials.ifBlank{"✦"},fontSize=30.sp,fontWeight=FontWeight.Black,color=Color.White)
     }
    }
    Row(Modifier.align(Alignment.TopStart).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
     Surface(color=Color(0xD910121D),shape=RoundedCornerShape(9.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.15f))) {
      Text(p.category.uppercase(),fontSize=8.sp,color=Color.White,fontWeight=FontWeight.Black,letterSpacing=1.sp,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
     }
    }
    Surface(color=Color(0xB70E101B),shape=CircleShape,border=BorderStroke(1.dp,Color.White.copy(alpha=.15f)),modifier=Modifier.align(Alignment.TopEnd).padding(10.dp).size(31.dp)) {
     Box(contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Color(0xFFD9C9FF),modifier=Modifier.size(15.dp))}
    }
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp)) {
     Text(p.name,fontSize=16.sp,fontWeight=FontWeight.Black,color=Color.White,maxLines=1)
     Text(p.creator.uppercase(),fontSize=8.sp,color=Color.White.copy(alpha=.62f),fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(top=3.dp))
    }
   }
   Column(Modifier.fillMaxWidth().background(Color(0xFF171A28)).padding(horizontal=12.dp,vertical=11.dp)) {
    Text(p.bio.ifBlank{"Новая история начинается здесь."},color=Color(0xFFC1C1D0),fontSize=10.sp,lineHeight=15.sp,maxLines=3,minLines=2)
    Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(5.dp).background(Color(0xFF8BDCB5),CircleShape))
      Spacer(Modifier.width(5.dp))
      Text("ГОТОВ К ДИАЛОГУ",fontSize=7.sp,color=Color(0xFFB7DCC9),fontWeight=FontWeight.Bold,letterSpacing=.55.sp)
     }
     Spacer(Modifier.weight(1f))
     Surface(color=Color(0xFFBDAAFF),shape=RoundedCornerShape(10.dp)) {
      Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
       Text("ОТКРЫТЬ",fontSize=8.sp,fontWeight=FontWeight.Black,color=Color(0xFF241B35))
       Spacer(Modifier.width(4.dp))
       Icon(Icons.Outlined.ArrowOutward,null,tint=Color(0xFF241B35),modifier=Modifier.size(12.dp))
      }
     }
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
 val shimmer=rememberInfiniteTransition(label="profile-hero")
 val glow by shimmer.animateFloat(initialValue=.13f,targetValue=.28f,animationSpec=infiniteRepeatable(animation=tween(2300),repeatMode=RepeatMode.Reverse),label="profile-glow")
 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=11.dp)) {
  PageHeader("Профиль","ТВОЙ АККАУНТ NEBULA")
  Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(28.dp),border=BorderStroke(1.dp,Color(0xFF49405F)),modifier=Modifier.fillMaxWidth().padding(top=13.dp)) {
   Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF51416F),Color(0xFF28283F),Color(0xFF151B2B))))) {
    Canvas(Modifier.matchParentSize()) {
     drawCircle(Color(0x337F6CC7).copy(alpha=glow),radius=size.width*.48f,center=androidx.compose.ui.geometry.Offset(size.width*.95f,size.height*.08f))
     drawCircle(Color(0x226D89C9),radius=size.width*.28f,center=androidx.compose.ui.geometry.Offset(size.width*.93f,size.height*.78f))
     drawCircle(Color(0x18E5D9FF),radius=size.width*.12f,center=androidx.compose.ui.geometry.Offset(size.width*.93f,size.height*.13f))
    }
    Column(Modifier.padding(19.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(66.dp).background(Brush.linearGradient(listOf(Color(0xFFE5DAFF),Color(0xFF9A82D5))),RoundedCornerShape(22.dp)).border(1.dp,Color.White.copy(alpha=.42f),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Color(0xFF29203D),modifier=Modifier.size(32.dp))}
      Spacer(Modifier.width(13.dp))
      Column(Modifier.weight(1f)) {
       Text("NEBULA MEMBER",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.7.sp)
       Text("Гость NEBULA",fontSize=21.sp,fontWeight=FontWeight.Black,color=Color.White,modifier=Modifier.padding(top=4.dp))
       Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=5.dp)) {
        Box(Modifier.size(6.dp).background(Color(0xFF8CE0B6),CircleShape))
        Spacer(Modifier.width(5.dp))
        Text("ЛОКАЛЬНЫЙ ПРОФИЛЬ",fontSize=7.sp,color=Color.White.copy(alpha=.68f),fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
       }
      }
     }
     Text("Твоя личная вселенная диалогов, персонажей и историй.",fontSize=12.sp,color=Color.White.copy(alpha=.83f),lineHeight=18.sp,modifier=Modifier.padding(top=17.dp))
     Row(Modifier.fillMaxWidth().padding(top=15.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      Surface(color=Color(0x33141120),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),modifier=Modifier.weight(1f)) {
       Column(Modifier.padding(10.dp)) { Text("РЕЖИМ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text("Personal",fontSize=11.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp)) }
      }
      Surface(color=Color(0x33141120),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),modifier=Modifier.weight(1f)) {
       Column(Modifier.padding(10.dp)) { Text("ХРАНЕНИЕ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text("На устройстве",fontSize=11.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp)) }
      }
     }
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=23.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text("ТВОЯ АКТИВНОСТЬ",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.6.sp)
    Text("Статистика пространства",fontSize=18.sp,color=Color.White,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=3.dp))
   }
   Icon(Icons.Outlined.Insights,null,tint=Purple,modifier=Modifier.size(22.dp))
  }
  Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
   Surface(color=Card,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.weight(1f)) {
    Column(Modifier.padding(14.dp)) {
     Box(Modifier.size(33.dp).background(Color(0xFF302747),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Forum,null,tint=Purple,modifier=Modifier.size(17.dp))}
     Text(chatCount.toString(),fontSize=27.sp,fontWeight=FontWeight.Black,color=Color.White,modifier=Modifier.padding(top=8.dp))
     Text("СОХРАНЁННЫЕ ЧАТЫ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.7.sp)
    }
   }
   Surface(color=Card,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.weight(1f)) {
    Column(Modifier.padding(14.dp)) {
     Box(Modifier.size(33.dp).background(Color(0xFF263A3F),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Groups,null,tint=Color(0xFF9BD7C0),modifier=Modifier.size(17.dp))}
     Text(personCount.toString(),fontSize=27.sp,fontWeight=FontWeight.Black,color=Color.White,modifier=Modifier.padding(top=8.dp))
     Text("ПЕРСОНАЖИ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.7.sp)
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("БЫСТРЫЙ ДОСТУП",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.6.sp,modifier=Modifier.weight(1f))
   Text("3 РАЗДЕЛА",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
  }
  ProfileAction("Мои чаты","Продолжить сохранённые истории",Icons.Outlined.Forum,onChats)
  ProfileAction("Создать персонажа","Настроить имя, характер и детали",Icons.Outlined.AddCircleOutline,onCreate)
  ProfileAction("Настройки AI","Модель, API и стиль общения",Icons.Outlined.Tune,onSettings)
  Surface(color=Color(0xFF151927),shape=RoundedCornerShape(19.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=18.dp,bottom=20.dp)) {
   Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top) {
    Icon(Icons.Outlined.Shield,null,tint=Purple,modifier=Modifier.size(18.dp))
    Spacer(Modifier.width(10.dp))
    Column {
     Text("ЛОКАЛЬНОЕ ХРАНЕНИЕ",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.sp)
     Text("История диалогов и созданные персонажи сохраняются на устройстве. Для генерации ответов требуется действующий API-провайдер.",fontSize=10.sp,color=Muted,lineHeight=16.sp,modifier=Modifier.padding(top=5.dp))
    }
   }
  }
 }
}

@Composable private fun ChatsScreen(sessions:List<ChatSession>,onOpen:(ChatSession)->Unit,onDelete:(String)->Unit,onCatalog:()->Unit) {
 var filter by remember { mutableStateOf("") }
 val filtered=sessions.sortedByDescending{it.updatedAt}.filter{it.person.name.contains(filter,true)||(it.lines.lastOrNull()?.text?.contains(filter,true)==true)}
 Column(Modifier.fillMaxSize().background(Bg)) {
  Column(Modifier.padding(horizontal=18.dp)) {
   PageHeader("Мои чаты","АРХИВ ТВОИХ ИСТОРИЙ")
   Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=14.dp)) {
    Column(Modifier.padding(16.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text("ТВОЯ ИСТОРИЯ",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.5.sp)
       Text("Продолжить с места",fontSize=22.sp,fontWeight=FontWeight.Black,color=Color.White,modifier=Modifier.padding(top=4.dp))
       Text("Все беседы в одном пространстве.",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
      }
      Box(Modifier.size(54.dp).background(Brush.linearGradient(listOf(Color(0xFF5B477F),Color(0xFF29243E))),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Forum,null,tint=Color(0xFFD9CEFF),modifier=Modifier.size(26.dp))}
     }
     Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      Surface(color=Color(0xFF26243A),shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f)) { Column(Modifier.padding(10.dp)){Text("ДИАЛОГИ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold);Text(sessions.size.toString(),fontSize=17.sp,color=Color.White,fontWeight=FontWeight.Black,modifier=Modifier.padding(top=3.dp))} }
      Surface(color=Color(0xFF202D32),shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f)) { Column(Modifier.padding(10.dp)){Text("ХРАНЕНИЕ",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold);Text("Локально",fontSize=12.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))} }
     }
    }
   }
   Surface(color=Color(0xFF171A27),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=13.dp)) {
    Row(Modifier.padding(horizontal=12.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
     Icon(Icons.Outlined.Search,null,tint=Purple,modifier=Modifier.size(18.dp))
     Spacer(Modifier.width(8.dp))
     BasicTextField(filter,{filter=it},modifier=Modifier.weight(1f).padding(vertical=12.dp),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=12.sp),decorationBox={inner->if(filter.isEmpty())Text("Поиск по имени или сообщениям",color=Muted,fontSize=10.sp);inner()})
     Text("\${filtered.size}",fontSize=9.sp,color=Muted,fontWeight=FontWeight.Bold)
    }
   }
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(bottom=9.dp)) {
    Text("НЕДАВНИЕ ДИАЛОГИ",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.4.sp,modifier=Modifier.weight(1f))
    Text("МАКС. 30",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
   }
  }
  if(filtered.isEmpty()) {
   Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(horizontal=30.dp)) {
     Box(Modifier.size(82.dp).background(Brush.linearGradient(listOf(Color(0xFF40345F),Color(0xFF1D2030))),RoundedCornerShape(27.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Forum,null,tint=Purple,modifier=Modifier.size(34.dp))}
     Text(if(sessions.isEmpty())"История пока пуста" else "Ничего не найдено",fontWeight=FontWeight.Black,fontSize=19.sp,modifier=Modifier.padding(top=16.dp))
     Text(if(sessions.isEmpty())"Выбери персонажа в каталоге, чтобы начать первый диалог." else "Проверь запрос или попробуй другое имя.",fontSize=11.sp,color=Muted,lineHeight=17.sp,modifier=Modifier.padding(top=6.dp))
     Button(onClick=onCatalog,modifier=Modifier.padding(top=17.dp).height(48.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple,contentColor=Color(0xFF171321))){Text("Перейти в каталог",fontWeight=FontWeight.ExtraBold,fontSize=11.sp)}
    }
   }
  } else {
   LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=13.dp),contentPadding=PaddingValues(bottom=17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    items(filtered,key={it.id}) { session ->
     val idx=people.indexOfFirst{it.name==session.person.name}.let{if(it>=0)it else 2}
     Surface(color=Color(0xFF161927),shape=RoundedCornerShape(21.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().animateContentSize()) {
      Column {
       Row(Modifier.fillMaxWidth().clickable{onOpen(session)}.padding(start=12.dp,end=12.dp,top=13.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(55.dp).background(Brush.linearGradient(personColors(idx)),RoundedCornerShape(18.dp))) {
         PortraitAvatar(idx,55,session.person.initials)
         Box(Modifier.align(Alignment.BottomEnd).size(11.dp).background(Color(0xFF8BDCB5),CircleShape).border(2.dp,Color(0xFF161927),CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
         Row(verticalAlignment=Alignment.CenterVertically) {
          Text(session.person.name,fontWeight=FontWeight.ExtraBold,fontSize=14.sp,modifier=Modifier.weight(1f),maxLines=1)
          Surface(color=Color(0xFF2B2540),shape=RoundedCornerShape(7.dp)) { Text(session.person.category.uppercase(),color=Color(0xFFD2C4FF),fontSize=7.sp,fontWeight=FontWeight.Black,letterSpacing=.6.sp,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp)) }
         }
         Text(session.lines.lastOrNull()?.text?.ifBlank{"Новый диалог"}?:"Новый диалог",color=Muted,fontSize=10.sp,maxLines=2,lineHeight=14.sp,modifier=Modifier.padding(top=5.dp))
         Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=7.dp)) {
          Icon(Icons.Outlined.History,null,tint=Color(0xFF8A8CA1),modifier=Modifier.size(11.dp))
          Spacer(Modifier.width(4.dp))
          Text("СОХРАНЕНО НА УСТРОЙСТВЕ",fontSize=7.sp,color=Color(0xFF8A8CA1),fontWeight=FontWeight.Bold,letterSpacing=.45.sp)
         }
        }
       }
       Divider(color=Border)
       Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("\${session.lines.size} СООБЩ.",fontSize=7.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=.7.sp,modifier=Modifier.weight(1f))
        Text("ПРОДОЛЖИТЬ",fontSize=8.sp,color=Color(0xFFD3C5FF),fontWeight=FontWeight.Black,letterSpacing=.8.sp,modifier=Modifier.clickable{onOpen(session)}.padding(horizontal=8.dp,vertical=6.dp))
        IconButton(onClick={onDelete(session.id)},modifier=Modifier.size(30.dp)){Icon(Icons.Outlined.DeleteOutline,"Удалить диалог",tint=Color(0xFFAA8C9B),modifier=Modifier.size(16.dp))}
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun CreatePersonScreen(name:String,onName:(String)->Unit,bio:String,onBio:(String)->Unit,onBack:()->Unit,onCreate:()->Unit) {
 val progress=(if(name.isNotBlank())0.45f else 0f)+(if(bio.isNotBlank())0.55f else 0f)
 val progressAnim by animateFloatAsState(progress,animationSpec=spring(),label="character-progress")
 val pulse=rememberInfiniteTransition(label="studio-pulse")
 val aura by pulse.animateFloat(initialValue=.12f,targetValue=.25f,animationSpec=infiniteRepeatable(animation=tween(2000),repeatMode=RepeatMode.Reverse),label="studio-aura")
 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=11.dp)) {
  PageHeader("Новый персонаж","CHARACTER STUDIO",onBack)
  Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(26.dp),border=BorderStroke(1.dp,Color(0xFF4B4164)),modifier=Modifier.fillMaxWidth().padding(top=13.dp)) {
   Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF51416F),Color(0xFF29263F),Color(0xFF151B2A)))).padding(17.dp)) {
    Canvas(Modifier.matchParentSize()) {
     drawCircle(Color(0x337C6BC2).copy(alpha=aura+.1f),radius=size.width*.4f,center=androidx.compose.ui.geometry.Offset(size.width*.98f,size.height*.03f))
     drawCircle(Color(0x1E8E7ECF),radius=size.width*.22f,center=androidx.compose.ui.geometry.Offset(size.width*.98f,size.height*.05f))
    }
    Column {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(47.dp).background(Color(0x44D6C9FF),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,null,tint=Color(0xFFE0D5FF),modifier=Modifier.size(24.dp))}
      Spacer(Modifier.width(10.dp))
      Column {
       Text("PERSONA LAB  /  001",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.2.sp)
       Text("Создай новый образ",fontSize=18.sp,color=Color.White,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=3.dp))
      }
     }
     Text("Продумай имя, характер и детали — так собеседник будет звучать цельно.",fontSize=11.sp,color=Color.White.copy(alpha=.77f),lineHeight=17.sp,modifier=Modifier.padding(top=13.dp))
     Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=15.dp)) {
      Text("ГОТОВНОСТЬ ПРОФИЛЯ",fontSize=7.sp,color=Color.White.copy(alpha=.7f),fontWeight=FontWeight.Bold,letterSpacing=.8.sp,modifier=Modifier.weight(1f))
      Text("\${(progress*100).toInt()}%",fontSize=10.sp,color=Color.White,fontWeight=FontWeight.Black)
     }
     Box(Modifier.fillMaxWidth().padding(top=6.dp).height(5.dp).background(Color.White.copy(alpha=.13f),CircleShape)) {
      Box(Modifier.fillMaxWidth(progressAnim).height(5.dp).background(Brush.horizontalGradient(listOf(Color(0xFFD4C7FF),Color(0xFF9D83E6))),CircleShape))
     }
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=22.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("01",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Black)
   Spacer(Modifier.width(8.dp))
   Text("ОСНОВА ПЕРСОНАЖА",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.4.sp)
  }
  Surface(color=Card,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(15.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(38.dp).background(Color(0xFF302747),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Badge,null,tint=Purple)}
     Spacer(Modifier.width(10.dp))
     Column(Modifier.weight(1f)) { Text("Имя персонажа",fontWeight=FontWeight.ExtraBold,fontSize=12.sp); Text("Имя, которое увидят в диалоге",fontSize=9.sp,color=Muted,modifier=Modifier.padding(top=2.dp)) }
     Text("\${name.length}/40",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(value=name,onValueChange={if(it.length<=40)onName(it)},placeholder={Text("Например, Акира",color=Muted,fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Field,unfocusedContainerColor=Field,focusedBorderColor=Purple,unfocusedBorderColor=Border,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedPlaceholderColor=Muted,unfocusedPlaceholderColor=Muted))
    Divider(color=Border,modifier=Modifier.padding(vertical=15.dp))
    Row(verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(38.dp).background(Color(0xFF302747),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.PsychologyAlt,null,tint=Purple)}
     Spacer(Modifier.width(10.dp))
     Column(Modifier.weight(1f)) { Text("Характер и история",fontWeight=FontWeight.ExtraBold,fontSize=12.sp); Text("Манера речи, привычки, интересы",fontSize=9.sp,color=Muted,modifier=Modifier.padding(top=2.dp)) }
     Text("\${bio.length}/1200",fontSize=8.sp,color=Muted,fontWeight=FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(value=bio,onValueChange={if(it.length<=1200)onBio(it)},placeholder={Text("Опиши личность, поведение, привычки и детали мира…",color=Muted,fontSize=11.sp)},modifier=Modifier.fillMaxWidth(),minLines=5,maxLines=7,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Field,unfocusedContainerColor=Field,focusedBorderColor=Purple,unfocusedBorderColor=Border,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedPlaceholderColor=Muted,unfocusedPlaceholderColor=Muted))
    Text("Совет: укажи не только качества, но и то, как персонаж реагирует на разные ситуации.",fontSize=9.sp,color=Muted,lineHeight=14.sp,modifier=Modifier.padding(top=9.dp))
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=21.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("02",fontSize=10.sp,color=Purple,fontWeight=FontWeight.Black)
   Spacer(Modifier.width(8.dp))
   Text("ПРЕДПРОСМОТР",fontSize=8.sp,color=Yellow,fontWeight=FontWeight.Black,letterSpacing=1.4.sp)
  }
  Surface(color=Color(0xFF171A29),shape=RoundedCornerShape(21.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
   Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
    Box(Modifier.size(54.dp).background(Brush.linearGradient(listOf(Color(0xFF5B477F),Color(0xFF24253D))),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){Text(name.firstOrNull()?.uppercase()?: "✦",fontSize=22.sp,fontWeight=FontWeight.Black,color=Color.White)}
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
     Text(name.ifBlank{"Имя персонажа"},fontSize=14.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
     Text("Пользовательский персонаж · NEBULA",fontSize=8.sp,color=Purple,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=3.dp))
     Text(bio.ifBlank{"Здесь появится краткое описание характера."},fontSize=9.sp,color=Muted,maxLines=2,lineHeight=13.sp,modifier=Modifier.padding(top=5.dp))
    }
   }
  }
  Button(onClick=onCreate,enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=17.dp).height(56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFCDBEFF),contentColor=Color(0xFF211A32))) {
   Icon(Icons.Outlined.AutoAwesome,null,modifier=Modifier.size(18.dp))
   Spacer(Modifier.width(8.dp))
   Text("Создать персонажа",fontWeight=FontWeight.ExtraBold,fontSize=13.sp)
   Spacer(Modifier.width(5.dp))
   Icon(Icons.Outlined.ArrowForward,null,modifier=Modifier.size(17.dp))
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
 LaunchedEffect(lines.size,busy,error) {
  if(lines.isNotEmpty() && followOutput) {
   val target=lines.lastIndex+(if(busy)1 else 0)+(if(error.isNotBlank())1 else 0)
   listState.animateScrollToItem(target.coerceAtLeast(0))
  }
 }
 Box(Modifier.fillMaxSize().background(Bg)) {
  Canvas(Modifier.fillMaxSize()) {
   drawRect(Brush.linearGradient(personColors(avatarIndex)))
   if(avatarIndex in 0..1 && backdrop!=null) {
    val tileWidth=backdrop.width/2
    val tileHeight=backdrop.height/2
    drawImage(backdrop,srcOffset=IntOffset(avatarIndex*tileWidth,0),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(size.width.toInt(),(size.height*.72f).toInt()),alpha=.28f)
   }
   drawRect(Brush.verticalGradient(listOf(Color(0xB5090A12),Color(0xE9090B13),Color(0xFF0B0C13))))
  }
  Column(Modifier.fillMaxSize().imePadding()) {
   Surface(color=Color(0xD9161826),shape=RoundedCornerShape(bottomStart=23.dp,bottomEnd=23.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.07f)),modifier=Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
     IconButton(onClick=onBack,modifier=Modifier.size(40.dp).background(Color(0xFF252438),CircleShape)){Icon(Icons.Outlined.ArrowBack,null,tint=Color.White)}
     Spacer(Modifier.width(10.dp))
     Box {
      PortraitAvatar(avatarIndex,46,person.initials)
      Box(Modifier.align(Alignment.BottomEnd).size(11.dp).background(Color(0xFF86D8B4),CircleShape).border(2.dp,Color(0xFF191A29),CircleShape))
     }
     Spacer(Modifier.width(11.dp))
     Column(Modifier.weight(1f)) {
      Text(person.name,fontWeight=FontWeight.ExtraBold,fontSize=15.sp,color=Color.White,maxLines=1)
      Row(verticalAlignment=Alignment.CenterVertically) {
       Box(Modifier.size(5.dp).background(if(busy)Yellow else Color(0xFF86D8B4),CircleShape))
       Spacer(Modifier.width(5.dp))
       Text(if(busy)"Печатает…" else "В сети · NEBULA",fontSize=10.sp,color=if(busy)Yellow else Muted)
      }
     }
     IconButton(onClick=onSettings,modifier=Modifier.size(40.dp).background(Color(0xFF252438),CircleShape)){Icon(Icons.Outlined.Tune,null,tint=Color.White)}
    }
   }
   AnimatedVisibility(visible=lines.size<=1,enter=fadeIn(tween(180)),exit=fadeOut(tween(140))) {
    Surface(color=Color(0xD91B1D2D),shape=RoundedCornerShape(19.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.09f)),modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp).padding(top=10.dp,bottom=5.dp)) {
     Column(Modifier.padding(horizontal=16.dp,vertical=13.dp)) {
      Row(verticalAlignment=Alignment.CenterVertically) {
       Text("ПЕРЕД НАЧАЛОМ",fontSize=9.sp,color=Yellow,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,modifier=Modifier.weight(1f))
       Icon(Icons.Outlined.AutoAwesome,null,tint=Purple,modifier=Modifier.size(15.dp))
      }
      Text(person.bio.ifBlank{"Новая история начинается здесь."},fontSize=12.sp,lineHeight=18.sp,color=Color.White.copy(alpha=.86f),modifier=Modifier.padding(top=6.dp))
     }
    }
   }
   LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth().padding(horizontal=13.dp),contentPadding=PaddingValues(top=12.dp,bottom=15.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
    items(lines) { line ->
     Row(Modifier.fillMaxWidth(),horizontalArrangement=if(line.user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
      if(!line.user) { PortraitAvatar(avatarIndex,31,person.initials); Spacer(Modifier.width(8.dp)) }
      Column(horizontalAlignment=if(line.user)Alignment.End else Alignment.Start) {
       Text(if(line.user)"ТЫ" else person.name.uppercase(),fontSize=8.sp,fontWeight=FontWeight.Bold,color=if(line.user)Color(0xFFD4C5FF) else Muted,letterSpacing=1.sp,modifier=Modifier.padding(start=5.dp,bottom=4.dp))
       Surface(color=if(line.user)Color(0xFF59477E) else Color(0xF01B1E2C),shape=RoundedCornerShape(topStart=if(line.user)19.dp else 7.dp,topEnd=if(line.user)7.dp else 19.dp,bottomStart=19.dp,bottomEnd=19.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.09f)),modifier=Modifier.widthIn(max=310.dp)) {
        RichMessageText(line.text,line.user)
       }
      }
     }
    }
    if(busy) item { Row(verticalAlignment=Alignment.CenterVertically){PortraitAvatar(avatarIndex,31,person.initials);Spacer(Modifier.width(8.dp));TypingIndicator()} }
    if(error.isNotBlank()) item { Text(error,color=Color(0xFFFFB5B5),fontSize=11.sp,modifier=Modifier.padding(start=39.dp)) }
   }
   Surface(color=Color(0xF0171927),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.07f)),modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal=12.dp,vertical=9.dp)) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
      Surface(color=Color(0xFF242638),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Color(0xFF39364E)),modifier=Modifier.weight(1f)) {
       Row(Modifier.padding(start=14.dp,end=6.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.Bottom) {
        Icon(Icons.Outlined.AutoAwesome,null,tint=Purple,modifier=Modifier.padding(bottom=10.dp).size(17.dp))
        Spacer(Modifier.width(9.dp))
        BasicTextField(input,onInput,modifier=Modifier.weight(1f).padding(vertical=9.dp),singleLine=false,maxLines=4,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=14.sp,lineHeight=19.sp),decorationBox={inner->if(input.isEmpty())Text("Написать сообщение…",color=Muted,fontSize=13.sp);inner()})
        Spacer(Modifier.width(5.dp))
        IconButton(enabled=input.isNotBlank()&&!busy,onClick={onSend(input.trim())},modifier=Modifier.size(40.dp).background(if(input.isNotBlank()&&!busy)Color(0xFFC9B8FF) else Color(0xFF343747),CircleShape)) {
         Icon(Icons.Outlined.Send,"Отправить",tint=if(input.isNotBlank()&&!busy)Color(0xFF211A32) else Muted,modifier=Modifier.size(18.dp))
        }
       }
      }
     }
     Text("NEBULA AI  ·  ТВОЯ ИСТОРИЯ ПРОДОЛЖАЕТСЯ",fontSize=8.sp,color=Color(0xFF77798E),fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=7.dp))
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
    drawImage(image,srcOffset=IntOffset(index*tileWidth,0),srcSize=IntSize(tileWidth,tileHeight),dstSize=IntSize(this.size.width.toInt(),this.size.height.toInt()))
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
