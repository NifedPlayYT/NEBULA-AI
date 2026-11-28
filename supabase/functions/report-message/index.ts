import {
  corsHeaders,
  jsonError,
  jsonResponse,
  preflight,
  requireUser,
  safeError,
} from "../_shared/common.ts";

interface ReportRequest {
  message_id: string;
  reason: "unsafe" | "harassment" | "sexual_content" | "other";
  details?: string;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  if (request.method !== "POST") return jsonError("Method not allowed.", 405);

  try {
    const { user, db } = await requireUser(request);
    const input = await request.json() as ReportRequest;
    if (!/^[0-9a-f-]{36}$/i.test(input.message_id ?? "")) {
      return jsonError("Message id is invalid.", 400);
    }
    const reasons = ["unsafe", "harassment", "sexual_content", "other"];
    if (!reasons.includes(input.reason)) return jsonError("Report reason is invalid.", 400);

    const { data: message } = await db.from("chat_messages")
      .select("id")
      .eq("id", input.message_id)
      .eq("owner_id", user.id)
      .eq("role", "assistant")
      .maybeSingle();
    if (!message) return jsonError("The message was not found.", 404);

    const { data, error } = await db.from("user_reports").insert({
      reporter_id: user.id,
      message_id: message.id,
      reason: input.reason,
      details: String(input.details ?? "").trim().slice(0, 1200),
    }).select("id,created_at").single();

    if (error || !data) throw error ?? new Error("Report could not be stored.");
    return jsonResponse({ report: data, accepted: true });
  } catch (error) {
    if (error instanceof Response) return jsonError("Войдите в аккаунт.", 401);
    console.error("Report function failed.", String(error).slice(0, 200));
    return jsonError(safeError(error), 500);
  }
});
