package org.ssafy.b102.backend.waitingroom.websocket;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;

public class StartGameFrame {

	private String type;
	private final Map<String, Object> unknownFields = new HashMap<>();

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

	public boolean hasUnknownFields() {
		return !unknownFields.isEmpty();
	}
}
