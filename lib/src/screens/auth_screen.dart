import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../app.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _isSignUp = false;
  bool _busy = false;
  bool _obscurePassword = true;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _busy) return;

    final client = repository.client;
    if (client == null) {
      setState(() => _error = 'Supabase не настроен. Добавьте URL проекта и публичный anon key при сборке.');
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      if (_isSignUp) {
        final response = await client.auth.signUp(email: _email.text.trim(), password: _password.text);
        if (!mounted) return;
        if (response.session == null) {
          setState(() {
            _busy = false;
            _error = 'Аккаунт создан. Проверьте почту и подтвердите адрес, затем войдите.';
          });
          return;
        }
      } else {
        await client.auth.signInWithPassword(email: _email.text.trim(), password: _password.text);
      }

      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on AuthException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'Не удалось связаться с сервером. Проверьте подключение.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: Stack(
            children: [
              Positioned(
                top: -100,
                right: -100,
                child: Container(
                  width: 260,
                  height: 260,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    boxShadow: [BoxShadow(color: nebulaAccent.withValues(alpha: .15), blurRadius: 100, spreadRadius: 30)],
                  ),
                ),
              ),
              SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(23, 16, 23, 28),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 440),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Align(
                        alignment: Alignment.centerLeft,
                        child: IconButton(
                          onPressed: () => Navigator.of(context).maybePop(),
                          icon: const Icon(Icons.arrow_back_rounded),
                          style: IconButton.styleFrom(backgroundColor: nebulaSurface),
                        ),
                      ),
                      const SizedBox(height: 36),
                      Center(
                        child: Container(
                          width: 66,
                          height: 66,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(22),
                            gradient: const LinearGradient(colors: [Color(0xFFBCA4FF), Color(0xFF7056C8)]),
                            boxShadow: [BoxShadow(color: nebulaAccent.withValues(alpha: .22), blurRadius: 26, offset: const Offset(0, 9))],
                          ),
                          child: const Icon(Icons.auto_awesome_rounded, size: 31, color: Colors.white),
                        ),
                      ),
                      const SizedBox(height: 22),
                      Text(_isSignUp ? 'Создай свой мир' : 'С возвращением', textAlign: TextAlign.center, style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w900, letterSpacing: -.8)),
                      const SizedBox(height: 9),
                      const Text('Войди в NEBULA, чтобы сохранять персонажей и продолжать истории на любом устройстве.', textAlign: TextAlign.center, style: TextStyle(color: Color(0xFF9299AB), fontSize: 13, height: 1.5)),
                      const SizedBox(height: 30),
                      Container(
                        padding: const EdgeInsets.all(19),
                        decoration: BoxDecoration(
                          color: nebulaSurface,
                          borderRadius: BorderRadius.circular(24),
                          border: Border.all(color: const Color(0xFF252B3D)),
                        ),
                        child: Form(
                          key: _formKey,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Text('ЭЛЕКТРОННАЯ ПОЧТА', style: TextStyle(fontSize: 9, letterSpacing: 1.4, fontWeight: FontWeight.w800, color: Colors.white.withValues(alpha: .55))),
                              const SizedBox(height: 9),
                              TextFormField(
                                controller: _email,
                                keyboardType: TextInputType.emailAddress,
                                autofillHints: const [AutofillHints.email],
                                decoration: const InputDecoration(hintText: 'name@example.com', prefixIcon: Icon(Icons.mail_outline_rounded)),
                                validator: (value) {
                                  final email = value?.trim() ?? '';
                                  if (!email.contains('@') || email.length < 5) return 'Введите корректный адрес почты';
                                  return null;
                                },
                              ),
                              const SizedBox(height: 18),
                              Text('ПАРОЛЬ', style: TextStyle(fontSize: 9, letterSpacing: 1.4, fontWeight: FontWeight.w800, color: Colors.white.withValues(alpha: .55))),
                              const SizedBox(height: 9),
                              TextFormField(
                                controller: _password,
                                obscureText: _obscurePassword,
                                autofillHints: _isSignUp ? const [AutofillHints.newPassword] : const [AutofillHints.password],
                                decoration: InputDecoration(
                                  hintText: 'Не менее 8 символов',
                                  prefixIcon: const Icon(Icons.lock_outline_rounded),
                                  suffixIcon: IconButton(
                                    onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                                    icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                                  ),
                                ),
                                validator: (value) => (value ?? '').length < 8 ? 'Минимум 8 символов' : null,
                              ),
                              if (_error != null) ...[
                                const SizedBox(height: 14),
                                Container(
                                  padding: const EdgeInsets.all(12),
                                  decoration: BoxDecoration(color: const Color(0xFF3B202C), borderRadius: BorderRadius.circular(12)),
                                  child: Text(_error!, style: const TextStyle(color: Color(0xFFFFB6C2), fontSize: 12, height: 1.4)),
                                ),
                              ],
                              const SizedBox(height: 22),
                              SizedBox(
                                height: 52,
                                child: FilledButton(
                                  onPressed: _busy ? null : _submit,
                                  child: _busy
                                      ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                                      : Text(_isSignUp ? 'Создать аккаунт' : 'Войти в NEBULA'),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 20),
                      TextButton(
                        onPressed: _busy ? null : () => setState(() {
                          _isSignUp = !_isSignUp;
                          _error = null;
                        }),
                        child: Text(_isSignUp ? 'Уже есть аккаунт? Войти' : 'Впервые здесь? Создать аккаунт', style: const TextStyle(color: Color(0xFFC9B7FF), fontWeight: FontWeight.w700)),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      );
}
