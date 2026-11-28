class Character {
  const Character({required this.id, required this.name, required this.avatarUrl, required this.coverUrl,
    required this.genderPronouns, required this.universeTag, required this.greeting, required this.messageCount});
  final String id, name, avatarUrl, coverUrl, genderPronouns, universeTag, greeting;
  final int messageCount;
  factory Character.fromMap(Map<String,dynamic> m) => Character(
    id: m['id'] as String, name: m['name'] as String,
    avatarUrl: (m['avatar_url'] ?? '') as String, coverUrl: (m['cover_url'] ?? m['avatar_url'] ?? '') as String,
    genderPronouns: (m['gender_pronouns'] ?? 'they/them') as String,
    universeTag: (m['universe_tag'] ?? 'other') as String,
    greeting: (m['greeting'] ?? '') as String, messageCount: (m['message_count'] ?? 0) as int);
}
const demoCharacters = <Character>[
 Character(id:'00000000-0000-4000-8000-000000000101',name:'Лира Эвергрин',avatarUrl:'https://image.pollinations.ai/prompt/anime%20fantasy%20elf%20mage%20girl%20emerald%20eyes%20moonlit%20forest?width=600&height=800&nologo=true',coverUrl:'https://image.pollinations.ai/prompt/anime%20fantasy%20elf%20mage%20girl%20emerald%20eyes%20moonlit%20forest?width=600&height=800&nologo=true',genderPronouns:'она/её',universeTag:'fantasy',greeting:'Лес сегодня необычайно тих. Ты тоже это чувствуешь?',messageCount:12840),
 Character(id:'00000000-0000-4000-8000-000000000102',name:'Ноа Вейл',avatarUrl:'https://image.pollinations.ai/prompt/cyberpunk%20male%20detective%20neon%20rain%20noir%20anime%20portrait?width=600&height=800&nologo=true',coverUrl:'https://image.pollinations.ai/prompt/cyberpunk%20male%20detective%20neon%20rain%20noir%20anime%20portrait?width=600&height=800&nologo=true',genderPronouns:'он/его',universeTag:'sci-fi',greeting:'Ты опоздал. Надеюсь, у тебя есть причина.',messageCount:8640),
 Character(id:'00000000-0000-4000-8000-000000000103',name:'Рен Аоки',avatarUrl:'https://image.pollinations.ai/prompt/modern%20anime%20young%20man%20rainy%20tokyo%20street%20portrait?width=600&height=800&nologo=true',coverUrl:'https://image.pollinations.ai/prompt/modern%20anime%20young%20man%20rainy%20tokyo%20street%20portrait?width=600&height=800&nologo=true',genderPronouns:'он/его',universeTag:'realism',greeting:'Привет. Рад, что ты написал.',messageCount:4210),
 Character(id:'00000000-0000-4000-8000-000000000104',name:'ARIA-7',avatarUrl:'https://image.pollinations.ai/prompt/female%20sci%20fi%20android%20anime%20silver%20hair%20blue%20eyes%20starship?width=600&height=800&nologo=true',coverUrl:'https://image.pollinations.ai/prompt/female%20sci%20fi%20android%20anime%20silver%20hair%20blue%20eyes%20starship?width=600&height=800&nologo=true',genderPronouns:'она/её',universeTag:'sci-fi',greeting:'Связь установлена. Какую звёздную систему исследуем?',messageCount:2090),
];
