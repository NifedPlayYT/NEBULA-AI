import 'package:flutter/material.dart';
import 'models/character.dart';
import 'screens/chat_screen.dart';
import 'screens/home_screen.dart';
import 'services/repository.dart';

final repository = NebulaRepository();

class NebulaApp extends StatelessWidget {
  const NebulaApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'NEBULA AI', debugShowCheckedModeBanner: false,
    theme: ThemeData(brightness: Brightness.dark, useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xFF090B12),
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF9B7BFF), brightness: Brightness.dark)),
    home: const HomeScreen(),
  );
}
void openCharacter(BuildContext context, Character character) {
  Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ChatScreen(character: character)));
}
