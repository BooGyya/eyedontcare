package org.ssafy.b102.backend.waitingroom.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.waitingroom.config.WaitingRoomProperties;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.repository.CreateRandomRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.RoomIdGenerator;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;

@Component
public class WaitingRoomRandomRoomCreator implements RandomRoomCreator {

	private final GameService gameService;
	private final WaitingRoomParticipantResolver participantResolver;
	private final WaitingRoomStore waitingRoomStore;
	private final WaitingRoomProperties properties;
	private final RoomIdGenerator roomIdGenerator;
	private final Clock clock;

	public WaitingRoomRandomRoomCreator(
		GameService gameService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties,
		RoomIdGenerator roomIdGenerator
	) {
		this.gameService = gameService;
		this.participantResolver = participantResolver;
		this.waitingRoomStore = waitingRoomStore;
		this.properties = properties;
		this.roomIdGenerator = roomIdGenerator;
		this.clock = Clock.systemUTC();
	}

	@Override
	public Optional<UUID> createRandomRoom(
		GameName gameName,
		List<String> participantKeys
	) {
		RandomRoomParticipants keys = RandomRoomParticipants.from(participantKeys);
		if (!gameService.supportsPlayMode(gameName, PlayMode.RANDOM)) {
			return Optional.empty();
		}
		ResolvedWaitingRoomParticipant first =
			participantResolver.resolveExisting(keys.firstParticipantKey());
		ResolvedWaitingRoomParticipant second =
			participantResolver.resolveExisting(keys.secondParticipantKey());
		UUID roomId = roomIdGenerator.generate();
		Instant now = clock.instant();
		WaitingRoom room = new WaitingRoom(
			roomId,
			RoomType.RANDOM,
			gameName,
			null,
			RoomStatus.WAITING,
			now
		);
		List<WaitingRoomParticipant> participants = List.of(
			participant(first, 1, now),
			participant(second, 2, now)
		);
		return waitingRoomStore.createRandomRoomAtomically(
			new CreateRandomRoomCommand(room, participants, properties.activeTtl())
		)
			? Optional.of(roomId)
			: Optional.empty();
	}

	private WaitingRoomParticipant participant(
		ResolvedWaitingRoomParticipant identity,
		int slotNo,
		Instant joinedAt
	) {
		return new WaitingRoomParticipant(
			identity.participantKey(),
			identity.displayName(),
			RoomRole.PLAYER,
			slotNo,
			false,
			CalibrationStatus.PENDING,
			joinedAt
		);
	}
}
