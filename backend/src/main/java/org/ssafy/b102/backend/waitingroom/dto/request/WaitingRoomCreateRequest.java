package org.ssafy.b102.backend.waitingroom.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

public class WaitingRoomCreateRequest {

	@NotBlank
	private String gameName;
	private final Map<String, Object> unknownFields = new LinkedHashMap<>();

	public String getGameName() {
		return gameName;
	}

	public void setGameName(String gameName) {
		this.gameName = gameName;
	}

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

	public boolean hasUnknownFields() {
		return !unknownFields.isEmpty();
	}
}
