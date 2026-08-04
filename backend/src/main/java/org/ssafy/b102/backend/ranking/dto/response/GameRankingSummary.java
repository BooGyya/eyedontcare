package org.ssafy.b102.backend.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.ranking.support.RankType;

/**
 * 홈 요약의 게임 한 개 랭킹(상위 몇 명 + 내 순위).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameRankingSummary(
	GameName gameName,
	RankType rankType,
	String unit,
	List<RankingEntry> top,
	MyRank myRank
) {
}
