package org.ssafy.b102.backend.matchmaking.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.guest.config.GuestSessionProperties;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;

/**
 * 매칭 신청 시 "이 사람이 누구인지"를 참가자 키로 푸는 로직을 Spring 없이 단위 테스트한다.
 *
 * <p>회원은 JWT principal에서 {@code USER:{userId}}로, 게스트는 세션에서 {@code GUEST:{uuid}}로 푼다.
 * 게스트 세션 발급·조회는 손으로 만든 {@link GuestSessionService} 하위 클래스 스텁으로 대신한다.
 */
class MatchParticipantResolverTest {

	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Test
	void memberIsResolvedFromJwtPrincipal() {
		StubGuestSessionService guest = new StubGuestSessionService();
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		ResolvedParticipant resolved = resolver.resolveForJoin(new AuthenticatedUser(1L), null);

		assertThat(resolved.participantKey()).isEqualTo("USER:1");
		assertThat(resolved.isGuest()).isFalse();
		assertThat(guest.issueCalls()).isZero();
	}

	/**
	 * 회원 요청에는 게스트 세션을 발급하지 않는다. 게스트 헤더가 섞여 와도 회원이 우선한다.
	 */
	@Test
	void memberNeverIssuesGuestSessionEvenIfGuestIdPresent() {
		StubGuestSessionService guest = new StubGuestSessionService();
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		ResolvedParticipant resolved = resolver.resolveForJoin(new AuthenticatedUser(7L), UUID.randomUUID());

		assertThat(resolved.participantKey()).isEqualTo("USER:7");
		assertThat(guest.issueCalls()).isZero();
	}

	@Test
	void guestWithoutSessionIdIssuesNewSession() {
		UUID newId = UUID.randomUUID();
		StubGuestSessionService guest = new StubGuestSessionService();
		guest.willIssue(new GuestSession(newId, "졸린너구리", NOW, NOW.plus(Duration.ofHours(24))));
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		ResolvedParticipant resolved = resolver.resolveForJoin(null, null);

		assertThat(resolved.isGuest()).isTrue();
		assertThat(resolved.participantKey()).isEqualTo("GUEST:" + newId);
		assertThat(resolved.guestSessionId()).isEqualTo(newId);
		assertThat(resolved.guestNickname()).isEqualTo("졸린너구리");
		assertThat(guest.issueCalls()).isEqualTo(1);
	}

	@Test
	void guestWithValidExistingSessionIsReused() {
		UUID existingId = UUID.randomUUID();
		StubGuestSessionService guest = new StubGuestSessionService();
		guest.hasExisting(new GuestSession(existingId, "용감한수달", NOW, NOW.plus(Duration.ofHours(24))));
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		ResolvedParticipant resolved = resolver.resolveForJoin(null, existingId);

		assertThat(resolved.participantKey()).isEqualTo("GUEST:" + existingId);
		assertThat(resolved.guestNickname()).isEqualTo("용감한수달");
		assertThat(guest.issueCalls()).isZero();
	}

	/**
	 * 만료됐거나 Redis에 없는 세션 id가 오면(=조회 실패) 새로 발급한다.
	 * 만료된 세션을 그대로 재사용해서는 안 된다.
	 */
	@Test
	void guestWithExpiredOrMissingSessionIssuesNewSession() {
		UUID staleId = UUID.randomUUID();
		UUID freshId = UUID.randomUUID();
		StubGuestSessionService guest = new StubGuestSessionService();
		guest.willIssue(new GuestSession(freshId, "새너구리", NOW, NOW.plus(Duration.ofHours(24))));
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		ResolvedParticipant resolved = resolver.resolveForJoin(null, staleId);

		assertThat(resolved.guestSessionId()).isEqualTo(freshId);
		assertThat(guest.issueCalls()).isEqualTo(1);
	}

	@Test
	void resolveExistingKeyForMemberDoesNotIssue() {
		StubGuestSessionService guest = new StubGuestSessionService();
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		String key = resolver.resolveExistingKey(new AuthenticatedUser(3L), null);

		assertThat(key).isEqualTo("USER:3");
		assertThat(guest.issueCalls()).isZero();
	}

	@Test
	void resolveExistingKeyForGuestDoesNotIssue() {
		UUID id = UUID.randomUUID();
		StubGuestSessionService guest = new StubGuestSessionService();
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		String key = resolver.resolveExistingKey(null, id);

		assertThat(key).isEqualTo("GUEST:" + id);
		assertThat(guest.issueCalls()).isZero();
	}

	@Test
	void resolveExistingKeyRejectsGuestWithoutSessionId() {
		StubGuestSessionService guest = new StubGuestSessionService();
		MatchParticipantResolver resolver = new MatchParticipantResolver(guest);

		assertThatThrownBy(() -> resolver.resolveExistingKey(null, null))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(MatchmakingErrorCode.INVALID_PARTICIPANT_KEY));
	}

	private static final class StubGuestSessionService extends GuestSessionService {

		private GuestSession existing;
		private GuestSession toIssue;
		private int issueCalls;

		private StubGuestSessionService() {
			super(null, null, new GuestSessionProperties(Duration.ofHours(24)));
		}

		void hasExisting(GuestSession session) {
			this.existing = session;
		}

		void willIssue(GuestSession session) {
			this.toIssue = session;
		}

		int issueCalls() {
			return issueCalls;
		}

		@Override
		public Optional<GuestSession> findById(UUID guestSessionId) {
			return Optional.ofNullable(existing);
		}

		@Override
		public GuestSession issue() {
			issueCalls++;
			return toIssue;
		}
	}
}
