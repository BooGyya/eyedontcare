package org.ssafy.b102.backend.waitingroom.websocket;

import java.time.Instant;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

public record WaitingRoomGameStart(
	UUID roomId,
	GameName gameName,
	Instant startedAt
) {
}
