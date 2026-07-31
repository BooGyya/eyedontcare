package org.ssafy.b102.backend.waitingroom.service;

import java.util.List;
import java.util.UUID;

public interface RandomRoomLifecyclePort {

	void markParticipantEntered(UUID roomId, String participantKey);

	void completeRandomRoom(UUID roomId, List<String> participantKeys);
}
