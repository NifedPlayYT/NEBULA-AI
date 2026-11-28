import { createClient } from "npm:@supabase/supabase-js@2";
import { complete, embed, type Message } from "../_shared/llm.ts";

type ExtractedMemory = {
  memory_type: "fact" | "event" | "inventory" | "relationship" | "preference";
  content: string;
  importance: number;
};

function parsePayload(raw: string): { summary: string; memories: ExtractedMemory[] } {
  const start = raw.indexOf("{");
  const end = raw.lastIndexOf("}");
  if (start < 0 || end <= start) {
    return { summary: raw.trim().slice(0, 1600), memories: [] };
  }

  try {
    const value = JSON.parse(raw.slice(start, end + 1));
    const validTypes = new Set(["fact", "event", "inventory", "relationship", "preference"]);
    const memories = Array.isArray(value.memories)
      ? value.memories.flatMap((item: any) => {
          if (
            !item ||
            typeof item.content !== "string" ||
            typeof item.memory_type !== "string" ||
            !validTypes.has(item.memory_type)
          ) return [];
          const text = item.content.trim().slice(0, 600);
          if (text.length < 8) return [];
          return [{
            memory_type: item.memory_type as ExtractedMemory["memory_type"],
            content: text,
            importance: Math.min(5, Math.max(1, Number(item.importance) || 3)),
          }];
        }).slice(0, 8)
      : [];

    return {
      summary: typeof value.summary === "string"
        ? value.summary.trim().slice(0, 1600)
        : raw.trim().slice(0, 1600),
      memories,
    };
  } catch {
    return { summary: raw.trim().slice(0, 1600), memories: [] };
  }
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value.toLocaleLowerCase()));
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-headers": "authorization, apikey, content-type",
        "access-control-allow-methods": "POST, OPTIONS",
      },
    });
  }
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const authorization = req.headers.get("Authorization");
  if (!url || !serviceKey || authorization !== `Bearer ${serviceKey}`) {
    return new Response("Unauthorized", { status: 401 });
  }

  try {
    const { branch_id, user_id } = await req.json();
    if (typeof branch_id !== "string" || typeof user_id !== "string") {
      return new Response("Invalid input", { status: 400 });
    }

    const db = createClient(url, serviceKey);
    const { data: branch } = await db
      .from("chat_branches")
      .select("id,character_id")
      .eq("id", branch_id)
      .eq("user_id", user_id)
      .maybeSingle();
    if (!branch) return new Response("Not found", { status: 404 });

    const [{ data: rows }, { data: previous }, { count: totalMessageCount }] = await Promise.all([
      db.from("messages")
        .select("role,content,created_at")
        .eq("branch_id", branch_id)
        .eq("selected", true)
        .order("created_at", { ascending: false })
        .limit(150),
      db.from("conversation_summaries")
        .select("summary,summarized_message_count")
        .eq("branch_id", branch_id)
        .maybeSingle(),
      db.from("messages")
        .select("id", { count: "exact", head: true })
        .eq("branch_id", branch_id)
        .eq("selected", true),
    ]);

    const latestRows = (rows ?? []).reverse();
    const totalCount = totalMessageCount ?? latestRows.length;
    const previouslySummarizedCount = previous?.summarized_message_count ?? 0;
    if (totalCount - previouslySummarizedCount < 15) {
      return Response.json({ skipped: true });
    }

    const firstLoadedOffset = Math.max(0, totalCount - latestRows.length);
    const overlapStartOffset = Math.max(0, previouslySummarizedCount - 5);
    const transcriptStartIndex = Math.max(0, overlapStartOffset - firstLoadedOffset);
    const transcript = latestRows
      .slice(transcriptStartIndex)
      .map((message: any) => `${message.role}: ${message.content}`)
      .join("\n")
      .slice(-16000);

    const prompt: Message[] = [
      {
        role: "system",
        content: [
          "Верни только валидный JSON объект без markdown.",
          "Формат: {\"summary\":\"...\",\"memories\":[{\"memory_type\":\"fact|event|inventory|relationship|preference\",\"content\":\"...\",\"importance\":1}]}",
          "Составь короткое точное резюме диалога на русском: события, места, инвентарь, обещания и динамика отношений.",
          "Извлекай только устойчивые сведения, прямо подтверждённые в сообщениях, необходимые для продолжения этой вымышленной истории.",
          "Не сохраняй пароли, контакты, платёжные данные, медицинские сведения, секреты или другие чувствительные личные данные.",
          "Не превращай догадки персонажей в факты. Не более 8 воспоминаний; importance от 1 до 5.",
        ].join(" "),
      },
      {
        role: "user",
        content: `Предыдущее резюме:\n${previous?.summary ?? "Нет"}\n\nНовые сообщения:\n${transcript}`,
      },
    ];

    const raw = await complete(prompt, 1100);
    const extracted = parsePayload(raw);
    const { error: summaryError } = await db.from("conversation_summaries").upsert({
      branch_id,
      user_id,
      summary: extracted.summary,
      summarized_message_count: totalCount,
      updated_at: new Date().toISOString(),
    }, { onConflict: "branch_id" });
    if (summaryError) return new Response("Summary save failed", { status: 500 });

    let savedMemories = 0;
    for (const memory of extracted.memories) {
      const contentHash = await sha256(memory.content);
      const vector = await embed(memory.content);
      const embedding = vector
        ? `[${vector.map((value) => value.toFixed(8)).join(",")}]`
        : null;

      const { error } = await db.from("long_term_memories").upsert({
        user_id,
        character_id: branch.character_id,
        branch_id: ["fact", "preference"].includes(memory.memory_type) ? null : branch_id,
        memory_type: memory.memory_type,
        content: memory.content,
        content_hash: contentHash,
        embedding,
        importance: memory.importance,
      }, {
        onConflict: "user_id,character_id,content_hash",
        ignoreDuplicates: true,
      });
      if (!error) savedMemories += 1;
    }

    return Response.json({
      summarized_messages: totalCount,
      memories_saved: savedMemories,
    });
  } catch {
    return new Response("Summary failed", { status: 500 });
  }
});
