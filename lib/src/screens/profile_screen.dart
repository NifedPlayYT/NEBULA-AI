import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../app.dart';
import '../models/user_persona.dart';
import 'auth_screen.dart';
import 'privacy_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late Future<List<UserPersona>> _personas;

  @override
  void initState() {
    super.initState();
    _personas = repository.isSignedIn
        ? repository.loadPersonas()
        : Future.value(const <UserPersona>[]);
  }

  void _reload() {
    setState(() {
      _personas = repository.isSignedIn
          ? repository.loadPersonas()
          : Future.value(const <UserPersona>[]);
    });
  }

  Future<void> _openAuth() async {
    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const AuthScreen()),
    );
    if (result == true && mounted) _reload();
  }

  Future<void> _openEditor([UserPersona? persona]) async {
    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => PersonaEditorScreen(persona: persona)),
    );
    if (result == true && mounted) _reload();
  }

  Future<void> _signOut() async {
    try {
      await repository.signOut();
      if (mounted) _reload();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось выйти: $error')),
        );
      }
    }
  }

  Future<void> _deleteAccount() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Удалить аккаунт?'),
        content: const Text(
          'Будут удалены профиль, персоны, переписки и воспоминания. Это действие нельзя отменить.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Отмена'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFFB63F55)),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Удалить'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      await repository.deleteAccount();
      if (mounted) {
        _reload();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Аккаунт удалён.')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Ошибка удаления аккаунта: $error')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Профиль')),
        body: !repository.isConfigured
            ? const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                    'Для входа и синхронизации подключите Supabase: SUPABASE_URL и SUPABASE_ANON_KEY.',
                    textAlign: TextAlign.center,
                  ),
                ),
              )
            : !repository.isSignedIn
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.account_circle_outlined, size: 64, color: Colors.white54),
                          const SizedBox(height: 16),
                          const Text(
                            'Войдите, чтобы сохранять истории и создавать персоны.',
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 20),
                          FilledButton.icon(
                            onPressed: _openAuth,
                            icon: const Icon(Icons.login),
                            label: const Text('Войти или зарегистрироваться'),
                          ),
                        ],
                      ),
                    ),
                  )
                : FutureBuilder<List<UserPersona>>(
                    future: _personas,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (snapshot.hasError) {
                        return Center(child: Text('Не удалось загрузить персоны: ${snapshot.error}'));
                      }
                      final personas = snapshot.data ?? const <UserPersona>[];
                      return ListView(
                        padding: const EdgeInsets.all(20),
                        children: [
                          Text(
                            repository.client?.auth.currentUser?.email ?? 'Аккаунт NEBULA',
                            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17),
                          ),
                          const SizedBox(height: 6),
                          const Text(
                            'Персоны игрока',
                            style: TextStyle(color: Colors.white60),
                          ),
                          const SizedBox(height: 14),
                          ...personas.map(
                            (persona) => Card(
                              color: const Color(0xFF171B29),
                              child: ListTile(
                                leading: CircleAvatar(
                                  backgroundImage: persona.avatarUrl.isNotEmpty
                                      ? NetworkImage(persona.avatarUrl)
                                      : null,
                                  child: persona.avatarUrl.isEmpty
                                      ? const Icon(Icons.person_outline)
                                      : null,
                                ),
                                title: Text(persona.name),
                                subtitle: Text(persona.genderPronouns),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    if (persona.isActive)
                                      const Icon(Icons.check_circle, color: Color(0xFFB69AFF)),
                                    IconButton(
                                      tooltip: 'Изменить',
                                      onPressed: () => _openEditor(persona),
                                      icon: const Icon(Icons.edit_outlined),
                                    ),
                                  ],
                                ),
                                onTap: persona.isActive
                                    ? null
                                    : () async {
                                        try {
                                          await repository.activatePersona(persona.id);
                                          _reload();
                                        } catch (error) {
                                          if (mounted) {
                                            ScaffoldMessenger.of(context).showSnackBar(
                                              SnackBar(content: Text('Не удалось выбрать персону: $error')),
                                            );
                                          }
                                        }
                                      },
                              ),
                            ),
                          ),
                          const SizedBox(height: 12),
                          OutlinedButton.icon(
                            onPressed: () => _openEditor(),
                            icon: const Icon(Icons.add),
                            label: const Text('Создать персону'),
                          ),
                          const SizedBox(height: 18),
                          const Divider(color: Colors.white12),
                          ListTile(
                            leading: const Icon(Icons.privacy_tip_outlined),
                            title: const Text('Конфиденциальность'),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () => Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => const PrivacyScreen()),
                            ),
                          ),
                          ListTile(
                            leading: const Icon(Icons.logout),
                            title: const Text('Выйти из аккаунта'),
                            onTap: _signOut,
                          ),
                          ListTile(
                            leading: const Icon(Icons.delete_forever_outlined, color: Color(0xFFFF8A9B)),
                            title: const Text('Удалить аккаунт', style: TextStyle(color: Color(0xFFFF8A9B))),
                            onTap: _deleteAccount,
                          ),
                        ],
                      );
                    },
                  ),
      );
}

class PersonaEditorScreen extends StatefulWidget {
  const PersonaEditorScreen({super.key, this.persona});

  final UserPersona? persona;

  @override
  State<PersonaEditorScreen> createState() => _PersonaEditorScreenState();
}

class _PersonaEditorScreenState extends State<PersonaEditorScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _pronouns;
  late final TextEditingController _description;
  XFile? _image;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _name = TextEditingController(text: widget.persona?.name ?? '');
    _pronouns = TextEditingController(text: widget.persona?.genderPronouns ?? 'они/их');
    _description = TextEditingController(text: widget.persona?.description ?? '');
  }

  @override
  void dispose() {
    _name.dispose();
    _pronouns.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _pickImage() async {
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 86,
      maxWidth: 1400,
      maxHeight: 1400,
    );
    if (file == null || !mounted) return;
    final bytes = await file.length();
    if (bytes > 5 * 1024 * 1024) {
      setState(() => _error = 'Изображение должно быть меньше 5 МБ.');
      return;
    }
    setState(() {
      _image = file;
      _error = null;
    });
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      var avatarUrl = widget.persona?.avatarUrl ?? '';
      if (_image != null) {
        final mimeType = _image!.mimeType ?? 'image/jpeg';
        avatarUrl = await repository.uploadPersonaAvatar(
          await _image!.readAsBytes(),
          mimeType,
        );
      }
      await repository.savePersona(
        id: widget.persona?.id,
        name: _name.text,
        genderPronouns: _pronouns.text,
        description: _description.text,
        avatarUrl: avatarUrl,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.persona == null ? 'Новая персона' : 'Изменить персону')),
        body: SafeArea(
          child: Form(
            key: _formKey,
            child: ListView(
              padding: const EdgeInsets.all(20),
              children: [
                Center(
                  child: GestureDetector(
                    onTap: _pickImage,
                    child: CircleAvatar(
                      radius: 48,
                      backgroundImage: _image != null
                          ? FileImage(File(_image!.path))
                          : (widget.persona?.avatarUrl.isNotEmpty ?? false)
                              ? NetworkImage(widget.persona!.avatarUrl)
                              : null,
                      child: (_image == null && (widget.persona?.avatarUrl.isEmpty ?? true))
                          ? const Icon(Icons.add_a_photo_outlined, size: 30)
                          : null,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _pickImage,
                  child: const Text('Выбрать аватар'),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _name,
                  maxLength: 60,
                  decoration: const InputDecoration(labelText: 'Имя персоны'),
                  validator: (value) => (value?.trim().isEmpty ?? true) ? 'Укажите имя' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _pronouns,
                  maxLength: 40,
                  decoration: const InputDecoration(
                    labelText: 'Пол и местоимения',
                    hintText: 'например: он/его, она/её, они/их',
                  ),
                  validator: (value) => (value?.trim().isEmpty ?? true) ? 'Укажите местоимения' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _description,
                  maxLines: 5,
                  maxLength: 1200,
                  decoration: const InputDecoration(
                    labelText: 'Описание персонажа игрока',
                    alignLabelWithHint: true,
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: const TextStyle(color: Color(0xFFFF9B9B))),
                ],
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _saving ? null : _save,
                  child: _saving
                      ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('Сохранить персону'),
                ),
              ],
            ),
          ),
        ),
      );
}
