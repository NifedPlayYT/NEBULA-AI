package ai.nebula.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF202020)
private val Card = Color(0xFF2B2B2B)
private val Field = Color(0xFF363636)
private val Purple = Color(0xFF8860F5)
private val Yellow = Color(0xFFFFC85A)
private val Muted = Color(0xFFAAA7B0)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val p = getSharedPreferences("nebula", MODE_PRIVATE)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme(background=Bg, surface=Card, primary=Purple, onBackground=Color.White, onSurface=Color.White)) {
    Nebula(p.getString("base","https://api.openai.com/v1")!!, p.getString("model","gpt-4o-mini")!!, p.getString("key","")!!) { b,m,k ->
     p.edit().putString("base",b).putString("model",m).putString("key",k).apply()
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

@Composable private fun Nebula(base0:String, model0:String, key0:String, save:(String,String,String)->Unit) {
 var page by remember { mutableStateOf("home") }
 var selected by remember { mutableStateOf(people[0]) }
 var category by remember { mutableStateOf("Все") }
 var search by remember { mutableStateOf("") }
 var base by remember { mutableStateOf(base0) }
 var model by remember { mutableStateOf(model0) }
 var apiKey by remember { mutableStateOf(key0) }
 var showSettings by remember { mutableStateOf(false) }
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
   Text("NEBULA",fontSize=28.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(horizontal=22.dp))
   Text("ТВОЯ ВСЕЛЕННАЯ ИСТОРИЙ",fontSize=10.sp,color=Muted,letterSpacing=1.5.sp,modifier=Modifier.padding(horizontal=22.dp))
   Spacer(Modifier.height(26.dp))
   Text("СОЗДАТЬ",fontSize=11.sp,color=Muted,modifier=Modifier.padding(horizontal=22.dp,vertical=8.dp))
   NavItem("Новый персонаж",Icons.Outlined.AddCircle){page="create";scope.launch{drawer.close()}}
   Spacer(Modifier.height(16.dp))
   Text("ОБЗОР",fontSize=11.sp,color=Muted,modifier=Modifier.padding(horizontal=22.dp,vertical=8.dp))
   NavItem("Главная",Icons.Outlined.Home){page="home";scope.launch{drawer.close()}}
   NavItem("Каталог",Icons.Outlined.GridView){page="home";scope.launch{drawer.close()}}
   NavItem("Мои чаты",Icons.Outlined.Forum){page="chats";scope.launch{drawer.close()}}
   Spacer(Modifier.weight(1f))
   NavItem("Настройки AI",Icons.Outlined.Settings){showSettings=true;scope.launch{drawer.close()}}
   Text("Сообщения в NEBULA бесплатны. Тарифы API зависят от провайдера.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(20.dp))
  }
 }) {
  Column(Modifier.fillMaxSize().background(Bg)) {
   Row(Modifier.fillMaxWidth().background(Card).padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
    IconButton(onClick={scope.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,"Меню")}
    Text("NEBULA",fontSize=25.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
    Surface(color=Field,shape=RoundedCornerShape(12.dp)){Icon(Icons.Outlined.Bolt,null,tint=Yellow,modifier=Modifier.padding(10.dp).size(22.dp))}
    Spacer(Modifier.width(9.dp))
    Surface(color=Purple,shape=RoundedCornerShape(12.dp),modifier=Modifier.clickable{showSettings=true}) {
     Text("API ⚙",fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(horizontal=14.dp,vertical=11.dp))
    }
   }
   when(page) {
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
    "chat" -> Column(Modifier.fillMaxSize()) {
     Row(Modifier.fillMaxWidth().background(Card).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
      IconButton(onClick={page="home"}){Icon(Icons.Outlined.ArrowBack,null)}
      Column(Modifier.weight(1f)){Text(selected.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text("Персонаж NEBULA",fontSize=11.sp,color=Muted)}
      Icon(Icons.Outlined.MoreVert,null,tint=Muted)
     }
     LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
      items(lines){line->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(line.user)Arrangement.End else Arrangement.Start){
       Surface(color=if(line.user)Purple else Card,shape=RoundedCornerShape(17.dp),modifier=Modifier.widthIn(max=310.dp)){Text(line.text,modifier=Modifier.padding(14.dp))}
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
       scope.launch{try{askAiStream(base,model,apiKey,selected.name,lines.dropLast(1).toList()){chunk->lines[replyIndex]=Line(false,lines[replyIndex].text+chunk)};if(lines[replyIndex].text.isBlank())error="Модель вернула пустой ответ."}catch(e:Exception){error=e.message?:"Ошибка API";if(lines[replyIndex].text.isBlank())lines.removeAt(replyIndex)}finally{busy=false}}
      },modifier=Modifier.background(Purple,RoundedCornerShape(14.dp))){Icon(Icons.Outlined.Send,"Отправить",tint=Color.White)}
     }
    }
    else -> Column(Modifier.padding(22.dp)){Text("МОИ ЧАТЫ",fontSize=24.sp,fontWeight=FontWeight.Black);Text("История диалогов появится здесь.",color=Muted);Button(onClick={page="home"}){Text("К персонажам")}}
   }
  }
 }
 if(showSettings) AlertDialog(onDismissRequest={showSettings=false},containerColor=Card,title={Text("Подключение AI")},text={
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("Адрес API",color=Muted,fontSize=12.sp);FieldInput(base,{base=it},"https://provider.example/v1")
   Text("Модель",color=Muted,fontSize=12.sp);FieldInput(model,{model=it},"Название модели")
   Text("API-ключ",color=Muted,fontSize=12.sp);FieldInput(apiKey,{apiKey=it},"Ключ провайдера")
   Text("В приложении нет платных сообщений. API-провайдер может взимать плату за запросы. Не публикуй APK с личным ключом.",fontSize=11.sp,color=Muted)
  }
 },confirmButton={TextButton(onClick={save(base,model,apiKey);showSettings=false}){Text("Сохранить")}},dismissButton={TextButton(onClick={showSettings=false}){Text("Отмена")}})
}

@Composable private fun NavItem(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(horizontal=20.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Muted);Spacer(Modifier.width(18.dp));Text(label,fontSize=16.sp)}
}
@Composable private fun Home(category:String,onCategory:(String)->Unit,search:String,onSearch:(String)->Unit,onOpen:(Person)->Unit){
 val categories=listOf("Все","Аниме","Игры","ОС","Фэнтези","Реализм")
 val shown=people.filter{(category=="Все"||it.category==category)&&(it.name.contains(search,true)||it.bio.contains(search,true))}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp)){
  Spacer(Modifier.height(22.dp));Text("НАЙДИ СВОЮ ИСТОРИЮ",fontSize=24.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(horizontal=22.dp))
  Text("Выбери собеседника и начни общение",color=Muted,fontSize=14.sp,modifier=Modifier.padding(horizontal=22.dp,vertical=5.dp))
  Spacer(Modifier.height(16.dp))
  Surface(color=Field,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp)){
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
 val colors=listOf(Color(0xFF544052),Color(0xFF35465A),Color(0xFF66513C),Color(0xFF443B60))
 Surface(color=Card,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=6.dp).clickable{onOpen(p)}){
  Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(100.dp).background(Brush.linearGradient(listOf(colors[index%4],Bg)),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Text(p.initials,fontSize=30.sp,fontWeight=FontWeight.Black)}
   Column(Modifier.weight(1f).padding(start=13.dp)){Text(p.name,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(p.creator,color=Yellow,fontSize=12.sp);Text(p.bio,color=Muted,fontSize=12.sp,maxLines=2);Spacer(Modifier.height(7.dp));Text(p.category.uppercase(),color=Purple,fontSize=10.sp,fontWeight=FontWeight.Bold)}
   Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
  }
 }
}
@Composable private fun FieldInput(value:String,onChange:(String)->Unit,hint:String,multi:Boolean=false){
 OutlinedTextField(value,onChange,placeholder={Text(hint,color=Muted)},modifier=Modifier.fillMaxWidth(),minLines=if(multi)4 else 1,maxLines=if(multi)6 else 1,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Purple,unfocusedBorderColor=Color(0xFF555555),focusedTextColor=Color.White,unfocusedTextColor=Color.White))
}
private suspend fun askAiStream(base:String,model:String,key:String,character:String,history:List<Line>,onChunk:(String)->Unit)=withContext(Dispatchers.IO){
 require(base.isNotBlank()&&model.isNotBlank()&&key.isNotBlank()){"Открой настройки API и укажи адрес, модель и ключ."}
 val msgs=JSONArray().put(JSONObject().put("role","system").put("content","Ты персонаж по имени $character. Сохраняй характер и отвечай естественно. Пиши быстро и по делу: обычно 1–4 коротких предложения. Не повторяй вопрос и не добавляй лишние вступления. В ролевой сцене описывай только нужные действия и реплики, не растягивай ответ без причины. Если пользователь просит подробности — отвечай подробнее."))
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
