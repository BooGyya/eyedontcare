-- 참가자 스테이징: 서빙 컬럼 score를 포함해 얇게 노출한다.
select
    p.id          as participant_id,
    p.result_id,
    p.user_id,
    p.slot_no,
    p.outcome,
    p.score,
    p.display_name
from {{ source('operational', 'participants_') }} p
