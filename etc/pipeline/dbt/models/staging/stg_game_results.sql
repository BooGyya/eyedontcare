-- 경기 결과 스테이징: 원본 games_results에 게임 메타(이름·모드)를 얇게 붙인다.
select
    gr.id          as result_id,
    gr.play_id,
    gr.game_id,
    g.game_name,
    g.play_mode,
    gr.game_result,
    gr.started_at,
    gr.ended_at
from {{ source('operational', 'games_results') }} gr
join {{ source('operational', 'games') }} g
    on g.id = gr.game_id
