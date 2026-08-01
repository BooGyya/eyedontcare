package org.ssafy.b102.backend.waitingroom.websocket;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;

public class ReadyStatusFrame {

	private String type;
	private Boolean ready;
	private final Map<String, Object> unknownFields = new HashMap<>();

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Boolean getIsReady() {
		return ready;
	}

	public void setIsReady(Boolean ready) {
		this.ready = ready;
	}

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

	public boolean hasUnknownFields() {
		return !unknownFields.isEmpty();
	}
}
