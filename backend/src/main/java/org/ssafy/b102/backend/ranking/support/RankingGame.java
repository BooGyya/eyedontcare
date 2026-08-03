package org.ssafy.b102.backend.ranking.support;

import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.ranking.exception.RankingErrorCode;

/**
 * 게임별 랭킹 계약.
 *
 * <p>어느 모드가 랭킹 대상인지, 무엇으로 순위를 매기는지(rankType), 표시 단위를 담는다.
 * BEST_SCORE 게임은 {@code game_result[slot].score} 최고값, 에어하키는 승리 횟수로 랭킹한다.
 */
public enum RankingGame {

	HOCKEY(GameName.HOCKEY, PlayMode.RANDOM, RankType.WIN_COUNT, "win"),
	EYEFIGHT(GameName.EYEFIGHT, PlayMode.SOLO, RankType.BEST_SCORE, "second"),
	DRAWING(GameName.DRAWING, PlayMode.AI, RankType.BEST_SCORE, "point"),
	RHYTHM(GameName.RHYTHM, PlayMode.SOLO, RankType.BEST_SCORE, "point"),
	BLINK(GameName.BLINK, PlayMode.SOLO, RankType.BEST_SCORE, "count");

	private final GameName gameName;
	private final PlayMode rankedMode;
	private final RankType rankType;
	private final String unit;

	RankingGame(
		GameName gameName,
		PlayMode rankedMode,
		RankType rankType,
		String unit
	) {
		this.gameName = gameName;
		this.rankedMode = rankedMode;
		this.rankType = rankType;
		this.unit = unit;
	}

	public static RankingGame of(String gameName) {
		if (gameName == null) {
			throw new BusinessException(RankingErrorCode.INVALID_GAME);
		}
		try {
			return of(GameName.valueOf(gameName));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(RankingErrorCode.INVALID_GAME);
		}
	}

	public static RankingGame of(GameName gameName) {
		for (RankingGame game : values()) {
			if (game.gameName == gameName) {
				return game;
			}
		}
		throw new BusinessException(RankingErrorCode.INVALID_GAME);
	}

	public GameName gameName() {
		return gameName;
	}

	public PlayMode rankedMode() {
		return rankedMode;
	}

	public RankType rankType() {
		return rankType;
	}

	public String unit() {
		return unit;
	}
}
