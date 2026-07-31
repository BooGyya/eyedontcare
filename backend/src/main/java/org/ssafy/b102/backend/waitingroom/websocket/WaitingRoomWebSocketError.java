package org.ssafy.b102.backend.waitingroom.websocket;

import org.ssafy.b102.backend.global.error.ErrorCode;

public record WaitingRoomWebSocketError(String code, String message) {

	public static WaitingRoomWebSocketError from(ErrorCode errorCode) {
		return new WaitingRoomWebSocketError(errorCode.code(), errorCode.message());
	}
}
