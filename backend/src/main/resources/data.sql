-- 게임 카탈로그 시드
-- games 한 행 = (게임 × 플레이 모드 × 난이도) 조합
-- 플레이 모드는 매칭 경로까지 구분한다. SOLO(혼자하기) / INVITE(친구와 대결) / RANDOM(랜덤 매칭) / AI(AI 대결)
-- 난이도는 눈싸움 AI 대결에만 적용된다. (1 EASY / 2 NORMAL / 3 HARD)
--
-- 애플리케이션 기동마다 실행되므로 이미 존재하는 조합은 건너뛴다.
-- difficulty가 NULL인 조합도 중복 삽입되지 않도록 IS NOT DISTINCT FROM으로 비교한다.

INSERT INTO games (game_name, play_mode, difficulty, created_at, updated_at)
SELECT seed.game_name, seed.play_mode, seed.difficulty, now(), now()
FROM (
	VALUES
		-- 에어하키: 친구와 대결 / 랜덤 매칭 / AI 대결
		('HOCKEY', 'INVITE', NULL::int),
		('HOCKEY', 'RANDOM', NULL),
		('HOCKEY', 'AI', NULL),
		-- 눈싸움: 혼자하기 / 친구와 대결 / 랜덤 매칭 / AI 대결(난이도 3종)
		('EYEFIGHT', 'SOLO', NULL),
		('EYEFIGHT', 'INVITE', NULL),
		('EYEFIGHT', 'RANDOM', NULL),
		('EYEFIGHT', 'AI', 1),
		('EYEFIGHT', 'AI', 2),
		('EYEFIGHT', 'AI', 3),
		-- 그림 그리기: AI와 함께
		('DRAWING', 'AI', NULL),
		-- 리듬 게임: 혼자하기 / 친구와 대결 / 랜덤 매칭
		('RHYTHM', 'SOLO', NULL),
		('RHYTHM', 'INVITE', NULL),
		('RHYTHM', 'RANDOM', NULL),
		-- 눈 깜빡이기: 혼자하기 / 친구와 대결 / 랜덤 매칭
		('BLINK', 'SOLO', NULL),
		('BLINK', 'INVITE', NULL),
		('BLINK', 'RANDOM', NULL)
) AS seed(game_name, play_mode, difficulty)
WHERE NOT EXISTS (
	SELECT 1
	FROM games existing
	WHERE existing.game_name = seed.game_name
		AND existing.play_mode = seed.play_mode
		AND existing.difficulty IS NOT DISTINCT FROM seed.difficulty
);
