import {
  createAdminClient,
  jsonError,
  jsonResponse,
  preflight,
  requireUser,
  safeError,
} from "../_shared/common.ts";

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  if (request.method !== "POST") return jsonError("Method not allowed.", 405);

  try {
    const { user } = await requireUser(request);
    const input = await request.json().catch(() => ({})) as { confirm?: boolean };
    if (input.confirm !== true) {
      return jsonError("Confirm account deletion explicitly.", 400, "confirmation_required");
    }

    const admin = createAdminClient();
    const { error } = await admin.auth.admin.deleteUser(user.id, false);
    if (error) throw error;

    return jsonResponse({ deleted: true });
  } catch (error) {
    if (error instanceof Response) return jsonError("Войдите в аккаунт.", 401);
    console.error("Account deletion failed.", String(error).slice(0, 200));
    return jsonError(safeError(error), 500);
  }
});
