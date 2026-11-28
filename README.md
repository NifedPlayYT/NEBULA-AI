# NEBULA AI

Flutter Android client and Supabase backend for PG-13 character roleplay.

## What is currently implemented

### Flutter
- Dark three-tab shell: chats, catalogue, profile.
- Character catalogue with four demo characters, cached remote artwork and shimmer loading.
- Email/password sign-up and sign-in using Supabase Auth.
- Universal character editor with pronouns, universe, relationship dynamic, personality, backstory, speech style, greeting, example dialogue and image upload or remote art URL generation.
- Multiple player personas, active persona selection, pronouns and avatar upload.
- Persistent chat inbox and conversation restore.
- Three modes: ordinary chat, creative roleplay and story adventure.
- Regenerate and select saved response alternatives, horizontal swipe regeneration, edit the most recent user turn, branch from an assistant reply, and report a message.
- Privacy information and account deletion screen.

### Supabase
- PostgreSQL schema, RLS policies and Storage bucket.
- DeepSeek server-side provider adapter; optional OpenRouter strategy selected by server environment.
- Recent context window, bounded context construction, Lorebook keyword retrieval and pgvector semantic memory lookup.
- Background conversation summaries and long-term story memory extraction.
- Per-branch chat turn locks, turn IDs, persisted response variants, branch cloning and transactional edit of the most recent turn.
- Account removal also attempts to remove user-owned artwork in Storage.

## Local setup

1. Install Flutter stable and Deno 2.
2. Create a Supabase project.
3. Apply all SQL migrations in `supabase/migrations/` in filename order, then run `supabase/seed.sql`.
4. Set server-side Supabase Edge Function secrets:

   ```sh
   supabase secrets set DEEPSEEK_API_KEY=... SUPABASE_URL=... SUPABASE_ANON_KEY=... SUPABASE_SERVICE_ROLE_KEY=... HUGGINGFACE_API_KEY=...
   ```

   Optional provider routing:

   ```sh
   supabase secrets set LLM_PROVIDER=openrouter OPENROUTER_API_KEY=... OPENROUTER_MODEL=...
   ```

5. Deploy functions:

   ```sh
   supabase functions deploy chat
   supabase functions deploy summarize
   supabase functions deploy delete-account
   ```

6. Run the app:

   ```sh
   flutter pub get
   flutter run --dart-define=SUPABASE_URL=https://YOUR_PROJECT.supabase.co --dart-define=SUPABASE_ANON_KEY=YOUR_PUBLIC_KEY
   ```

Provider keys and the Supabase service role key must never be embedded in the Flutter client or APK.

## CI

`.github/workflows/build_apk.yml` runs Deno type checking and unit tests, Dart formatting, Flutter analysis, Flutter tests, and a release APK build uploaded as a GitHub Actions artifact. The repository also contains a legacy Gradle workflow for the existing v0.2 native app; its artifact must not be confused with the new Flutter build.

## Known production gaps

- Streaming responses are not yet wired to the Flutter chat. Current DeepSeek calls are non-streaming.
- Sign-in recovery, email change, session management and authentication edge-case UX need more work.
- Full branch navigation/history management and deletion/renaming of chats are not yet in the UI.
- Reports are stored but there is no staff moderation console.
- Subscription entitlements are only a database foundation. Play Billing and rewarded-ad grants need server-side purchase/ad verification.
- Privacy screen is an in-app information page, not a hosted legal privacy policy. Data-retention and backup deletion terms must be published before public release.
- Pollinations artwork URLs are demo sources. Verify service availability and commercial usage rights; production art should be stored in a project-controlled bucket.
- Validate pgvector and BGE-M3 output with your Supabase project and confirm the exact provider response shape before production.
- Run end-to-end testing against a staging Supabase project, configure Play signing and build a signed Android App Bundle before Google Play submission.

This repository branch is a development foundation. A green build does not prove a production Supabase deployment, billing compliance or Google Play acceptance.
