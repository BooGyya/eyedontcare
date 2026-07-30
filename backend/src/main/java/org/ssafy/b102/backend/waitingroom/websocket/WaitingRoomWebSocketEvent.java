package org.ssafy.b102.backend.waitingroom.websocket;

public record WaitingRoomWebSocketEvent<T>(String type, T data) {

	public static WaitingRoomWebSocketEvent<WaitingRoomRoomState> roomState(
		WaitingRoomRoomState data
	) {
		return new WaitingRoomWebSocketEvent<>("ROOM_STATE", data);
	}

	public static WaitingRoomWebSocketEvent<WaitingRoomWebSocketError> error(
		WaitingRoomWebSocketError data
	) {
		return new WaitingRoomWebSocketEvent<>("ERROR", data);
	}
}
