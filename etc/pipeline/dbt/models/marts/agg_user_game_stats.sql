-- 마트: 유저 × 게임 누적 지표. /me/stats(내 게임별 전적)의 소스.
select
    user_id,
    game_name,
    count(*)                                 as play_count,
    count(*) filter (where outcome = 'WIN')  as win_count,
    max(score)                               as best_score
from {{ ref('fact_game_play') }}
group by user_id, game_name
