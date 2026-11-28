with ranked_legacy_messages as (
  select
    id,
    branch_id,
    role,
    sum(case when role = 'user' then 1 else 0 end) over (
      partition by branch_id
      order by
        created_at asc,
        case when role = 'user' then 0 else 1 end asc,
        id asc
      rows between unbounded preceding and current row
    ) as turn_number
  from public.messages
  where turn_id is null
),
legacy_turns as (
  select
    branch_id,
    turn_number,
    gen_random_uuid() as generated_turn_id
  from ranked_legacy_messages
  where turn_number > 0
  group by branch_id, turn_number
)
update public.messages as target
set turn_id = turns.generated_turn_id
from ranked_legacy_messages as ranked
join legacy_turns as turns
  on turns.branch_id = ranked.branch_id
 and turns.turn_number = ranked.turn_number
where target.id = ranked.id
  and target.turn_id is null;
