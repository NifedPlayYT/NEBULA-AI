import 'character.dart';

class ChatListItem {
  const ChatListItem({
    required this.branchId,
    required this.title,
    required this.createdAt,
    required this.character,
    required this.lastMessage,
  });

  final String branchId;
  final String title;
  final DateTime createdAt;
  final Character character;
  final String lastMessage;
}
