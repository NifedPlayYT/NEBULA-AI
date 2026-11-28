import 'package:flutter/material.dart';

import '../app.dart';
import '../models/character.dart';
import '../widgets/character_card.dart';
import 'auth_screen.dart';
import 'character_editor_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late Future<List<Character>> future;
  String query = '';

  @override
  void initState() {
    super.initState();
    future = repository.loadCharacters();
  }

  Future<void> createCharacter() async {
    if (!repository.isConfigured) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Для создания персонажей подключите Supabase.')),
      );
      return;
    }

    if (!repository.isSignedIn) {
      final signedIn = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => const AuthScreen()),
      );
      if (signedIn != true || !mounted) {
        return;
      }
    }

    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const CharacterEditorScreen()),
    );
    if (saved == true && mounted) {
      setState(() => future = repository.loadCharacters());
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: RefreshIndicator(
            onRefresh: () async {
              setState(() => future = repository.loadCharacters());
              await future;
            },
            child: CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 22, 20, 18),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Expanded(
                              child: Text(
                                'NEBULA',
                                style: TextStyle(
                                  fontSize: 28,
                                  fontWeight: FontWeight.w900,
                                  letterSpacing: 3,
                                ),
                              ),
                            ),
                            IconButton(
                              tooltip: 'Создать персонажа',
                              onPressed: createCharacter,
                              icon: const Icon(Icons.add_circle_outline, size: 26),
                            ),
                          ],
                        ),
                        const Text(
                          'ТВОИ ИСТОРИИ НАЧИНАЮТСЯ ЗДЕСЬ',
                          style: TextStyle(fontSize: 10, letterSpacing: 1.6, color: Colors.white54),
                        ),
                        const SizedBox(height: 20),
                        TextField(
                          onChanged: (value) => setState(() => query = value.trim().toLowerCase()),
                          decoration: InputDecoration(
                            hintText: 'Найти персонажа или вселенную',
                            prefixIcon: const Icon(Icons.search),
                            filled: true,
                            fillColor: const Color(0xFF171B29),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(16),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                        const SizedBox(height: 18),
                        const Text(
                          'Открой новую историю',
                          style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800),
                        ),
                      ],
                    ),
                  ),
                ),
                FutureBuilder<List<Character>>(
                  future: future,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const SliverFillRemaining(
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    if (snapshot.hasError) {
                      return SliverFillRemaining(
                        child: Center(child: Text('Не удалось загрузить каталог: ${snapshot.error}')),
                      );
                    }

                    final items = (snapshot.data ?? demoCharacters)
                        .where((character) =>
                            character.name.toLowerCase().contains(query) ||
                            character.universeTag.toLowerCase().contains(query))
                        .toList();

                    if (items.isEmpty) {
                      return const SliverFillRemaining(
                        child: Center(child: Text('Персонажи не найдены')),
                      );
                    }

                    return SliverPadding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                      sliver: SliverGrid(
                        delegate: SliverChildBuilderDelegate(
                          (context, index) => CharacterCard(
                            character: items[index],
                            onTap: () => openCharacter(context, items[index]),
                          ),
                          childCount: items.length,
                        ),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                          childAspectRatio: .69,
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        ),
      );
}
