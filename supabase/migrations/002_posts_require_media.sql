-- Optional server enforcement of the same rule as the Android client.
-- NOT VALID preserves existing text-only posts; new inserts/updates must include media.
begin;
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.posts'::regclass and conname = 'picgram_posts_require_media'
    ) then
        alter table public.posts add constraint picgram_posts_require_media
        check (
            media_url is not null and length(btrim(media_url)) > 0
            and media_type is not null and media_type in ('image', 'video')
        ) not valid;
    end if;
end $$;
commit;
