import 'package:flutter/material.dart';

import '../app.dart';

class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  Future<void> _deleteAccount(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Удалить все данные?'),
        content: const Text(
          'Будут удалены аккаунт, персоны, переписки и сохранённые воспоминания. Восстановить их будет нельзя.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Отмена')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFFB63F55)),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Удалить навсегда'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await repository.deleteAccount();
      if (context.mounted) {
        Navigator.of(context).popUntil((route) => route.isFirst);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Аккаунт и данные удалены.')),
        );
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Удаление не выполнено: $error')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Конфиденциальность')),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const Text('Какие данные используются', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
            const SizedBox(height: 10),
            const Text(
              'NEBULA хранит адрес электронной почты в Supabase Auth, созданные вами персоны, настройки персонажей, сообщения, краткие сводки и сохранённые воспоминания. Эти данные нужны для синхронизации диалогов и продолжения историй.',
              style: TextStyle(height: 1.5, color: Colors.white70),
            ),
            const SizedBox(height: 18),
            const Text('Обработка сообщений', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text(
              'Сообщения, отправленные в чат, передаются серверной функции NEBULA и выбранному поставщику языковой модели для формирования ответа. Не отправляйте пароли, платёжные данные и другую конфиденциальную информацию.',
              style: TextStyle(height: 1.5, color: Colors.white70),
            ),
            const SizedBox(height: 18),
            const Text('Удаление данных', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text(
              'Удаление аккаунта удаляет связанные записи в базе данных. Перед публичным запуском владелец сервиса должен также настроить удаление файлов из Supabase Storage, сроки хранения резервных копий и опубликованную политику конфиденциальности.',
              style: TextStyle(height: 1.5, color: Colors.white70),
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFFFF8A9B)),
              onPressed: () => _deleteAccount(context),
              icon: const Icon(Icons.delete_forever_outlined),
              label: const Text('Удалить аккаунт и данные'),
            ),
          ],
        ),
      );
}
