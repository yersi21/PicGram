begin;
create function public.picgram_server_now() returns bigint language sql stable as $$
    select floor(extract(epoch from now()) * 1000)::bigint;
$$;
revoke all on function public.picgram_server_now() from public,anon;
grant execute on function public.picgram_server_now() to authenticated;
create table public.stories (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    media_url text not null, media_type text not null check(media_type in ('image','video')),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default(now() + interval '24 hours'),
    check(expires_at = created_at + interval '24 hours')
);
create index stories_expiry on public.stories(expires_at,user_id);
create table public.messages (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references auth.users(id) on delete cascade,
    recipient_id uuid not null references auth.users(id) on delete cascade,
    content text not null check(char_length(btrim(content)) between 1 and 4000),
    created_at timestamptz not null default now(), read_at timestamptz,
    check(sender_id <> recipient_id)
);
create index messages_conversation on public.messages(sender_id,recipient_id,created_at desc,id);
create index messages_inbox on public.messages(recipient_id,created_at desc,id);
create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    actor_id uuid not null references auth.users(id) on delete cascade,
    kind text not null check(kind in ('follow','request','like','comment','message')),
    post_id uuid, message_id uuid,
    created_at timestamptz not null default now(), read_at timestamptz
);
create index notifications_inbox on public.notifications(user_id,created_at desc,id);
create table public.push_devices (
    token text primary key check(char_length(token) between 10 and 4096),
    user_id uuid not null references auth.users(id) on delete cascade,
    updated_at timestamptz not null default now()
);
alter table public.stories enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;
alter table public.push_devices enable row level security;
grant select,insert,delete on public.stories to authenticated;
grant select,insert on public.messages to authenticated;
grant select on public.notifications to authenticated;
grant select,delete on public.push_devices to authenticated;
create policy stories_read on public.stories for select to authenticated using (
    expires_at > now() and public.picgram_can_view(user_id) and (user_id = auth.uid() or exists (
        select 1 from public.follows where follower_id = auth.uid() and following_id = user_id))
);
create policy stories_write on public.stories for insert to authenticated with check(user_id = auth.uid());
create policy stories_delete on public.stories for delete to authenticated using(user_id = auth.uid());
create policy messages_read on public.messages for select to authenticated using(sender_id = auth.uid() or recipient_id = auth.uid());
create policy messages_send on public.messages for insert to authenticated with check(sender_id = auth.uid());
create policy notifications_read on public.notifications for select to authenticated using(user_id = auth.uid());
create policy devices_owner on public.push_devices for select to authenticated using(user_id = auth.uid());
create policy devices_remove on public.push_devices for delete to authenticated using(user_id = auth.uid());

-- Clients cannot forge dates, expiry, read receipts or notification recipients.
create function public.picgram_server_dates() returns trigger language plpgsql set search_path = '' as $$
begin
    new.created_at := now();
    if tg_table_name = 'stories' then new.expires_at := new.created_at + interval '24 hours';
    else new.read_at := null; end if;
    return new;
end $$;
create trigger stories_dates before insert on public.stories for each row execute function public.picgram_server_dates();
create trigger messages_dates before insert on public.messages for each row execute function public.picgram_server_dates();

create function public.picgram_mark_messages(peer uuid, through_time timestamptz) returns void
language sql security definer set search_path = '' as $$
    update public.messages set read_at = now() where recipient_id = auth.uid() and sender_id = peer
        and read_at is null and created_at <= through_time;
$$;
create function public.picgram_mark_notification(notification_id uuid) returns void
language sql security definer set search_path = '' as $$
    update public.notifications set read_at = now() where id = notification_id and user_id = auth.uid() and read_at is null;
$$;
create function public.picgram_register_device(device_token text) returns void
language sql security definer set search_path = '' as $$
    insert into public.push_devices(token,user_id) values(device_token,auth.uid())
    on conflict(token) do update set user_id = excluded.user_id, updated_at = now();
$$;
-- Keyset paging: history cannot truncate silently at the API's default row limit.
create function public.picgram_messages(peer uuid, before_time timestamptz default 'infinity', before_id uuid default 'ffffffff-ffff-ffff-ffff-ffffffffffff')
returns setof public.messages language sql stable security invoker set search_path = '' as $$
    select * from public.messages where ((sender_id = auth.uid() and recipient_id = peer)
        or (recipient_id = auth.uid() and sender_id = peer)) and (created_at,id) < (before_time,before_id)
    order by created_at desc,id desc limit 50;
$$;
create function public.picgram_conversations() returns table(peer_id uuid,last_content text,last_at timestamptz,unread bigint)
language sql stable security invoker set search_path = '' as $$
    with mine as (select *, case when sender_id = auth.uid() then recipient_id else sender_id end as peer
        from public.messages where sender_id = auth.uid() or recipient_id = auth.uid()),
    latest as (select distinct on(peer) peer,content,created_at,id from mine order by peer,created_at desc,id desc)
    select l.peer,l.content,l.created_at,(select count(*) from mine m where m.peer = l.peer and m.recipient_id = auth.uid() and m.read_at is null)
    from latest l order by l.created_at desc,l.id desc;
$$;

create function public.picgram_notify() returns trigger language plpgsql security definer set search_path = '' as $$
declare recipient uuid; actor uuid; kind text; pid uuid; mid uuid;
begin
    if tg_table_name = 'follows' then recipient := new.following_id; actor := new.follower_id; kind := 'follow';
    elsif tg_table_name = 'follow_requests' then recipient := new.target_id; actor := new.requester_id; kind := 'request';
    elsif tg_table_name = 'messages' then recipient := new.recipient_id; actor := new.sender_id; kind := 'message'; mid := new.id;
    else
        select user_id into recipient from public.posts where id = new.post_id;
        actor := new.user_id; pid := new.post_id;
        kind := case when tg_table_name = 'likes' then 'like' else 'comment' end;
    end if;
    if recipient is not null and recipient <> actor then
        insert into public.notifications(user_id,actor_id,kind,post_id,message_id) values(recipient,actor,kind,pid,mid);
    end if;
    return new;
end $$;
create trigger notify_follow after insert on public.follows for each row execute function public.picgram_notify();
create trigger notify_request after insert on public.follow_requests for each row execute function public.picgram_notify();
create trigger notify_like after insert on public.likes for each row execute function public.picgram_notify();
create trigger notify_comment after insert on public.comments for each row execute function public.picgram_notify();
create trigger notify_message after insert on public.messages for each row execute function public.picgram_notify();
revoke all on function public.picgram_notify(), public.picgram_server_dates() from public,anon,authenticated;
revoke all on function public.picgram_mark_messages(uuid,timestamptz),public.picgram_mark_notification(uuid),
    public.picgram_register_device(text),public.picgram_messages(uuid,timestamptz,uuid),public.picgram_conversations() from public,anon;
grant execute on function public.picgram_mark_messages(uuid,timestamptz),public.picgram_mark_notification(uuid),
    public.picgram_register_device(text),public.picgram_messages(uuid,timestamptz,uuid),public.picgram_conversations() to authenticated;
do $$ declare t text; begin
    foreach t in array array['messages','notifications','follow_requests','stories'] loop
        if exists(select 1 from pg_publication where pubname = 'supabase_realtime') and not exists (
            select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
        ) then execute format('alter publication supabase_realtime add table public.%I',t); end if;
    end loop;
end $$;
notify pgrst, 'reload schema';
commit;
