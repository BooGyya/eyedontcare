-- 감사(reconcile): participants_.score(실시간 사본) vs game_result JSONB(원본 기대값) 대조.
-- 불일치·누락 행만 남긴다. 정상이면 0행. (data_quality_issues 리포트로 사용)
--   MISSING  : 사본이 비었는데 원본엔 점수 있음 → 안전 백필 후보
--   MISMATCH : 사본 ≠ 원본 → 사람 검토
with expected as (
    select
        p.id        as participant_id,
        p.user_id,
        p.slot_no,
        p.score     as stored_score,
        (gr.game_result -> p.slot_no::text ->> 'score')::bigint as expected_score
    from {{ source('operational', 'participants_') }} p
    join {{ source('operational', 'games_results') }} gr
        on gr.id = p.result_id
    where p.user_id is not null
)
select
    participant_id,
    user_id,
    slot_no,
    stored_score,
    expected_score,
    case
        when stored_score is null and expected_score is not null then 'MISSING'
        else 'MISMATCH'
    end as issue_type
from expected
where stored_score is distinct from expected_score
