import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/character.dart';
class NebulaRepository {
 SupabaseClient? get client { try { return Supabase.instance.client; } catch (_) { return null; } }
 Future<List<Character>> loadCharacters() async {
  final c=client; if(c==null) return demoCharacters;
  try { final rows=await c.from('characters').select().eq('is_public',true).limit(50);
   if(rows.isEmpty) return demoCharacters; return rows.map((r)=>Character.fromMap(r)).toList();
  } catch (_) { return demoCharacters; }
 }
 Future<String> sendMessage({required String characterId,required String branchId,required String content,required String mode}) async {
  final c=client; if(c==null || c.auth.currentUser==null) throw StateError('Для облачного чата войдите в аккаунт и настройте Supabase.');
  final r=await c.functions.invoke('chat',body:{'character_id':characterId,'branch_id':branchId,'content':content,'mode':mode});
  if(r.status<200 || r.status>=300) throw StateError('Ошибка сервера: ${r.status}');
  return (r.data as Map<String,dynamic>)['reply'] as String? ?? 'Пустой ответ модели.';
 }
}
