begin;
alter table public.notifications add column push_sent_at timestamptz;
alter table public.notifications add column push_lease_until timestamptz;
create function public.picgram_claim_push(notification_id uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
    update public.notifications set push_lease_until = now() + interval '2 minutes'
    where id = notification_id and push_sent_at is null and (push_lease_until is null or push_lease_until < now());
    return found;
end $$;
revoke all on function public.picgram_claim_push(uuid) from public,anon,authenticated;
grant execute on function public.picgram_claim_push(uuid) to service_role;
create index notifications_pending_push on public.notifications(created_at) where push_sent_at is null;
notify pgrst, 'reload schema';
commit;
