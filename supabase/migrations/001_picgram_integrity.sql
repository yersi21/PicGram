-- Review against your existing schema, then run in Supabase SQL Editor.
-- This migration does not delete posts, accounts or media. It fails atomically
-- if existing duplicates or incompatible column types need manual repair.
begin;

create table if not exists public.users (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null,
    username text not null,
    created_at bigint not null,
    avatar_url text not null default ''
);
create table if not exists public.posts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    user_email text not null,
    username text not null,
    caption text not null default '',
    media_url text not null default '',
    media_type text not null default 'image',
    timestamp bigint not null
);
create table if not exists public.likes (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.posts(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade
);
create table if not exists public.comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.posts(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    username text not null,
    content text not null,
    timestamp bigint not null
);
create table if not exists public.follows (
    id uuid primary key default gen_random_uuid(),
    follower_id uuid not null references public.users(id) on delete cascade,
    following_id uuid not null references public.users(id) on delete cascade,
    check (follower_id <> following_id)
);

alter table public.users add column if not exists avatar_url text not null default '';

-- UUID IDs are required for idempotent publication retries in the Android client.
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name in ('users', 'posts', 'comments')
          and column_name = 'id' and data_type <> 'uuid'
    ) then
        raise exception 'PicGram expects UUID IDs. Review the existing schema before migrating.';
    end if;
end $$;

create unique index if not exists picgram_likes_unique on public.likes(post_id, user_id);
create unique index if not exists picgram_follows_unique on public.follows(follower_id, following_id);
create index if not exists picgram_posts_author_date on public.posts(user_id, timestamp desc, id desc);
create index if not exists picgram_comments_post_date on public.comments(post_id, timestamp, id);
create index if not exists picgram_follows_target on public.follows(following_id);

-- Ensure child deletion works even when an older schema has non-cascading FKs.
create or replace function public.picgram_delete_post_children()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    delete from public.likes where post_id = old.id;
    delete from public.comments where post_id = old.id;
    return old;
end $$;
revoke all on function public.picgram_delete_post_children() from public;
drop trigger if exists picgram_delete_post_children on public.posts;
create trigger picgram_delete_post_children before delete on public.posts
for each row execute function public.picgram_delete_post_children();

-- A confirmed-email signup needs its profile even before the first login.
create or replace function public.picgram_create_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.users(id, email, username, created_at)
    values (new.id, coalesce(new.email, ''),
        coalesce(nullif(new.raw_user_meta_data->>'username', ''), split_part(new.email, '@', 1)),
        floor(extract(epoch from now()) * 1000)::bigint)
    on conflict (id) do nothing;
    return new;
end $$;
revoke all on function public.picgram_create_profile() from public;
drop trigger if exists picgram_create_profile on auth.users;
create trigger picgram_create_profile after insert on auth.users
for each row execute function public.picgram_create_profile();

-- Restrictive guards also apply when a pre-existing permissive policy is broad.
-- Install named policies only; unrelated policies and tables are preserved.
do $$
declare t text; owner_column text;
begin
    foreach t in array array['users', 'posts', 'likes', 'comments', 'follows'] loop
        owner_column := case when t = 'users' then 'id'
                             when t = 'follows' then 'follower_id' else 'user_id' end;
        execute format('alter table public.%I enable row level security', t);
        execute format('grant select, insert, update, delete on public.%I to authenticated', t);
        execute format('drop policy if exists picgram_read on public.%I', t);
        execute format('drop policy if exists picgram_write on public.%I', t);
        execute format('drop policy if exists picgram_read_guard on public.%I', t);
        execute format('drop policy if exists picgram_insert_guard on public.%I', t);
        execute format('drop policy if exists picgram_update_guard on public.%I', t);
        execute format('drop policy if exists picgram_delete_guard on public.%I', t);
        execute format('create policy picgram_read on public.%I for select to authenticated using (true)', t);
        execute format('create policy picgram_write on public.%I for all to authenticated using ((select auth.uid()) = %I) with check ((select auth.uid()) = %I)', t, owner_column, owner_column);
        execute format('create policy picgram_read_guard on public.%I as restrictive for select to public using ((select auth.uid()) is not null)', t);
        execute format('create policy picgram_insert_guard on public.%I as restrictive for insert to public with check ((select auth.uid()) = %I)', t, owner_column);
        execute format('create policy picgram_update_guard on public.%I as restrictive for update to public using ((select auth.uid()) = %I) with check ((select auth.uid()) = %I)', t, owner_column, owner_column);
        execute format('create policy picgram_delete_guard on public.%I as restrictive for delete to public using ((select auth.uid()) = %I)', t, owner_column);
    end loop;
end $$;

-- Media is public for reading, but writes must match the authenticated user's path.
insert into storage.buckets(id, name, public, file_size_limit)
values ('picgram-posts', 'picgram-posts', true, 52428800)
on conflict (id) do update set public = true, file_size_limit = excluded.file_size_limit;

create or replace function public.picgram_owns_media(object_name text)
returns boolean language sql stable security invoker set search_path = '' as $$
    select (select auth.uid()) is not null and (
        (split_part(object_name, '/', 1) in ('posts', 'avatars')
            and split_part(object_name, '/', 2) = (select auth.uid())::text)
        -- Allow owners to clean up legacy avatars uploaded by the old client.
        or object_name = 'avatars/' || (select auth.uid())::text || '.jpg'
    );
$$;

drop policy if exists picgram_media_write on storage.objects;
drop policy if exists picgram_media_insert_guard on storage.objects;
drop policy if exists picgram_media_update_guard on storage.objects;
drop policy if exists picgram_media_delete_guard on storage.objects;
create policy picgram_media_write on storage.objects for all to authenticated
using (bucket_id = 'picgram-posts' and public.picgram_owns_media(name))
with check (bucket_id = 'picgram-posts' and public.picgram_owns_media(name));
create policy picgram_media_insert_guard on storage.objects as restrictive for insert to public
with check (bucket_id <> 'picgram-posts' or public.picgram_owns_media(name));
create policy picgram_media_update_guard on storage.objects as restrictive for update to public
using (bucket_id <> 'picgram-posts' or public.picgram_owns_media(name))
with check (bucket_id <> 'picgram-posts' or public.picgram_owns_media(name));
create policy picgram_media_delete_guard on storage.objects as restrictive for delete to public
using (bucket_id <> 'picgram-posts' or public.picgram_owns_media(name));

commit;
