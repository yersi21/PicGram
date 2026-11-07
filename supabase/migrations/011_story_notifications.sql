begin;
alter table public.notifications drop constraint notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
    check(kind in ('follow','request','like','comment','message','story_like'));
alter table public.notifications add column story_id uuid references public.stories(id) on delete set null;

-- The recipient is always derived from the story, never supplied by the client.
create function public.picgram_notify_story_like() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into public.notifications(user_id,actor_id,kind,story_id)
    select s.user_id,new.user_id,'story_like',s.id from public.stories s
    where s.id = new.story_id and s.user_id <> new.user_id;
    return new;
end $$;
create trigger notify_story_like after insert on public.story_likes
for each row execute function public.picgram_notify_story_like();
revoke all on function public.picgram_notify_story_like() from public,anon,authenticated;
-- Messages already use notify_message from 006, including replies sent from stories.
-- Every INSERT in notifications enters the existing webhook / push delivery pipeline.
notify pgrst, 'reload schema';
commit;
