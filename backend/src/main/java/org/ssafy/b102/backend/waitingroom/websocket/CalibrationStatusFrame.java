package org.ssafy.b102.backend.waitingroom.websocket;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;

public class CalibrationStatusFrame {

	private String type;
	private CalibrationStatus calibrationStatus;
	private final Map<String, Object> unknownFields = new HashMap<>();

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public CalibrationStatus getCalibrationStatus() {
		return calibrationStatus;
	}

	public void setCalibrationStatus(CalibrationStatus calibrationStatus) {
		this.calibrationStatus = calibrationStatus;
	}

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

	public boolean hasUnknownFields() {
		return !unknownFields.isEmpty();
	}
}
