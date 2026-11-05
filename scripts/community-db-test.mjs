// Real PostgreSQL in WASM, with mocked Supabase auth/storage schemas. Never contacts production.
import { PGlite } from '../build/security-tests/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
let passed = 0;
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const c = '00000000-0000-0000-0000-000000000003';
const p = '10000000-0000-0000-0000-000000000001';
async function check(label, action) { await action(); passed++; console.log(`PASS ${label}`); }
async function as(id) { await db.exec(`reset role; set request.jwt.claim.sub = '${id}'; set role authenticated;`); }
async function root() { await db.exec('reset role'); }
async function scalar(sql) { return Object.values((await db.query(sql)).rows[0])[0]; }
async function denied(sql) { await assert.rejects(() => db.exec(sql)); }
await db.exec(`
 create role anon; create role authenticated; create role service_role bypassrls;
 create schema auth; create schema storage;
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth, public, storage to anon, authenticated;
 grant execute on function auth.uid() to anon,authenticated;
 create table auth.users(id uuid primary key,email text,raw_user_meta_data jsonb default '{}');
 create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint);
 create table storage.objects(id uuid default gen_random_uuid(),bucket_id text,name text);
 alter table storage.objects enable row level security;
 grant select,insert,update,delete on storage.objects to authenticated;
`);
for (const file of ['001_picgram_integrity','002_posts_require_media','003_user_names_search','004_registration_display_name',
    '005_profiles_privacy','006_stories_messages_notifications','007_private_media','008_push_delivery']) {
  await db.exec(await readFile(new URL(`../supabase/migrations/${file}.sql`,import.meta.url),'utf8'));
  console.log(`MIGRATED ${file}`);
}
await db.exec(`insert into auth.users(id,email,raw_user_meta_data) values
 ('${a}','a@test.local','{"username":"ana","display_name":"Ana López"}'),
 ('${b}','b@test.local','{"username":"bea","display_name":"Beatriz"}'),
 ('${c}','c@test.local','{"username":"carlos","display_name":"Carlos"}');`);
// A permissive legacy SELECT must not defeat the new restrictive byte policy.
await db.exec(`create policy legacy_public_read on storage.objects for select to public using(true);
 insert into storage.objects(bucket_id,name) values('picgram-posts','posts/${a}/photo.jpg');`);
await as(a);
await check('registration metadata creates name', async () => assert.equal(await scalar(`select display_name from public.users where id='${a}'`),'Ana López'));
await db.exec(`insert into public.posts(id,user_id,user_email,username,media_url,timestamp) values('${p}','${a}','a@test.local','ana','https://local/storage/v1/object/public/picgram-posts/posts/${a}/photo.jpg',1)`);
await db.exec(`select public.picgram_save_profile('Ana nueva','Biografía',true,'123','2000-01-01','Lima')`);
await as(b);
await check('private post invisible to nonfollower', async () => assert.equal(await scalar('select count(*) from public.posts'),0));
await check('private media denied to nonfollower', async () => assert.equal(await scalar(`select public.picgram_can_read_media('posts/${a}/photo.jpg')`),false));
await check('legacy broad storage SELECT cannot bypass privacy', async () => assert.equal(await scalar('select count(*) from storage.objects'),0));
await check('email is not public', async () => await denied('select email from public.users'));
await check('old author emails are not exposed in posts', async () => await denied('select user_email from public.posts'));
await check('wildcards in storage paths never match other files', async () => assert.equal(await scalar(`select public.picgram_can_read_media('posts/%/photo.jpg')`),false));
await check('personal details invisible to other account', async () => assert.equal(await scalar('select count(*) from public.personal_details'),0));
await check('direct following bypass blocked', async () => await denied(`insert into public.follows(follower_id,following_id) values('${b}','${a}')`));
await check('following private creates request only', async () => assert.equal(await scalar(`select public.picgram_follow('${a}')`),'requested'));
await db.exec(`select public.picgram_follow('${a}')`);
await check('request retry is idempotent', async () => assert.equal(await scalar('select count(*) from public.follow_requests'),1));
await check('request does not grant access', async () => assert.equal(await scalar('select count(*) from public.posts'),0));
await as(c);
await check('third party cannot accept a request', async () => { await db.exec(`select public.picgram_resolve_request('${b}',true)`); assert.equal(await scalar('select count(*) from public.follows'),0); });
await as(a);
await db.exec(`select public.picgram_resolve_request('${b}',true)`);
await as(b);
await check('acceptance grants post access', async () => assert.equal(await scalar('select count(*) from public.posts'),1));
await check('acceptance grants file access', async () => assert.equal(await scalar(`select public.picgram_can_read_media('posts/${a}/photo.jpg')`),true));
await check('approved follower can select authorized storage object', async () => assert.equal(await scalar('select count(*) from storage.objects'),1));
await db.exec(`insert into public.likes(post_id,user_id) values('${p}','${b}')`);
await db.exec(`insert into public.comments(post_id,user_id,username,content,timestamp) values('${p}','${b}','bea','Hola',1)`);
await as(a);
await check('follow like comment notifications generated', async () => assert.equal(await scalar(`select count(*) from public.notifications where kind in ('follow','like','comment')`),3));
await db.exec(`insert into public.stories(user_id,media_url,media_type,created_at,expires_at)
 values('${a}','https://local/storage/v1/object/public/picgram-posts/stories/${a}/s.jpg','image','2000-01-01','2100-01-01')`);
await check('story server overrides forged dates to 24h', async () => assert.equal(await scalar(`select expires_at = created_at + interval '24 hours' and created_at > '2020-01-01' from public.stories`),true));
await as(b);
await check('followed account story visible', async () => assert.equal(await scalar('select count(*) from public.stories'),1));
await as(c);
await check('unfollowed story invisible', async () => assert.equal(await scalar('select count(*) from public.stories'),0));
await root();
await db.exec(`update public.stories set created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour'`);
await as(b);
await check('expired story invisible without scheduler', async () => assert.equal(await scalar('select count(*) from public.stories'),0));
await check('expired story bytes denied', async () => assert.equal(await scalar(`select public.picgram_can_read_media('stories/${a}/s.jpg')`),false));
await db.exec(`insert into public.messages(id,sender_id,recipient_id,content,read_at) values('${p}','${b}','${a}','Mensaje','2100-01-01')`);
await check('sender cannot forge read receipt', async () => assert.equal(await scalar('select read_at is null from public.messages'),true));
await as(c);
await check('third party cannot read messages', async () => assert.equal(await scalar('select count(*) from public.messages'),0));
await check('third party cannot forge sender', async () => await denied(`insert into public.messages(sender_id,recipient_id,content) values('${b}','${a}','fake')`));
await as(a);
await check('conversation unread count', async () => assert.equal(Number(await scalar('select unread from public.picgram_conversations()')),1));
await check('message history is visible to recipient', async () => assert.equal((await db.query(`select * from public.picgram_messages('${b}')`)).rows.length,1));
await db.exec(`select public.picgram_mark_messages('${b}',now())`);
await check('read acknowledgement clears unread', async () => assert.equal(Number(await scalar('select unread from public.picgram_conversations()')),0));
await check('clients cannot fabricate notifications', async () => await denied(`insert into public.notifications(user_id,actor_id,kind) values('${b}','${a}','follow')`));
await check('clients cannot claim server push deliveries', async () => await denied(`select public.picgram_claim_push('${p}')`));
await as(b);
await db.exec(`select public.picgram_follow('${a}',true)`);
await check('unfollow revokes private access', async () => assert.equal(await scalar('select count(*) from public.posts'),0));
await check('cannot like a hidden private post', async () => await denied(`insert into public.likes(post_id,user_id) values('${p}','${b}')`));
await check('cannot comment on a hidden private post', async () => await denied(`insert into public.comments(post_id,user_id,username,content,timestamp) values('${p}','${b}','bea','hidden',1)`));
await db.exec(`select public.picgram_follow('${a}')`);
await as(a);
await db.exec(`select public.picgram_resolve_request('${b}',false)`);
await check('rejection removes request', async () => assert.equal(await scalar('select count(*) from public.follow_requests'),0));
await as(b); await db.exec(`select public.picgram_follow('${a}')`);
await as(a); await db.exec(`select public.picgram_save_profile('Ana nueva','Pública',false,'123',null,'Lima')`);
await check('switch to public accepts pending request', async () => assert.equal(await scalar(`select count(*) from public.follows where follower_id='${b}' and following_id='${a}'`),1));
await as(c);
await db.exec(`select public.picgram_save_profile('Carlos','Privado',true,'',null,'');
 insert into public.posts(user_id,user_email,username,media_url,timestamp) values('${c}','c@test.local','carlos','https://local/storage/v1/object/public/picgram-posts/posts/${c}/secret.jpg',2)`);
await as(b);
await db.exec(`insert into public.posts(user_id,user_email,username,media_url,timestamp) values('${b}','b@test.local','bea','https://local/storage/v1/object/public/picgram-posts/posts/${c}/secret.jpg',3)`);
await check('referencing someone else private path cannot make its bytes public', async () => assert.equal(await scalar(`select public.picgram_can_read_media('posts/${c}/secret.jpg')`),false));
await check('other account cannot update personal details', async () => assert.equal((await db.query(
 `update public.personal_details set phone='changed' where user_id='${a}' returning user_id`)).rows.length,0));
await as(a);
await check('invalid profile does not partially change public fields', async () => {
  await denied(`select public.picgram_save_profile('Changed','Bio',false,'',current_date+1,'')`);
  assert.equal(await scalar(`select display_name from public.users where id='${a}'`),'Ana nueva');
});
await db.exec(`insert into public.messages(sender_id,recipient_id,content) select '${a}','${b}','history ' || i from generate_series(1,61) as i`);
await db.exec(`select public.picgram_mark_messages('${b}',now())`);
await check('sender cannot forge peer read receipts', async () => assert.equal(Number(await scalar(`select count(*) from public.messages where sender_id='${a}' and read_at is null`)),61));
await as(b);
await check('keyset pagination preserves every message without duplicates', async () => {
  const first = (await db.query(`select * from public.picgram_messages('${a}')`)).rows;
  assert.equal(first.length,50);
  const last = first[first.length-1];
  const older = (await db.query('select * from public.picgram_messages($1,$2,$3)',[a,last.created_at,last.id])).rows;
  assert.equal(older.length,12);
  assert.equal(new Set([...first,...older].map(row => row.id)).size,62);
});
await as(c);
await check('message RPC cannot expose someone else conversation', async () => assert.equal((await db.query(`select * from public.picgram_messages('${a}')`)).rows.length,0));
await as('');
await check('missing authenticated identity grants no profile access', async () => assert.equal(await scalar('select count(*) from public.users'),0));
await root();
await check('media bucket is not public', async () => assert.equal(await scalar(`select public from storage.buckets where id='picgram-posts'`),false));
await db.close();
console.log(`${passed} PostgreSQL security / integration checks passed`);
