alter table public.long_term_memories
  add column if not exists content_hash text;

create unique index if not exists long_term_memories_deduplicate_idx
  on public.long_term_memories(user_id, character_id, content_hash);

alter table public.chat_branches
  add column if not exists turn_lock_token uuid,
  add column if not exists turn_lock_until timestamptz;

create or replace function public.acquire_chat_turn(
  target_branch_id uuid,
  target_user_id uuid,
  target_lock_token uuid
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  changed_rows integer := 0;
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;

  update public.chat_branches
  set turn_lock_token = target_lock_token,
      turn_lock_until = now() + interval '90 seconds'
  where id = target_branch_id
    and user_id = target_user_id
    and (turn_lock_until is null or turn_lock_until < now());

  get diagnostics changed_rows = row_count;
  return changed_rows > 0;
end;
$$;

create or replace function public.release_chat_turn(
  target_branch_id uuid,
  target_lock_token uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;

  update public.chat_branches
  set turn_lock_token = null,
      turn_lock_until = null
  where id = target_branch_id
    and turn_lock_token = target_lock_token;
end;
$$;

revoke all on function public.acquire_chat_turn(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public.release_chat_turn(uuid, uuid) from public, anon, authenticated;
grant execute on function public.acquire_chat_turn(uuid, uuid, uuid) to service_role;
grant execute on function public.release_chat_turn(uuid, uuid) to service_role;
