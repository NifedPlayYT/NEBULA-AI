import 'package:flutter/material.dart';

import 'models/character.dart';
import 'screens/chat_screen.dart';
import 'screens/chats_screen.dart';
import 'screens/home_screen.dart';
import 'screens/profile_screen.dart';
import 'services/repository.dart';

final repository = NebulaRepository();

class NebulaApp extends StatelessWidget {
  const NebulaApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'NEBULA AI',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          brightness: Brightness.dark,
          useMaterial3: true,
          scaffoldBackgroundColor: const Color(0xFF090B12),
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF9B7BFF),
            brightness: Brightness.dark,
          ),
        ),
        home: const MainNavigationScreen(),
      );
}

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  final _chatsKey = GlobalKey<ChatsScreenState>();
  int _selectedIndex = 1;

  late final List<Widget> _pages = [
    ChatsScreen(key: _chatsKey),
    const HomeScreen(),
    const ProfileScreen(),
  ];

  void _selectTab(int index) {
    setState(() => _selectedIndex = index);
    if (index == 0) {
      _chatsKey.currentState?.reload();
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: IndexedStack(
          index: _selectedIndex,
          children: _pages,
        ),
        bottomNavigationBar: NavigationBar(
          height: 72,
          selectedIndex: _selectedIndex,
          onDestinationSelected: _selectTab,
          backgroundColor: const Color(0xFF111522),
          indicatorColor: const Color(0xFF4A3A77),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.forum_outlined),
              selectedIcon: Icon(Icons.forum_rounded),
              label: 'Чаты',
            ),
            NavigationDestination(
              icon: Icon(Icons.explore_outlined),
              selectedIcon: Icon(Icons.explore_rounded),
              label: 'Каталог',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_outline_rounded),
              selectedIcon: Icon(Icons.person_rounded),
              label: 'Профиль',
            ),
          ],
        ),
      );
}

void openCharacter(BuildContext context, Character character) {
  Navigator.of(context).push(MaterialPageRoute<void>(
    builder: (_) => ChatScreen(character: character),
  ));
}
