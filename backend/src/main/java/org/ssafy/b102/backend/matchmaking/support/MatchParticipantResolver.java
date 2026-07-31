package org.ssafy.b102.backend.matchmaking.support;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;

/**
 * 매칭 신청자의 신원을 참가자 키로 푼다.
 *
 * <p>회원은 JWT principal({@link AuthenticatedUser})에서 {@code USER:{userId}}를 만든다.
 * 클라이언트가 보낸 문자열을 믿지 않고 인증된 principal에서만 만든다.
 *
 * <p>게스트는 기존 세션 id가 유효하면 재사용하고, 없거나 만료됐으면 새로 발급한다.
 * 발급·조회는 공용 {@link GuestSessionService}에만 맡기며, 여기서 UUID나 닉네임을 직접 만들지 않는다.
 * 회원 요청에는 게스트 세션을 발급하지 않는다.
 */
@Component
public class MatchParticipantResolver {

	private static final String MEMBER_KEY_PREFIX = "USER:";

	private final GuestSessionService guestSessionService;

	public MatchParticipantResolver(GuestSessionService guestSessionService) {
		this.guestSessionService = guestSessionService;
	}

	public ResolvedParticipant resolveForJoin(AuthenticatedUser member, UUID guestSessionId) {
		if (member != null) {
			return ResolvedParticipant.member(MEMBER_KEY_PREFIX + member.userId());
		}

		GuestSession session = resolveOrIssueGuest(guestSessionId);

		return ResolvedParticipant.guest(
			new GuestParticipantKey(session.guestSessionId()).value(),
			session.guestSessionId(),
			session.nickname()
		);
	}

	/**
	 * 취소처럼 이미 신청이 있는 경우의 참가자 키를 푼다. 발급하지 않는다.
	 *
	 * <p>게스트는 세션 id를 반드시 함께 보내야 한다. 없으면 누구인지 알 수 없으므로 거절한다.
	 * 존재하지 않는 세션 id로 취소를 시도하면 뒤이은 조회가 신청 없음으로 처리한다.
	 */
	public String resolveExistingKey(AuthenticatedUser member, UUID guestSessionId) {
		if (member != null) {
			return MEMBER_KEY_PREFIX + member.userId();
		}
		if (guestSessionId == null) {
			throw new BusinessException(MatchmakingErrorCode.INVALID_PARTICIPANT_KEY);
		}

		return new GuestParticipantKey(guestSessionId).value();
	}

	private GuestSession resolveOrIssueGuest(UUID guestSessionId) {
		if (guestSessionId != null) {
			return guestSessionService.findById(guestSessionId)
				.orElseGet(guestSessionService::issue);
		}

		return guestSessionService.issue();
	}
}
