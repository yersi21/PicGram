begin;
create table public.story_views (
    story_id uuid not null references public.stories(id) on delete cascade,
    viewer_id uuid not null references auth.users(id) on delete cascade,
    viewed_at timestamptz not null default now(),
    primary key(story_id, viewer_id)
);
alter table public.story_views enable row level security;
grant select on public.story_views to authenticated;
create policy story_views_author on public.story_views for select to authenticated
using(exists(select 1 from public.stories s where s.id = story_id and s.user_id = auth.uid()));

-- Identity and first-view time are assigned by the server; clients cannot forge them.
create function public.picgram_record_story_view(target_story uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare author uuid;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    select s.user_id into author from public.stories s
    where s.id = target_story and s.expires_at > now() and public.picgram_can_view(s.user_id)
        and (s.user_id = auth.uid() or exists(select 1 from public.follows f
            where f.follower_id = auth.uid() and f.following_id = s.user_id));
    if author is null then raise exception 'Story unavailable'; end if;
    if author = auth.uid() then return; end if;
    insert into public.story_views(story_id, viewer_id) values(target_story, auth.uid()) on conflict do nothing;
end $$;
revoke all on function public.picgram_record_story_view(uuid) from public, anon;
grant execute on function public.picgram_record_story_view(uuid) to authenticated;
notify pgrst, 'reload schema';
commit;
