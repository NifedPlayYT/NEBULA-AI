import { createClient } from "npm:@supabase/supabase-js@2";

async function collectUserObjects(
  storage: ReturnType<ReturnType<typeof createClient>["storage"]["from"]>,
  prefix: string,
  depth = 0,
): Promise<string[]> {
  if (depth > 5) throw new Error("storage_folder_depth_exceeded");

  const { data, error } = await storage.list(prefix, { limit: 1000, offset: 0 });
  if (error) throw error;

  const paths: string[] = [];
  for (const entry of data ?? []) {
    const path = `${prefix}/${entry.name}`;
    if (entry.id === null) {
      paths.push(...await collectUserObjects(storage, path, depth + 1));
    } else {
      paths.push(path);
    }
  }
  return paths;
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
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const authorization = req.headers.get("Authorization");
  if (!url || !anon || !service || !authorization) {
    return new Response("Unauthorized", { status: 401 });
  }

  const userClient = createClient(url, anon, {
    global: { headers: { Authorization: authorization } },
  });
  const { data, error } = await userClient.auth.getUser();
  if (error || !data.user) return new Response("Unauthorized", { status: 401 });

  const admin = createClient(url, service);
  try {
    const bucket = admin.storage.from("character-art");
    const paths = await collectUserObjects(bucket, data.user.id);
    for (let offset = 0; offset < paths.length; offset += 100) {
      const batch = paths.slice(offset, offset + 100);
      if (batch.length === 0) continue;
      const { error: removeError } = await bucket.remove(batch);
      if (removeError) throw removeError;
    }

    const deletion = await admin.auth.admin.deleteUser(data.user.id);
    if (deletion.error) throw deletion.error;

    return Response.json({ deleted: true });
  } catch {
    return new Response(
      "Account deletion could not be completed. Retry or contact support.",
      { status: 500 },
    );
  }
});
