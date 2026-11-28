import {
  createClient,
  type SupabaseClient,
  type User,
} from "npm:@supabase/supabase-js@2.57.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";

function keyFromBundle(variableName: string): string {
  const raw = Deno.env.get(variableName);
  if (!raw) return "";
  try {
    const parsed = JSON.parse(raw) as Record<string, string>;
    return parsed.default ?? Object.values(parsed)[0] ?? "";
  } catch {
    return raw;
  }
}

export function publishableKey(): string {
  return Deno.env.get("SUPABASE_ANON_KEY") ||
    keyFromBundle("SUPABASE_PUBLISHABLE_KEYS");
}

export function secretKey(): string {
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ||
    keyFromBundle("SUPABASE_SECRET_KEYS");
}

function ensureSupabaseConfig(): void {
  if (!SUPABASE_URL || !publishableKey()) {
    throw new Error("Supabase project configuration is missing.");
  }
}

export const corsHeaders: HeadersInit = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Cache-Control": "no-store",
};

export function preflight(): Response {
  return new Response("ok", { headers: corsHeaders });
}

export function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

export function jsonError(message: string, status = 400, code = "request_error"): Response {
  return jsonResponse({ error: { code, message } }, status);
}

export interface AuthContext {
  user: User;
  db: SupabaseClient;
  token: string;
}

export async function requireUser(request: Request): Promise<AuthContext> {
  ensureSupabaseConfig();
  const authorization = request.headers.get("Authorization") ?? "";
  const token = authorization.replace(/^Bearer\s+/i, "").trim();
  if (!token) throw new Response("Authentication required.", { status: 401 });

  const url = SUPABASE_URL;
  const anon = publishableKey();
  const verifier = createClient(url, anon, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
  const { data, error } = await verifier.auth.getUser(token);
  if (error || !data.user) {
    throw new Response("The session is invalid or expired.", { status: 401 });
  }

  const db = createClient(url, anon, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
  return { user: data.user, db, token };
}

export function createAdminClient(): SupabaseClient {
  ensureSupabaseConfig();
  const key = secretKey();
  if (!key) throw new Error("Supabase server secret key is not configured.");
  return createClient(SUPABASE_URL, key, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
}

export function safeError(error: unknown): string {
  if (error instanceof Response) return error.status === 401
    ? "Войдите в аккаунт и повторите попытку."
    : "Не удалось выполнить запрос.";
  if (error instanceof Error) return error.message.slice(0, 240);
  return "Неизвестная ошибка сервера.";
}
