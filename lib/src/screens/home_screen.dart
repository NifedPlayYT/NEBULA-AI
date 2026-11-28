import 'package:flutter/material.dart';

import '../app.dart';
import '../models/character.dart';
import '../widgets/character_card.dart';
import 'auth_screen.dart';
import 'character_editor_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => HomeScreenState();
}

class HomeScreenState extends State<HomeScreen> {
  late Future<List<Character>> future;
  String query = '';
  String selectedFilter = 'Все';

  static const filters = ['Все', 'Реализм', 'Фэнтези', 'Sci-fi'];

  @override
  void initState() {
    super.initState();
    future = repository.loadCharacters();
  }

  void reload() {
    if (!mounted) return;
    setState(() => future = repository.loadCharacters());
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
      if (signedIn != true || !mounted) return;
    }

    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const CharacterEditorScreen()),
    );
    if (saved == true && mounted) {
      setState(() => future = repository.loadCharacters());
    }
  }

  bool _matchesFilter(Character character) {
    switch (selectedFilter) {
      case 'Реализм':
        return character.universeTag.toLowerCase().contains('realism');
      case 'Фэнтези':
        return character.universeTag.toLowerCase().contains('fantasy');
      case 'Sci-fi':
        return character.universeTag.toLowerCase().contains('sci-fi');
      default:
        return true;
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
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 17, 20, 18),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 34,
                              height: 34,
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(11),
                                gradient: const LinearGradient(colors: [Color(0xFFB69AFF), Color(0xFF6D56C5)]),
                              ),
                              child: const Icon(Icons.auto_awesome_rounded, color: Colors.white, size: 19),
                            ),
                            const SizedBox(width: 10),
                            const Expanded(
                              child: Text('NEBULA', style: TextStyle(fontSize: 23, fontWeight: FontWeight.w900, letterSpacing: 2.7)),
                            ),
                            IconButton.filledTonal(
                              tooltip: 'Создать персонажа',
                              onPressed: createCharacter,
                              style: IconButton.styleFrom(
                                backgroundColor: nebulaAccent.withValues(alpha: .14),
                                foregroundColor: nebulaAccent,
                              ),
                              icon: const Icon(Icons.add_rounded, size: 23),
                            ),
                          ],
                        ),
                        const SizedBox(height: 23),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.fromLTRB(19, 20, 19, 19),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(25),
                            gradient: const LinearGradient(
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                              colors: [Color(0xFF27213F), Color(0xFF171B2D), Color(0xFF111725)],
                            ),
                            border: Border.all(color: const Color(0xFF8C76D4).withValues(alpha: .22)),
                          ),
                          child: Stack(
                            children: [
                              Positioned(
                                right: -5,
                                top: -13,
                                child: Icon(Icons.auto_awesome, size: 94, color: Colors.white.withValues(alpha: .055)),
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: .08), borderRadius: BorderRadius.circular(20)),
                                    child: const Text('ТВОЯ ВСЕЛЕННАЯ', style: TextStyle(fontSize: 9, letterSpacing: 1.7, fontWeight: FontWeight.w800, color: Color(0xFFD7C9FF))),
                                  ),
                                  const SizedBox(height: 13),
                                  const Text('Истории, которые\nоживают вместе с тобой', style: TextStyle(fontSize: 22, height: 1.13, fontWeight: FontWeight.w800, letterSpacing: -.5)),
                                  const SizedBox(height: 8),
                                  const Text('Выбирай персонажа. Задавай направление. Создавай свою историю.', style: TextStyle(fontSize: 11, height: 1.45, color: Color(0xFFB6B4C7))),
                                ],
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 22),
                        TextField(
                          onChanged: (value) => setState(() => query = value.trim().toLowerCase()),
                          decoration: const InputDecoration(
                            hintText: 'Найти персонажа или вселенную',
                            prefixIcon: Icon(Icons.search_rounded),
                            suffixIcon: Icon(Icons.tune_rounded, size: 19),
                          ),
                        ),
                        const SizedBox(height: 21),
                        Row(
                          children: [
                            const Expanded(child: Text('Открой новую историю', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800, letterSpacing: -.3))),
                            Text('КАТАЛОГ', style: TextStyle(fontSize: 9, letterSpacing: 1.5, color: Colors.white.withValues(alpha: .42), fontWeight: FontWeight.w800)),
                          ],
                        ),
                        const SizedBox(height: 13),
                        SizedBox(
                          height: 36,
                          child: ListView.separated(
                            scrollDirection: Axis.horizontal,
                            itemCount: filters.length,
                            separatorBuilder: (_, __) => const SizedBox(width: 8),
                            itemBuilder: (context, index) {
                              final filter = filters[index];
                              final selected = selectedFilter == filter;
                              return ChoiceChip(
                                label: Text(filter),
                                selected: selected,
                                onSelected: (_) => setState(() => selectedFilter = filter),
                                showCheckmark: false,
                                backgroundColor: nebulaSurface,
                                selectedColor: nebulaAccent.withValues(alpha: .22),
                                side: BorderSide(color: selected ? nebulaAccent.withValues(alpha: .55) : const Color(0xFF252B3C)),
                                labelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: selected ? const Color(0xFFE7DEFF) : const Color(0xFF9EA4B5)),
                                padding: const EdgeInsets.symmetric(horizontal: 7),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                FutureBuilder<List<Character>>(
                  future: future,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const SliverFillRemaining(child: Center(child: CircularProgressIndicator()));
                    }
                    if (snapshot.hasError) {
                      return SliverFillRemaining(
                        child: Center(child: Padding(padding: const EdgeInsets.all(24), child: Text('Не удалось загрузить каталог: ${snapshot.error}'))),
                      );
                    }

                    final items = (snapshot.data ?? demoCharacters).where((character) {
                      final matchesQuery = character.name.toLowerCase().contains(query) || character.universeTag.toLowerCase().contains(query);
                      return matchesQuery && _matchesFilter(character);
                    }).toList();

                    if (items.isEmpty) {
                      return const SliverFillRemaining(child: Center(child: Text('Персонажи не найдены')));
                    }

                    return SliverPadding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 26),
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
                          mainAxisSpacing: 13,
                          childAspectRatio: .70,
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
