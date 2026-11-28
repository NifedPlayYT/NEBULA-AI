import 'package:flutter_test/flutter_test.dart';
import 'package:nebula_ai/src/models/chat_conversation.dart';

void main() {
  test('selected variant is preferred over other saved alternatives', () {
    const turn = ChatTurn(
      turnId: 'turn-1',
      userMessageId: 'user-1',
      userText: 'Привет',
      variants: [
        ChatMessageVariant(
          id: 'assistant-1',
          text: 'Первый ответ',
          index: 0,
          isSelected: false,
        ),
        ChatMessageVariant(
          id: 'assistant-2',
          text: 'Выбранный ответ',
          index: 1,
          isSelected: true,
        ),
      ],
    );

    expect(turn.selectedVariant?.id, 'assistant-2');
    expect(turn.selectedVariant?.text, 'Выбранный ответ');
  });

  test('first alternative is used when no selected flag exists', () {
    const turn = ChatTurn(
      turnId: 'turn-2',
      userMessageId: 'user-2',
      userText: 'Продолжим',
      variants: [
        ChatMessageVariant(
          id: 'assistant-3',
          text: 'Ответ',
          index: 0,
          isSelected: false,
        ),
      ],
    );

    expect(turn.selectedVariant?.id, 'assistant-3');
  });

  test('turn without assistant alternatives exposes no selected reply', () {
    const turn = ChatTurn(
      turnId: 'turn-3',
      userMessageId: 'user-3',
      userText: 'Новый ход',
      variants: [],
    );

    expect(turn.selectedVariant, isNull);
  });
}
