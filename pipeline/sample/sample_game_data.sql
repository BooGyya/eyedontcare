-- 개발/검증용 샘플 게임 결과 (운영 실데이터가 없을 때 파이프라인을 돌려보기 위함).
-- 고정 play_id를 써서 재실행해도 중복 없이 갱신된다.
-- BLINK 혼자하기 3판(점수 128/116/103) + HOCKEY 랜덤 3경기(승수: user1 2, user2 1).
-- 각 참가자는 game_result JSONB와 participants_.score(사본)를 함께 채운다(감사 대조용).

BEGIN;

-- 0) 이전 샘플 정리
DELETE FROM participants_ WHERE result_id IN (
    SELECT id FROM games_results WHERE play_id IN (
        '11111111-1111-4111-8111-000000000001',
        '11111111-1111-4111-8111-000000000002',
        '11111111-1111-4111-8111-000000000003',
        '22222222-2222-4222-8222-000000000001',
        '22222222-2222-4222-8222-000000000002',
        '22222222-2222-4222-8222-000000000003'
    )
);
DELETE FROM games_results WHERE play_id IN (
    '11111111-1111-4111-8111-000000000001',
    '11111111-1111-4111-8111-000000000002',
    '11111111-1111-4111-8111-000000000003',
    '22222222-2222-4222-8222-000000000001',
    '22222222-2222-4222-8222-000000000002',
    '22222222-2222-4222-8222-000000000003'
);
DELETE FROM users WHERE email LIKE 'sample%@edc.local';

-- 1) 샘플 유저 3명
INSERT INTO users (email, nickname, password, profile_img_code, created_at, updated_at) VALUES
    ('sample1@edc.local', '샘플방울반짝',  NULL, 'PROFILE_1', now(), now()),
    ('sample2@edc.local', '샘플졸린너구리', NULL, 'PROFILE_1', now(), now()),
    ('sample3@edc.local', '샘플밤하늘',    NULL, 'PROFILE_1', now(), now());

-- 2) BLINK 혼자하기 결과 (JSONB {"1":{"score":N}} + score 컬럼)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('11111111-1111-4111-8111-000000000001',
        (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":128}}', now() - interval '61 min', now() - interval '60 min', now(), now()),
    ('11111111-1111-4111-8111-000000000002',
        (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":116}}', now() - interval '121 min', now() - interval '120 min', now(), now()),
    ('11111111-1111-4111-8111-000000000003',
        (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":103}}', now() - interval '181 min', now() - interval '180 min', now(), now());

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    ((SELECT id FROM games_results WHERE play_id='11111111-1111-4111-8111-000000000001'),
        (SELECT id FROM users WHERE email='sample1@edc.local'), 'USER', 1, 'COMPLETED', 1, '샘플방울반짝', 128, now(), now()),
    ((SELECT id FROM games_results WHERE play_id='11111111-1111-4111-8111-000000000002'),
        (SELECT id FROM users WHERE email='sample2@edc.local'), 'USER', 1, 'COMPLETED', 1, '샘플졸린너구리', 116, now(), now()),
    ((SELECT id FROM games_results WHERE play_id='11111111-1111-4111-8111-000000000003'),
        (SELECT id FROM users WHERE email='sample3@edc.local'), 'USER', 1, 'COMPLETED', 1, '샘플밤하늘', 103, now(), now());

-- 3) HOCKEY 랜덤 경기 (2인) — user1 2승, user2 1승. score 컬럼 = 각자 골 수
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('22222222-2222-4222-8222-000000000001',
        (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='RANDOM'),
        '{"1":{"score":3},"2":{"score":1}}', now() - interval '61 min', now() - interval '60 min', now(), now()),
    ('22222222-2222-4222-8222-000000000002',
        (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='RANDOM'),
        '{"1":{"score":2},"2":{"score":0}}', now() - interval '121 min', now() - interval '120 min', now(), now()),
    ('22222222-2222-4222-8222-000000000003',
        (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='RANDOM'),
        '{"1":{"score":3},"2":{"score":2}}', now() - interval '181 min', now() - interval '180 min', now(), now());

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    -- 경기1: user1 승(3) vs user2 패(1)
    ((SELECT id FROM games_results WHERE play_id='22222222-2222-4222-8222-000000000001'),
        (SELECT id FROM users WHERE email='sample1@edc.local'), 'USER', 1, 'WIN',  1, '샘플방울반짝', 3, now(), now()),
    ((SELECT id FROM games_results WHERE play_id='22222222-2222-4222-8222-000000000001'),
        (SELECT id FROM users WHERE email='sample2@edc.local'), 'USER', 2, 'LOSE', 2, '샘플졸린너구리', 1, now(), now()),
    -- 경기2: user1 승(2) vs user3 패(0)
    ((SELECT id FROM games_results WHERE play_id='22222222-2222-4222-8222-000000000002'),
        (SELECT id FROM users WHERE email='sample1@edc.local'), 'USER', 1, 'WIN',  1, '샘플방울반짝', 2, now(), now()),
    ((SELECT id FROM games_results WHERE play_id='22222222-2222-4222-8222-000000000002'),
        (SELECT id FROM users WHERE email='sample3@edc.local'), 'USER', 2, 'LOSE', 2, '샘플밤하늘', 0, now(), now()),
    -- 경기3: user2 승(3) vs user3 패(2)
    ((SELECT id FROM games_results WHERE play_id='22222222-2222-4222-8222-000000000003'),
        (SELECT id FROM users WHERE email='sample2@edc.local'), 'USER', 1, 'WIN',  1, '샘플졸린너구리', 3, now(), now()),
    ((SELECT id FROM games_results WHERE play_id='22222222-2222-4222-8222-000000000003'),
        (SELECT id FROM users WHERE email='sample3@edc.local'), 'USER', 2, 'LOSE', 2, '샘플밤하늘', 2, now(), now());

COMMIT;

-- 기대: fact_game_play BLINK 3행·HOCKEY 6행 / reconcile_score 0행(사본=원본 일치)
-- agg: user1 HOCKEY win_count=2, user2 HOCKEY win_count=1, BLINK best 128/116/103
