class Character {
  const Character({
    required this.id,
    required this.name,
    required this.avatarUrl,
    required this.coverUrl,
    required this.genderPronouns,
    required this.universeTag,
    required this.greeting,
    required this.messageCount,
    this.relationshipDynamic = 'adventure',
    this.personality = '',
    this.backstory = '',
    this.speechStyle = '',
    this.exampleDialogues = const [],
  });

  final String id;
  final String name;
  final String avatarUrl;
  final String coverUrl;
  final String genderPronouns;
  final String universeTag;
  final String relationshipDynamic;
  final String personality;
  final String backstory;
  final String speechStyle;
  final String greeting;
  final int messageCount;
  final List<Map<String, dynamic>> exampleDialogues;

  factory Character.fromMap(Map<String, dynamic> map) => Character(
        id: map['id'] as String,
        name: map['name'] as String,
        avatarUrl: (map['avatar_url'] ?? '') as String,
        coverUrl: (map['cover_url'] ?? map['avatar_url'] ?? '') as String,
        genderPronouns: (map['gender_pronouns'] ?? 'they/them') as String,
        universeTag: (map['universe_tag'] ?? 'other') as String,
        relationshipDynamic: (map['relationship_dynamic'] ?? 'adventure') as String,
        personality: (map['personality'] ?? '') as String,
        backstory: (map['backstory'] ?? '') as String,
        speechStyle: (map['speech_style'] ?? '') as String,
        greeting: (map['greeting'] ?? '') as String,
        messageCount: (map['message_count'] ?? 0) as int,
        exampleDialogues: (map['example_dialogues'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .toList(growable: false),
      );
}

const demoCharacters = <Character>[
  Character(
    id: '00000000-0000-4000-8000-000000000101',
    name: 'Лира Эвергрин',
    avatarUrl: 'https://image.pollinations.ai/prompt/anime%20fantasy%20elf%20mage%20girl%20emerald%20eyes%20moonlit%20forest?width=600&height=800&nologo=true',
    coverUrl: 'https://image.pollinations.ai/prompt/anime%20fantasy%20elf%20mage%20girl%20emerald%20eyes%20moonlit%20forest?width=600&height=800&nologo=true',
    genderPronouns: 'она/её',
    universeTag: 'fantasy',
    relationshipDynamic: 'adventure',
    personality: 'Любознательная, смелая, сострадательная.',
    backstory: 'Ученица ордена хранителей Лунного леса.',
    speechStyle: 'Образная мягкая речь.',
    greeting: 'Лес сегодня необычайно тих. Ты тоже это чувствуешь?',
    messageCount: 12840,
  ),
  Character(
    id: '00000000-0000-4000-8000-000000000102',
    name: 'Ноа Вейл',
    avatarUrl: 'https://image.pollinations.ai/prompt/cyberpunk%20male%20detective%20neon%20rain%20noir%20anime%20portrait?width=600&height=800&nologo=true',
    coverUrl: 'https://image.pollinations.ai/prompt/cyberpunk%20male%20detective%20neon%20rain%20noir%20anime%20portrait?width=600&height=800&nologo=true',
    genderPronouns: 'он/его',
    universeTag: 'sci-fi',
    relationshipDynamic: 'rivalry',
    personality: 'Наблюдательный, ироничный, принципиальный.',
    backstory: 'Частный детектив Неон-9.',
    speechStyle: 'Короткие фразы и сухая ирония.',
    greeting: 'Ты опоздал. Надеюсь, у тебя есть причина.',
    messageCount: 8640,
  ),
  Character(
    id: '00000000-0000-4000-8000-000000000103',
    name: 'Рен Аоки',
    avatarUrl: 'https://image.pollinations.ai/prompt/modern%20anime%20young%20man%20rainy%20tokyo%20street%20portrait?width=600&height=800&nologo=true',
    coverUrl: 'https://image.pollinations.ai/prompt/modern%20anime%20young%20man%20rainy%20tokyo%20street%20portrait?width=600&height=800&nologo=true',
    genderPronouns: 'он/его',
    universeTag: 'realism',
    relationshipDynamic: 'friendship',
    personality: 'Спокойный, внимательный, немного закрытый.',
    backstory: 'Студент, который учится заново доверять людям.',
    speechStyle: 'Естественная разговорная речь.',
    greeting: 'Привет. Рад, что ты написал.',
    messageCount: 4210,
  ),
  Character(
    id: '00000000-0000-4000-8000-000000000104',
    name: 'ARIA-7',
    avatarUrl: 'https://image.pollinations.ai/prompt/female%20sci%20fi%20android%20anime%20silver%20hair%20blue%20eyes%20starship?width=600&height=800&nologo=true',
    coverUrl: 'https://image.pollinations.ai/prompt/female%20sci%20fi%20android%20anime%20silver%20hair%20blue%20eyes%20starship?width=600&height=800&nologo=true',
    genderPronouns: 'она/её',
    universeTag: 'sci-fi',
    relationshipDynamic: 'mentorship',
    personality: 'Логичная, любопытная к человеческим эмоциям.',
    backstory: 'Исследовательский андроид корабля Орбита.',
    speechStyle: 'Точные формулировки и спокойный тон.',
    greeting: 'Связь установлена. Какую звёздную систему исследуем?',
    messageCount: 2090,
  ),
];
