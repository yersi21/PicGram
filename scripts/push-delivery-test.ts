// No network permission is granted to this test. All HTTP endpoints are mocked.
let handler: (request: Request) => Promise<Response>;
Deno.serve = ((callback: typeof handler) => { handler = callback; return {}; }) as unknown as typeof Deno.serve;
const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
  publicExponent: new Uint8Array([1,0,1]), hash: "SHA-256" }, true, ["sign","verify"]);
const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
const pem = "-----BEGIN PRIVATE KEY-----\n" + btoa(String.fromCharCode(...pkcs8)) + "\n-----END PRIVATE KEY-----";
Deno.env.set("PICGRAM_PUSH_HOOK_SECRET","test-secret");
Deno.env.set("SUPABASE_URL","https://test.invalid");
Deno.env.set("SUPABASE_SERVICE_ROLE_KEY","test-service-key");
Deno.env.set("FCM_SERVICE_ACCOUNT",JSON.stringify({ client_email:"test@test.invalid",project_id:"test-project",private_key:pem }));
await import("../supabase/functions/deliver-push/index.ts");
const id = "10000000-0000-0000-0000-000000000001";
let calls: {url: string; method: string; body: any}[] = [];
let mode = "success";
globalThis.fetch = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
  const url = String(input);
  const body = typeof init?.body === "string" ? JSON.parse(init.body) : null;
  calls.push({url,method:init?.method ?? "GET",body});
  if (url === "https://oauth2.googleapis.com/token") return Response.json({access_token:"test-access"});
  if (url.includes("/notifications?push_sent_at")) return Response.json([{id,user_id:"recipient-from-db",read_at:mode === "read" ? "now" : null}]);
  if (url.includes("/rpc/picgram_claim_push")) return Response.json(mode !== "claimed");
  if (url.includes("/push_devices?user_id=")) return Response.json([{token:"test-device"}]);
  if (url.includes("fcm.googleapis.com")) {
    if (mode === "temporary") return Response.json({error:{status:"UNAVAILABLE"}},{status:503});
    if (mode === "invalid") return Response.json({error:{details:[{errorCode:"UNREGISTERED"}]}},{status:404});
    return Response.json({name:"test-delivery"});
  }
  if (url.includes("/notifications?id=") || url.includes("/push_devices?token=")) return Response.json([]);
  throw new Error("Unexpected HTTP endpoint: " + url);
};
function assert(value: unknown, message: string) { if (!value) throw new Error(message); }
function request(secret = "test-secret", body: unknown = {record:{id,user_id:"forged-user"}}) {
  return new Request("https://test.invalid/function", {method:"POST",headers:{"x-picgram-hook-secret":secret},body:JSON.stringify(body)});
}
function reset(next = "success") { calls = []; mode = next; }
Deno.test("unauthorized webhook makes no database or FCM requests", async () => {
  reset(); const response = await handler(request("wrong"));
  assert(response.status === 401,"must reject bad secret"); assert(calls.length === 0,"unauthorized network access");
});
Deno.test("delivery uses authoritative DB recipient, not webhook recipient", async () => {
  reset(); const response = await handler(request());
  assert(response.ok,"delivery failed");
  const send = calls.find(c => c.url.includes("fcm.googleapis.com"));
  assert(send?.body.message.data.user_id === "recipient-from-db","forged recipient accepted");
  assert(send?.body.message.notification === undefined,"sensitive background notification leaked");
  assert(calls.some(c => c.method === "PATCH" && c.body.push_sent_at),"success was not recorded");
});
Deno.test("malformed event identifiers are rejected", async () => {
  reset(); const response = await handler(request("test-secret",{record:{id:"id&user_id=anything"}}));
  assert(response.status === 400,"identifier interpolation accepted"); assert(calls.length === 0,"unexpected network request");
});
Deno.test("temporary FCM failures are not marked delivered", async () => {
  reset("temporary"); await handler(request());
  assert(!calls.some(c => c.method === "PATCH"),"failed delivery was acknowledged");
});
Deno.test("invalid device tokens are removed", async () => {
  reset("invalid"); await handler(request());
  assert(calls.some(c => c.method === "DELETE" && c.url.includes("/push_devices?token=")),"invalid token kept");
});
Deno.test("duplicate claimed events do not send a second push", async () => {
  reset("claimed"); await handler(request());
  assert(!calls.some(c => c.url.includes("fcm.googleapis.com")),"duplicate push sent");
});
Deno.test("already read activity is not pushed", async () => {
  reset("read"); await handler(request());
  assert(!calls.some(c => c.url.includes("fcm.googleapis.com")),"read event was pushed");
});
