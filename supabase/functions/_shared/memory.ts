import { createAdminClient } from "./common.ts";
import type { SupabaseClient } from "npm:@supabase/supabase-js@2.57.4";
import { completeChat } from "./ai.ts";
import type { ChatLine } from "./ai.ts";

const EMBEDDING_DIMENSIONS = 768;

export async function createEmbedding(
  text: string,
  taskType: "RETRIEVAL_QUERY" | "RETRIEVAL_DOCUMENT",
): Promise<number[]> {
  const apiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
  if (!apiKey) throw new Error("GEMINI_API_KEY is not configured.");
  const input = text.trim().slice(0, 6000);
  if (!input) throw new Error("Embedding input is empty.");

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      signal: AbortSignal.timeout(25_000),
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "models/gemini-embedding-001",
        taskType,
        outputDimensionality: EMBEDDING_DIMENSIONS,
        content: { parts: [{ text: input }] },
      }),
    },
  );

  if (!response.ok) {
    const details = await response.text().catch(() => "");
    throw new Error(`Embedding provider returned HTTP ${response.status}: ${details.slice(0, 180)}`);
  }

  const payload = await response.json() as {
    embedding?: { values?: number[] };
  };
  const values = payload.embedding?.values ?? [];
  if (values.length !== EMBEDDING_DIMENSIONS || values.some((value) => !Number.isFinite(value))) {
    throw new Error("Embedding provider returned an invalid vector.");
  }
  return values;
}

export function vectorLiteral(values: number[]): string {
  if (values.length !== EMBEDDING_DIMENSIONS) {
    throw new Error("Embedding dimensions do not match the database schema.");
  }
  return `[${values.map((value) => Number(value).toFixed(8)).join(",")}]`;
}

export async function retrieveMemories(
  db: SupabaseClient,
  conversationId: string,
  characterId: string,
  query: string,
): Promise<Array<{ fact_type: string; content: string; importance: number }>> {
  try {
    const embedding = await createEmbedding(query, "RETRIEVAL_QUERY");
    const { data, error } = await db.rpc("match_memory_facts", {
      p_query_embedding: vectorLiteral(embedding),
      p_conversation_id: conversationId,
      p_character_id: characterId,
      p_match_count: 8,
      p_threshold: 0.58,
    });
    if (error) throw error;
    return (data ?? []).slice(0, 8);
  } catch (error) {
    console.warn("Long-term memory retrieval was skipped.", String(error).slice(0, 200));
    return [];
  }
}

function extractJson(text: string): Record<string, unknown> | null {
  const cleaned = text.trim()
    .replace(/^\`\`\`(?:json)?\s*/i, "")
    .replace(/\s*\`\`\`$/i, "");
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    const parsed = JSON.parse(cleaned.slice(start, end + 1));
    return parsed && typeof parsed === "object" ? parsed as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

async function hashText(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value.toLocaleLowerCase().trim()));
  return Array.from(new Uint8Array(bytes)).map((item) => item.toString(16).padStart(2, "0")).join("");
}

export async function runBranchSummary(ownerId: string, branchId: string): Promise<void> {
  const admin = createAdminClient();
  const { data: branch, error: branchError } = await admin
    .from("conversation_branches")
    .select("id,conversation_id,owner_id")
    .eq("id", branchId)
    .eq("owner_id", ownerId)
    .maybeSingle();
  if (branchError || !branch) return;

  const { data: conversation } = await admin
    .from("conversations")
    .select("character_id")
    .eq("id", branch.conversation_id)
    .eq("owner_id", ownerId)
    .maybeSingle();
  if (!conversation) return;

  const { data: previous } = await admin
    .from("chat_summaries")
    .select("summary,up_to_sequence")
    .eq("branch_id", branchId)
    .eq("owner_id", ownerId)
    .maybeSingle();

  const lastSequence = previous?.up_to_sequence ?? 0;
  const { data: messages, error: messagesError } = await admin
    .from("chat_messages")
    .select("id,role,content,sequence")
    .eq("branch_id", branchId)
    .eq("owner_id", ownerId)
    .eq("status", "complete")
    .gt("sequence", lastSequence)
    .order("sequence", { ascending: true })
    .limit(40);

  if (messagesError || !messages || messages.length < 15) return;
  const maxSequence = messages[messages.length - 1].sequence as number;
  const digest = messages.map((item) =>
    `${item.role === "user" ? "Игрок" : "Персонаж"}: ${String(item.content).slice(0, 1100)}`
  ).join("\n").slice(-18000);

  const prompt: ChatLine[] = [
    {
      role: "system",
      content: "Create a compact age-appropriate fictional roleplay continuity summary. Extract only stable facts useful in later turns: player preferences, inventory, key events, relationship status as friendship/team status only, and world state. Never infer sensitive personal traits. Return only valid JSON with keys summary (string) and facts (array of objects with type, content, importance). Allowed types: player_fact, inventory, event, relationship, preference, world_state. Do not include sexual or romantic content.",
    },
    {
      role: "user",
      content: JSON.stringify({
        previous_summary: String(previous?.summary ?? "").slice(0, 3500),
        new_messages: digest,
        output_limits: { summary_characters: 4200, facts_max: 8, fact_characters: 500 },
      }),
    },
  ];

  let modelText: string;
  try {
    const result = await completeChat(prompt);
    modelText = result.text;
  } catch (error) {
    console.error("Background summary generation failed.", String(error).slice(0, 200));
    return;
  }

  const parsed = extractJson(modelText);
  const summary = String(parsed?.summary ?? "").trim().slice(0, 4500);
  const facts = Array.isArray(parsed?.facts) ? parsed.facts.slice(0, 8) : [];
  if (!summary) return;

  const { error: summaryError } = await admin.from("chat_summaries").upsert({
    conversation_id: branch.conversation_id,
    branch_id: branchId,
    owner_id: ownerId,
    summary,
    up_to_sequence: maxSequence,
    updated_at: new Date().toISOString(),
  }, { onConflict: "branch_id" });

  if (summaryError) {
    console.error("Background summary could not be stored.", summaryError.message);
    return;
  }

  const sourceIds = messages.map((item) => item.id);
  for (const rawFact of facts) {
    if (!rawFact || typeof rawFact !== "object") continue;
    const fact = rawFact as { type?: unknown; content?: unknown; importance?: unknown };
    const type = String(fact.type ?? "");
    const content = String(fact.content ?? "").trim().slice(0, 1000);
    const allowed = ["player_fact", "inventory", "event", "relationship", "preference", "world_state"];
    if (!allowed.includes(type) || content.length < 8) continue;

    try {
      const embedding = await createEmbedding(content, "RETRIEVAL_DOCUMENT");
      const globalFact = type === "player_fact" || type === "preference";
      const dedupeHash = await hashText(
        globalFact ? `${type}:${content}` : `${branch.conversation_id}:${type}:${content}`,
      );
      const importance = Math.max(0, Math.min(1, Number(fact.importance ?? 0.5)));
      const { error } = await admin.from("memory_facts").upsert({
        owner_id: ownerId,
        conversation_id: globalFact ? null : branch.conversation_id,
        character_id: globalFact ? null : conversation.character_id,
        fact_type: type,
        content,
        importance: Number.isFinite(importance) ? importance : 0.5,
        dedupe_hash: dedupeHash,
        embedding: vectorLiteral(embedding),
        source_message_ids: sourceIds,
      }, { onConflict: "owner_id,dedupe_hash" });
      if (error) console.warn("Memory fact was not stored.", error.message);
    } catch (error) {
      console.warn("Embedding for a memory fact failed.", String(error).slice(0, 160));
    }
  }
}
