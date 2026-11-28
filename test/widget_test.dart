import 'package:flutter_test/flutter_test.dart';
import 'package:nebula_ai/src/models/character.dart';
void main(){test('preview catalog has four unique characters',(){expect(demoCharacters,hasLength(4));expect(demoCharacters.map((c)=>c.id).toSet(),hasLength(4));});}
