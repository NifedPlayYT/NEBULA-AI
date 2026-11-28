import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../app.dart';
import '../models/character.dart';

class CharacterEditorScreen extends StatefulWidget {
  const CharacterEditorScreen({super.key, this.character});

  final Character? character;

  @override
  State<CharacterEditorScreen> createState() => _CharacterEditorScreenState();
}

class _CharacterEditorScreenState extends State<CharacterEditorScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _pronouns;
  late final TextEditingController _personality;
  late final TextEditingController _backstory;
  late final TextEditingController _speechStyle;
  late final TextEditingController _greeting;
  late final TextEditingController _example;
  late final TextEditingController _imageDescription;

  final _picker = ImagePicker();
  XFile? _pickedImage;
  String _avatarUrl = '';
  String _coverUrl = '';
  String _universe = 'anime';
  String _relationship = 'friendship';
  bool _saving = false;
  String? _error;

  static const _universes = <String, String>{
    'anime': 'Аниме',
    'fantasy': 'Фэнтези',
    'realism': 'Современность',
    'sci-fi': 'Научная фантастика',
    'other': 'Другое',
  };

  static const _relationships = <String, String>{
    'friendship': 'Дружба',
    'rivalry': 'Соперничество',
    'mentorship': 'Наставничество',
    'romance': 'Романтическая линия',
    'adventure': 'Приключение',
  };

  @override
  void initState() {
    super.initState();
    final character = widget.character;
    _name = TextEditingController(text: character?.name ?? '');
    _pronouns = TextEditingController(text: character?.genderPronouns ?? 'они/их');
    _personality = TextEditingController(text: character?.personality ?? '');
    _backstory = TextEditingController(text: character?.backstory ?? '');
    _speechStyle = TextEditingController(text: character?.speechStyle ?? '');
    _greeting = TextEditingController(text: character?.greeting ?? '');
    _example = TextEditingController(
      text: character?.exampleDialogues.isNotEmpty == true
          ? 'Игрок: ${character!.exampleDialogues.first['user'] ?? ''} | Персонаж: ${character.exampleDialogues.first['character'] ?? ''}'
          : '',
    );
    _imageDescription = TextEditingController(text: character?.personality ?? '');
    _avatarUrl = character?.avatarUrl ?? '';
    _coverUrl = character?.coverUrl ?? '';
    if (_universes.containsKey(character?.universeTag)) {
      _universe = character!.universeTag;
    }
    if (_relationships.containsKey(character?.relationshipDynamic)) {
      _relationship = character!.relationshipDynamic;
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _pronouns.dispose();
    _personality.dispose();
    _backstory.dispose();
    _speechStyle.dispose();
    _greeting.dispose();
    _example.dispose();
    _imageDescription.dispose();
    super.dispose();
  }

  Future<void> _pickImage() async {
    final image = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 86,
      maxWidth: 1600,
      maxHeight: 2000,
    );
    if (image == null || !mounted) return;
    if (await image.length() > 5 * 1024 * 1024) {
      setState(() => _error = 'Изображение должно быть меньше 5 МБ.');
      return;
    }
    setState(() {
      _pickedImage = image;
      _error = null;
    });
  }

  void _generateImageUrl() {
    final description = _imageDescription.text.trim();
    if (description.length < 8) {
      setState(() => _error = 'Добавьте описание внешности для генерации обложки.');
      return;
    }
    final prompt = 'PG-13 safe, non-sexual, fully clothed anime character portrait, $description, cinematic lighting, detailed background';
    final encodedPrompt = Uri.encodeComponent(prompt);
    final uri = Uri.parse(
      'https://image.pollinations.ai/prompt/$encodedPrompt?width=600&height=800&nologo=true',
    );
    setState(() {
      _avatarUrl = uri.toString();
      _coverUrl = uri.toString();
      _pickedImage = null;
      _error = null;
    });
  }

  List<Map<String, dynamic>> _parseExamples() {
    final value = _example.text.trim();
    if (value.isEmpty) return const [];
    final parts = value.split('|');
    if (parts.length < 2) return const [];
    final userText = parts.first.replaceFirst(RegExp(r'^\s*Игрок:\s*'), '').trim();
    final characterText = parts.sublist(1).join('|').replaceFirst(RegExp(r'^\s*Персонаж:\s*'), '').trim();
    if (userText.isEmpty || characterText.isEmpty) return const [];
    return [{'user': userText, 'character': characterText}];
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      var avatarUrl = _avatarUrl;
      if (_pickedImage != null) {
        avatarUrl = await repository.uploadCharacterArt(
          await _pickedImage!.readAsBytes(),
          _pickedImage!.mimeType ?? 'image/jpeg',
        );
      }
      if (avatarUrl.isEmpty) {
        throw StateError('Загрузите изображение или создайте обложку по описанию.');
      }

      await repository.saveCharacter(
        id: widget.character?.id,
        name: _name.text,
        avatarUrl: avatarUrl,
        coverUrl: _coverUrl.isEmpty ? avatarUrl : _coverUrl,
        genderPronouns: _pronouns.text,
        universeTag: _universe,
        relationshipDynamic: _relationship,
        personality: _personality.text,
        backstory: _backstory.text,
        speechStyle: _speechStyle.text,
        greeting: _greeting.text,
        exampleDialogues: _parseExamples(),
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _imagePreview() {
    if (_pickedImage != null) {
      return Image.file(File(_pickedImage!.path), fit: BoxFit.cover);
    }
    if (_avatarUrl.isNotEmpty) {
      return CachedNetworkImage(
        imageUrl: _avatarUrl,
        fit: BoxFit.cover,
        placeholder: (_, __) => const Center(child: CircularProgressIndicator()),
        errorWidget: (_, __, ___) => const Center(child: Icon(Icons.broken_image_outlined, size: 40)),
      );
    }
    return const Center(child: Icon(Icons.add_photo_alternate_outlined, size: 44, color: Colors.white54));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(widget.character == null ? 'Создать персонажа' : 'Редактировать персонажа'),
        ),
        body: SafeArea(
          child: Form(
            key: _formKey,
            child: ListView(
              padding: const EdgeInsets.all(20),
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(20),
                  child: AspectRatio(
                    aspectRatio: 3 / 4,
                    child: ColoredBox(
                      color: const Color(0xFF171B29),
                      child: _imagePreview(),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _pickImage,
                        icon: const Icon(Icons.upload_outlined),
                        label: const Text('Загрузить'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.tonalIcon(
                        onPressed: _generateImageUrl,
                        icon: const Icon(Icons.auto_awesome),
                        label: const Text('Создать арт'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _imageDescription,
                  maxLength: 400,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'Описание внешности для арта',
                    hintText: 'Например: девушка-маг с серебряными волосами и зелёными глазами',
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _name,
                  maxLength: 80,
                  decoration: const InputDecoration(labelText: 'Имя персонажа'),
                  validator: (value) => (value?.trim().isEmpty ?? true) ? 'Укажите имя' : null,
                ),
                TextFormField(
                  controller: _pronouns,
                  maxLength: 40,
                  decoration: const InputDecoration(
                    labelText: 'Пол и местоимения',
                    hintText: 'он/его, она/её, они/их, существо/оно',
                  ),
                  validator: (value) => (value?.trim().isEmpty ?? true) ? 'Укажите местоимения' : null,
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: _universe,
                  decoration: const InputDecoration(labelText: 'Вселенная'),
                  items: _universes.entries
                      .map((entry) => DropdownMenuItem(value: entry.key, child: Text(entry.value)))
                      .toList(),
                  onChanged: (value) => setState(() => _universe = value ?? _universe),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: _relationship,
                  decoration: const InputDecoration(labelText: 'Динамика отношений'),
                  items: _relationships.entries
                      .map((entry) => DropdownMenuItem(value: entry.key, child: Text(entry.value)))
                      .toList(),
                  onChanged: (value) => setState(() => _relationship = value ?? _relationship),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _personality,
                  maxLines: 4,
                  maxLength: 1800,
                  decoration: const InputDecoration(labelText: 'Характер', alignLabelWithHint: true),
                  validator: (value) => (value?.trim().isEmpty ?? true) ? 'Опишите характер' : null,
                ),
                TextFormField(
                  controller: _backstory,
                  maxLines: 5,
                  maxLength: 3000,
                  decoration: const InputDecoration(labelText: 'Предыстория', alignLabelWithHint: true),
                  validator: (value) => (value?.trim().isEmpty ?? true) ? 'Добавьте предысторию' : null,
                ),
                TextFormField(
                  controller: _speechStyle,
                  maxLines: 3,
                  maxLength: 1000,
                  decoration: const InputDecoration(labelText: 'Манера речи', alignLabelWithHint: true),
                ),
                TextFormField(
                  controller: _greeting,
                  maxLines: 3,
                  maxLength: 1000,
                  decoration: const InputDecoration(labelText: 'Приветствие', alignLabelWithHint: true),
                ),
                TextFormField(
                  controller: _example,
                  maxLines: 3,
                  maxLength: 1200,
                  decoration: const InputDecoration(
                    labelText: 'Пример диалога (необязательно)',
                    hintText: 'Игрок: Привет | Персонаж: Рад тебя видеть',
                    alignLabelWithHint: true,
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: const TextStyle(color: Color(0xFFFF9B9B))),
                ],
                const SizedBox(height: 18),
                FilledButton(
                  onPressed: _saving ? null : _save,
                  child: _saving
                      ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('Сохранить персонажа'),
                ),
              ],
            ),
          ),
        ),
      );
}
