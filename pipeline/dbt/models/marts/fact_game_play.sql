-- 팩트: 참가자 1명당 1행. 서빙 컬럼 participants_.score + 게임 메타(이름·모드).
-- 분석(agg·daily)의 공통 입력. (원본 JSONB와의 대조는 reconcile_score가 담당)
select
    sp.participant_id,
    sp.result_id,
    sr.game_name,
    sr.play_mode,
    sp.user_id,
    sp.slot_no,
    sp.outcome,
    sp.score,
    sr.ended_at
from {{ ref('stg_participants') }} sp
join {{ ref('stg_game_results') }} sr
    on sr.result_id = sp.result_id
where sp.user_id is not null
