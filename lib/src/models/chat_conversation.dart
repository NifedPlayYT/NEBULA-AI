class ChatMessageVariant {
  const ChatMessageVariant({
    required this.id,
    required this.text,
    required this.index,
    required this.isSelected,
  });

  final String id;
  final String text;
  final int index;
  final bool isSelected;
}

class ChatTurn {
  const ChatTurn({
    required this.turnId,
    required this.userMessageId,
    required this.userText,
    required this.variants,
  });

  final String turnId;
  final String userMessageId;
  final String userText;
  final List<ChatMessageVariant> variants;

  ChatMessageVariant? get selectedVariant {
    for (final variant in variants) {
      if (variant.isSelected) return variant;
    }
    return variants.isEmpty ? null : variants.first;
  }
}

class ChatConversation {
  const ChatConversation({
    required this.branchId,
    required this.turns,
  });

  final String branchId;
  final List<ChatTurn> turns;
}
