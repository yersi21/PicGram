-- Apply after 001..004. Review and deploy with the matching Android build.
begin;
-- Fail closed during the multi-migration rollout; 007 removes this temporary gate.
update storage.buckets set public = false where id = 'picgram-posts';
create policy picgram_media_maintenance on storage.objects as restrictive for select to public
using(bucket_id <> 'picgram-posts');
alter table public.users add column if not exists bio text not null default '';
alter table public.users add column if not exists is_private boolean not null default false;
create table if not exists public.personal_details (
    user_id uuid primary key references auth.users(id) on delete cascade,
    phone text not null default '' check (char_length(phone) <= 30),
    birth_date date,
    location text not null default '' check (char_length(location) <= 120)
);
create table if not exists public.follow_requests (
    requester_id uuid references auth.users(id) on delete cascade,
    target_id uuid references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key(requester_id, target_id), check(requester_id <> target_id)
);
alter table public.personal_details enable row level security;
alter table public.follow_requests enable row level security;
grant select, insert, update, delete on public.personal_details to authenticated;
grant select on public.follow_requests to authenticated;
create policy personal_owner on public.personal_details for all to authenticated
using(user_id = auth.uid()) with check(user_id = auth.uid());
create policy request_participant on public.follow_requests for select to authenticated
using(requester_id = auth.uid() or target_id = auth.uid());

-- Definer helpers avoid recursive policies. They only answer for the caller.
create or replace function public.picgram_can_view(author_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select auth.uid() is not null and exists (
        select 1 from public.users u where u.id = author_id and (
            u.id = auth.uid() or not u.is_private or exists (
                select 1 from public.follows f where f.follower_id = auth.uid() and f.following_id = author_id
            )
        )
    );
$$;
create or replace function public.picgram_can_view_post(target_post uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists(select 1 from public.posts p where p.id = target_post and public.picgram_can_view(p.user_id));
$$;
revoke all on function public.picgram_can_view(uuid), public.picgram_can_view_post(uuid) from public, anon;
grant execute on function public.picgram_can_view(uuid), public.picgram_can_view_post(uuid) to authenticated;

-- Restrictive policies protect data even if older permissive policies remain.
create policy picgram_private_posts on public.posts as restrictive for select to authenticated
using(public.picgram_can_view(user_id));
create policy picgram_private_likes on public.likes as restrictive for all to authenticated
using(public.picgram_can_view_post(post_id)) with check(public.picgram_can_view_post(post_id));
create policy picgram_private_comments on public.comments as restrictive for all to authenticated
using(public.picgram_can_view_post(post_id)) with check(public.picgram_can_view_post(post_id));

-- Direct client inserts must never bypass approval. Functions below own writes.
create policy picgram_follow_approval on public.follows as restrictive for insert to authenticated
with check(false);
create policy picgram_follow_no_update on public.follows as restrictive for update to authenticated
using(false) with check(false);

create or replace function public.picgram_follow(target_id uuid, cancel boolean default false)
returns text language plpgsql security definer set search_path = '' as $$
declare private_account boolean;
begin
    if auth.uid() is null or target_id = auth.uid() then raise exception 'Invalid follower'; end if;
    -- Serialize account privacy changes, concurrent requests and accept/reject.
    select is_private into strict private_account from public.users where id = target_id for update;
    if cancel then
        delete from public.follow_requests r where r.requester_id = auth.uid() and r.target_id = picgram_follow.target_id;
        delete from public.follows where follower_id = auth.uid() and following_id = target_id;
        return 'none';
    end if;
    if exists(select 1 from public.follows where follower_id = auth.uid() and following_id = target_id) then return 'following'; end if;
    if private_account then
        insert into public.follow_requests(requester_id,target_id) values(auth.uid(),target_id) on conflict do nothing;
        return 'requested';
    else
        insert into public.follows(follower_id,following_id) values(auth.uid(),target_id) on conflict do nothing;
        return 'following';
    end if;
end $$;
create or replace function public.picgram_resolve_request(requester uuid, accept boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    perform 1 from public.users where id = auth.uid() for update;
    if not exists(select 1 from public.follow_requests where requester_id = requester and target_id = auth.uid()) then return; end if;
    if accept then insert into public.follows(follower_id,following_id) values(requester,auth.uid()) on conflict do nothing; end if;
    delete from public.follow_requests where requester_id = requester and target_id = auth.uid();
end $$;
create or replace function public.picgram_save_profile(full_name text, biography text, private_account boolean,
    phone_number text, birthday date, city text)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if char_length(btrim(full_name)) not between 2 and 80 or full_name ~ '[[:cntrl:]]'
        or char_length(biography) > 500 or char_length(phone_number) > 30 or char_length(city) > 120
        or birthday > current_date then raise exception 'Invalid profile'; end if;
    update public.users set display_name = btrim(full_name), bio = biography, is_private = private_account where id = auth.uid();
    if not found then raise exception 'Profile missing'; end if;
    insert into public.personal_details(user_id,phone,birth_date,location) values(auth.uid(),phone_number,birthday,city)
    on conflict(user_id) do update set phone = excluded.phone, birth_date = excluded.birth_date, location = excluded.location;
    -- Switching to public accepts outstanding requests atomically.
    if not private_account then
        insert into public.follows(follower_id,following_id)
        select requester_id,target_id from public.follow_requests where target_id = auth.uid() on conflict do nothing;
        delete from public.follow_requests where target_id = auth.uid();
    end if;
end $$;
revoke all on function public.picgram_follow(uuid,boolean), public.picgram_resolve_request(uuid,boolean),
    public.picgram_save_profile(text,text,boolean,text,date,text) from public, anon;
grant execute on function public.picgram_follow(uuid,boolean), public.picgram_resolve_request(uuid,boolean),
    public.picgram_save_profile(text,text,boolean,text,date,text) to authenticated;

-- Do not expose email / uid through users?select=* to other authenticated users.
revoke select on public.users from anon, authenticated;
grant select(id,username,avatar_url,display_name,bio,is_private,created_at) on public.users to authenticated;
revoke select on public.posts from anon,authenticated;
grant select(id,user_id,username,caption,media_url,media_type,timestamp) on public.posts to authenticated;
notify pgrst, 'reload schema';
commit;
