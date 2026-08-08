package org.ssafy.b102.backend.guest.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.guest.GuestSessionSuccessCode;
import org.ssafy.b102.backend.guest.dto.response.GuestSessionResponse;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.ClientIpResolver;
import org.ssafy.b102.backend.guest.support.GuestSessionIssueRateLimiter;

/**
 * 게스트 신원을 단독으로 발급한다.
 *
 * <p>기존에는 게스트 세션이 대기방 생성·참가와 랜덤 매칭 응답에만 딸려 나왔다. 그래서 백엔드를
 * 거치지 않는 솔로·AI 모드로 바로 들어온 비로그인 사용자는 {@code participantKey}를 만들 수
 * 없었고, 게임 진입과 결과 저장이 모두 막혔다. 이 엔드포인트는 그 경로를 메운다 — 신원이 필요한
 * 클라이언트가 언제든 게스트 세션 하나를 확보할 수 있다.
 *
 * <p>회원 여부를 보지 않는다. 회원인지 아닌지는 클라이언트가 저장된 토큰으로 이미 판단해서,
 * 신원이 없을 때만 호출한다. 서버가 회원 요청을 거절하면 클라이언트에 쓸모없는 오류 처리만
 * 늘고, 발급된 게스트 세션은 어차피 회원 토큰에 밀려 쓰이지 않는다.
 */
@RestController
@RequestMapping("/api/v1/guests")
public class GuestSessionController {

	/** 클라이언트가 이미 가진 세션을 재사용하려 할 때 보내는 헤더. */
	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";

	private final GuestSessionService guestSessionService;
	private final GuestSessionIssueRateLimiter rateLimiter;

	public GuestSessionController(
		GuestSessionService guestSessionService,
		GuestSessionIssueRateLimiter rateLimiter
	) {
		this.guestSessionService = guestSessionService;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * 게스트 세션을 확보한다. 유효한 세션을 이미 갖고 있으면 그대로 돌려주고(200), 없거나
	 * 만료됐으면 새로 발급한다(201).
	 *
	 * <p>요청 제한은 <b>새로 발급할 때만</b> 건다. 재사용은 저장소를 늘리지 않으므로, 정상적으로
	 * 세션을 갖고 새로고침을 반복하는 사용자가 제한에 걸리면 안 된다.
	 */
	@PostMapping("/session")
	public ResponseEntity<ApiResponse<GuestSessionResponse>> ensureSession(
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) String guestSessionId,
		HttpServletRequest request
	) {
		Optional<GuestSession> reusable = findReusable(guestSessionId);
		if (reusable.isPresent()) {
			return respond(HttpStatus.OK, reusable.get());
		}

		rateLimiter.check(ClientIpResolver.resolve(request));

		return respond(HttpStatus.CREATED, guestSessionService.issue());
	}

	/**
	 * 헤더로 받은 세션 id 중 아직 살아 있는 것만 돌려준다.
	 *
	 * <p>⚠️ 헤더를 {@code UUID}로 직접 바인딩하지 않는다. 다른 엔드포인트와 달리 이곳은 신원을
	 * 확보하는 **첫 관문**이라, 저장소에 남은 깨진 값 하나로 400이 나면 클라이언트가 스스로
	 * 회복할 방법이 없다. 형식이 잘못된 값은 신원이 없는 것으로 보고 새로 발급한다.
	 */
	private Optional<GuestSession> findReusable(String guestSessionId) {
		if (guestSessionId == null || guestSessionId.isBlank()) {
			return Optional.empty();
		}

		try {
			return guestSessionService.findById(UUID.fromString(guestSessionId.trim()));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private ResponseEntity<ApiResponse<GuestSessionResponse>> respond(
		HttpStatus status,
		GuestSession guestSession
	) {
		return ResponseEntity.status(status).body(
			ApiResponse.success(
				GuestSessionSuccessCode.GUEST_SESSION_READY,
				GuestSessionResponse.from(guestSession)
			)
		);
	}
}
