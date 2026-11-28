import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../models/character.dart';
class CharacterCard extends StatelessWidget {
 const CharacterCard({super.key,required this.character,required this.onTap});
 final Character character; final VoidCallback onTap;
 @override Widget build(BuildContext context)=>Material(color:const Color(0xFF171B29),borderRadius:BorderRadius.circular(22),clipBehavior:Clip.antiAlias,
  child:InkWell(onTap:onTap,child:AspectRatio(aspectRatio:.75,child:Stack(fit:StackFit.expand,children:[
   CachedNetworkImage(imageUrl:character.coverUrl,fit:BoxFit.cover,
    placeholder:(_,__)=>Shimmer.fromColors(baseColor:const Color(0xFF222638),highlightColor:const Color(0xFF353B52),child:Container(color:Colors.white)),
    errorWidget:(_,__,___)=>const ColoredBox(color:Color(0xFF20263A),child:Center(child:Icon(Icons.person_outline,size:44)))),
   const DecoratedBox(decoration:BoxDecoration(gradient:LinearGradient(begin:Alignment.topCenter,end:Alignment.bottomCenter,colors:[Colors.transparent,Color(0xF0000000)]))),
   Positioned(left:12,right:12,bottom:14,child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
    Text(character.name,maxLines:1,overflow:TextOverflow.ellipsis,style:const TextStyle(fontSize:17,fontWeight:FontWeight.w800)),
    Text(character.universeTag.toUpperCase(),style:const TextStyle(fontSize:10,letterSpacing:1.2,color:Color(0xFFC9B9FF))),
    Text('${character.messageCount} сообщений',style:const TextStyle(fontSize:11,color:Colors.white70))
   ]))
  ]))));
}
