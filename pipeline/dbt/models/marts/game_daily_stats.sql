-- 분석: 게임·모드·일자별 지표(관리자 대시보드·추세용). 실시간 서빙엔 안 씀.
select
    game_name,
    play_mode,
    (ended_at at time zone 'Asia/Seoul')::date as play_date,
    count(*)                as play_count,
    count(distinct user_id) as player_count,
    avg(score)              as avg_score,
    max(score)              as best_score
from {{ ref('fact_game_play') }}
group by game_name, play_mode, (ended_at at time zone 'Asia/Seoul')::date
