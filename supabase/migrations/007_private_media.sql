-- SECURITY CHANGE: old app versions using public URLs must be upgraded.
-- No objects are deleted. Images already downloaded outside the app cannot be recalled.
begin;
update storage.buckets set public = false where id = 'picgram-posts';
create or replace function public.picgram_owns_media(object_name text) returns boolean
language sql stable set search_path = '' as $$
    select auth.uid() is not null and (
        (split_part(object_name,'/',1) in ('posts','avatars','stories') and split_part(object_name,'/',2) = auth.uid()::text)
        or object_name = 'avatars/' || auth.uid()::text || '.jpg');
$$;
create or replace function public.picgram_can_read_media(object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
    select auth.uid() is not null and (
        object_name like 'avatars/%'
        or (split_part(object_name,'/',1) = 'posts' and exists (
            select 1 from public.posts p where public.picgram_can_view(p.user_id)
                and p.user_id::text = split_part(object_name,'/',2)
                and right(split_part(p.media_url,'?',1),char_length('/picgram-posts/' || object_name)) = '/picgram-posts/' || object_name))
        or (split_part(object_name,'/',1) = 'stories' and exists (
            select 1 from public.stories s where s.expires_at > now() and public.picgram_can_view(s.user_id)
                and s.user_id::text = split_part(object_name,'/',2)
                and right(split_part(s.media_url,'?',1),char_length('/picgram-posts/' || object_name)) = '/picgram-posts/' || object_name
                and (s.user_id = auth.uid() or exists(select 1 from public.follows where follower_id = auth.uid() and following_id = s.user_id))))
        or (public.picgram_owns_media(object_name) and split_part(object_name,'/',1) = 'posts')
    );
$$;
revoke all on function public.picgram_can_read_media(text) from public,anon;
grant execute on function public.picgram_can_read_media(text) to authenticated;
create policy picgram_media_read on storage.objects for select to authenticated
using(bucket_id = 'picgram-posts' and public.picgram_can_read_media(name));
create policy picgram_media_read_guard on storage.objects as restrictive for select to public
using(bucket_id <> 'picgram-posts' or (auth.uid() is not null and public.picgram_can_read_media(name)));
drop policy picgram_media_maintenance on storage.objects;
notify pgrst, 'reload schema';
commit;
