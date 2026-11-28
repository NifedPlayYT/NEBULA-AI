alter table public.chat_branches
  add column if not exists last_message_at timestamptz not null default now();

update public.chat_branches b
set last_message_at = coalesce(
  (select max(m.created_at) from public.messages m where m.branch_id = b.id),
  b.created_at
);
