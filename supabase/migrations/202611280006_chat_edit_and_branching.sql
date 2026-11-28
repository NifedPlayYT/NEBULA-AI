alter table public.chat_branches
  add column if not exists forked_from_message_id uuid
  references public.messages(id) on delete set null;

create or replace function public.replace_chat_turn_reply(
  target_branch_id uuid,
  target_user_id uuid,
  target_user_message_id uuid,
  replacement_content text,
  replacement_reply text,
  replacement_variant_group uuid
)
returns table(user_message_id uuid, assistant_message_id uuid)
language plpgsql
security definer
set search_path = public
as $$
declare
  target_turn_id uuid;
  inserted_assistant_id uuid;
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;
  if char_length(trim(replacement_content)) < 1 or char_length(replacement_content) > 6000 then
    raise exception 'invalid user message';
  end if;
  if char_length(trim(replacement_reply)) < 1 or char_length(replacement_reply) > 12000 then
    raise exception 'invalid assistant reply';
  end if;

  select m.turn_id into target_turn_id
  from public.messages m
  where m.id = target_user_message_id
    and m.branch_id = target_branch_id
    and m.user_id = target_user_id
    and m.role = 'user'
  for update;

  if target_turn_id is null then
    raise exception 'user turn not found';
  end if;

  update public.messages
  set content = trim(replacement_content), edited_at = now()
  where id = target_user_message_id
    and branch_id = target_branch_id
    and user_id = target_user_id
    and role = 'user';

  delete from public.messages
  where branch_id = target_branch_id
    and user_id = target_user_id
    and role = 'assistant'
    and turn_id = target_turn_id;

  inserted_assistant_id := gen_random_uuid();
  insert into public.messages(
    id, branch_id, user_id, role, content, turn_id,
    variant_group, variant_index, selected
  ) values (
    inserted_assistant_id, target_branch_id, target_user_id, 'assistant',
    trim(replacement_reply), target_turn_id, replacement_variant_group, 0, true
  );

  return query select target_user_message_id, inserted_assistant_id;
end;
$$;

create or replace function public.fork_chat_branch(
  source_branch_id uuid,
  target_user_id uuid,
  source_message_id uuid,
  new_branch_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  source_branch public.chat_branches%rowtype;
  source_message public.messages%rowtype;
  item public.messages%rowtype;
  group_map jsonb := '{}'::jsonb;
  mapped_group uuid;
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;

  select * into source_branch
  from public.chat_branches
  where id = source_branch_id and user_id = target_user_id;

  if source_branch.id is null then
    raise exception 'source branch not found';
  end if;

  select * into source_message
  from public.messages
  where id = source_message_id
    and branch_id = source_branch_id
    and user_id = target_user_id
    and role = 'assistant'
    and selected = true;

  if source_message.id is null then
    raise exception 'source assistant message not found';
  end if;

  insert into public.chat_branches(
    id, user_id, character_id, parent_branch_id,
    forked_from_message_id, title, mode
  ) values (
    new_branch_id, target_user_id, source_branch.character_id, source_branch_id,
    source_message_id, 'Ветка истории', source_branch.mode
  );

  for item in
    select m.*
    from public.messages m
    where m.branch_id = source_branch_id
      and m.user_id = target_user_id
      and m.selected = true
      and m.created_at <= source_message.created_at
    order by m.created_at asc, m.id asc
  loop
    mapped_group := null;
    if item.variant_group is not null then
      if group_map ? item.variant_group::text then
        mapped_group := (group_map ->> item.variant_group::text)::uuid;
      else
        mapped_group := gen_random_uuid();
        group_map := group_map || jsonb_build_object(item.variant_group::text, mapped_group::text);
      end if;
    end if;

    insert into public.messages(
      branch_id, user_id, role, content, turn_id,
      variant_group, variant_index, selected, edited_at, created_at
    ) values (
      new_branch_id, target_user_id, item.role, item.content, item.turn_id,
      mapped_group, case when mapped_group is null then 0 else 0 end,
      true, item.edited_at, item.created_at
    );
  end loop;
end;
$$;

revoke all on function public.replace_chat_turn_reply(uuid, uuid, uuid, text, text, uuid)
  from public, anon, authenticated;
revoke all on function public.fork_chat_branch(uuid, uuid, uuid, uuid)
  from public, anon, authenticated;
grant execute on function public.replace_chat_turn_reply(uuid, uuid, uuid, text, text, uuid)
  to service_role;
grant execute on function public.fork_chat_branch(uuid, uuid, uuid, uuid)
  to service_role;
