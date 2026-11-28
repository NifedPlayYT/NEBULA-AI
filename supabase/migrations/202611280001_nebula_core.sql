create extension if not exists pgcrypto;
create schema if not exists extensions;
create extension if not exists vector with schema extensions;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null default 'NEBULA User' check (char_length(display_name) <= 60),
    safe_mode boolean not null default true check (safe_mode is true),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.player_personas (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null check (char_length(name) between 1 and 60),
    description text not null default '' check (char_length(description) <= 2000),
    is_default boolean not null default false,
    created_at timestamptz not null default now()
);

create unique index if not exists player_personas_one_default
    on public.player_personas(owner_id) where is_default;

create table if not exists public.characters (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid references auth.users(id) on delete cascade,
    name text not null check (char_length(name) between 1 and 80),
    creator text not null default '@nebula' check (char_length(creator) <= 80),
    bio text not null default '' check (char_length(bio) <= 4000),
    category text not null default 'OC' check (char_length(category) <= 40),
    greeting text not null default '' check (char_length(greeting) <= 1200),
    instructions text not null default '' check (char_length(instructions) <= 6000),
    examples jsonb not null default '[]'::jsonb check (jsonb_typeof(examples) = 'array'),
    tags text[] not null default '{}',
    age_rating text not null default '13+' check (age_rating = '13+'),
    is_public boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((owner_id is null and is_public) or owner_id is not null)
);

create index if not exists characters_public_category_idx
    on public.characters(is_public, category);

create table if not exists public.lorebook_entries (
    id uuid primary key default gen_random_uuid(),
    character_id uuid not null references public.characters(id) on delete cascade,
    owner_id uuid references auth.users(id) on delete cascade,
    title text not null check (char_length(title) between 1 and 100),
    triggers text[] not null default '{}',
    content text not null check (char_length(content) between 1 and 3000),
    priority integer not null default 50 check (priority between 0 and 100),
    is_enabled boolean not null default true,
    created_at timestamptz not null default now(),
    check (cardinality(triggers) <= 30)
);

create index if not exists lorebook_character_enabled_idx
    on public.lorebook_entries(character_id, is_enabled, priority desc);

create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    character_id uuid not null references public.characters(id) on delete cascade,
    persona_id uuid references public.player_personas(id) on delete set null,
    title text not null default 'Новый диалог' check (char_length(title) <= 120),
    mode text not null default 'literary' check (mode in ('plain', 'literary', 'adventure')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists conversations_owner_updated_idx
    on public.conversations(owner_id, updated_at desc);

create table if not exists public.conversation_branches (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    owner_id uuid not null references auth.users(id) on delete cascade,
    parent_branch_id uuid references public.conversation_branches(id) on delete set null,
    forked_from_message_id uuid,
    title text not null default 'Основная ветка' check (char_length(title) <= 100),
    created_at timestamptz not null default now()
);

create index if not exists conversation_branches_owner_idx
    on public.conversation_branches(owner_id, conversation_id, created_at);

create table if not exists public.chat_messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    branch_id uuid not null references public.conversation_branches(id) on delete cascade,
    owner_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('user', 'assistant')),
    content text not null default '' check (char_length(content) <= 24000),
    parent_message_id uuid references public.chat_messages(id) on delete set null,
    variant_group_id uuid,
    variant_index integer not null default 0 check (variant_index >= 0),
    sequence bigint not null check (sequence > 0),
    status text not null default 'complete' check (status in ('pending', 'complete', 'failed', 'cancelled')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (branch_id, sequence)
);

create index if not exists chat_messages_branch_sequence_idx
    on public.chat_messages(branch_id, sequence);

create index if not exists chat_messages_owner_created_idx
    on public.chat_messages(owner_id, created_at desc);

create table if not exists public.chat_summaries (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    branch_id uuid not null references public.conversation_branches(id) on delete cascade,
    owner_id uuid not null references auth.users(id) on delete cascade,
    summary text not null check (char_length(summary) <= 5000),
    up_to_sequence bigint not null check (up_to_sequence >= 0),
    updated_at timestamptz not null default now(),
    unique (branch_id)
);

create table if not exists public.memory_facts (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    conversation_id uuid references public.conversations(id) on delete cascade,
    character_id uuid references public.characters(id) on delete cascade,
    fact_type text not null check (fact_type in ('player_fact', 'inventory', 'event', 'relationship', 'preference', 'world_state')),
    content text not null check (char_length(content) between 1 and 1200),
    importance real not null default 0.5 check (importance between 0 and 1),
    dedupe_hash text not null,
    embedding extensions.vector(768) not null,
    source_message_ids uuid[] not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(owner_id, dedupe_hash)
);

create index if not exists memory_facts_owner_character_idx
    on public.memory_facts(owner_id, character_id, importance desc);

create index if not exists memory_facts_embedding_idx
    on public.memory_facts using hnsw (embedding extensions.vector_cosine_ops);

create table if not exists public.chat_usage_events (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    conversation_id uuid references public.conversations(id) on delete set null,
    created_at timestamptz not null default now()
);

create index if not exists chat_usage_owner_created_idx
    on public.chat_usage_events(owner_id, created_at desc);

create table if not exists public.user_reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references auth.users(id) on delete cascade,
    message_id uuid references public.chat_messages(id) on delete set null,
    reason text not null check (reason in ('unsafe', 'harassment', 'sexual_content', 'other')),
    details text not null default '' check (char_length(details) <= 1200),
    status text not null default 'open' check (status in ('open', 'reviewing', 'closed')),
    created_at timestamptz not null default now()
);

create table if not exists public.app_config (
    id smallint primary key default 1 check (id = 1),
    minimum_version_code integer not null default 1,
    safe_mode boolean not null default true check (safe_mode is true),
    safe_mode_setting_visible boolean not null default false check (safe_mode_setting_visible is false),
    rewarded_ads_enabled boolean not null default false,
    updated_at timestamptz not null default now()
);

insert into public.app_config(id)
values (1)
on conflict (id) do nothing;

create table if not exists public.user_entitlements (
    user_id uuid primary key references auth.users(id) on delete cascade,
    plan text not null default 'free' check (plan in ('free', 'plus')),
    store text check (store in ('google_play')),
    product_id text,
    valid_until timestamptz,
    updated_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    insert into public.profiles(id, display_name)
    values (
        new.id,
        left(coalesce(nullif(new.raw_user_meta_data ->> 'name', ''), 'NEBULA User'), 60)
    )
    on conflict (id) do nothing;

    insert into public.user_entitlements(user_id, plan)
    values (new.id, 'free')
    on conflict (user_id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created_nebula on auth.users;
create trigger on_auth_user_created_nebula
    after insert on auth.users
    for each row execute procedure public.handle_new_user();

drop trigger if exists profiles_updated_at on public.profiles;
create trigger profiles_updated_at before update on public.profiles
    for each row execute procedure public.set_updated_at();

drop trigger if exists characters_updated_at on public.characters;
create trigger characters_updated_at before update on public.characters
    for each row execute procedure public.set_updated_at();

drop trigger if exists conversations_updated_at on public.conversations;
create trigger conversations_updated_at before update on public.conversations
    for each row execute procedure public.set_updated_at();

drop trigger if exists chat_messages_updated_at on public.chat_messages;
create trigger chat_messages_updated_at before update on public.chat_messages
    for each row execute procedure public.set_updated_at();

drop trigger if exists memory_facts_updated_at on public.memory_facts;
create trigger memory_facts_updated_at before update on public.memory_facts
    for each row execute procedure public.set_updated_at();

alter table public.profiles enable row level security;
alter table public.player_personas enable row level security;
alter table public.characters enable row level security;
alter table public.lorebook_entries enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_branches enable row level security;
alter table public.chat_messages enable row level security;
alter table public.chat_summaries enable row level security;
alter table public.memory_facts enable row level security;
alter table public.user_reports enable row level security;
alter table public.chat_usage_events enable row level security;
alter table public.app_config enable row level security;
alter table public.user_entitlements enable row level security;

create policy "profiles_select_own" on public.profiles
    for select to authenticated using (id = auth.uid());
create policy "profiles_update_own_safe" on public.profiles
    for update to authenticated
    using (id = auth.uid())
    with check (id = auth.uid() and safe_mode is true);

create policy "personas_select_own" on public.player_personas
    for select to authenticated using (owner_id = auth.uid());
create policy "personas_insert_own" on public.player_personas
    for insert to authenticated with check (owner_id = auth.uid());
create policy "personas_update_own" on public.player_personas
    for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "personas_delete_own" on public.player_personas
    for delete to authenticated using (owner_id = auth.uid());

create policy "characters_read_visible" on public.characters
    for select to authenticated using (is_public or owner_id = auth.uid());
create policy "characters_insert_own_private" on public.characters
    for insert to authenticated with check (owner_id = auth.uid() and is_public = false and age_rating = '13+');
create policy "characters_update_own_private" on public.characters
    for update to authenticated using (owner_id = auth.uid())
    with check (owner_id = auth.uid() and is_public = false and age_rating = '13+');
create policy "characters_delete_own" on public.characters
    for delete to authenticated using (owner_id = auth.uid());

create policy "lore_read_visible" on public.lorebook_entries
    for select to authenticated using (
        owner_id = auth.uid()
        or exists (
            select 1 from public.characters c
            where c.id = character_id and c.is_public
        )
    );
create policy "lore_insert_own" on public.lorebook_entries
    for insert to authenticated with check (
        owner_id = auth.uid()
        and exists (select 1 from public.characters c where c.id = character_id and c.owner_id = auth.uid())
    );
create policy "lore_update_own" on public.lorebook_entries
    for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "lore_delete_own" on public.lorebook_entries
    for delete to authenticated using (owner_id = auth.uid());

create policy "conversations_select_own" on public.conversations
    for select to authenticated using (owner_id = auth.uid());
create policy "conversations_insert_own" on public.conversations
    for insert to authenticated with check (owner_id = auth.uid());
create policy "conversations_update_own" on public.conversations
    for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "conversations_delete_own" on public.conversations
    for delete to authenticated using (owner_id = auth.uid());

create policy "branches_select_own" on public.conversation_branches
    for select to authenticated using (owner_id = auth.uid());
create policy "branches_insert_own" on public.conversation_branches
    for insert to authenticated with check (
        owner_id = auth.uid()
        and exists (select 1 from public.conversations c where c.id = conversation_id and c.owner_id = auth.uid())
    );
create policy "branches_update_own" on public.conversation_branches
    for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "branches_delete_own" on public.conversation_branches
    for delete to authenticated using (owner_id = auth.uid());

create policy "messages_select_own" on public.chat_messages
    for select to authenticated using (owner_id = auth.uid());
create policy "messages_insert_own" on public.chat_messages
    for insert to authenticated with check (
        owner_id = auth.uid()
        and role = 'user'
        and status = 'complete'
        and exists (
            select 1 from public.conversation_branches b
            where b.id = branch_id and b.owner_id = auth.uid() and b.conversation_id = conversation_id
        )
    );
create policy "messages_update_own_user" on public.chat_messages
    for update to authenticated using (owner_id = auth.uid() and role = 'user')
    with check (owner_id = auth.uid() and role = 'user');
create policy "messages_delete_own" on public.chat_messages
    for delete to authenticated using (owner_id = auth.uid());

create policy "summaries_read_own" on public.chat_summaries
    for select to authenticated using (owner_id = auth.uid());

create policy "memory_read_own" on public.memory_facts
    for select to authenticated using (owner_id = auth.uid());

create policy "reports_insert_own" on public.user_reports
    for insert to authenticated with check (
        reporter_id = auth.uid()
        and (
            message_id is null
            or exists (select 1 from public.chat_messages m where m.id = message_id and m.owner_id = auth.uid())
        )
    );

create policy "usage_read_own" on public.chat_usage_events
    for select to authenticated using (owner_id = auth.uid());

create policy "config_read_authenticated" on public.app_config
    for select to authenticated using (true);

create policy "entitlements_read_own" on public.user_entitlements
    for select to authenticated using (user_id = auth.uid());

create or replace function public.create_conversation(
    p_character_id uuid,
    p_persona_id uuid default null,
    p_mode text default 'literary',
    p_title text default 'Новый диалог'
)
returns table(conversation_id uuid, branch_id uuid)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user_id uuid := auth.uid();
    v_conversation_id uuid := gen_random_uuid();
    v_branch_id uuid := gen_random_uuid();
begin
    if v_user_id is null then
        raise exception 'authentication_required' using errcode = '28000';
    end if;
    if p_mode not in ('plain', 'literary', 'adventure') then
        raise exception 'invalid_chat_mode' using errcode = '22023';
    end if;
    if not exists (
        select 1 from public.characters c
        where c.id = p_character_id and (c.is_public or c.owner_id = v_user_id)
    ) then
        raise exception 'character_not_found' using errcode = 'P0002';
    end if;
    if p_persona_id is not null and not exists (
        select 1 from public.player_personas p
        where p.id = p_persona_id and p.owner_id = v_user_id
    ) then
        raise exception 'persona_not_found' using errcode = 'P0002';
    end if;

    insert into public.conversations(id, owner_id, character_id, persona_id, mode, title)
    values (v_conversation_id, v_user_id, p_character_id, p_persona_id, p_mode, left(p_title, 120));

    insert into public.conversation_branches(id, conversation_id, owner_id, title)
    values (v_branch_id, v_conversation_id, v_user_id, 'Основная ветка');

    return query select v_conversation_id, v_branch_id;
end;
$$;

create or replace function public.append_user_message(
    p_branch_id uuid,
    p_content text,
    p_parent_message_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $fn$
declare
    v_user_id uuid := auth.uid();
    v_branch public.conversation_branches%rowtype;
    v_sequence bigint;
    v_message_id uuid := gen_random_uuid();
    v_parent_message_id uuid := p_parent_message_id;
begin
    if v_user_id is null then
        raise exception 'authentication_required' using errcode = '28000';
    end if;
    if char_length(trim(coalesce(p_content, ''))) not between 1 and 12000 then
        raise exception 'message_length_invalid' using errcode = '22023';
    end if;

    select * into v_branch
    from public.conversation_branches
    where id = p_branch_id and owner_id = v_user_id
    for update;

    if not found then
        raise exception 'branch_not_found' using errcode = 'P0002';
    end if;

    if v_parent_message_id is null then
        v_parent_message_id := v_branch.forked_from_message_id;
    end if;

    if v_parent_message_id is not null and not exists (
        select 1 from public.chat_messages m
        where m.id = v_parent_message_id
          and m.owner_id = v_user_id
          and m.conversation_id = v_branch.conversation_id
          and m.status = 'complete'
    ) then
        raise exception 'parent_message_not_found' using errcode = 'P0002';
    end if;

    v_sequence := coalesce((
        select max(m.sequence) + 1
        from public.chat_messages m
        where m.branch_id = p_branch_id
    ), 1);

    insert into public.chat_messages(
        id, conversation_id, branch_id, owner_id, role, content,
        parent_message_id, sequence, status
    )
    values (
        v_message_id, v_branch.conversation_id, p_branch_id, v_user_id, 'user',
        trim(p_content), v_parent_message_id, v_sequence, 'complete'
    );

    update public.conversations set updated_at = now()
    where id = v_branch.conversation_id and owner_id = v_user_id;
    return v_message_id;
end;
$fn$;

create or replace function public.create_conversation_branch(
    p_conversation_id uuid,
    p_from_message_id uuid default null,
    p_title text default 'Новая ветка'
)
returns uuid
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $fn$
declare
    v_user_id uuid := auth.uid();
    v_branch_id uuid := gen_random_uuid();
    v_parent_branch_id uuid;
    v_fork_sequence bigint;
begin
    if v_user_id is null then
        raise exception 'authentication_required' using errcode = '28000';
    end if;
    if not exists (
        select 1 from public.conversations c
        where c.id = p_conversation_id and c.owner_id = v_user_id
    ) then
        raise exception 'conversation_not_found' using errcode = 'P0002';
    end if;

    if p_from_message_id is not null then
        select m.branch_id, m.sequence
        into v_parent_branch_id, v_fork_sequence
        from public.chat_messages m
        where m.id = p_from_message_id
          and m.conversation_id = p_conversation_id
          and m.owner_id = v_user_id
          and m.status = 'complete';
        if v_parent_branch_id is null then
            raise exception 'message_not_found' using errcode = 'P0002';
        end if;
    end if;

    insert into public.conversation_branches(
        id, conversation_id, owner_id, parent_branch_id, forked_from_message_id, title
    )
    values (
        v_branch_id, p_conversation_id, v_user_id, v_parent_branch_id,
        p_from_message_id, left(p_title, 100)
    );

    if p_from_message_id is not null then
        insert into public.chat_summaries(
            conversation_id, branch_id, owner_id, summary, up_to_sequence, updated_at
        )
        select
            p_conversation_id, v_branch_id, v_user_id, s.summary, s.up_to_sequence, now()
        from public.chat_summaries s
        where s.branch_id = v_parent_branch_id
          and s.owner_id = v_user_id
          and s.up_to_sequence <= v_fork_sequence
        on conflict (branch_id) do nothing;
    end if;

    return v_branch_id;
end;
$fn$;

create unique index if not exists chat_messages_variant_unique
    on public.chat_messages(parent_message_id, variant_group_id, variant_index)
    where role = 'assistant' and variant_group_id is not null;

create or replace function public.append_assistant_message(
    p_owner_id uuid,
    p_branch_id uuid,
    p_parent_message_id uuid,
    p_variant_group_id uuid
)
returns table(message_id uuid, variant_index integer)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $fn$
declare
    v_branch public.conversation_branches%rowtype;
    v_sequence bigint;
    v_next_variant integer;
    v_message_id uuid := gen_random_uuid();
begin
    if auth.role() is distinct from 'service_role' then
        raise exception 'service_role_required' using errcode = '42501';
    end if;

    select * into v_branch
    from public.conversation_branches
    where id = p_branch_id and owner_id = p_owner_id
    for update;
    if not found then
        raise exception 'branch_not_found' using errcode = 'P0002';
    end if;

    perform 1
    from public.chat_messages m
    where m.id = p_parent_message_id
      and m.owner_id = p_owner_id
      and m.conversation_id = v_branch.conversation_id
      and m.role = 'user'
      and m.status = 'complete'
    for update;
    if not found then
        raise exception 'parent_message_not_found' using errcode = 'P0002';
    end if;

    v_next_variant := coalesce((
        select max(m.variant_index) + 1
        from public.chat_messages m
        where m.parent_message_id = p_parent_message_id
          and m.variant_group_id = p_variant_group_id
          and m.role = 'assistant'
    ), 0);

    v_sequence := coalesce((
        select max(m.sequence) + 1
        from public.chat_messages m
        where m.branch_id = p_branch_id
    ), 1);

    insert into public.chat_messages(
        id, conversation_id, branch_id, owner_id, role, content,
        parent_message_id, variant_group_id, variant_index, sequence, status
    )
    values (
        v_message_id, v_branch.conversation_id, p_branch_id, p_owner_id, 'assistant', '',
        p_parent_message_id, p_variant_group_id, v_next_variant, v_sequence, 'pending'
    );

    update public.conversations set updated_at = now()
    where id = v_branch.conversation_id and owner_id = p_owner_id;
    return query select v_message_id, v_next_variant;
end;
$fn$;

revoke all on function public.append_assistant_message(uuid, uuid, uuid, uuid) from public, anon, authenticated;
grant execute on function public.append_assistant_message(uuid, uuid, uuid, uuid) to service_role;

create or replace function public.get_branch_history(
    p_branch_id uuid,
    p_tip_message_id uuid,
    p_limit integer default 30
)
returns table(id uuid, role text, content text, sequence bigint)
language sql
stable
security invoker
set search_path = public, auth, pg_temp
as $fn$
    with recursive path as (
        select
            m.id, m.conversation_id, m.parent_message_id, m.role, m.content,
            m.sequence, 0 as depth, array[m.id] as visited
        from public.chat_messages m
        join public.conversation_branches b
          on b.id = m.branch_id and b.conversation_id = m.conversation_id
        where m.id = p_tip_message_id
          and m.branch_id = p_branch_id
          and m.owner_id = auth.uid()
          and b.owner_id = auth.uid()
          and m.status = 'complete'

        union all

        select
            parent.id, parent.conversation_id, parent.parent_message_id, parent.role,
            parent.content, parent.sequence, path.depth + 1, path.visited || parent.id
        from path
        join public.chat_messages parent
          on parent.id = path.parent_message_id
         and parent.conversation_id = path.conversation_id
         and parent.owner_id = auth.uid()
         and parent.status = 'complete'
        where not parent.id = any(path.visited)
    ),
    newest as (
        select id, role, content, sequence, depth
        from path
        order by depth asc
        limit greatest(1, least(p_limit, 30))
    )
    select id, role, content, sequence
    from newest
    order by depth desc;
$fn$;

grant execute on function public.get_branch_history(uuid, uuid, integer) to authenticated;

create or replace function public.consume_chat_quota(
    p_owner_id uuid,
    p_conversation_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $fn$
declare
    v_plan text := 'free';
    v_limit integer := 60;
    v_used integer := 0;
begin
    if auth.role() is distinct from 'service_role' then
        raise exception 'service_role_required' using errcode = '42501';
    end if;

    select
        case
            when e.plan = 'plus' and (e.valid_until is null or e.valid_until > now()) then 'plus'
            else 'free'
        end
    into v_plan
    from public.user_entitlements e
    where e.user_id = p_owner_id
    for update;

    if v_plan = 'plus' then v_limit := 300; end if;

    select count(*)::integer into v_used
    from public.chat_usage_events e
    where e.owner_id = p_owner_id
      and e.created_at > now() - interval '24 hours';

    if v_used >= v_limit then return false; end if;

    insert into public.chat_usage_events(owner_id, conversation_id)
    values (p_owner_id, p_conversation_id);
    return true;
end;
$fn$;

revoke all on function public.consume_chat_quota(uuid, uuid) from public, anon, authenticated;
grant execute on function public.consume_chat_quota(uuid, uuid) to service_role;

create or replace function public.match_memory_facts(
    p_query_embedding extensions.vector(768),
    p_conversation_id uuid,
    p_character_id uuid,
    p_match_count integer default 6,
    p_threshold real default 0.60
)
returns table(
    id uuid,
    fact_type text,
    content text,
    importance real,
    similarity real
)
language sql
stable
security invoker
set search_path = public, extensions, auth, pg_temp
as $$
    select
        f.id,
        f.fact_type,
        f.content,
        f.importance,
        (1 - (f.embedding <=> p_query_embedding))::real as similarity
    from public.memory_facts f
    where f.owner_id = auth.uid()
      and (
        f.conversation_id = p_conversation_id
        or (f.character_id = p_character_id and f.conversation_id is null)
        or (f.character_id is null and f.conversation_id is null)
      )
      and 1 - (f.embedding <=> p_query_embedding) >= p_threshold
    order by f.embedding <=> p_query_embedding, f.importance desc
    limit greatest(1, least(p_match_count, 10));
$$;

grant execute on function public.create_conversation(uuid, uuid, text, text) to authenticated;
grant execute on function public.append_user_message(uuid, text, uuid) to authenticated;
grant execute on function public.create_conversation_branch(uuid, uuid, text) to authenticated;
grant execute on function public.match_memory_facts(extensions.vector, uuid, uuid, integer, real) to authenticated;

revoke all on public.app_config from anon;
grant select on public.app_config to authenticated;
