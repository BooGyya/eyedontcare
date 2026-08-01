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
			return resolveMember(member);
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

	public ResolvedWaitingRoomParticipant resolveExisting(
		AuthenticatedUser member,
		UUID guestSessionId
	) {
		if (member != null) {
			return resolveMember(member);
		}

		GuestSession guestSession = guestSessionService.validate(guestSessionId);
		return ResolvedWaitingRoomParticipant.guest(
			new GuestParticipantKey(guestSession.guestSessionId()).value(),
			guestSession.nickname(),
			guestSession.guestSessionId()
		);
	}

	public ResolvedWaitingRoomParticipant resolveExisting(String participantKey) {
		if (participantKey != null && participantKey.startsWith(MEMBER_KEY_PREFIX)) {
			try {
				return resolveMember(
					new AuthenticatedUser(
						Long.parseLong(participantKey.substring(MEMBER_KEY_PREFIX.length()))
					)
				);
			} catch (NumberFormatException exception) {
				throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
			}
		}
		GuestParticipantKey guestKey = GuestParticipantKey.parse(participantKey);
		return resolveExisting(null, guestKey.guestSessionId());
	}

	private ResolvedWaitingRoomParticipant resolveMember(AuthenticatedUser member) {
		User user = userRepository.findByIdAndDeletedAtIsNull(member.userId())
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

		return ResolvedWaitingRoomParticipant.member(
			MEMBER_KEY_PREFIX + user.getId(),
			user.getNickname()
		);
	}
}
