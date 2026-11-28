import {
  createAdminClient,
  corsHeaders,
  jsonError,
  jsonResponse,
  preflight,
  requireUser,
  safeError,
} from "../_shared/common.ts";
import { openChatStream, ProviderError } from "../_shared/ai.ts";
import {
  buildPromptMessages,
  selectRelevantLore,
  type CharacterCard,
  type LoreEntry,
  type MemoryFact,
  type RecentMessage,
} from "../_shared/context.ts";
import { retrieveMemories, runBranchSummary } from "../_shared/memory.ts";

declare const EdgeRuntime: { waitUntil(promise: Promise<unknown>): void };

interface ChatRequest {
  conversation_id?: string;
  branch_id?: string;
  character_id: string;
  persona_id?: string | null;
  mode?: "plain" | "literary" | "adventure";
  user_text?: string;
  regenerate?: boolean;
  parent_message_id?: string;
}

function validUuid(value: unknown): value is string {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function sseError(message: string): Uint8Array {
  return new TextEncoder().encode(`data: ${JSON.stringify({ error: { message } })}\n\n`);
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  if (request.method !== "POST") return jsonError("Method not allowed.", 405, "method_not_allowed");

  let assistantMessageId = "";
  let admin: ReturnType<typeof createAdminClient> | null = null;

  try {
    const { user, db } = await requireUser(request);
    const contentLength = Number(request.headers.get("content-length") ?? "0");
    if (contentLength > 48_000) return jsonError("Request is too large.", 413, "payload_too_large");

    const input = await request.json() as ChatRequest;
    if (!validUuid(input.character_id)) {
      return jsonError("Character id is invalid.", 400, "invalid_character");
    }
    const mode = input.mode ?? "literary";
    if (!["plain", "literary", "adventure"].includes(mode)) {
      return jsonError("Chat mode is invalid.", 400, "invalid_mode");
    }

    const userText = String(input.user_text ?? "").trim();
    const isRegenerate = input.regenerate === true;
    if (isRegenerate && !validUuid(input.parent_message_id)) {
      return jsonError("A source user message is required to regenerate.", 400, "invalid_parent");
    }
    if (!isRegenerate && (userText.length < 1 || userText.length > 12000)) {
      return jsonError("Message must contain 1–12,000 characters.", 400, "invalid_message");
    }

    let conversationId = input.conversation_id ?? "";
    let branchId = input.branch_id ?? "";

    if (!conversationId) {
      const { data, error } = await db.rpc("create_conversation", {
        p_character_id: input.character_id,
        p_persona_id: validUuid(input.persona_id) ? input.persona_id : null,
        p_mode: mode,
        p_title: "Новый диалог",
      });
      if (error) throw new Error("Не удалось создать диалог.");
      const created = Array.isArray(data) ? data[0] : data;
      conversationId = created?.conversation_id ?? "";
      branchId = created?.branch_id ?? "";
    } else {
      if (!validUuid(conversationId) || !validUuid(branchId)) {
        return jsonError("Conversation or branch id is invalid.", 400, "invalid_conversation");
      }
      const { data: ownedConversation, error } = await db
        .from("conversations")
        .select("id,character_id,mode")
        .eq("id", conversationId)
        .eq("owner_id", user.id)
        .maybeSingle();
      if (error || !ownedConversation || ownedConversation.character_id !== input.character_id) {
        return jsonError("Conversation was not found.", 404, "conversation_not_found");
      }
      const { data: ownedBranch } = await db
        .from("conversation_branches")
        .select("id")
        .eq("id", branchId)
        .eq("conversation_id", conversationId)
        .eq("owner_id", user.id)
        .maybeSingle();
      if (!ownedBranch) return jsonError("Branch was not found.", 404, "branch_not_found");

      await db.from("conversations").update({ mode }).eq("id", conversationId).eq("owner_id", user.id);
    }

    const { data: character, error: characterError } = await db
      .from("characters")
      .select("id,name,bio,category,greeting,instructions,examples")
      .eq("id", input.character_id)
      .maybeSingle();
    if (characterError || !character) return jsonError("Character was not found.", 404, "character_not_found");

    let playerPersona: { name: string; description: string } | null = null;
    if (validUuid(input.persona_id)) {
      const { data } = await db.from("player_personas")
        .select("name,description")
        .eq("id", input.persona_id)
        .eq("owner_id", user.id)
        .maybeSingle();
      if (data) playerPersona = data;
    }

    let parentMessageId = "";
    if (isRegenerate) {
      parentMessageId = input.parent_message_id!;
      const { data: parent } = await db.from("chat_messages")
        .select("id,sequence,role")
        .eq("id", parentMessageId)
        .eq("branch_id", branchId)
        .eq("owner_id", user.id)
        .maybeSingle();
      if (!parent || parent.role !== "user") {
        return jsonError("The source message was not found.", 404, "message_not_found");
      }
    } else {
      const { data: insertedId, error } = await db.rpc("append_user_message", {
        p_branch_id: branchId,
        p_content: userText,
        p_parent_message_id: validUuid(input.parent_message_id) ? input.parent_message_id : null,
      });
      if (error || !insertedId) return jsonError("Could not save your message.", 409, "message_save_failed");
      parentMessageId = insertedId;
    }

    const { data: parentMessage } = await db.from("chat_messages")
      .select("id,sequence")
      .eq("id", parentMessageId)
      .eq("branch_id", branchId)
      .eq("owner_id", user.id)
      .maybeSingle();
    if (!parentMessage) return jsonError("The source message was not found.", 404, "message_not_found");

    const { data: siblings } = await db.from("chat_messages")
      .select("variant_group_id,variant_index")
      .eq("parent_message_id", parentMessageId)
      .eq("role", "assistant")
      .eq("owner_id", user.id)
      .order("variant_index", { ascending: true });

    const existingVariants = siblings ?? [];
    const groupId = existingVariants.find((item) => item.variant_group_id)?.variant_group_id ??
      crypto.randomUUID();
    const variantIndex = existingVariants.length;

    const { data: recentRows, error: recentError } = await db.from("chat_messages")
      .select("id,role,content,sequence,status")
      .eq("branch_id", branchId)
      .eq("owner_id", user.id)
      .eq("status", "complete")
      .lte("sequence", parentMessage.sequence)
      .order("sequence", { ascending: false })
      .limit(30);
    if (recentError) throw new Error("Не удалось загрузить историю диалога.");

    const recent = (recentRows ?? []).reverse() as Array<{
      role: "user" | "assistant";
      content: string;
    }>;

    const [{ data: summary }, { data: loreRows }] = await Promise.all([
      db.from("chat_summaries").select("summary").eq("branch_id", branchId).eq("owner_id", user.id).maybeSingle(),
      db.from("lorebook_entries")
        .select("title,triggers,content,priority")
        .eq("character_id", input.character_id)
        .eq("is_enabled", true)
        .order("priority", { ascending: false })
        .limit(50),
    ]);

    const memory: MemoryFact[] = await retrieveMemories(
      db,
      conversationId,
      input.character_id,
      recent.filter((message) => message.role === "user").slice(-2).map((message) => message.content).join("\n").slice(-5000),
    );
    const lore = selectRelevantLore(
      (loreRows ?? []) as LoreEntry[],
      recent.slice(-6).map((message) => message.content).join("\n"),
    );

    const promptMessages = buildPromptMessages({
      character: character as CharacterCard,
      playerPersona,
      mode,
      summary: String(summary?.summary ?? ""),
      memory,
      lore,
      recent: recent as RecentMessage[],
    });

    const abortController = new AbortController();
    request.signal.addEventListener("abort", () => abortController.abort(), { once: true });
    const upstream = await openChatStream(promptMessages, abortController.signal);
    admin = createAdminClient();

    const { data: assistantId, error: appendError } = await admin.rpc("append_assistant_message", {
      p_owner_id: user.id,
      p_branch_id: branchId,
      p_parent_message_id: parentMessageId,
      p_variant_group_id: groupId,
      p_variant_index: variantIndex,
    });
    if (appendError || !assistantId) {
      await upstream.response.body?.cancel();
      return jsonError("Could not create the assistant message.", 409, "assistant_save_failed");
    }
    assistantMessageId = assistantId;

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        const pump = async () => {
          const reader = upstream.response.body!.getReader();
          const decoder = new TextDecoder();
          let sseBuffer = "";
          let answer = "";
          let sawDone = false;
          let streamError = "";

          const parseLine = (line: string) => {
            if (!line.startsWith("data:")) return;
            const data = line.slice(5).trim();
            if (data === "[DONE]") {
              sawDone = true;
              return;
            }
            if (!data) return;
            try {
              const event = JSON.parse(data) as {
                error?: { message?: string };
                choices?: Array<{ delta?: { content?: string } }>;
              };
              if (event.error) streamError = "AI provider returned a stream error.";
              const delta = event.choices?.[0]?.delta?.content;
              if (typeof delta === "string") answer += delta;
            } catch {
              streamError = "AI provider returned an invalid stream event.";
            }
          };

          try {
            while (true) {
              if (abortController.signal.aborted) throw new DOMException("Cancelled", "AbortError");
              const { value, done } = await reader.read();
              if (done) break;
              controller.enqueue(value);
              sseBuffer += decoder.decode(value, { stream: true });
              let newline = sseBuffer.indexOf("\n");
              while (newline >= 0) {
                parseLine(sseBuffer.slice(0, newline).replace(/\r$/, ""));
                sseBuffer = sseBuffer.slice(newline + 1);
                newline = sseBuffer.indexOf("\n");
              }
            }
            sseBuffer += decoder.decode();
            if (sseBuffer.trim()) parseLine(sseBuffer.replace(/\r$/, ""));
            if (!sawDone || streamError) throw new Error("The AI stream ended before completion.");
            await admin!.from("chat_messages")
              .update({ content: answer.slice(0, 24000), status: "complete" })
              .eq("id", assistantMessageId)
              .eq("owner_id", user.id);

            const { count } = await admin!.from("chat_messages")
              .select("id", { count: "exact", head: true })
              .eq("branch_id", branchId)
              .eq("owner_id", user.id)
              .eq("status", "complete");
            if (count && count % 15 === 0) {
              EdgeRuntime.waitUntil(runBranchSummary(user.id, branchId).catch((error) => {
                console.warn("Background summary task failed.", String(error).slice(0, 200));
              }));
            }
            controller.close();
          } catch (error) {
            const status = abortController.signal.aborted ? "cancelled" : "failed";
            await admin!.from("chat_messages")
              .update({ content: answer.slice(0, 24000), status })
              .eq("id", assistantMessageId)
              .eq("owner_id", user.id);
            if (!abortController.signal.aborted) {
              try {
                controller.enqueue(sseError(
                  error instanceof Error && error.message.includes("Content Exists Risk")
                    ? "Запрос не прошёл проверку безопасности. Измени формулировку и попробуй снова."
                    : "Соединение прервалось. Попробуй повторить запрос.",
                ));
                controller.close();
              } catch {
                // The client may already have closed the stream.
              }
            }
          } finally {
            await reader.cancel().catch(() => undefined);
          }
        };
        void pump();
      },
      cancel() {
        abortController.abort();
      },
    });

    return new Response(stream, {
      status: 200,
      headers: {
        ...corsHeaders,
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache, no-transform",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no",
        "X-Nebula-Conversation-ID": conversationId,
        "X-Nebula-Branch-ID": branchId,
        "X-Nebula-Message-ID": assistantMessageId,
        "X-Nebula-Variant-Index": String(variantIndex),
        "X-Nebula-Provider": upstream.provider,
      },
    });
  } catch (error) {
    if (error instanceof ProviderError) {
      const message = error.safeMessage === "Content Exists Risk"
        ? "Запрос не прошёл проверку безопасности. Измени формулировку и попробуй снова."
        : error.status === 429
        ? "Сервис занят. Повтори запрос немного позже."
        : "AI-сервис временно недоступен. Попробуй ещё раз.";
      return jsonError(message, error.status === 429 ? 429 : 502, "provider_error");
    }
    if (error instanceof Response) {
      return jsonError(error.status === 401 ? "Требуется вход в аккаунт." : "Запрос отклонён.", error.status, "unauthorized");
    }
    console.error("Chat function failed.", String(error).slice(0, 300));
    return jsonError(safeError(error), 500, "server_error");
  }
});
