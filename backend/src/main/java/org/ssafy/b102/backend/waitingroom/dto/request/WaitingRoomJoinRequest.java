package org.ssafy.b102.backend.waitingroom.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;

public class WaitingRoomJoinRequest {

	@NotBlank
	@Pattern(regexp = "[0-9]{4}")
	private String roomCode;
	private final Map<String, Object> unknownFields = new LinkedHashMap<>();

	public String getRoomCode() {
		return roomCode;
	}

	public void setRoomCode(String roomCode) {
		this.roomCode = roomCode;
	}

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

	public boolean hasUnknownFields() {
		return !unknownFields.isEmpty();
	}
}
