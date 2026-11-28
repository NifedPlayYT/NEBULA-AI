# NEBULA AI

Flutter Android client and Supabase backend for PG-13 character roleplay.

## Current implementation
- Dark Flutter catalogue with four demo character portraits, image caching and shimmer loading.
- Chat UI with three interaction modes and a server-side chat invocation.
- Supabase schema for characters, player personas, branches, message variants, summaries, lorebook, vector memories, reports and entitlements.
- DeepSeek server adapter, optional OpenRouter adapter, Hugging Face BGE-M3 embedding call, recent-history window, lore keyword matching and asynchronous summary function.
- Account deletion Edge Function.
- GitHub Actions workflow for format/analyze/test/release APK artifact.

This is an engineering foundation, not yet a Play Store-ready release. The current UI does not yet include complete sign-in/sign-up, persona and character editors, message editing/swipe/branch controls, report UI, billing/rewarded-ad flows, privacy screens, or a full moderation/admin workflow. Do not market those features as shipped until implemented and tested.

## Setup
1. Install Flutter stable and run `flutter pub get`.
2. Create a Supabase project and apply `supabase/migrations/202611280001_initial_schema.sql`, then `supabase/seed.sql`.
3. Configure the app:
   ```sh
   flutter run --dart-define=SUPABASE_URL=https://YOUR_PROJECT.supabase.co --dart-define=SUPABASE_ANON_KEY=YOUR_ANON_KEY
   ```
4. Set Supabase Edge Function secrets (never put provider keys in Flutter):
   ```sh
   supabase secrets set DEEPSEEK_API_KEY=... SUPABASE_URL=... SUPABASE_ANON_KEY=... SUPABASE_SERVICE_ROLE_KEY=... HUGGINGFACE_API_KEY=...
   ```
   Optional routing: set `LLM_PROVIDER=openrouter`, `OPENROUTER_API_KEY`, and `OPENROUTER_MODEL`. DeepSeek is selected by default.
5. Deploy:
   ```sh
   supabase functions deploy chat
   supabase functions deploy summarize
   supabase functions deploy delete-account
   ```

## CI and release
GitHub Actions runs Dart formatting, analysis, tests and builds an unsigned APK artifact. Add `SUPABASE_URL` and `SUPABASE_ANON_KEY` as repository Actions secrets for a configured build. The anon key is designed for client use with RLS; provider and service-role keys must remain Supabase secrets. Play Store publication additionally requires a protected signing key, verified billing/ad flows, privacy disclosures, age/content declarations, data safety form and policy review.

## Important operational notes
- Pollinations image URLs are external demo endpoints. Availability and commercial usage rights must be verified before release; move production artwork to owned Supabase Storage.
- DeepSeek prompt-prefix caching is best-effort provider behavior; static system and character prefixes are kept at the front of the message list.
- The memory schema uses 1024-dimensional vectors. Confirm the embedding provider returns the expected dimension and pooling format in an integration test before enabling production RAG.
- Review RLS and deletion behavior against a staging project before accepting real user data.
