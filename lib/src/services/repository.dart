import 'dart:typed_data';

import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../models/character.dart';
import '../models/user_persona.dart';

class ChatReply {
  const ChatReply({
    required this.text,
    this.userMessageId,
    this.assistantMessageId,
    this.variantGroup,
    this.variantIndex = 0,
  });

  final String text;
  final String? userMessageId;
  final String? assistantMessageId;
  final String? variantGroup;
  final int variantIndex;
}

class NebulaRepository {
  SupabaseClient? get client {
    try {
      return Supabase.instance.client;
    } catch (_) {
      return null;
    }
  }

  bool get isConfigured => client != null;
  bool get isSignedIn => client?.auth.currentUser != null;

  Future<List<Character>> loadCharacters() async {
    final supabase = client;
    if (supabase == null) return demoCharacters;

    try {
      final publicRows = await supabase
          .from('characters')
          .select()
          .eq('is_public', true)
          .order('created_at', ascending: false)
          .limit(50);
      final byId = <String, Character>{};
      for (final row in publicRows) {
        final character = Character.fromMap(row);
        byId[character.id] = character;
      }

      final user = supabase.auth.currentUser;
      if (user != null) {
        final ownedRows = await supabase
            .from('characters')
            .select()
            .eq('creator_id', user.id)
            .order('created_at', ascending: false)
            .limit(50);
        for (final row in ownedRows) {
          final character = Character.fromMap(row);
          byId[character.id] = character;
        }
      }

      if (byId.isEmpty) return demoCharacters;
      return byId.values.toList(growable: false);
    } catch (_) {
      return demoCharacters;
    }
  }

  Future<Character> saveCharacter({
    String? id,
    required String name,
    required String avatarUrl,
    required String coverUrl,
    required String genderPronouns,
    required String universeTag,
    required String relationshipDynamic,
    required String personality,
    required String backstory,
    required String speechStyle,
    required String greeting,
    required List<Map<String, dynamic>> exampleDialogues,
  }) async {
    final supabase = client;
    final user = supabase?.auth.currentUser;
    if (supabase == null || user == null) {
      throw StateError('Сначала войдите в аккаунт.');
    }

    final values = <String, dynamic>{
      'name': name.trim(),
      'avatar_url': avatarUrl,
      'cover_url': coverUrl,
      'gender_pronouns': genderPronouns.trim(),
      'universe_tag': universeTag,
      'relationship_dynamic': relationshipDynamic,
      'personality': personality.trim(),
      'backstory': backstory.trim(),
      'speech_style': speechStyle.trim(),
      'greeting': greeting.trim(),
      'example_dialogues': exampleDialogues,
    };

    final Map<String, dynamic> saved;
    if (id == null) {
      values['creator_id'] = user.id;
      values['is_public'] = false;
      saved = await supabase.from('characters').insert(values).select().single();
    } else {
      saved = await supabase
          .from('characters')
          .update(values)
          .eq('id', id)
          .eq('creator_id', user.id)
          .select()
          .single();
    }
    return Character.fromMap(saved);
  }

  Future<String> uploadCharacterArt(Uint8List bytes, String mimeType) async {
    final supabase = client;
    final user = supabase?.auth.currentUser;
    if (supabase == null || user == null) {
      throw StateError('Сначала войдите в аккаунт.');
    }
    if (bytes.isEmpty || bytes.length > 5 * 1024 * 1024) {
      throw ArgumentError('Изображение должно быть меньше 5 МБ.');
    }

    final extension = switch (mimeType) {
      'image/png' => 'png',
      'image/webp' => 'webp',
      _ => 'jpg',
    };
    final objectPath = '${user.id}/characters/${const Uuid().v4()}.$extension';
    await supabase.storage.from('character-art').uploadBinary(
      objectPath,
      bytes,
      fileOptions: FileOptions(
        contentType: mimeType,
        cacheControl: '3600',
        upsert: false,
      ),
    );
    return supabase.storage.from('character-art').getPublicUrl(objectPath);
  }

  Future<List<UserPersona>> loadPersonas() async {
    final supabase = client;
    if (supabase == null || supabase.auth.currentUser == null) {
      throw StateError('Сначала войдите в аккаунт.');
    }

    final rows = await supabase
        .from('user_personas')
        .select()
        .order('created_at', ascending: true);
    return rows.map((row) => UserPersona.fromMap(row)).toList();
  }

  Future<UserPersona> savePersona({
    String? id,
    required String name,
    required String genderPronouns,
    required String description,
    String avatarUrl = '',
  }) async {
    final supabase = client;
    final user = supabase?.auth.currentUser;
    if (supabase == null || user == null) {
      throw StateError('Сначала войдите в аккаунт.');
    }

    final values = <String, dynamic>{
      'name': name.trim(),
      'gender_pronouns': genderPronouns.trim(),
      'description': description.trim(),
      'avatar_url': avatarUrl,
    };

    final Map<String, dynamic> saved;
    if (id == null) {
      values['user_id'] = user.id;
      saved = await supabase.from('user_personas').insert(values).select().single();
    } else {
      saved = await supabase
          .from('user_personas')
          .update(values)
          .eq('id', id)
          .eq('user_id', user.id)
          .select()
          .single();
    }

    final persona = UserPersona.fromMap(saved);
    final existing = await loadPersonas();
    if (existing.where((item) => item.isActive).isEmpty) {
      await activatePersona(persona.id);
      return (await loadPersonas()).firstWhere((item) => item.id == persona.id);
    }
    return persona;
  }

  Future<void> activatePersona(String personaId) async {
    final supabase = client;
    if (supabase == null || supabase.auth.currentUser == null) {
      throw StateError('Сначала войдите в аккаунт.');
    }
    await supabase.rpc('activate_user_persona', params: {
      'target_persona_id': personaId,
    });
  }

  Future<String> uploadPersonaAvatar(Uint8List bytes, String mimeType) async {
    final supabase = client;
    final user = supabase?.auth.currentUser;
    if (supabase == null || user == null) {
      throw StateError('Сначала войдите в аккаунт.');
    }
    if (bytes.isEmpty || bytes.length > 5 * 1024 * 1024) {
      throw ArgumentError('Изображение должно быть меньше 5 МБ.');
    }

    final extension = switch (mimeType) {
      'image/png' => 'png',
      'image/webp' => 'webp',
      _ => 'jpg',
    };
    final objectPath = '${user.id}/personas/${const Uuid().v4()}.$extension';
    await supabase.storage.from('character-art').uploadBinary(
      objectPath,
      bytes,
      fileOptions: FileOptions(
        contentType: mimeType,
        cacheControl: '3600',
        upsert: false,
      ),
    );
    return supabase.storage.from('character-art').getPublicUrl(objectPath);
  }

  Future<ChatReply> sendMessage({
    required String characterId,
    required String branchId,
    required String content,
    required String mode,
  }) async {
    final supabase = client;
    if (supabase == null || supabase.auth.currentUser == null) {
      throw StateError('Для облачного чата войдите в аккаунт и настройте Supabase.');
    }

    final response = await supabase.functions.invoke(
      'chat',
      body: {
        'character_id': characterId,
        'branch_id': branchId,
        'content': content,
        'mode': mode,
      },
    );
    if (response.status < 200 || response.status >= 300) {
      final message = (response.data is Map<String, dynamic>)
          ? (response.data as Map<String, dynamic>)['message']?.toString()
          : null;
      throw StateError(message ?? 'Ошибка сервера: ${response.status}');
    }
    final data = response.data as Map<String, dynamic>;
    return ChatReply(
      text: data['reply'] as String? ?? 'Пустой ответ модели.',
      userMessageId: data['user_message_id'] as String?,
      assistantMessageId: data['assistant_message_id'] as String?,
      variantGroup: data['variant_group'] as String?,
      variantIndex: (data['variant_index'] as int?) ?? 0,
    );
  }

  Future<void> reportMessage({
    required String messageId,
    required String reason,
  }) async {
    final supabase = client;
    final user = supabase?.auth.currentUser;
    if (supabase == null || user == null) {
      throw StateError('Войдите в аккаунт, чтобы отправить жалобу.');
    }
    await supabase.from('message_reports').insert({
      'reporter_id': user.id,
      'message_id': messageId,
      'reason': reason,
    });
  }

  Future<void> signOut() async {
    final supabase = client;
    if (supabase == null) return;
    await supabase.auth.signOut();
  }

  Future<void> deleteAccount() async {
    final supabase = client;
    if (supabase == null || supabase.auth.currentUser == null) {
      throw StateError('Аккаунт не найден.');
    }
    final response = await supabase.functions.invoke('delete-account');
    if (response.status < 200 || response.status >= 300) {
      throw StateError('Не удалось удалить аккаунт: ${response.status}');
    }
    await supabase.auth.signOut();
  }
}
