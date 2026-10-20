-- RF-06. Run in Supabase SQL Editor before installing the updated Android app.
-- Requires the existing users table and authenticated read / owner-write RLS
-- verified for PicGram (migration 001). No accounts, IDs or publications change.
begin;

alter table public.users
    add column if not exists display_name text not null default '';

-- Empty means the person has not set their name yet. Never infer a real name
-- from their username or email. Names are intentionally NOT unique.
do $$
begin
    if not exists (select 1 from pg_catalog.pg_constraint
                   where conrelid = 'public.users'::regclass
                     and conname = 'picgram_display_name_length') then
        alter table public.users add constraint picgram_display_name_length check (
            display_name = '' or (
                char_length(btrim(display_name)) between 2 and 80
                and display_name = btrim(display_name)
                and display_name !~ '[[:cntrl:]]'
            )
        );
    end if;
end $$;

-- Invoker privileges preserve users RLS. JSON parameters avoid filter-string
-- injection, and strpos treats %, _, quotes and parentheses as literal text.
create or replace function public.picgram_search_users(query_text text)
returns table (id uuid, username text, avatar_url text, display_name text)
language sql stable security invoker set search_path = '' as $$
    select u.id, u.username, u.avatar_url, u.display_name
    from public.users as u
    where (select auth.uid()) is not null
      and char_length(btrim(query_text)) between 1 and 80
      and (
          strpos(lower(u.username), lower(btrim(query_text))) > 0
          or strpos(lower(u.display_name), lower(btrim(query_text))) > 0
      )
    order by lower(u.username), u.id
    limit 50;
$$;

revoke all on function public.picgram_search_users(text) from public, anon;
grant execute on function public.picgram_search_users(text) to authenticated;

notify pgrst, 'reload schema';
commit;
