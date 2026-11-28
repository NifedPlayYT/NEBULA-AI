# NEBULA AI

NEBULA AI — Android-приложение для безопасных ролевых диалогов с вымышленными персонажами.

## Архитектура

- Android: Kotlin, Jetpack Compose, Android Gradle Plugin 8.10.1, JDK 17, compile/target SDK 36.
- Backend: Supabase Edge Functions на Deno.
- Data: Supabase PostgreSQL с Row Level Security и pgvector.
- Основной LLM: DeepSeek Chat Completions API.
- Резервный провайдер: OpenRouter, включается серверными секретами.
- Embeddings: Google Gemini Embedding API, модель gemini-embedding-001, размерность 768.
- CI: GitHub Actions собирает release-вариант APK при push и pull request.

DeepSeek и Gemini API keys хранятся только в секретах Supabase. Клиенту нельзя выдавать эти ключи и нельзя помещать их в APK, Gradle properties или исходный код.

## Безопасность и возраст

Приложение ограничено возрастным рейтингом 13+. Содержимое чатов должно оставаться безопасным и неэротическим. Сервер принудительно устанавливает safe_mode=true; клиент не может выключить его через запрос, настройки профиля или remote config. Игровые режимы — обычный диалог, художественный текст и сюжетное приключение — меняют формат повествования, а не возрастные ограничения.

## Локальная сборка Android

Требуются JDK 17, Android SDK Platform 36 и Gradle 8.11.1.

```bash
gradle testDebugUnitTest
gradle assembleRelease
```

Если `app/nebula-release.keystore` и переменные подписи не заданы, release-вариант подписывается Android debug-сертификатом и предназначен только для тестирования. Для Google Play необходимо настроить постоянный upload keystore через GitHub Actions secrets: `NEBULA_KEYSTORE_BASE64`, `NEBULA_KEYSTORE_PASSWORD`, `NEBULA_KEY_ALIAS`, `NEBULA_KEY_PASSWORD`.

## Backend

См. [инструкцию по развёртыванию](docs/DEPLOYMENT.md). Секреты задаются через Supabase Dashboard или Supabase CLI, а не в репозитории.

## Сборка GitHub Actions

Workflow `.github/workflows/build_apk.yml` запускается на каждый push и pull request. APK публикуется как артефакт Actions с именем `app-release.apk`. Без постоянного upload keystore это тестовый APK, а не файл для загрузки в Google Play.

## Ограничения этой версии

Backend-схема и Edge Functions подготовлены отдельно от текущего локального Compose-клиента. До первого публичного релиза необходимо завершить подключение экранов входа/регистрации, синхронизацию локальных чатов с conversation/branch API и UI для жалоб/удаления аккаунта, затем проверить сборку и сценарии на физических устройствах. CI-компиляция сама по себе не заменяет тестирование на устройстве и проверку Play Console.
