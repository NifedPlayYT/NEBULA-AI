# Backend deployment

## 1. Create a Supabase project

Create a Supabase project, then link this repository:

```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push
supabase db push --include-seed
```

The migration enables pgvector in the `extensions` schema, creates user-scoped tables, RLS policies, safe-mode constraints, message/branch RPC functions and the memory similarity-search function.

For local development, use `supabase start` and `supabase db reset`. The seed creates three safe test characters and their lorebook entries.

## 2. Configure server secrets

Set secrets in the Supabase Dashboard under Edge Functions → Secrets, or with the CLI:

```bash
supabase secrets set DEEPSEEK_API_KEY=YOUR_DEEPSEEK_KEY
supabase secrets set DEEPSEEK_MODEL=deepseek-flash
supabase secrets set GEMINI_API_KEY=YOUR_GEMINI_KEY
```

Optional fallback provider:

```bash
supabase secrets set OPENROUTER_API_KEY=YOUR_OPENROUTER_KEY
supabase secrets set OPENROUTER_MODEL=YOUR_ENABLED_OPENROUTER_MODEL_ID
```

Supabase injects its project URL and publishable/secret keys into Edge Functions. The shared auth helper supports the legacy `SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY` variables and the newer JSON key bundles. Never put the DeepSeek, Gemini, OpenRouter or Supabase secret key in Android resources, BuildConfig, GitHub artifacts, or a client preference.

Use the paid Gemini API tier for real user conversations. The free tier may use submitted content to improve Google products; use it only with synthetic test conversations and after reviewing the provider's data terms.

## 3. Deploy Edge Functions

```bash
supabase functions deploy chat
supabase functions deploy report-message
supabase functions deploy remote-config
supabase functions deploy delete-account
```

The functions require a valid Supabase user access token. Keep JWT verification enabled. Do not disable it to solve an authentication error.

## 4. Configure DeepSeek and fallback behavior

The `chat` function uses `https://api.deepseek.com/chat/completions` and the `deepseek-flash` model by default. It retries via OpenRouter only for transient transport, rate-limit or upstream 5xx failures, and only before any output has been streamed. It does not route safety rejections (including `Content Exists Risk`), invalid credentials, authorization errors or malformed requests to another provider.

The first two system messages are stable: a platform safety prefix followed by the character card. Dynamic summaries, lore, memory and recent messages follow them so repeated requests can benefit from DeepSeek's prefix cache. Check the returned usage object in server-side diagnostics for cache-hit and cache-miss counters; do not assume every request is a cache hit.

## 5. Data and privacy release checklist

Before a public launch:

- Publish a privacy policy that names the actual legal entity/contact, data collected, purpose, retention, processors and deletion workflow.
- Add an in-app report action for every generated response and route reports to a monitored moderation queue.
- Add an in-app account deletion flow and a public web resource linked from Play Console. The Edge Function deletes the authenticated Supabase user; PostgreSQL foreign keys cascade the user's owned chats, messages, memory, reports, personas and profile.
- Verify Google Play Data safety answers against actual telemetry, analytics, billing and provider behavior.
- Configure account verification, password reset, abuse throttling, moderation escalation and operational alerting.
- Complete the Android client connection to Supabase Auth and the `chat` function; the current Compose UI still contains the previous local/BYOK transport and is not yet wired to these Edge Functions.
- Configure Google Play Billing server verification before selling subscriptions. Never grant premium entitlement from a client-only boolean.
- Implement Google Play Rewarded Ads server-side verification before awarding any in-app currency. The seed/config currently leaves rewarded ads disabled.
- Use a persistent upload key for Play releases. The debug-signed CI artifact is only for internal testing.

This repository is engineering groundwork, not a declaration that Play review, legal review, penetration testing or device testing has already passed.
