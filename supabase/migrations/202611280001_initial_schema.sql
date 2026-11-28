create extension if not exists pgcrypto;
create extension if not exists vector with schema extensions;

create table public.characters (
 id uuid primary key default gen_random_uuid(),
 creator_id uuid references auth.users(id) on delete cascade,
 name text not null check(char_length(name) between 1 and 80),
 avatar_url text not null default '', cover_url text not null default '',
 gender_pronouns text not null default 'they/them',
 universe_tag text not null default 'other', relationship_dynamic text not null default 'adventure',
 personality text not null default '', backstory text not null default '', speech_style text not null default '',
 greeting text not null default '', example_dialogues jsonb not null default '[]'::jsonb,
 is_public boolean not null default false, created_at timestamptz not null default now()
);
create table public.user_personas (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 name text not null, gender_pronouns text not null default 'they/them', avatar_url text not null default '',
 description text not null default '', created_at timestamptz not null default now()
);
create table public.chat_branches (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 character_id uuid not null references public.characters(id) on delete cascade,
 parent_branch_id uuid references public.chat_branches(id) on delete set null,
 title text not null default 'Новая история', mode text not null default 'roleplay'
 check(mode in ('chat','roleplay','adventure')), created_at timestamptz not null default now()
);
create table public.messages (
 id uuid primary key default gen_random_uuid(), branch_id uuid not null references public.chat_branches(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 role text not null check(role in ('user','assistant')), content text not null check(char_length(content)<=12000),
 variant_group uuid, variant_index int not null default 0, selected boolean not null default true,
 edited_at timestamptz, created_at timestamptz not null default now()
);
create index messages_branch_created_idx on public.messages(branch_id,created_at desc);
create index messages_variant_idx on public.messages(variant_group,variant_index);
create table public.conversation_summaries (
 id uuid primary key default gen_random_uuid(), branch_id uuid not null unique references public.chat_branches(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade, summary text not null default '',
 summarized_message_count int not null default 0, updated_at timestamptz not null default now()
);
create table public.lorebook_entries (
 id uuid primary key default gen_random_uuid(), character_id uuid not null references public.characters(id) on delete cascade,
 title text not null, content text not null, keywords text[] not null default '{}', priority int not null default 0,
 enabled boolean not null default true
);
create index lorebook_character_idx on public.lorebook_entries(character_id,enabled,priority desc);
create table public.long_term_memories (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 character_id uuid references public.characters(id) on delete cascade,
 branch_id uuid references public.chat_branches(id) on delete cascade,
 memory_type text not null check(memory_type in ('fact','event','inventory','relationship','preference')),
 content text not null, embedding extensions.vector(1024),
 importance smallint not null default 3 check(importance between 1 and 5), created_at timestamptz not null default now()
);
create index memories_user_idx on public.long_term_memories(user_id,character_id);
create index memories_embedding_idx on public.long_term_memories using hnsw(embedding extensions.vector_cosine_ops);
create table public.message_reports (
 id uuid primary key default gen_random_uuid(), reporter_id uuid not null references auth.users(id) on delete cascade,
 message_id uuid not null references public.messages(id) on delete cascade,
 reason text not null check(char_length(reason) between 3 and 500), created_at timestamptz not null default now(),
 unique(reporter_id,message_id)
);
create table public.user_entitlements (
 user_id uuid primary key references auth.users(id) on delete cascade,
 plan text not null default 'free' check(plan in ('free','plus')), crystals int not null default 0 check(crystals>=0),
 updated_at timestamptz not null default now()
);

create or replace function public.match_memories(q extensions.vector(1024), uid uuid, cid uuid, amount int default 6)
returns table(id uuid,content text,memory_type text,similarity float)
language sql stable security invoker set search_path=public,extensions as $$
 select m.id,m.content,m.memory_type,1-(m.embedding<=>q) as similarity
 from public.long_term_memories m where m.user_id=uid and (m.character_id is null or m.character_id=cid)
 and m.embedding is not null order by m.embedding<=>q limit least(greatest(amount,1),12);
$$;

alter table public.characters enable row level security;
alter table public.user_personas enable row level security;
alter table public.chat_branches enable row level security;
alter table public.messages enable row level security;
alter table public.conversation_summaries enable row level security;
alter table public.lorebook_entries enable row level security;
alter table public.long_term_memories enable row level security;
alter table public.message_reports enable row level security;
alter table public.user_entitlements enable row level security;

create policy "Public characters readable" on public.characters for select using(is_public or creator_id=auth.uid());
create policy "Own characters manageable" on public.characters for all using(creator_id=auth.uid()) with check(creator_id=auth.uid());
create policy "Own personas manageable" on public.user_personas for all using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Own branches manageable" on public.chat_branches for all using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Own messages manageable" on public.messages for all using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Own summaries readable" on public.conversation_summaries for select using(user_id=auth.uid());
create policy "Visible character lore readable" on public.lorebook_entries for select using(exists(select 1 from public.characters c where c.id=character_id and (c.is_public or c.creator_id=auth.uid())));
create policy "Own memories manageable" on public.long_term_memories for all using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Own reports insert" on public.message_reports for insert with check(reporter_id=auth.uid());
create policy "Own reports read" on public.message_reports for select using(reporter_id=auth.uid());
create policy "Own entitlement read" on public.user_entitlements for select using(user_id=auth.uid());

create or replace function public.on_user_created() returns trigger language plpgsql security definer set search_path=public as $$
begin insert into public.user_entitlements(user_id) values(new.id) on conflict do nothing; return new; end; $$;
create trigger auth_user_created after insert on auth.users for each row execute procedure public.on_user_created();

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('character-art','character-art',true,5242880,array['image/jpeg','image/png','image/webp']) on conflict(id) do nothing;
create policy "Character art public read" on storage.objects for select using(bucket_id='character-art');
create policy "Own art upload" on storage.objects for insert to authenticated with check(bucket_id='character-art' and (storage.foldername(name))[1]=auth.uid()::text);
create policy "Own art update" on storage.objects for update to authenticated using(bucket_id='character-art' and (storage.foldername(name))[1]=auth.uid()::text) with check(bucket_id='character-art' and (storage.foldername(name))[1]=auth.uid()::text);
