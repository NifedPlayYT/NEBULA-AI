package ai.nebula.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val p = getSharedPreferences("nebula", MODE_PRIVATE)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme(background=Bg, surface=Card, primary=Purple, secondary=Yellow, onBackground=Color.White, onSurface=Color.White)) {
    Nebula(p.getString("base","https://api.orcarouter.ai/v1")!!, p.getString("model","deepseek/deepseek-v4.1-flash-free")!!, p.getString("key","")!!, p.getBoolean("clean_mode",true)) { b,m,k,clean ->
     p.edit().putString("base",b).putString("model",m).putString("key",k).putBoolean("clean_mode",clean).apply()
    }
   }
  }
 }
}
private data class Person(val name:String,val creator:String,val bio:String,val category:String,val initials:String)
private data class Line(val user:Boolean,val text:String)
private val people = listOf(
 Person("Рин Итоши","@nebula","Холодный и расчётливый футболист. Не любит пустых разговоров.","Аниме","РИ"),
 Person("Астра","@nebula","Спокойная, внимательная, с тонким чувством юмора.","ОС","А"),
 Person("Кай","@nebula","Ироничный напарник, который любит приключения.","Игры","К"),
 Person("Мира","@nebula","Загадочная собеседница со своей историей.","Фэнтези","М")
)

@Composable private fun Nebula(base0:String, model0:String, key0:String, clean0:Boolean, save:(String,String,String,Boolean)->Unit) {
 var page by remember { mutableStateOf("home") }
 var selected by remember { mutableStateOf(people[0]) }
 var category by remember { mutableStateOf("Все") }
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
 val scope = rememberCoroutineScope()
 val drawer = rememberDrawerState(DrawerValue.Closed)

 ModalNavigationDrawer(drawerState=drawer, drawerContent={
  ModalDrawerSheet(drawerContainerColor=Card) {
   Spacer(Modifier.height(20.dp))
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(horizontal=20.dp)){BrandMark(38);Spacer(Modifier.width(10.dp));Text("NEBULA",fontSize=25.sp,fontWeight=FontWeight.Black,letterSpacing=1.5.sp)}
   Text("ТВОЯ ВСЕЛЕННАЯ ИСТОРИЙ",fontSize=10.sp,color=Muted,letterSpacing=1.5.sp,modifier=Modifier.padding(horizontal=22.dp))
   Spacer(Modifier.height(26.dp))
   Text("СОЗДАТЬ",fontSize=11.sp,color=Muted,modifier=Modifier.padding(horizontal=22.dp,vertical=8.dp))
   NavItem("Новый персонаж",Icons.Outlined.AddCircle){page="create";scope.launch{drawer.close()}}
   Spacer(Modifier.height(16.dp))
   Text("ОБЗОР",fontSize=11.sp,color=Muted,modifier=Modifier.padding(horizontal=22.dp,vertical=8.dp))
   NavItem("Главная",Icons.Outlined.Home){page="home";scope.launch{drawer.close()}}
   NavItem("Каталог",Icons.Outlined.GridView){page="home";scope.launch{drawer.close()}}
   NavItem("Мои чаты",Icons.Outlined.Forum){page="chats";scope.launch{drawer.close()}}
   NavItem("Профиль",Icons.Outlined.Person){page="profile";scope.launch{drawer.close()}}
   Spacer(Modifier.weight(1f))
   NavItem("Настройки AI",Icons.Outlined.Settings){page="settings";scope.launch{drawer.close()}}
   Text("Сообщения в NEBULA бесплатны. Тарифы API зависят от провайдера.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(20.dp))
  }
 }) {
  Column(Modifier.fillMaxSize().background(Bg)) {
   Row(Modifier.fillMaxWidth().background(Card).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
    IconButton(onClick={scope.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,"Меню",tint=Muted)}
    BrandMark(34); Spacer(Modifier.width(9.dp)); Text("NEBULA",fontSize=22.sp,fontWeight=FontWeight.Black,letterSpacing=1.3.sp,modifier=Modifier.weight(1f))
    Surface(color=Field,shape=RoundedCornerShape(12.dp)){Icon(Icons.Outlined.Bolt,null,tint=Yellow,modifier=Modifier.padding(10.dp).size(22.dp))}
    Spacer(Modifier.width(9.dp))
    Surface(color=Purple,shape=RoundedCornerShape(12.dp),modifier=Modifier.clickable{page="settings"}) {
     Text("API ⚙",fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(horizontal=14.dp,vertical=11.dp))
    }
   }
   AnimatedContent(
    targetState=page,
    label="nebula-page-transition",
    transitionSpec={
     (fadeIn(tween(190)) + slideInHorizontally(tween(220)){it/12}) togetherWith
      (fadeOut(tween(130)) + slideOutHorizontally(tween(170)){-it/16})
    }
   ) { currentPage -> when(currentPage) {
    "home" -> Home(category,{category=it},search,{search=it}) { person ->
     selected=person; lines.clear(); error=""
     lines.add(Line(false,"Привет. Я ${person.name}. О чём поговорим?")); page="chat"
    }
    "create" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
     Text("‹  НАЗАД",color=Purple,modifier=Modifier.clickable{page="home"})
     Spacer(Modifier.height(22.dp)); Text("НОВЫЙ ПЕРСОНАЖ",fontSize=24.sp,fontWeight=FontWeight.Black)
     Text("Создай собеседника со своим характером.",color=Muted,modifier=Modifier.padding(vertical=8.dp))
     Text("ИМЯ",fontSize=11.sp,color=Muted); FieldInput(name,{name=it},"Например, Акира")
     Spacer(Modifier.height(15.dp)); Text("ХАРАКТЕР И ОПИСАНИЕ",fontSize=11.sp,color=Muted)
     FieldInput(bio,{bio=it},"Опиши личность и манеру общения",true)
     Spacer(Modifier.height(22.dp))
     Button(onClick={selected=Person(name.ifBlank{"Новый персонаж"},"@you",bio,"ОС","✦");lines.clear();lines.add(Line(false,"Привет! Я ${selected.name}."));page="chat"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Text("Создать персонажа")}
    }
    "settings" -> SettingsScreen(base,{base=it},model,{model=it},apiKey,{apiKey=it},cleanModeState,{cleanModeState=true},{save(base,model,apiKey,cleanModeState);page="home"},{page="home"})
    "profile" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) { Text("ПРОФИЛЬ",fontSize=24.sp,fontWeight=FontWeight.Black); Spacer(Modifier.height(18.dp)); Surface(color=Card,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){BrandMark(72);Text("ТВОЯ ВСЕЛЕННАЯ",fontWeight=FontWeight.Bold);Text("Создавай персонажей и истории.",color=Muted,fontSize=12.sp)}};Spacer(Modifier.height(16.dp));Button(onClick={page="create"},modifier=Modifier.fillMaxWidth()){Text("Создать персонажа")} }
    "chat" -> Column(Modifier.fillMaxSize()) {
     Row(Modifier.fillMaxWidth().background(Card).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
      IconButton(onClick={page="home"}){Icon(Icons.Outlined.ArrowBack,null)}
      Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){PortraitAvatar(people.indexOfFirst{it.name==selected.name}.coerceAtLeast(0),42);Spacer(Modifier.width(10.dp));Column{Text(selected.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text("Персонаж NEBULA",fontSize=11.sp,color=Muted)}}
      Icon(Icons.Outlined.MoreVert,null,tint=Muted)
     }
     LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
      items(lines){line->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(line.user)Arrangement.End else Arrangement.Start){
       Surface(color=if(line.user)Color(0xFF8060E8) else Color(0xFF20202E),shape=RoundedCornerShape(topStart=if(line.user)18.dp else 7.dp,topEnd=if(line.user)7.dp else 18.dp,bottomStart=18.dp,bottomEnd=18.dp),border=if(line.user)null else BorderStroke(1.dp,Border),modifier=Modifier.widthIn(max=310.dp)){RichMessageText(line.text,line.user)}
      }}
      if(busy)item{Text("${selected.name} печатает…",color=Muted)}
      if(error.isNotBlank())item{Text(error,color=Color(0xFFFF9999),fontSize=12.sp)}
     }
     Row(Modifier.fillMaxWidth().background(Card).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
      Surface(color=Field,shape=RoundedCornerShape(22.dp),modifier=Modifier.weight(1f)) {
       BasicTextField(input,{input=it},modifier=Modifier.padding(horizontal=15.dp,vertical=14.dp),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=15.sp),decorationBox={inner->if(input.isEmpty())Text("Написать сообщение…",color=Muted);inner()})
      }
      Spacer(Modifier.width(8.dp))
      IconButton(enabled=input.isNotBlank()&&!busy,onClick={
       val sent=input.trim();input="";lines.add(Line(true,sent));lines.add(Line(false,""));val replyIndex=lines.lastIndex;busy=true;error=""
       scope.launch{try{askAiStream(base,model,apiKey,selected.name,selected.bio,cleanModeState,lines.dropLast(1).toList()){chunk->lines[replyIndex]=Line(false,lines[replyIndex].text+chunk)};if(lines[replyIndex].text.isBlank())error="Модель вернула пустой ответ."}catch(e:Exception){error=e.message?:"Ошибка API";if(lines[replyIndex].text.isBlank())lines.removeAt(replyIndex)}finally{busy=false}}
      },modifier=Modifier.background(Purple,RoundedCornerShape(14.dp))){Icon(Icons.Outlined.Send,"Отправить",tint=Color.White)}
     }
    }
    else -> Column(Modifier.padding(22.dp)){Text("МОИ ЧАТЫ",fontSize=24.sp,fontWeight=FontWeight.Black);Text("История диалогов появится здесь.",color=Muted);Button(onClick={page="home"}){Text("К персонажам")}}
   }
   }
  }
 }
 

}

@Composable private fun SettingsScreen(base:String,onBase:(String)->Unit,model:String,onModel:(String)->Unit,key:String,onKey:(String)->Unit,clean:Boolean,onClean:(Boolean)->Unit,onSave:()->Unit,onBack:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,null,tint=Color.White)};Text("Настройки",fontSize=24.sp,fontWeight=FontWeight.Black)}
  Text("ПЕРСОНАЛИЗАЦИЯ",fontSize=11.sp,color=Purple,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=20.dp,bottom=10.dp))
  Surface(color=Card,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Shield,null,tint=Purple);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Чистый режим",fontWeight=FontWeight.Bold);Text("Фильтр грубой лексики и нежелательных выражений.",fontSize=12.sp,color=Muted)};Switch(checked=clean,onCheckedChange=onClean)};Text("Фильтр можно включать и выключать. Ограничения безопасности сохраняются всегда.",fontSize=11.sp,color=Muted)}}
  Text("ПОДКЛЮЧЕНИЕ AI",fontSize=11.sp,color=Purple,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=8.dp))
  Text("Адрес API",fontSize=12.sp,color=Muted);FieldInput(base,onBase,"https://api.orcarouter.ai/v1")
  Spacer(Modifier.height(12.dp));Text("Модель",fontSize=12.sp,color=Muted);FieldInput(model,onModel,"Название модели")
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){TextButton(onClick={onModel("deepseek/deepseek-v4.1-flash-free")}){Text("DeepSeek Free",fontSize=11.sp)};TextButton(onClick={onModel("orcarouter/free")}){Text("OrcaRouter Free",fontSize=11.sp)}}
  Text("API-ключ",fontSize=12.sp,color=Muted);FieldInput(key,onKey,"API-ключ провайдера")
  Surface(color=Field,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Text("Не публикуй APK с личным API-ключом. Запросы могут тарифицироваться провайдером.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(14.dp))}
  Spacer(Modifier.height(22.dp));Button(onClick=onSave,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(15.dp)){Text("Сохранить настройки",fontWeight=FontWeight.Bold)};Spacer(Modifier.height(18.dp))
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
@Composable private fun Home(category:String,onCategory:(String)->Unit,search:String,onSearch:(String)->Unit,onOpen:(Person)->Unit){
 val categories=listOf("Все","Аниме","Игры","ОС","Фэнтези","Реализм")
 val shown=people.filter{(category=="Все"||it.category==category)&&(it.name.contains(search,true)||it.bio.contains(search,true))}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp)){
  Spacer(Modifier.height(22.dp))
  Column(Modifier.fillMaxWidth().padding(horizontal=22.dp).background(Brush.linearGradient(listOf(Color(0xFF342957),Color(0xFF1D1D30),Bg)),RoundedCornerShape(22.dp)).padding(18.dp)){
   Text("ТВОЯ ВСЕЛЕННАЯ",fontSize=10.sp,color=Yellow,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
   Spacer(Modifier.height(7.dp))
   Text("Найди свою историю",fontSize=27.sp,fontWeight=FontWeight.Black,lineHeight=31.sp)
   Text("Персонажи, с которыми хочется говорить.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=6.dp))
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=15.dp)){BrandMark(26);Text("  NEBULA AI",fontSize=11.sp,color=Color.White.copy(alpha=.8f),letterSpacing=1.4.sp)}
  }
  Spacer(Modifier.height(16.dp))
  Surface(color=Field,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp)){
   Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Search,null,tint=Muted);Spacer(Modifier.width(10.dp));BasicTextField(search,onSearch,modifier=Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White),decorationBox={inner->if(search.isEmpty())Text("Поиск персонажей…",color=Muted);inner()})}
  }
  Spacer(Modifier.height(22.dp));Heading("🔥  ПОПУЛЯРНЫЕ КАТЕГОРИИ")
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=22.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
   categories.forEach{item->Surface(color=if(category==item)Purple else Card,shape=RoundedCornerShape(13.dp),modifier=Modifier.clickable{onCategory(item)}){Text(item,modifier=Modifier.padding(horizontal=18.dp,vertical=13.dp),fontWeight=FontWeight.SemiBold)}}
  }
  Spacer(Modifier.height(22.dp));Heading("✦  ПЕРСОНАЖИ ДЛЯ ТЕБЯ")
  shown.forEachIndexed{index,p->PersonCard(p,index,onOpen)}
  if(shown.isEmpty())Text("Ничего не найдено.",color=Muted,modifier=Modifier.padding(22.dp))
  Spacer(Modifier.height(18.dp));Heading("ТВОИ ПЕРСОНАЖИ")
  Surface(color=Card,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp)){
   Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AddCircleOutline,null,tint=Purple,modifier=Modifier.size(30.dp));Spacer(Modifier.width(12.dp));Column{Text("Создай собственного собеседника",fontWeight=FontWeight.Bold);Text("Настрой имя и характер",color=Muted,fontSize=12.sp)}}
  }
 }
}
@Composable private fun Heading(text:String){Text(text,fontSize=17.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(horizontal=22.dp,vertical=12.dp))}
@Composable private fun PersonCard(p:Person,index:Int,onOpen:(Person)->Unit){
 Surface(color=Card,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=6.dp).clickable{onOpen(p)}){
  Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
   PortraitAvatar(index,104)
   Column(Modifier.weight(1f).padding(start=14.dp)){Text(p.name,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(p.creator,color=Yellow,fontSize=11.sp);Text(p.bio,color=Muted,fontSize=12.sp,lineHeight=16.sp,maxLines=2);Spacer(Modifier.height(8.dp));Surface(color=Purple.copy(alpha=.16f),shape=RoundedCornerShape(7.dp)){Text(p.category.uppercase(),color=Purple,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))}}
   Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
  }
 }
}

@Composable private fun BrandMark(size:Int){
 Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color(0xFFB79CFF),Color(0xFF7651E8))),RoundedCornerShape((size/3).dp)),contentAlignment=Alignment.Center){
  Icon(Icons.Outlined.AutoAwesome,null,tint=Color.White,modifier=Modifier.size((size*.62f).dp))
 }
}

@Composable private fun PortraitAvatar(index:Int,size:Int){
 val palettes=listOf(
  listOf(Color(0xFF8A6BFF),Color(0xFF32254E),Color(0xFFE7BDAA),Color(0xFF242033)),
  listOf(Color(0xFF4F9DB2),Color(0xFF203E53),Color(0xFFE7C3A8),Color(0xFF26313F)),
  listOf(Color(0xFFE0A45D),Color(0xFF65412E),Color(0xFFDDAF91),Color(0xFF2E2525)),
  listOf(Color(0xFFB77BE5),Color(0xFF46305F),Color(0xFFF0C9B7),Color(0xFF30263B))
 )
 val p=palettes[index%palettes.size]
 Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(p[0],p[1])),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){
  Box(Modifier.fillMaxWidth(.72f).height((size*.66f).dp).align(Alignment.BottomCenter).background(p[3],RoundedCornerShape(topStart=50.dp,topEnd=50.dp,bottomStart=12.dp,bottomEnd=12.dp)))
  Box(Modifier.size((size*.53f).dp).offset(y=(-size*.02f).dp).background(p[2],RoundedCornerShape(42.dp)))
  Box(Modifier.size((size*.59f).dp).height((size*.29f).dp).align(Alignment.TopCenter).offset(y=(size*.13f).dp).background(p[3],RoundedCornerShape(topStart=40.dp,topEnd=40.dp,bottomStart=13.dp,bottomEnd=13.dp)))
  Row(Modifier.align(Alignment.Center).offset(y=(size*.035f).dp),horizontalArrangement=Arrangement.spacedBy((size*.14f).dp)){
   repeat(2){Box(Modifier.size((size*.045f).dp).background(Color(0xFF29202A),CircleShape))}
  }
  Box(Modifier.size((size*.10f).dp).align(Alignment.Center).offset(y=(size*.17f).dp).background(Color(0xFFB77E79),CircleShape))
 }
}
@Composable private fun FieldInput(value:String,onChange:(String)->Unit,hint:String,multi:Boolean=false){
 OutlinedTextField(value,onChange,placeholder={Text(hint,color=Muted)},modifier=Modifier.fillMaxWidth(),minLines=if(multi)4 else 1,maxLines=if(multi)6 else 1,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Purple,unfocusedBorderColor=Color(0xFF555555),focusedTextColor=Color.White,unfocusedTextColor=Color.White))
}
private suspend fun askAiStream(base:String,model:String,key:String,character:String,personality:String,cleanMode:Boolean,history:List<Line>,onChunk:(String)->Unit)=withContext(Dispatchers.IO){
 require(base.isNotBlank()&&model.isNotBlank()&&key.isNotBlank()){"Открой настройки API и укажи адрес, модель и ключ."}
 val msgs=JSONArray().put(JSONObject().put("role","system").put("content","Ты персонаж по имени $character. Режим общения: ${if(cleanMode) "чистый — избегай грубой лексики и резких выражений" else "обычный — допускается разговорная лексика"}. В любом режиме не создавай сексуальный или эротический контент. Твоя личность и характер: ${personality.ifBlank { "Самостоятельно поддерживай естественный характер и последовательный стиль общения." }}. Строго придерживайся этой личности в каждом ответе. Сохраняй характер и отвечай естественно. Пиши быстро и по делу: обычно 1–4 коротких предложения. Не повторяй вопрос и не добавляй лишние вступления. В ролевой сцене оформляй действия строго между одиночными звёздочками, например *сел на пол*; каждое действие должно быть отдельным фрагментом. Реплики персонажа в диалоге начинай с новой строки и тире, например: - Привет, как дела? Не используй Markdown-заголовки и лишние списки. В ролевой сцене описывай только нужные действия и реплики, не растягивай ответ без причины. Если пользователь просит подробности — отвечай подробнее."))
 history.takeLast(16).forEach{msgs.put(JSONObject().put("role",if(it.user)"user" else "assistant").put("content",it.text))}
 val body=JSONObject().put("model",model).put("messages",msgs).put("temperature",0.75).put("max_tokens",350).put("stream",true).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 val req=Request.Builder().url(base.trimEnd('/')+"/chat/completions").header("Authorization","Bearer $key").header("Accept","text/event-stream").post(body).build()
 val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).callTimeout(100,TimeUnit.SECONDS).build()
 client.newCall(req).execute().use{res->
  if(!res.isSuccessful){val raw=res.body?.string().orEmpty();throw IllegalStateException("API ${res.code}: ${raw.take(180)}")}
  val source=res.body?.source()?:throw IllegalStateException("Пустой ответ API")
  while(!source.exhausted()){
   val line=source.readUtf8Line()?:break
   if(!line.startsWith("data:"))continue
   val data=line.removePrefix("data:").trim()
   if(data=="[DONE]")break
   try{
    val delta=JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
    if(delta.isNotEmpty())withContext(Dispatchers.Main){onChunk(delta)}
   }catch(_:org.json.JSONException){}
  }
 }
}
