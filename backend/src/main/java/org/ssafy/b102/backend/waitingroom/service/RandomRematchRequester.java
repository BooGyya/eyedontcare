package org.ssafy.b102.backend.waitingroom.service;

import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

public interface RandomRematchRequester {

	RandomRematchRequestResult requeueRemaining(
		UUID previousRoomId,
		GameName gameName,
		String participantKey
	);
}
