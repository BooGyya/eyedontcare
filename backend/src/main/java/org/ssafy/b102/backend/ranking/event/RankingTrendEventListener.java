package org.ssafy.b102.backend.ranking.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ssafy.b102.backend.gameresult.event.GameResultSubmittedEvent;
import org.ssafy.b102.backend.ranking.service.RankingTrendService;

/**
 * 경기 결과 저장 후 랭킹 순위 변동을 갱신하는 리스너.
 *
 * <p>결과가 커밋된 뒤(AFTER_COMMIT) 처리해, 새 결과가 반영된 순위를 보고 변동을 계산한다.
 * 변동 갱신이 실패해도 이미 커밋된 경기 결과에는 영향을 주지 않는다.
 */
@Component
public class RankingTrendEventListener {

	private final RankingTrendService rankingTrendService;

	public RankingTrendEventListener(RankingTrendService rankingTrendService) {
		this.rankingTrendService = rankingTrendService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onGameResultSubmitted(GameResultSubmittedEvent event) {
		rankingTrendService.recordTrends(
			event.gameName(),
			event.playMode(),
			event.memberUserIds()
		);
	}
}
