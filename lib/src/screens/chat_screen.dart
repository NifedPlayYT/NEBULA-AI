import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import '../app.dart';
import '../models/character.dart';
class ChatScreen extends StatefulWidget { const ChatScreen({super.key,required this.character}); final Character character; @override State<ChatScreen> createState()=>_ChatScreenState(); }
class _Line { _Line(this.text,this.user); final String text; final bool user; }
class _ChatScreenState extends State<ChatScreen> {
 final input=TextEditingController(); final lines=<_Line>[]; final branch=const Uuid().v4(); String mode='roleplay'; bool sending=false;
 @override void initState(){super.initState();lines.add(_Line(widget.character.greeting,false));}
 @override void dispose(){input.dispose();super.dispose();}
 Future<void> send() async {
  final value=input.text.trim(); if(value.isEmpty||sending)return; input.clear();
  setState((){lines.add(_Line(value,true));sending=true;});
  try { final answer=await repository.sendMessage(characterId:widget.character.id,branchId:branch,content:value,mode:mode); if(mounted)setState(()=>lines.add(_Line(answer,false))); }
  catch(e){if(mounted)setState(()=>lines.add(_Line('Не удалось отправить сообщение: $e',false)));}
  finally{if(mounted)setState(()=>sending=false);}
 }
 @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:Text(widget.character.name)),
 body:Column(children:[
  Padding(padding:const EdgeInsets.all(10),child:SegmentedButton<String>(segments:const [ButtonSegment(value:'chat',label:Text('Чат')),ButtonSegment(value:'roleplay',label:Text('Ролеплей')),ButtonSegment(value:'adventure',label:Text('Сюжет'))],selected:{mode},onSelectionChanged:(v)=>setState(()=>mode=v.first))),
  Expanded(child:ListView.builder(padding:const EdgeInsets.all(14),itemCount:lines.length+(sending?1:0),itemBuilder:(context,i){
   if(sending&&i==lines.length)return const Padding(padding:EdgeInsets.all(12),child:Text('Персонаж печатает…',style:TextStyle(color:Colors.white54)));
   final line=lines[i];return Align(alignment:line.user?Alignment.centerRight:Alignment.centerLeft,child:Container(constraints:const BoxConstraints(maxWidth:310),margin:const EdgeInsets.only(bottom:10),padding:const EdgeInsets.all(14),decoration:BoxDecoration(color:line.user?const Color(0xFF7056C8):const Color(0xFF1A2030),borderRadius:BorderRadius.circular(18)),child:Text(line.text,style:const TextStyle(height:1.4))));
  })),
  SafeArea(top:false,child:Padding(padding:const EdgeInsets.all(12),child:Row(children:[
   Expanded(child:TextField(controller:input,minLines:1,maxLines:5,textInputAction:TextInputAction.send,onSubmitted:(_)=>send(),decoration:InputDecoration(hintText:'Напиши сообщение…',filled:true,fillColor:const Color(0xFF171B29),border:OutlineInputBorder(borderRadius:BorderRadius.circular(18),borderSide:BorderSide.none)))),
   const SizedBox(width:8),IconButton.filled(onPressed:sending?null:send,icon:const Icon(Icons.arrow_upward_rounded))
  ])))
 ]));
}
