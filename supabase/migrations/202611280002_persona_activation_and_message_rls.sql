alter table public.user_personas
  add column if not exists is_active boolean not null default false;

with first_persona as (
  select distinct on (user_id) id
  from public.user_personas
  order by user_id, created_at asc, id asc
)
update public.user_personas p
set is_active = true
from first_persona f
where p.id = f.id
  and not exists (
    select 1 from public.user_personas active
    where active.user_id = p.user_id and active.is_active
  );

create unique index if not exists user_personas_one_active_per_user
  on public.user_personas(user_id)
  where is_active = true;

create or replace function public.activate_user_persona(target_persona_id uuid)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'authentication required';
  end if;

  if not exists (
    select 1 from public.user_personas
    where id = target_persona_id and user_id = auth.uid()
  ) then
    raise exception 'persona not found';
  end if;

  update public.user_personas
  set is_active = false
  where user_id = auth.uid() and is_active;

  update public.user_personas
  set is_active = true
  where id = target_persona_id and user_id = auth.uid();
end;
$$;

drop policy if exists "Own messages manageable" on public.messages;
create policy "Users read own messages"
  on public.messages for select
  using (user_id = auth.uid());

create policy "Users create own user messages"
  on public.messages for insert
  with check (user_id = auth.uid() and role = 'user');

create policy "Users edit own user messages"
  on public.messages for update
  using (user_id = auth.uid() and role = 'user')
  with check (user_id = auth.uid() and role = 'user');
