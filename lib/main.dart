import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'src/app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const url = String.fromEnvironment('SUPABASE_URL');
  const publicKey = String.fromEnvironment('SUPABASE_ANON_KEY');

  if (url.isNotEmpty && publicKey.isNotEmpty) {
    await Supabase.initialize(
      url: url,
      publishableKey: publicKey,
    );
  }

  runApp(const ProviderScope(child: NebulaApp()));
}
