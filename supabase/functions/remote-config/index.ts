import {
  jsonError,
  jsonResponse,
  preflight,
  requireUser,
  safeError,
} from "../_shared/common.ts";

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  if (request.method !== "GET" && request.method !== "POST") {
    return jsonError("Method not allowed.", 405);
  }

  try {
    const { db } = await requireUser(request);
    const { data, error } = await db.from("app_config")
      .select("minimum_version_code,rewarded_ads_enabled")
      .eq("id", 1)
      .single();
    if (error || !data) throw error ?? new Error("Remote config is unavailable.");

    return jsonResponse({
      minimum_version_code: data.minimum_version_code,
      safe_mode: true,
      safe_mode_setting_visible: false,
      rewarded_ads_enabled: data.rewarded_ads_enabled === true,
    });
  } catch (error) {
    if (error instanceof Response) return jsonError("Войдите в аккаунт.", 401);
    console.error("Remote config failed.", String(error).slice(0, 200));
    return jsonError(safeError(error), 500);
  }
});
