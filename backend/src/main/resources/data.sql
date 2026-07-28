-- 게임 카탈로그 시드
-- games 한 행 = (게임 × 플레이 모드 × 난이도) 조합
-- 난이도는 눈싸움 AI 대결에만 적용된다. (1 EASY / 2 NORMAL / 3 HARD)
-- 친구와 대결과 랜덤 매칭은 모두 MULTI이며 waiting_rooms.room_type으로 구분한다.
--
-- 애플리케이션 기동마다 실행되므로 이미 존재하는 조합은 건너뛴다.
-- difficulty가 NULL인 조합도 중복 삽입되지 않도록 IS NOT DISTINCT FROM으로 비교한다.

INSERT INTO games (game_name, play_mode, difficulty, created_at, updated_at)
SELECT seed.game_name, seed.play_mode, seed.difficulty, now(), now()
FROM (
	VALUES
		('HOCKEY', 'MULTI', NULL::int),
		('HOCKEY', 'AI', NULL),
		('EYEFIGHT', 'SOLO', NULL),
		('EYEFIGHT', 'MULTI', NULL),
		('EYEFIGHT', 'AI', 1),
		('EYEFIGHT', 'AI', 2),
		('EYEFIGHT', 'AI', 3),
		('DRAWING', 'AI', NULL),
		('RHYTHM', 'SOLO', NULL),
		('RHYTHM', 'MULTI', NULL),
		('BLINK', 'SOLO', NULL),
		('BLINK', 'MULTI', NULL)
) AS seed(game_name, play_mode, difficulty)
WHERE NOT EXISTS (
	SELECT 1
	FROM games existing
	WHERE existing.game_name = seed.game_name
		AND existing.play_mode = seed.play_mode
		AND existing.difficulty IS NOT DISTINCT FROM seed.difficulty
);
