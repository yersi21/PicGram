begin;
create table public.story_likes (
    story_id uuid not null references public.stories(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    primary key(story_id, user_id)
);
alter table public.story_likes enable row level security;
grant select, insert, delete on public.story_likes to authenticated;
-- Story SELECT policies enforce approved following and server-side expiration.
-- Only the caller's reactions are returned, so the client can restore its heart state.
create policy story_likes_read on public.story_likes for select to authenticated
using(user_id = auth.uid() and exists(select 1 from public.stories s where s.id = story_id));
create policy story_likes_insert on public.story_likes for insert to authenticated
with check(user_id = auth.uid() and exists(
    select 1 from public.stories s where s.id = story_id and s.user_id <> auth.uid()));
create policy story_likes_delete on public.story_likes for delete to authenticated
using(user_id = auth.uid() and exists(select 1 from public.stories s where s.id = story_id));
notify pgrst, 'reload schema';
commit;
