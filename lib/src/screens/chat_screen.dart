import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../app.dart';
import '../models/character.dart';
import '../models/chat_conversation.dart';
import '../services/repository.dart';
import 'auth_screen.dart';

class _MessageVariant {
  const _MessageVariant({
    required this.id,
    required this.text,
    required this.index,
  });

  final String id;
  final String text;
  final int index;
}

class _ChatLine {
  _ChatLine({
    required this.text,
    required this.isUser,
    this.messageId,
    this.variantGroup,
  });

  String text;
  final bool isUser;
  String? messageId;
  String? variantGroup;
  final List<_MessageVariant> variants = [];
  int selectedVariant = 0;

  void addVariant(ChatReply reply) {
    final id = reply.assistantMessageId;
    if (id == null) return;

    final existing = variants.indexWhere((variant) => variant.id == id);
    if (existing < 0) {
      variants.add(_MessageVariant(
        id: id,
        text: reply.text,
        index: reply.variantIndex,
      ));
    }
    variants.sort((a, b) => a.index.compareTo(b.index));
    activateById(id);
    variantGroup = reply.variantGroup;
  }

  void clearVariants() {
    variants.clear();
    selectedVariant = 0;
    messageId = null;
    variantGroup = null;
  }

  void activateById(String id) {
    final index = variants.indexWhere((variant) => variant.id == id);
    if (index >= 0) activate(index);
  }

  void activate(int index) {
    if (index < 0 || index >= variants.length) return;
    selectedVariant = index;
    final variant = variants[index];
    text = variant.text;
    messageId = variant.id;
  }
}

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.character});

  final Character character;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _input = TextEditingController();
  final _scrollController = ScrollController();
  final _lines = <_ChatLine>[];
  String _branchId = const Uuid().v4();
  String _mode = 'roleplay';
  bool _sending = false;
  bool _loading = false;
  bool _conversationLoaded = false;

  @override
  void initState() {
    super.initState();
    _lines.add(_ChatLine(text: widget.character.greeting, isUser: false));
    if (repository.isSignedIn) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _loadConversation();
      });
    }
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

  Future<bool> _loadConversation() async {
    if (_conversationLoaded) return true;
    if (_loading) return false;

    setState(() => _loading = true);
    try {
      final conversation = await repository.openConversation(widget.character.id);
      if (!mounted) return false;
      _applyConversation(conversation);
      _conversationLoaded = true;
      return true;
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось восстановить историю: $error')),
        );
      }
      return false;
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _applyConversation(ChatConversation conversation) {
    _branchId = conversation.branchId;
    _lines.clear();

    if (conversation.turns.isEmpty) {
      _lines.add(_ChatLine(text: widget.character.greeting, isUser: false));
      setState(() {});
      return;
    }

    for (final turn in conversation.turns) {
      _lines.add(_ChatLine(
        text: turn.userText,
        isUser: true,
        messageId: turn.userMessageId,
      ));

      if (turn.variants.isEmpty) continue;
      final selected = turn.selectedVariant;
      final assistantLine = _ChatLine(
        text: selected?.text ?? turn.variants.first.text,
        isUser: false,
      );
      for (final variant in turn.variants) {
        assistantLine.addVariant(ChatReply(
          text: variant.text,
          assistantMessageId: variant.id,
          variantIndex: variant.index,
        ));
      }
      if (selected != null) {
        assistantLine.activateById(selected.id);
      }
      _lines.add(assistantLine);
    }
    setState(() {});
    _scrollToBottom();
  }

  Future<bool> _ensureConversation() async {
    if (!await _ensureSignedIn() || !mounted) return false;
    if (_conversationLoaded) return true;
    return _loadConversation();
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty || _sending) return;
    if (!await _ensureConversation() || !mounted) return;

    _input.clear();
    final userLine = _ChatLine(text: text, isUser: true);
    setState(() {
      _lines.add(userLine);
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

      userLine.messageId = result.userMessageId;
      final assistantLine = _ChatLine(text: result.text, isUser: false);
      assistantLine.addVariant(result);
      setState(() => _lines.add(assistantLine));
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

  Future<void> _regenerate(_ChatLine line) async {
    final messageId = line.messageId;
    if (messageId == null || _sending) return;
    setState(() => _sending = true);

    try {
      final result = await repository.regenerateMessage(
        characterId: widget.character.id,
        branchId: _branchId,
        messageId: messageId,
        mode: _mode,
      );
      if (!mounted) return;
      setState(() => line.addVariant(result));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось создать вариант: $error')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _sending = false);
        _scrollToBottom();
      }
    }
  }

  Future<void> _selectVariant(_ChatLine line, int index) async {
    if (_sending || index < 0 || index >= line.variants.length) return;
    final variant = line.variants[index];
    if (variant.id == line.messageId) return;

    setState(() => _sending = true);
    try {
      await repository.selectMessageVariant(
        characterId: widget.character.id,
        branchId: _branchId,
        messageId: variant.id,
        mode: _mode,
      );
      if (mounted) setState(() => line.activate(index));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось выбрать вариант: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _editUserMessage(_ChatLine line) async {
    final messageId = line.messageId;
    if (messageId == null || _sending) return;

    final controller = TextEditingController(text: line.text);
    final editedText = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Изменить сообщение'),
        content: TextField(
          controller: controller,
          minLines: 2,
          maxLines: 6,
          maxLength: 6000,
          autofocus: true,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: 'Текст сообщения',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          FilledButton(
            onPressed: () {
              final value = controller.text.trim();
              if (value.isEmpty) return;
              Navigator.pop(context, value);
            },
            child: const Text('Сохранить'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (editedText == null || editedText == line.text || !mounted) return;

    setState(() => _sending = true);
    try {
      final result = await repository.editUserMessage(
        characterId: widget.character.id,
        branchId: _branchId,
        messageId: messageId,
        content: editedText,
        mode: _mode,
      );
      if (!mounted) return;

      final lineIndex = _lines.indexOf(line);
      line.text = editedText;
      line.messageId = result.userMessageId;
      if (lineIndex + 1 < _lines.length && !_lines[lineIndex + 1].isUser) {
        final assistantLine = _lines[lineIndex + 1];
        assistantLine.clearVariants();
        assistantLine.text = result.text;
        assistantLine.addVariant(result);
      } else {
        final assistantLine = _ChatLine(text: result.text, isUser: false);
        assistantLine.addVariant(result);
        _lines.insert(lineIndex + 1, assistantLine);
      }
      setState(() {});
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось изменить ход: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _createBranch(_ChatLine line) async {
    final messageId = line.messageId;
    if (messageId == null || _sending) return;

    setState(() => _sending = true);
    try {
      final conversation = await repository.branchFromMessage(
        characterId: widget.character.id,
        branchId: _branchId,
        messageId: messageId,
        mode: _mode,
      );
      if (!mounted) return;
      _applyConversation(conversation);
      _conversationLoaded = true;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Создана отдельная ветка истории.')),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Не удалось создать ветку: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
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

  Widget _buildMessage(
    _ChatLine line, {
    required bool canRegenerate,
  }) {
    final alignment = line.isUser ? Alignment.centerRight : Alignment.centerLeft;
    final color = line.isUser ? const Color(0xFF7056C8) : const Color(0xFF1A2030);

    final actions = <Widget>[];
    if (line.isUser && line.messageId != null) {
      actions.add(
        TextButton.icon(
          onPressed: _sending ? null : () => _editUserMessage(line),
          icon: const Icon(Icons.edit_outlined, size: 14),
          label: const Text('Изменить', style: TextStyle(fontSize: 11)),
          style: TextButton.styleFrom(
            foregroundColor: Colors.white54,
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
            minimumSize: Size.zero,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
        ),
      );
    }
    if (!line.isUser && line.messageId != null) {
      if (canRegenerate && line.variants.length > 1) {
        actions.addAll([
          IconButton(
            tooltip: 'Предыдущий вариант',
            visualDensity: VisualDensity.compact,
            onPressed: line.selectedVariant > 0
                ? () => _selectVariant(line, line.selectedVariant - 1)
                : null,
            icon: const Icon(Icons.chevron_left, size: 19),
          ),
          Text(
            '${line.selectedVariant + 1}/${line.variants.length}',
            style: const TextStyle(fontSize: 11, color: Colors.white54),
          ),
          IconButton(
            tooltip: 'Следующий вариант',
            visualDensity: VisualDensity.compact,
            onPressed: line.selectedVariant < line.variants.length - 1
                ? () => _selectVariant(line, line.selectedVariant + 1)
                : null,
            icon: const Icon(Icons.chevron_right, size: 19),
          ),
        ]);
      }
      if (canRegenerate) {
        actions.add(
          IconButton(
            tooltip: 'Создать другой ответ',
            visualDensity: VisualDensity.compact,
            onPressed: _sending ? null : () => _regenerate(line),
            icon: const Icon(Icons.refresh_rounded, size: 17),
          ),
        );
      }
      actions.add(
        IconButton(
          tooltip: 'Создать ветку истории',
          visualDensity: VisualDensity.compact,
          onPressed: _sending ? null : () => _createBranch(line),
          icon: const Icon(Icons.account_tree_outlined, size: 17),
        ),
      );
      actions.add(
        TextButton.icon(
          onPressed: () => _report(line),
          icon: const Icon(Icons.flag_outlined, size: 14),
          label: const Text('Пожаловаться', style: TextStyle(fontSize: 11)),
          style: TextButton.styleFrom(
            foregroundColor: Colors.white54,
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
            minimumSize: Size.zero,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
        ),
      );
    }

    final bubble = Align(
      alignment: alignment,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 330),
        child: Padding(
          padding: const EdgeInsets.only(bottom: 8),
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
              if (actions.isNotEmpty)
                Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 2,
                  children: actions,
                ),
            ],
          ),
        ),
      ),
    );

    if (line.isUser || !canRegenerate) return bubble;
    return GestureDetector(
      onHorizontalDragEnd: (details) {
        final velocity = details.primaryVelocity ?? 0;
        if (velocity < -220) {
          _regenerate(line);
        } else if (velocity > 220 && line.selectedVariant > 0) {
          _selectVariant(line, line.selectedVariant - 1);
        }
      },
      child: bubble,
    );
  }

  @override
  Widget build(BuildContext context) {
    final latestAssistantIndex = _lines.lastIndexWhere(
      (line) => !line.isUser && line.messageId != null,
    );

    return Scaffold(
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
                  Text(
                    widget.character.name,
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
                  ),
                  const Text(
                    'NEBULA STORY',
                    style: TextStyle(fontSize: 9, letterSpacing: 1.4, color: Colors.white54),
                  ),
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
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
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
                      return _buildMessage(
                        _lines[index],
                        canRegenerate: index == latestAssistantIndex,
                      );
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
                    onPressed: _sending || _loading ? null : _send,
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
}
