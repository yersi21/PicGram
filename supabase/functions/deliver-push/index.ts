// FCM is only Android's delivery transport. All authoritative data stays in Supabase.
// Required server secrets: PICGRAM_PUSH_HOOK_SECRET, FCM_SERVICE_ACCOUNT (JSON).
// Never put the service account / service_role key in Android or Git.
const env = (name: string) => { const value = Deno.env.get(name); if (!value) throw new Error(`Missing ${name}`); return value; };
const encode = (value: Uint8Array | string) => btoa(typeof value === "string" ? value : String.fromCharCode(...value))
  .replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
async function sameSecret(actual: string, expected: string) {
  const digest = async (s: string) => new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
  const [a,b] = await Promise.all([digest(actual),digest(expected)]);
  let different = 0; for (let i = 0; i < a.length; i++) different |= a[i] ^ b[i];
  return different === 0;
}
async function accessToken(account: { client_email: string; private_key: string }) {
  const now = Math.floor(Date.now()/1000);
  const unsigned = encode(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + encode(JSON.stringify({
    iss: account.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3500,
  }));
  const pem = account.private_key.replace(/-----[^-]+-----|\s/g, "");
  const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), c => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const response = await fetch("https://oauth2.googleapis.com/token", { method: "POST",
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: unsigned + "." + encode(signature) }) });
  if (!response.ok) throw new Error("FCM authorization failed");
  return (await response.json()).access_token as string;
}
Deno.serve(async request => {
  try {
    if (request.method !== "POST" || !await sameSecret(request.headers.get("x-picgram-hook-secret") ?? "", env("PICGRAM_PUSH_HOOK_SECRET")))
      return new Response("Unauthorized", { status: 401 });
    const account = JSON.parse(env("FCM_SERVICE_ACCOUNT"));
    const base = env("SUPABASE_URL") + "/rest/v1";
    const secret = env("SUPABASE_SERVICE_ROLE_KEY");
    const db = async (path: string, method = "GET", data?: unknown) => {
      const response = await fetch(base + path, { method, headers: { apikey: secret, Authorization: `Bearer ${secret}`,
        "Content-Type": "application/json", Prefer: "return=representation" }, body: data === undefined ? undefined : JSON.stringify(data) });
      if (!response.ok) throw new Error("Push database operation failed");
      const text = await response.text(); return text ? JSON.parse(text) : null;
    };
    const body = await request.json();
    const id = body?.record?.id;
    if (id !== undefined && !/^[0-9a-f-]{36}$/.test(id)) return new Response("Invalid event", { status: 400 });
    const pending = await db("/notifications?push_sent_at=is.null&select=id,user_id,actor_id,message_id,read_at,kind&order=created_at&limit=25" + (id ? `&id=eq.${id}` : ""));
    const bearer = await accessToken(account);
    let delivered = 0;
    for (const n of pending) {
      if (!await db("/rpc/picgram_claim_push", "POST", { notification_id: n.id })) continue;
      const devices = await db(`/push_devices?user_id=eq.${n.user_id}&select=token`);
      // Resolve the actor and the exact message from trusted rows, never from the webhook body.
      const [actors, messages] = await Promise.all([
        db(`/users?id=eq.${n.actor_id}&select=display_name,username&limit=1`),
        n.kind === "message" && n.message_id
          ? db(`/messages?id=eq.${n.message_id}&sender_id=eq.${n.actor_id}&recipient_id=eq.${n.user_id}&select=content&limit=1`)
          : Promise.resolve([]),
      ]);
      const actor = actors[0];
      const actorName = actor?.display_name?.trim() || actor?.username?.trim() || "Alguien";
      // Keep the entire FCM payload comfortably below its 4096-byte limit, including emoji.
      const preview = Array.from(messages[0]?.content?.trim() || "").slice(0, 500).join("");
      let success = true;
      if (!n.read_at) for (const device of devices) {
        const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
          method: "POST", headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
          body: JSON.stringify({ message: { token: device.token, data: { notification_id: n.id, user_id: n.user_id, kind: n.kind,
            actor_id: n.actor_id, actor_name: actorName, message_preview: preview },
            android: { priority: "high", ttl: "86400s" } } }),
        });
        if (!response.ok) {
          const failure = await response.json();
          const invalid = failure?.error?.details?.some((e: { errorCode?: string }) => e.errorCode === "UNREGISTERED");
          if (invalid) await db(`/push_devices?token=eq.${encodeURIComponent(device.token)}`, "DELETE");
          else success = false;
        }
      }
      if (success) {
        await db(`/notifications?id=eq.${n.id}`, "PATCH", { push_sent_at: new Date().toISOString(), push_lease_until: null });
        delivered++;
      }
      // A periodic retry reclaims failed / interrupted leases after two minutes.
    }
    return Response.json({ delivered });
  } catch (_error) { return new Response("Push delivery failed; inspect server configuration", { status: 503 }); }
});
