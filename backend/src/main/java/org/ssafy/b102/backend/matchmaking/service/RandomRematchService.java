package org.ssafy.b102.backend.matchmaking.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.matchmaking.repository.RematchRegistrationResult;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequestResult;

@Service
public class RandomRematchService {

	private static final String USER_KEY_PREFIX = "USER:";
	private static final String GUEST_KEY_PREFIX = "GUEST:";

	private final MatchmakingEntryRepository matchmakingEntryRepository;
	private final UserRepository userRepository;
	private final GuestSessionService guestSessionService;
	private final Clock clock;

	@Autowired
	public RandomRematchService(
		MatchmakingEntryRepository matchmakingEntryRepository,
		UserRepository userRepository,
		GuestSessionService guestSessionService
	) {
		this(
			matchmakingEntryRepository,
			userRepository,
			guestSessionService,
			Clock.systemUTC()
		);
	}

	RandomRematchService(
		MatchmakingEntryRepository matchmakingEntryRepository,
		UserRepository userRepository,
		GuestSessionService guestSessionService,
		Clock clock
	) {
		this.matchmakingEntryRepository = matchmakingEntryRepository;
		this.userRepository = userRepository;
		this.guestSessionService = guestSessionService;
		this.clock = clock;
	}

	public RandomRematchRequestResult requeueRemaining(
		UUID previousRoomId,
		GameName gameName,
		String participantKey
	) {
		if (!isParticipantValid(participantKey)) {
			return RandomRematchRequestResult.PARTICIPANT_INVALID;
		}

		RematchRegistrationResult result = matchmakingEntryRepository.requeueRemaining(
			previousRoomId,
			gameName,
			participantKey,
			clock.instant()
		);

		return switch (result) {
			case REQUEUED -> RandomRematchRequestResult.REQUEUED;
			case ALREADY_REQUEUED -> RandomRematchRequestResult.ALREADY_REQUEUED;
			case STALE -> RandomRematchRequestResult.FAILED;
		};
	}

	private boolean isParticipantValid(String participantKey) {
		if (participantKey == null) {
			return false;
		}
		if (participantKey.startsWith(USER_KEY_PREFIX)) {
			try {
				long userId = Long.parseLong(participantKey.substring(USER_KEY_PREFIX.length()));
				return userRepository.existsByIdAndDeletedAtIsNull(userId);
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		if (participantKey.startsWith(GUEST_KEY_PREFIX)) {
			try {
				return guestSessionService.exists(
					GuestParticipantKey.parse(participantKey).guestSessionId()
				);
			} catch (RuntimeException exception) {
				return false;
			}
		}
		return false;
	}
}
