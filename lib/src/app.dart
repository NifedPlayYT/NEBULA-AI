import 'package:flutter/material.dart';

import 'models/character.dart';
import 'screens/chat_screen.dart';
import 'screens/chats_screen.dart';
import 'screens/home_screen.dart';
import 'screens/profile_screen.dart';
import 'services/repository.dart';

final repository = NebulaRepository();

const nebulaBackground = Color(0xFF080A12);
const nebulaSurface = Color(0xFF121624);
const nebulaSurfaceRaised = Color(0xFF1A2031);
const nebulaAccent = Color(0xFFAA8BFF);
const nebulaAccentSoft = Color(0xFF7660C8);

class NebulaApp extends StatelessWidget {
  const NebulaApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'NEBULA AI',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          brightness: Brightness.dark,
          useMaterial3: true,
          scaffoldBackgroundColor: nebulaBackground,
          colorScheme: ColorScheme.fromSeed(
            seedColor: nebulaAccent,
            brightness: Brightness.dark,
            surface: nebulaSurface,
          ),
          appBarTheme: const AppBarTheme(
            backgroundColor: nebulaBackground,
            surfaceTintColor: Colors.transparent,
            elevation: 0,
            centerTitle: false,
            titleTextStyle: TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.w800,
              letterSpacing: -.4,
            ),
          ),
          inputDecorationTheme: InputDecorationTheme(
            filled: true,
            fillColor: nebulaSurface,
            hintStyle: const TextStyle(color: Color(0xFF777E91), fontSize: 14),
            labelStyle: const TextStyle(color: Color(0xFFA6AABD)),
            contentPadding: const EdgeInsets.symmetric(horizontal: 17, vertical: 16),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(17),
              borderSide: BorderSide.none,
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(17),
              borderSide: const BorderSide(color: Color(0xFF242A3B)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(17),
              borderSide: const BorderSide(color: nebulaAccent, width: 1.3),
            ),
          ),
          filledButtonTheme: FilledButtonThemeData(
            style: FilledButton.styleFrom(
              backgroundColor: nebulaAccentSoft,
              foregroundColor: Colors.white,
              disabledBackgroundColor: nebulaAccentSoft.withValues(alpha: .35),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
              textStyle: const TextStyle(fontWeight: FontWeight.w700, letterSpacing: .1),
            ),
          ),
          navigationBarTheme: NavigationBarThemeData(
            height: 72,
            backgroundColor: const Color(0xFF0D111D),
            indicatorColor: nebulaAccent.withValues(alpha: .18),
            labelTextStyle: WidgetStateProperty.resolveWith((states) => TextStyle(
                  fontSize: 11,
                  fontWeight: states.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
                  color: states.contains(WidgetState.selected) ? Colors.white : const Color(0xFF7F879A),
                )),
            iconTheme: WidgetStateProperty.resolveWith((states) => IconThemeData(
                  size: 22,
                  color: states.contains(WidgetState.selected) ? nebulaAccent : const Color(0xFF7F879A),
                )),
          ),
          dividerColor: const Color(0xFF242A3B),
          snackBarTheme: SnackBarThemeData(
            backgroundColor: nebulaSurfaceRaised,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
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
  final _catalogKey = GlobalKey<HomeScreenState>();
  final _profileKey = GlobalKey<ProfileScreenState>();
  int _selectedIndex = 1;

  late final List<Widget> _pages = [
    ChatsScreen(key: _chatsKey),
    HomeScreen(key: _catalogKey),
    ProfileScreen(key: _profileKey),
  ];

  void _selectTab(int index) {
    setState(() => _selectedIndex = index);
    if (index == 0) {
      _chatsKey.currentState?.reload();
    } else if (index == 1) {
      _catalogKey.currentState?.reload();
    } else if (index == 2) {
      _profileKey.currentState?.reload();
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: IndexedStack(index: _selectedIndex, children: _pages),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _selectedIndex,
          onDestinationSelected: _selectTab,
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
