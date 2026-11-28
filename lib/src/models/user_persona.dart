class UserPersona {
  const UserPersona({
    required this.id,
    required this.name,
    required this.genderPronouns,
    required this.avatarUrl,
    required this.description,
    required this.isActive,
  });

  final String id;
  final String name;
  final String genderPronouns;
  final String avatarUrl;
  final String description;
  final bool isActive;

  factory UserPersona.fromMap(Map<String, dynamic> map) => UserPersona(
        id: map['id'] as String,
        name: map['name'] as String,
        genderPronouns: (map['gender_pronouns'] ?? 'they/them') as String,
        avatarUrl: (map['avatar_url'] ?? '') as String,
        description: (map['description'] ?? '') as String,
        isActive: (map['is_active'] ?? false) as bool,
      );
}
