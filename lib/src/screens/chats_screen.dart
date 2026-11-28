import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../app.dart';
import '../models/chat_list_item.dart';
import '../models/character.dart';
import 'auth_screen.dart';
import 'chat_screen.dart';

class ChatsScreen extends StatefulWidget {
  const ChatsScreen({super.key});

  @override
  State<ChatsScreen> createState() => ChatsScreenState();
}

class ChatsScreenState extends State<ChatsScreen> {
  Future<List<ChatListItem>>? _future;

  @override
  void initState() {
    super.initState();
    reload();
  }

  void reload() {
    if (!mounted && _future != null) return;
    if (!repository.isSignedIn) {
      setState(() => _future = null);
      return;
    }
    setState(() => _future = repository.loadRecentChats());
  }

  Future<void> _openSignIn() async {
    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const AuthScreen()),
    );
    if (result == true && mounted) reload();
  }

  Future<void> _openChat(ChatListItem item) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => ChatScreen(
          character: item.character,
          initialBranchId: item.branchId,
        ),
      ),
    );
    if (mounted) reload();
  }

  String _formatDate(DateTime date) {
    final local = date.toLocal();
    final now = DateTime.now();
    final sameDay = local.year == now.year && local.month == now.month && local.day == now.day;
    final time = '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    if (sameDay) return time;
    return '${local.day.toString().padLeft(2, '0')}.${local.month.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Мои истории'),
          actions: [
            IconButton(
              tooltip: 'Обновить',
              onPressed: reload,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: !repository.isConfigured
            ? const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                    'Истории синхронизируются через Supabase. Подключите проект, чтобы сохранять переписки.',
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
                          const Icon(Icons.forum_outlined, size: 58, color: Colors.white54),
                          const SizedBox(height: 14),
                          const Text(
                            'Войдите, чтобы продолжать диалоги с того места, где остановились.',
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 18),
                          FilledButton(
                            onPressed: _openSignIn,
                            child: const Text('Войти'),
                          ),
                        ],
                      ),
                    ),
                  )
                : FutureBuilder<List<ChatListItem>>(
                    future: _future,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (snapshot.hasError) {
                        return Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Text('Не удалось загрузить истории: ${snapshot.error}'),
                          ),
                        );
                      }

                      final chats = snapshot.data ?? const <ChatListItem>[];
                      if (chats.isEmpty) {
                        return const Center(
                          child: Padding(
                            padding: EdgeInsets.all(24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.auto_stories_outlined, size: 58, color: Colors.white54),
                                SizedBox(height: 14),
                                Text(
                                  'Здесь появятся твои диалоги. Выбери персонажа в каталоге и начни историю.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(color: Colors.white70, height: 1.4),
                                ),
                              ],
                            ),
                          ),
                        );
                      }

                      return RefreshIndicator(
                        onRefresh: () async {
                          reload();
                          await _future;
                        },
                        child: ListView.separated(
                          padding: const EdgeInsets.all(14),
                          itemCount: chats.length,
                          separatorBuilder: (_, __) => const SizedBox(height: 8),
                          itemBuilder: (context, index) {
                            final item = chats[index];
                            return Material(
                              color: const Color(0xFF171B29),
                              borderRadius: BorderRadius.circular(18),
                              clipBehavior: Clip.antiAlias,
                              child: InkWell(
                                onTap: () => _openChat(item),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
                                  child: Row(
                                    children: [
                                      CircleAvatar(
                                        radius: 25,
                                        backgroundImage: item.character.avatarUrl.isNotEmpty
                                            ? CachedNetworkImageProvider(item.character.avatarUrl)
                                            : null,
                                        child: item.character.avatarUrl.isEmpty
                                            ? const Icon(Icons.person_outline)
                                            : null,
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              item.character.name,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(fontWeight: FontWeight.w700),
                                            ),
                                            const SizedBox(height: 4),
                                            Text(
                                              item.lastMessage,
                                              maxLines: 2,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(color: Colors.white60, fontSize: 12),
                                            ),
                                          ],
                                        ),
                                      ),
                                      const SizedBox(width: 8),
                                      Column(
                                        crossAxisAlignment: CrossAxisAlignment.end,
                                        children: [
                                          Text(
                                            _formatDate(item.createdAt),
                                            style: const TextStyle(color: Colors.white38, fontSize: 10),
                                          ),
                                          const SizedBox(height: 8),
                                          const Icon(Icons.chevron_right, color: Colors.white38),
                                        ],
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      );
                    },
                  ),
      );
}
