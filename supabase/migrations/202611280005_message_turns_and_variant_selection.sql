alter table public.messages
  add column if not exists turn_id uuid;

create index if not exists messages_branch_turn_idx
  on public.messages(branch_id, turn_id);

create unique index if not exists messages_variant_position_unique
  on public.messages(variant_group, variant_index)
  where variant_group is not null;

create or replace function public.select_message_variant(
  target_branch_id uuid,
  target_user_id uuid,
  target_message_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  selected_group uuid;
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;

  select variant_group
    into selected_group
  from public.messages
  where id = target_message_id
    and branch_id = target_branch_id
    and user_id = target_user_id
    and role = 'assistant';

  if selected_group is null then
    raise exception 'message variant not found';
  end if;

  update public.messages
  set selected = false
  where branch_id = target_branch_id
    and user_id = target_user_id
    and variant_group = selected_group;

  update public.messages
  set selected = true
  where id = target_message_id
    and branch_id = target_branch_id
    and user_id = target_user_id
    and variant_group = selected_group;
end;
$$;

revoke all on function public.select_message_variant(uuid, uuid, uuid) from public, anon, authenticated;
grant execute on function public.select_message_variant(uuid, uuid, uuid) to service_role;
