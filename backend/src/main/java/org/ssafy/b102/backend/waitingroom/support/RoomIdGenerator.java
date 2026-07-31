package org.ssafy.b102.backend.waitingroom.support;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoomIdGenerator {

	public UUID generate() {
		return UUID.randomUUID();
	}
}
