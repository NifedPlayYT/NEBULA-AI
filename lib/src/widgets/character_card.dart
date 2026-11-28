import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';

import '../app.dart';
import '../models/character.dart';

class CharacterCard extends StatelessWidget {
  const CharacterCard({super.key, required this.character, required this.onTap});

  final Character character;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: nebulaSurface,
        borderRadius: BorderRadius.circular(23),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Stack(
            fit: StackFit.expand,
            children: [
              CachedNetworkImage(
                imageUrl: character.coverUrl,
                fit: BoxFit.cover,
                placeholder: (_, __) => Shimmer.fromColors(
                  baseColor: const Color(0xFF171D2C),
                  highlightColor: const Color(0xFF30364B),
                  child: const ColoredBox(color: Colors.white),
                ),
                errorWidget: (_, __, ___) => const ColoredBox(
                  color: Color(0xFF20263A),
                  child: Center(child: Icon(Icons.person_outline_rounded, size: 44, color: Colors.white38)),
                ),
              ),
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: [0, .38, 1],
                    colors: [Color(0x18000000), Color(0x08000000), Color(0xF0000000)],
                  ),
                ),
              ),
              Positioned(
                top: 11,
                left: 11,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
                  decoration: BoxDecoration(
                    color: const Color(0xB80B0E18),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white.withValues(alpha: .12)),
                  ),
                  child: Text(
                    character.universeTag.toUpperCase(),
                    style: const TextStyle(fontSize: 8, letterSpacing: 1.1, fontWeight: FontWeight.w800, color: Color(0xFFE0D5FF)),
                  ),
                ),
              ),
              Positioned(
                left: 12,
                right: 12,
                bottom: 13,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      character.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: -.2, shadows: [Shadow(color: Colors.black87, blurRadius: 12)]),
                    ),
                    const SizedBox(height: 5),
                    Row(
                      children: [
                        const Icon(Icons.chat_bubble_outline_rounded, size: 11, color: Color(0xFFB8B8C8)),
                        const SizedBox(width: 5),
                        Text(
                          _compactCount(character.messageCount),
                          style: const TextStyle(fontSize: 10, color: Color(0xFFD0D1DB)),
                        ),
                        const Spacer(),
                        const Icon(Icons.arrow_outward_rounded, size: 15, color: Color(0xFFD8CCFF)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      );

  String _compactCount(int count) {
    if (count >= 1000000) return '\${(count / 1000000).toStringAsFixed(1)}M сообщений';
    if (count >= 1000) return '\${(count / 1000).toStringAsFixed(1)}K сообщений';
    return '$count сообщений';
  }
}
