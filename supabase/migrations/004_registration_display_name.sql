-- Run after 003_user_names_search.sql, even if 003 was already applied.
-- Store the registration name before email confirmation / first login.
-- Existing accounts and names are preserved; legacy clients may omit the name.
begin;

do $$
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'users'
          and column_name = 'display_name'
    ) then
        raise exception 'Run 003_user_names_search.sql before migration 004.';
    end if;
end $$;

create or replace function public.picgram_create_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.users(id, email, username, created_at, display_name)
    values (new.id, coalesce(new.email, ''),
        coalesce(nullif(new.raw_user_meta_data->>'username', ''), split_part(new.email, '@', 1)),
        floor(extract(epoch from now()) * 1000)::bigint,
        btrim(coalesce(new.raw_user_meta_data->>'display_name', '')))
    on conflict (id) do nothing;
    return new;
end $$;

revoke all on function public.picgram_create_profile() from public, anon, authenticated;
-- Reuse the existing enabled AFTER INSERT trigger on auth.users.
notify pgrst, 'reload schema';
commit;
