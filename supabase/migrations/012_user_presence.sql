begin;
create table public.user_presence (
    user_id uuid not null references auth.users(id) on delete cascade,
    device_id uuid not null,
    active boolean not null,
    last_seen_at timestamptz not null default now(),
    primary key(user_id, device_id)
);
alter table public.user_presence enable row level security;
-- Device identifiers are never exposed. Only aggregate status is returned below.
create function public.picgram_touch_presence(device uuid, online boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    insert into public.user_presence(user_id,device_id,active,last_seen_at)
    values(auth.uid(),device,online,now()) on conflict(user_id,device_id)
    do update set active = excluded.active,last_seen_at = now();
end $$;
create function public.picgram_presence(peer uuid)
returns table(online boolean,last_seen_at timestamptz,server_now timestamptz)
language sql stable security definer set search_path = '' as $$
    select coalesce(bool_or(p.active and p.last_seen_at > now() - interval '60 seconds'),false),
        max(p.last_seen_at),now()
    from public.user_presence p where p.user_id = peer and auth.uid() is not null;
$$;
revoke all on function public.picgram_touch_presence(uuid,boolean),public.picgram_presence(uuid) from public,anon;
grant execute on function public.picgram_touch_presence(uuid,boolean),public.picgram_presence(uuid) to authenticated;
notify pgrst, 'reload schema';
commit;
