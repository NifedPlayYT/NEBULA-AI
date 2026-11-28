drop function if exists public.match_memories(extensions.vector, uuid, uuid, integer);

create or replace function public.match_memories(
  q extensions.vector(1024),
  uid uuid,
  cid uuid,
  match_branch_id uuid,
  amount int default 6
)
returns table(id uuid, content text, memory_type text, similarity float)
language sql
stable
security invoker
set search_path = public, extensions
as $$
  select
    m.id,
    m.content,
    m.memory_type,
    1 - (m.embedding <=> q) as similarity
  from public.long_term_memories m
  where m.user_id = uid
    and (m.character_id is null or m.character_id = cid)
    and (m.branch_id is null or m.branch_id = match_branch_id)
    and m.embedding is not null
  order by m.embedding <=> q
  limit least(greatest(amount, 1), 12);
$$;
