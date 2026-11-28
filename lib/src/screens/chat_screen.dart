import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../app.dart';
import '../models/character.dart';
import 'auth_screen.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.character});

  final Character character;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatLine {
  const _ChatLine({
    required this.text,
    required this.isUser,
    this.messageId,
  });

  final String text;
  final bool isUser;
  final String? messageId;
}

class _ChatScreenState extends State<ChatScreen> {
  final _input = TextEditingController();
  final _scrollController = ScrollController();
  final _lines = <_ChatLine>[];
  final _branchId = const Uuid().v4();
  String _mode = 'roleplay';
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _lines.add(_ChatLine(text: widget.character.greeting, isUser: false));
  }

  @override
  void dispose() {
    _input.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<bool> _ensureSignedIn() async {
    if (!repository.isConfigured) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Облачный чат пока не настроен. Подключите Supabase.')),
      );
      return false;
    }
    if (repository.isSignedIn) return true;

    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const AuthScreen()),
    );
    return result == true && mounted;
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty || _sending) return;

    if (!await _ensureSignedIn()) return;
    if (!mounted) return;

    _input.clear();
    setState(() {
      _lines.add(_ChatLine(text: text, isUser: true));
      _sending = true;
    });
    _scrollToBottom();

    try {
      final result = await repository.sendMessage(
        characterId: widget.character.id,
        branchId: _branchId,
        content: text,
        mode: _mode,
      );
      if (!mounted) return;
      setState(() {
        _lines.add(_ChatLine(
          text: result.text,
          isUser: false,
          messageId: result.assistantMessageId,
        ));
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _lines.add(_ChatLine(
          text: 'Не удалось отправить сообщение. $error',
          isUser: false,
        ));
      });
    } finally {
      if (mounted) {
        setState(() => _sending = false);
        _scrollToBottom();
      }
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    });
  }

  Future<void> _report(_ChatLine line) async {
    final messageId = line.messageId;
    if (messageId == null) return;

    final reasonController = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Пожаловаться на сообщение'),
        content: TextField(
          controller: reasonController,
          minLines: 2,
          maxLines: 4,
          maxLength: 500,
          decoration: const InputDecoration(
            hintText: 'Кратко опишите проблему',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          FilledButton(
            onPressed: () {
              final value = reasonController.text.trim();
              if (value.length < 3) return;
              Navigator.pop(context, value);
            },
            child: const Text('Отправить'),
          ),
        ],
      ),
    );
    reasonController.dispose();
    if (reason == null || !mounted) return;

    try {
      await repository.reportMessage(messageId: messageId, reason: reason);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Жалоба отправлена. Спасибо за обратную связь.')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось отправить жалобу: $error')),
        );
      }
    }
  }

  Widget _buildMessage(_ChatLine line) {
    final alignment = line.isUser ? Alignment.centerRight : Alignment.centerLeft;
    final color = line.isUser ? const Color(0xFF7056C8) : const Color(0xFF1A2030);
    return Align(
      alignment: alignment,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 330),
        child: Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Column(
            crossAxisAlignment: line.isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 12),
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Text(line.text, style: const TextStyle(height: 1.4)),
              ),
              if (!line.isUser && line.messageId != null)
                TextButton.icon(
                  onPressed: () => _report(line),
                  icon: const Icon(Icons.flag_outlined, size: 15),
                  label: const Text('Пожаловаться', style: TextStyle(fontSize: 11)),
                  style: TextButton.styleFrom(
                    foregroundColor: Colors.white54,
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    minimumSize: Size.zero,
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          titleSpacing: 0,
          title: Row(
            children: [
              CircleAvatar(
                radius: 18,
                backgroundImage: widget.character.avatarUrl.isNotEmpty
                    ? CachedNetworkImageProvider(widget.character.avatarUrl)
                    : null,
                child: widget.character.avatarUrl.isEmpty
                    ? const Icon(Icons.person_outline, size: 18)
                    : null,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.character.name, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                    const Text('NEBULA STORY', style: TextStyle(fontSize: 9, letterSpacing: 1.4, color: Colors.white54)),
                  ],
                ),
              ),
            ],
          ),
        ),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(10),
              child: SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'chat', label: Text('Чат')),
                  ButtonSegment(value: 'roleplay', label: Text('Ролеплей')),
                  ButtonSegment(value: 'adventure', label: Text('Сюжет')),
                ],
                selected: {_mode},
                onSelectionChanged: (value) => setState(() => _mode = value.first),
              ),
            ),
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.all(14),
                itemCount: _lines.length + (_sending ? 1 : 0),
                itemBuilder: (context, index) {
                  if (_sending && index == _lines.length) {
                    return const Align(
                      alignment: Alignment.centerLeft,
                      child: Padding(
                        padding: EdgeInsets.all(12),
                        child: Text('Персонаж печатает…', style: TextStyle(color: Colors.white54)),
                      ),
                    );
                  }
                  return _buildMessage(_lines[index]);
                },
              ),
            ),
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _input,
                        minLines: 1,
                        maxLines: 5,
                        textInputAction: TextInputAction.send,
                        onSubmitted: (_) => _send(),
                        decoration: InputDecoration(
                          hintText: 'Напиши сообщение…',
                          filled: true,
                          fillColor: const Color(0xFF171B29),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(18),
                            borderSide: BorderSide.none,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton.filled(
                      onPressed: _sending ? null : _send,
                      icon: const Icon(Icons.arrow_upward_rounded),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
}
