package org.ssafy.b102.backend.waitingroom.support;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.UserRepository;

@Component
public class WaitingRoomParticipantResolver {

	private static final String MEMBER_KEY_PREFIX = "USER:";

	private final UserRepository userRepository;
	private final GuestSessionService guestSessionService;

	public WaitingRoomParticipantResolver(
		UserRepository userRepository,
		GuestSessionService guestSessionService
	) {
		this.userRepository = userRepository;
		this.guestSessionService = guestSessionService;
	}

	public ResolvedWaitingRoomParticipant resolve(
		AuthenticatedUser member,
		UUID guestSessionId
	) {
		if (member != null) {
			User user = userRepository.findByIdAndDeletedAtIsNull(member.userId())
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

			return ResolvedWaitingRoomParticipant.member(
				MEMBER_KEY_PREFIX + user.getId(),
				user.getNickname()
			);
		}

		GuestSession guestSession = guestSessionId == null
			? guestSessionService.issue()
			: guestSessionService.findById(guestSessionId)
				.orElseGet(guestSessionService::issue);

		return ResolvedWaitingRoomParticipant.guest(
			new GuestParticipantKey(guestSession.guestSessionId()).value(),
			guestSession.nickname(),
			guestSession.guestSessionId()
		);
	}
}
