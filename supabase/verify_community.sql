-- Read-only. Run in SQL Editor after rollout; no personal values are returned.
select table_name, column_name, data_type from information_schema.columns
where table_schema = 'public' and (
    (table_name = 'users' and column_name in ('display_name','bio','is_private')) or
    table_name in ('personal_details','follow_requests','stories','messages','notifications','push_devices'))
order by table_name,ordinal_position;

select relname as table_name,relrowsecurity as rls_enabled from pg_class
where relnamespace = 'public'::regnamespace and relname in
('users','posts','likes','comments','follows','personal_details','follow_requests','stories','messages','notifications','push_devices');

select id,public,file_size_limit from storage.buckets where id = 'picgram-posts';
select schemaname,tablename,policyname,permissive,cmd from pg_policies
where (schemaname='public' and policyname like 'picgram_private%')
   or (schemaname='storage' and policyname in ('picgram_media_read_guard','picgram_media_maintenance'));
-- Expect read_guard present, maintenance absent after 007.
select tablename from pg_publication_tables where pubname='supabase_realtime' and schemaname='public';
select tgname,tgenabled from pg_trigger where not tgisinternal
and tgrelid in ('public.stories'::regclass,'public.messages'::regclass,'public.notifications'::regclass,
               'public.follows'::regclass,'public.follow_requests'::regclass,'public.likes'::regclass,'public.comments'::regclass);
select has_function_privilege('anon','public.picgram_follow(uuid,boolean)','EXECUTE') as anon_follow_rpc,
       has_function_privilege('authenticated','public.picgram_follow(uuid,boolean)','EXECUTE') as authenticated_follow_rpc,
       has_function_privilege('authenticated','public.picgram_claim_push(uuid)','EXECUTE') as client_push_claim;
-- Expected: false, true, false.
select count(*) as legacy_or_external_post_media_needing_review from public.posts
where media_url <> '' and split_part(media_url,'?',1) not like '%/picgram-posts/posts/' || user_id::text || '/%';
-- Review nonzero results before enabling privacy; do not silently relax file authorization.
