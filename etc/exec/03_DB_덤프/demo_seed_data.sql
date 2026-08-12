-- =============================================================================
-- eye-dont-care 데모 시드 데이터 (포팅 매뉴얼 제출용 DB 덤프 준비)
-- =============================================================================
-- 용도: 빈 운영 DB에 시연·제출용 데모 데이터를 채운다.
--       적재 후 pg_dump로 덤프를 뜨면 "데이터가 담긴 DB 덤프 최신본"이 된다.
--
-- 전제: 백엔드가 최소 1회 기동되어 스키마(ddl-auto)와 games 카탈로그(data.sql)가
--       생성된 상태여야 한다. (games 테이블의 게임×모드 조합을 참조하기 때문)
--
-- 재실행 안전(멱등): 고정 play_id(dddddddd-... 프리픽스)와 demo 이메일 패턴으로
--       이전 데모 데이터를 지우고 다시 넣는다. 실제 사용자 데이터는 건드리지 않는다.
--
-- 구성: 데모 회원 10명 / 길드 2개(게시글·댓글 포함) / 5개 게임 전체의 플레이 기록 23건
--       (혼자하기·랜덤 매칭·초대 대결·AI 대결) / 주간 랭킹 변동 배지 4건
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 0) 이전 데모 데이터 정리 (실사용 데이터는 건드리지 않음)
-- -----------------------------------------------------------------------------
DELETE FROM participants_ WHERE result_id IN (
    SELECT id FROM games_results
    WHERE play_id::text LIKE 'dddddddd-dddd-4ddd-8ddd-%'
);
DELETE FROM games_results
WHERE play_id::text LIKE 'dddddddd-dddd-4ddd-8ddd-%';

DELETE FROM group_comments WHERE post_id IN (
    SELECT id FROM group_posts WHERE group_id IN (
        SELECT id FROM groups_ WHERE group_code IN ('EYECON', 'BLINK2')
    )
);
DELETE FROM group_posts WHERE group_id IN (
    SELECT id FROM groups_ WHERE group_code IN ('EYECON', 'BLINK2')
);
DELETE FROM group_members WHERE group_id IN (
    SELECT id FROM groups_ WHERE group_code IN ('EYECON', 'BLINK2')
);
DELETE FROM groups_ WHERE group_code IN ('EYECON', 'BLINK2');

DELETE FROM ranking_trends WHERE user_id IN (
    SELECT id FROM users WHERE email LIKE 'demo%@eyedontcare.local'
);
DELETE FROM users WHERE email LIKE 'demo%@eyedontcare.local';

-- -----------------------------------------------------------------------------
-- 1) 데모 회원 10명 (password NULL = 소셜 가입 형태, 로그인 불가한 전시용 계정)
-- -----------------------------------------------------------------------------
INSERT INTO users (email, nickname, password, profile_img_code, created_at, updated_at) VALUES
    ('demo1@eyedontcare.local',  '반짝이는눈망울', NULL, 'PROFILE_1', now() - interval '14 days', now() - interval '14 days'),
    ('demo2@eyedontcare.local',  '깜빡임의달인',   NULL, 'PROFILE_2', now() - interval '13 days', now() - interval '13 days'),
    ('demo3@eyedontcare.local',  '눈싸움챔피언',   NULL, 'PROFILE_3', now() - interval '12 days', now() - interval '12 days'),
    ('demo4@eyedontcare.local',  '시선강탈자',     NULL, 'PROFILE_4', now() - interval '11 days', now() - interval '11 days'),
    ('demo5@eyedontcare.local',  '졸린판다',       NULL, 'PROFILE_5', now() - interval '10 days', now() - interval '10 days'),
    ('demo6@eyedontcare.local',  '매의눈',         NULL, 'PROFILE_6', now() - interval '9 days',  now() - interval '9 days'),
    ('demo7@eyedontcare.local',  '윙크요정',       NULL, 'PROFILE_7', now() - interval '8 days',  now() - interval '8 days'),
    ('demo8@eyedontcare.local',  '눈치백단',       NULL, 'PROFILE_8', now() - interval '7 days',  now() - interval '7 days'),
    ('demo9@eyedontcare.local',  '레이저시선',     NULL, 'PROFILE_1', now() - interval '6 days',  now() - interval '6 days'),
    ('demo10@eyedontcare.local', '안깜빡할게',     NULL, 'PROFILE_2', now() - interval '5 days',  now() - interval '5 days');

-- -----------------------------------------------------------------------------
-- 2) 길드 2개 + 길드원 + 게시글·댓글
-- -----------------------------------------------------------------------------
INSERT INTO groups_ (name, description, group_code, owner_user_id, visibility, capacity, created_at, updated_at) VALUES
    ('눈빛교환소', '눈으로 대화하는 사람들의 모임. 주간 랭킹 상위권을 노려요!', 'EYECON',
        (SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'PUBLIC', 20,
        now() - interval '10 days', now() - interval '10 days'),
    ('깜빡깜빡', '리듬게임 위주로 같이 연습하는 소모임', 'BLINK2',
        (SELECT id FROM users WHERE email='demo6@eyedontcare.local'), 'PUBLIC', 10,
        now() - interval '8 days', now() - interval '8 days');

INSERT INTO group_members (group_id, user_id, role, joined_at) VALUES
    ((SELECT id FROM groups_ WHERE group_code='EYECON'), (SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'OWNER',  now() - interval '10 days'),
    ((SELECT id FROM groups_ WHERE group_code='EYECON'), (SELECT id FROM users WHERE email='demo2@eyedontcare.local'), 'MEMBER', now() - interval '9 days'),
    ((SELECT id FROM groups_ WHERE group_code='EYECON'), (SELECT id FROM users WHERE email='demo3@eyedontcare.local'), 'MEMBER', now() - interval '9 days'),
    ((SELECT id FROM groups_ WHERE group_code='EYECON'), (SELECT id FROM users WHERE email='demo4@eyedontcare.local'), 'MEMBER', now() - interval '8 days'),
    ((SELECT id FROM groups_ WHERE group_code='EYECON'), (SELECT id FROM users WHERE email='demo5@eyedontcare.local'), 'MEMBER', now() - interval '7 days'),
    ((SELECT id FROM groups_ WHERE group_code='BLINK2'), (SELECT id FROM users WHERE email='demo6@eyedontcare.local'), 'OWNER',  now() - interval '8 days'),
    ((SELECT id FROM groups_ WHERE group_code='BLINK2'), (SELECT id FROM users WHERE email='demo7@eyedontcare.local'), 'MEMBER', now() - interval '7 days'),
    ((SELECT id FROM groups_ WHERE group_code='BLINK2'), (SELECT id FROM users WHERE email='demo8@eyedontcare.local'), 'MEMBER', now() - interval '6 days');

INSERT INTO group_posts (group_id, author_user_id, content, created_at, updated_at) VALUES
    ((SELECT id FROM groups_ WHERE group_code='EYECON'),
        (SELECT id FROM users WHERE email='demo2@eyedontcare.local'),
        '오늘 눈싸움 3연승 했어요! 다들 도전해 보세요 👀', now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM groups_ WHERE group_code='EYECON'),
        (SELECT id FROM users WHERE email='demo1@eyedontcare.local'),
        '이번 주 랭킹 1위 탈환 갑니다. 에어하키 연습 상대 구해요!', now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM groups_ WHERE group_code='BLINK2'),
        (SELECT id FROM users WHERE email='demo6@eyedontcare.local'),
        '리듬게임 팁: 박자보다 반 박자 먼저 깜빡이면 판정이 훨씬 잘 나와요', now() - interval '3 days', now() - interval '3 days');

INSERT INTO group_comments (post_id, author_user_id, content, created_at, updated_at) VALUES
    ((SELECT p.id FROM group_posts p
        JOIN groups_ g ON g.id = p.group_id
        WHERE g.group_code='EYECON' AND p.content LIKE '오늘 눈싸움%'),
        (SELECT id FROM users WHERE email='demo3@eyedontcare.local'),
        '오 대단해요! 저랑도 한 판 해요', now() - interval '2 days' + interval '1 hour', now() - interval '2 days' + interval '1 hour'),
    ((SELECT p.id FROM group_posts p
        JOIN groups_ g ON g.id = p.group_id
        WHERE g.group_code='BLINK2' AND p.content LIKE '리듬게임 팁%'),
        (SELECT id FROM users WHERE email='demo7@eyedontcare.local'),
        '이 팁 덕분에 최고 기록 갱신했어요 감사합니다!', now() - interval '2 days', now() - interval '2 days');

-- -----------------------------------------------------------------------------
-- 3) 플레이 기록 — games_results(JSONB 원본) + participants_(score 사본)
--    최근 7일에 분산시켜 주간 랭킹에 잡히게 한다.
-- -----------------------------------------------------------------------------

-- 3-1) BLINK 혼자하기 6판 (점수 = 깜빡인 횟수)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000001', (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":142}}', now() - interval '1 day 61 minutes', now() - interval '1 day 60 minutes', now() - interval '1 day', now() - interval '1 day'),
    ('dddddddd-dddd-4ddd-8ddd-000000000002', (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":128}}', now() - interval '2 days 61 minutes', now() - interval '2 days 60 minutes', now() - interval '2 days', now() - interval '2 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000003', (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":117}}', now() - interval '3 days 61 minutes', now() - interval '3 days 60 minutes', now() - interval '3 days', now() - interval '3 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000004', (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":105}}', now() - interval '2 days 30 minutes', now() - interval '2 days 29 minutes', now() - interval '2 days', now() - interval '2 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000005', (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":98}}',  now() - interval '4 days 15 minutes', now() - interval '4 days 14 minutes', now() - interval '4 days', now() - interval '4 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000006', (SELECT id FROM games WHERE game_name='BLINK' AND play_mode='SOLO'),
        '{"1":{"score":88}}',  now() - interval '5 days 20 minutes', now() - interval '5 days 19 minutes', now() - interval '5 days', now() - interval '5 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000001'), (SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '반짝이는눈망울', 142, now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000002'), (SELECT id FROM users WHERE email='demo2@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '깜빡임의달인', 128, now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000003'), (SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '반짝이는눈망울', 117, now() - interval '3 days', now() - interval '3 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000004'), (SELECT id FROM users WHERE email='demo3@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '눈싸움챔피언', 105, now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000005'), (SELECT id FROM users WHERE email='demo5@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '졸린판다', 98, now() - interval '4 days', now() - interval '4 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000006'), (SELECT id FROM users WHERE email='demo7@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '윙크요정', 88, now() - interval '5 days', now() - interval '5 days');

-- 3-2) EYEFIGHT(눈싸움) 혼자하기 4판 (점수 = 버틴 초)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000007', (SELECT id FROM games WHERE game_name='EYEFIGHT' AND play_mode='SOLO'),
        '{"1":{"score":87}}', now() - interval '1 day 90 minutes', now() - interval '1 day 88 minutes', now() - interval '1 day', now() - interval '1 day'),
    ('dddddddd-dddd-4ddd-8ddd-000000000008', (SELECT id FROM games WHERE game_name='EYEFIGHT' AND play_mode='SOLO'),
        '{"1":{"score":62}}', now() - interval '2 days 45 minutes', now() - interval '2 days 44 minutes', now() - interval '2 days', now() - interval '2 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000009', (SELECT id FROM games WHERE game_name='EYEFIGHT' AND play_mode='SOLO'),
        '{"1":{"score":55}}', now() - interval '3 days 30 minutes', now() - interval '3 days 29 minutes', now() - interval '3 days', now() - interval '3 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000010', (SELECT id FROM games WHERE game_name='EYEFIGHT' AND play_mode='SOLO'),
        '{"1":{"score":43}}', now() - interval '4 days 60 minutes', now() - interval '4 days 59 minutes', now() - interval '4 days', now() - interval '4 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000007'), (SELECT id FROM users WHERE email='demo3@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '눈싸움챔피언', 87, now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000008'), (SELECT id FROM users WHERE email='demo4@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '시선강탈자', 62, now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000009'), (SELECT id FROM users WHERE email='demo6@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '매의눈', 55, now() - interval '3 days', now() - interval '3 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000010'), (SELECT id FROM users WHERE email='demo2@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '깜빡임의달인', 43, now() - interval '4 days', now() - interval '4 days');

-- 3-3) RHYTHM 혼자하기 4판 (점수 = 리듬 점수)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000011', (SELECT id FROM games WHERE game_name='RHYTHM' AND play_mode='SOLO'),
        '{"1":{"score":9500}}', now() - interval '1 day 120 minutes', now() - interval '1 day 118 minutes', now() - interval '1 day', now() - interval '1 day'),
    ('dddddddd-dddd-4ddd-8ddd-000000000012', (SELECT id FROM games WHERE game_name='RHYTHM' AND play_mode='SOLO'),
        '{"1":{"score":8800}}', now() - interval '2 days 100 minutes', now() - interval '2 days 98 minutes', now() - interval '2 days', now() - interval '2 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000013', (SELECT id FROM games WHERE game_name='RHYTHM' AND play_mode='SOLO'),
        '{"1":{"score":8200}}', now() - interval '3 days 80 minutes', now() - interval '3 days 78 minutes', now() - interval '3 days', now() - interval '3 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000014', (SELECT id FROM games WHERE game_name='RHYTHM' AND play_mode='SOLO'),
        '{"1":{"score":7600}}', now() - interval '5 days 40 minutes', now() - interval '5 days 38 minutes', now() - interval '5 days', now() - interval '5 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000011'), (SELECT id FROM users WHERE email='demo7@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '윙크요정', 9500, now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000012'), (SELECT id FROM users WHERE email='demo8@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '눈치백단', 8800, now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000013'), (SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '반짝이는눈망울', 8200, now() - interval '3 days', now() - interval '3 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000014'), (SELECT id FROM users WHERE email='demo9@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '레이저시선', 7600, now() - interval '5 days', now() - interval '5 days');

-- 3-4) DRAWING(눈으로 그리기) AI 채점 3판 (점수 = AI 채점 0~100)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000015', (SELECT id FROM games WHERE game_name='DRAWING' AND play_mode='AI'),
        '{"1":{"score":92}}', now() - interval '1 day 150 minutes', now() - interval '1 day 148 minutes', now() - interval '1 day', now() - interval '1 day'),
    ('dddddddd-dddd-4ddd-8ddd-000000000016', (SELECT id FROM games WHERE game_name='DRAWING' AND play_mode='AI'),
        '{"1":{"score":85}}', now() - interval '3 days 50 minutes', now() - interval '3 days 48 minutes', now() - interval '3 days', now() - interval '3 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000017', (SELECT id FROM games WHERE game_name='DRAWING' AND play_mode='AI'),
        '{"1":{"score":78}}', now() - interval '4 days 90 minutes', now() - interval '4 days 88 minutes', now() - interval '4 days', now() - interval '4 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000015'), (SELECT id FROM users WHERE email='demo4@eyedontcare.local'),  'USER', 1, 'COMPLETED', 1, '시선강탈자', 92, now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000016'), (SELECT id FROM users WHERE email='demo10@eyedontcare.local'), 'USER', 1, 'COMPLETED', 1, '안깜빡할게', 85, now() - interval '3 days', now() - interval '3 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000017'), (SELECT id FROM users WHERE email='demo5@eyedontcare.local'),  'USER', 1, 'COMPLETED', 1, '졸린판다', 78, now() - interval '4 days', now() - interval '4 days');

-- 3-5) HOCKEY 랜덤 매칭 3경기 (2인, 점수 = 골 수)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000018', (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='RANDOM'),
        '{"1":{"score":3},"2":{"score":1}}', now() - interval '1 day 180 minutes', now() - interval '1 day 178 minutes', now() - interval '1 day', now() - interval '1 day'),
    ('dddddddd-dddd-4ddd-8ddd-000000000019', (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='RANDOM'),
        '{"1":{"score":2},"2":{"score":3}}', now() - interval '2 days 140 minutes', now() - interval '2 days 138 minutes', now() - interval '2 days', now() - interval '2 days'),
    ('dddddddd-dddd-4ddd-8ddd-000000000020', (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='RANDOM'),
        '{"1":{"score":3},"2":{"score":0}}', now() - interval '3 days 110 minutes', now() - interval '3 days 108 minutes', now() - interval '3 days', now() - interval '3 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    -- 경기1: demo1 3 : 1 demo2
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000018'), (SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'USER', 1, 'WIN',  1, '반짝이는눈망울', 3, now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000018'), (SELECT id FROM users WHERE email='demo2@eyedontcare.local'), 'USER', 2, 'LOSE', 2, '깜빡임의달인', 1, now() - interval '1 day', now() - interval '1 day'),
    -- 경기2: demo3 2 : 3 demo4
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000019'), (SELECT id FROM users WHERE email='demo3@eyedontcare.local'), 'USER', 1, 'LOSE', 2, '눈싸움챔피언', 2, now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000019'), (SELECT id FROM users WHERE email='demo4@eyedontcare.local'), 'USER', 2, 'WIN',  1, '시선강탈자', 3, now() - interval '2 days', now() - interval '2 days'),
    -- 경기3: demo6 3 : 0 demo9
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000020'), (SELECT id FROM users WHERE email='demo6@eyedontcare.local'), 'USER', 1, 'WIN',  1, '매의눈', 3, now() - interval '3 days', now() - interval '3 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000020'), (SELECT id FROM users WHERE email='demo9@eyedontcare.local'), 'USER', 2, 'LOSE', 2, '레이저시선', 0, now() - interval '3 days', now() - interval '3 days');

-- 3-6) HOCKEY AI 대결 1경기 (AI 참가자: user_id NULL, participant_type='AI')
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000021', (SELECT id FROM games WHERE game_name='HOCKEY' AND play_mode='AI'),
        '{"1":{"score":2},"2":{"score":3}}', now() - interval '2 days 200 minutes', now() - interval '2 days 198 minutes', now() - interval '2 days', now() - interval '2 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000021'), (SELECT id FROM users WHERE email='demo5@eyedontcare.local'), 'USER', 1, 'LOSE', 2, '졸린판다', 2, now() - interval '2 days', now() - interval '2 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000021'), NULL, 'AI', 2, 'WIN', 1, 'AI 상대', 3, now() - interval '2 days', now() - interval '2 days');

-- 3-7) EYEFIGHT 친구 초대 대결 2경기 (점수 = 버틴 초)
INSERT INTO games_results (play_id, game_id, game_result, started_at, ended_at, created_at, updated_at) VALUES
    ('dddddddd-dddd-4ddd-8ddd-000000000022', (SELECT id FROM games WHERE game_name='EYEFIGHT' AND play_mode='INVITE'),
        '{"1":{"score":34},"2":{"score":29}}', now() - interval '1 day 220 minutes', now() - interval '1 day 219 minutes', now() - interval '1 day', now() - interval '1 day'),
    ('dddddddd-dddd-4ddd-8ddd-000000000023', (SELECT id FROM games WHERE game_name='EYEFIGHT' AND play_mode='INVITE'),
        '{"1":{"score":38},"2":{"score":41}}', now() - interval '4 days 130 minutes', now() - interval '4 days 129 minutes', now() - interval '4 days', now() - interval '4 days');

INSERT INTO participants_ (result_id, user_id, participant_type, slot_no, outcome, rank_no, display_name, score, created_at, updated_at) VALUES
    -- 경기1: demo3 34초 승 vs demo8 29초 패
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000022'), (SELECT id FROM users WHERE email='demo3@eyedontcare.local'), 'USER', 1, 'WIN',  1, '눈싸움챔피언', 34, now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000022'), (SELECT id FROM users WHERE email='demo8@eyedontcare.local'), 'USER', 2, 'LOSE', 2, '눈치백단', 29, now() - interval '1 day', now() - interval '1 day'),
    -- 경기2: demo7 38초 패 vs demo10 41초 승
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000023'), (SELECT id FROM users WHERE email='demo7@eyedontcare.local'),  'USER', 1, 'LOSE', 2, '윙크요정', 38, now() - interval '4 days', now() - interval '4 days'),
    ((SELECT id FROM games_results WHERE play_id='dddddddd-dddd-4ddd-8ddd-000000000023'), (SELECT id FROM users WHERE email='demo10@eyedontcare.local'), 'USER', 2, 'WIN',  1, '안깜빡할게', 41, now() - interval '4 days', now() - interval '4 days');

-- -----------------------------------------------------------------------------
-- 4) 주간 랭킹 변동 배지 (이번 주 월요일 기준)
-- -----------------------------------------------------------------------------
INSERT INTO ranking_trends (user_id, game_name, week_start, last_rank, trend, created_at, updated_at) VALUES
    ((SELECT id FROM users WHERE email='demo1@eyedontcare.local'), 'BLINK',    date_trunc('week', now())::date, 1, 'UP',   now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM users WHERE email='demo2@eyedontcare.local'), 'BLINK',    date_trunc('week', now())::date, 2, 'DOWN', now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM users WHERE email='demo3@eyedontcare.local'), 'EYEFIGHT', date_trunc('week', now())::date, 1, 'SAME', now() - interval '1 day', now() - interval '1 day'),
    ((SELECT id FROM users WHERE email='demo7@eyedontcare.local'), 'RHYTHM',   date_trunc('week', now())::date, 1, 'UP',   now() - interval '1 day', now() - interval '1 day');

COMMIT;

-- =============================================================================
-- 적재 결과 확인용 쿼리
-- =============================================================================
-- SELECT count(*) FROM users WHERE email LIKE 'demo%@eyedontcare.local';   -- 10
-- SELECT count(*) FROM games_results WHERE play_id::text LIKE 'dddddddd%'; -- 23
-- SELECT count(*) FROM participants_ p JOIN games_results r ON r.id=p.result_id
--   WHERE r.play_id::text LIKE 'dddddddd%';                                -- 30
-- SELECT g.game_name, g.play_mode, count(*) FROM games_results r
--   JOIN games g ON g.id=r.game_id WHERE r.play_id::text LIKE 'dddddddd%'
--   GROUP BY 1,2 ORDER BY 1,2;
