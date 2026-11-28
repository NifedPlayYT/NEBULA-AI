delete from public.lorebook_entries older
using public.lorebook_entries newer
where older.character_id = newer.character_id
  and older.title = newer.title
  and older.ctid < newer.ctid;

create unique index if not exists lorebook_character_title_unique
  on public.lorebook_entries(character_id, title);
