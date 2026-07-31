package org.ssafy.b102.backend.waitingroom.websocket;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;

public class WaitingRoomAuthFrame {

	private String type;
	private String accessToken;
	private String guestSessionId;
	private final Map<String, Object> unknownFields = new HashMap<>();

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getGuestSessionId() {
		return guestSessionId;
	}

	public void setGuestSessionId(String guestSessionId) {
		this.guestSessionId = guestSessionId;
	}

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

	public boolean hasUnknownFields() {
		return !unknownFields.isEmpty();
	}
}
