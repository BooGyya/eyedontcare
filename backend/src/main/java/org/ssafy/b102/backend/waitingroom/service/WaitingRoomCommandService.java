package org.ssafy.b102.backend.waitingroom.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.waitingroom.config.WaitingRoomProperties;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.CompleteCountdownCommand;
import org.ssafy.b102.backend.waitingroom.repository.CompleteCountdownResult;
import org.ssafy.b102.backend.waitingroom.repository.RollbackCountdownCommand;
import org.ssafy.b102.backend.waitingroom.repository.RollbackCountdownResult;
import org.ssafy.b102.backend.waitingroom.repository.RandomReadyResult;
import org.ssafy.b102.backend.waitingroom.repository.StartInviteGameCommand;
import org.ssafy.b102.backend.waitingroom.repository.StartInviteGameResult;
import org.ssafy.b102.backend.waitingroom.repository.UpdateCalibrationCommand;
import org.ssafy.b102.backend.waitingroom.repository.UpdateCalibrationResult;
import org.ssafy.b102.backend.waitingroom.repository.UpdateReadyCommand;
import org.ssafy.b102.backend.waitingroom.repository.UpdateReadyResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;

@Service
public class WaitingRoomCommandService {

	private final WaitingRoomService waitingRoomService;
	private final WaitingRoomStore waitingRoomStore;
	private final WaitingRoomProperties properties;
	private final Clock clock;

	@Autowired
	public WaitingRoomCommandService(
		WaitingRoomService waitingRoomService,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties
	) {
		this(
			waitingRoomService,
			waitingRoomStore,
			properties,
			Clock.systemUTC()
		);
	}

	WaitingRoomCommandService(
		WaitingRoomService waitingRoomService,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties,
		Clock clock
	) {
		this.waitingRoomService = waitingRoomService;
		this.waitingRoomStore = waitingRoomStore;
		this.properties = properties;
		this.clock = clock;
	}

	public boolean updateCalibration(
		UUID roomId,
		String participantKey,
		CalibrationStatus calibrationStatus
	) {
		WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(roomId);
		UpdateCalibrationResult result =
			waitingRoomStore.updateCalibrationAtomically(
				new UpdateCalibrationCommand(
					roomId,
					snapshot.room().roomCode(),
					participantKey,
					calibrationStatus,
					properties.maxParticipants(),
					properties.activeTtl()
				)
			);
		return switch (result) {
			case UPDATED -> true;
			case UNCHANGED -> false;
			case ROOM_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case STATE_CHANGE_NOT_ALLOWED ->
				throw error(WaitingRoomErrorCode.STATE_CHANGE_NOT_ALLOWED);
			case CORRUPTED ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	public boolean updateReady(
		UUID roomId,
		String participantKey,
		boolean ready
	) {
		WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(roomId);
		UpdateReadyResult result = waitingRoomStore.updateReadyAtomically(
			new UpdateReadyCommand(
				roomId,
				snapshot.room().roomCode(),
				participantKey,
				ready,
				properties.maxParticipants(),
				properties.activeTtl()
			)
		);
		return switch (result) {
			case UPDATED -> true;
			case UNCHANGED -> false;
			case ROOM_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case CALIBRATION_REQUIRED ->
				throw error(WaitingRoomErrorCode.CALIBRATION_REQUIRED);
			case STATE_CHANGE_NOT_ALLOWED ->
				throw error(WaitingRoomErrorCode.STATE_CHANGE_NOT_ALLOWED);
			case CORRUPTED ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	public ReadyCommandResult updateReadyAndStartRandom(
		UUID roomId,
		String participantKey,
		boolean ready
	) {
		WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(roomId);
		if (snapshot.room().roomType() != org.ssafy.b102.backend.waitingroom.entity.RoomType.RANDOM) {
			return new ReadyCommandResult(
				updateReady(roomId, participantKey, ready),
				false,
				null,
				null,
				snapshot.room().roomCode()
			);
		}
		UUID countdownId = UUID.randomUUID();
		Instant countdownEndsAt =
			clock.instant().plus(properties.countdownDuration());
		RandomReadyResult result = waitingRoomStore.updateRandomReadyAtomically(
			new UpdateReadyCommand(
				roomId,
				null,
				participantKey,
				ready,
				properties.maxParticipants(),
				properties.activeTtl()
			),
			countdownId,
			countdownEndsAt
		);
		return switch (result.status()) {
			case UPDATED ->
				new ReadyCommandResult(true, false, null, null, null);
			case UNCHANGED ->
				new ReadyCommandResult(false, false, null, null, null);
			case COUNTDOWN_STARTED, ALREADY_COUNTDOWN ->
				new ReadyCommandResult(
					result.status() == RandomReadyResult.Status.COUNTDOWN_STARTED,
					true,
					result.countdownId(),
					result.countdownEndsAt(),
					null
				);
			case ROOM_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case CALIBRATION_REQUIRED ->
				throw error(WaitingRoomErrorCode.CALIBRATION_REQUIRED);
			case STATE_CHANGE_NOT_ALLOWED ->
				throw error(WaitingRoomErrorCode.STATE_CHANGE_NOT_ALLOWED);
			case CORRUPTED ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	public StartCommandResult startGame(
		UUID roomId,
		String participantKey
	) {
		WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(roomId);
		if (snapshot.room().roomType() == org.ssafy.b102.backend.waitingroom.entity.RoomType.RANDOM) {
			throw error(WaitingRoomErrorCode.STATE_CHANGE_NOT_ALLOWED);
		}
		UUID countdownId = UUID.randomUUID();
		Instant countdownEndsAt =
			clock.instant().plus(properties.countdownDuration());
		StartInviteGameResult result =
			waitingRoomStore.startInviteGameAtomically(
				new StartInviteGameCommand(
					roomId,
					snapshot.room().roomCode(),
					participantKey,
					countdownId,
					countdownEndsAt,
					properties.maxParticipants(),
					properties.activeTtl()
				)
			);

		return switch (result.status()) {
			case STARTED, ALREADY_COUNTDOWN ->
				new StartCommandResult(
					result.status() == StartInviteGameResult.Status.STARTED,
					snapshot.room().roomCode(),
					result.countdownId(),
					result.countdownEndsAt()
				);
			case ROOM_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw error(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case CALIBRATION_REQUIRED ->
				throw error(WaitingRoomErrorCode.CALIBRATION_REQUIRED);
			case GAME_START_FORBIDDEN ->
				throw error(WaitingRoomErrorCode.GAME_START_FORBIDDEN);
			case PARTICIPANTS_NOT_READY ->
				throw error(WaitingRoomErrorCode.PARTICIPANTS_NOT_READY);
			case STATE_CHANGE_NOT_ALLOWED ->
				throw error(WaitingRoomErrorCode.STATE_CHANGE_NOT_ALLOWED);
			case CORRUPTED ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	public boolean completeCountdown(
		UUID roomId,
		String roomCode,
		UUID countdownId,
		Instant countdownEndsAt
	) {
		CompleteCountdownResult result =
			waitingRoomStore.completeCountdownAtomically(
				new CompleteCountdownCommand(
					roomId,
					roomCode,
					countdownId,
					countdownEndsAt,
					properties.maxParticipants(),
					properties.activeTtl()
				)
			);
		return switch (result) {
			case STARTED -> true;
			case STALE, ROOM_CLOSED, INVALID_STATE -> false;
			case CORRUPTED ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	public boolean rollbackCountdown(
		UUID roomId,
		String roomCode,
		UUID countdownId
	) {
		RollbackCountdownResult result =
			waitingRoomStore.rollbackCountdownAtomically(
				new RollbackCountdownCommand(
					roomId,
					roomCode,
					countdownId,
					properties.activeTtl()
				)
			);
		return switch (result) {
			case ROLLED_BACK -> true;
			case STALE -> false;
			case CORRUPTED ->
				throw error(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	private static BusinessException error(WaitingRoomErrorCode errorCode) {
		return new BusinessException(errorCode);
	}

	public record StartCommandResult(
		boolean started,
		String roomCode,
		UUID countdownId,
		Instant countdownEndsAt
	) {
	}

	public record ReadyCommandResult(
		boolean changed,
		boolean countdown,
		UUID countdownId,
		Instant countdownEndsAt,
		String roomCode
	) {
	}
}
