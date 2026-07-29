package org.ssafy.b102.backend.matchmaking.entity;

public enum MatchStatus {

	/**
	 * 매칭 큐에서 상대를 기다리는 중.
	 */
	SEARCHING,

	/**
	 * 매칭이 성사되어 대기방으로 이동하는 중.
	 */
	ENTERING_ROOM,

	/**
	 * 대기방 WebSocket 연결에 성공해 입장이 확인된 상태. WaitingRoom이 전환시킨다.
	 */
	IN_WAITING_ROOM,

	/**
	 * 취소 응답에만 쓰는 값. Redis에 저장되지 않는다.
	 *
	 * <p>취소는 entry를 삭제하므로 저장될 자리가 없다. ERD의 {@code match_status}에도 없는 값이며
	 * API 명세서의 취소 응답({@code matchStatus: CANCELLED})을 맞추기 위해서만 존재한다.
	 */
	CANCELLED
}
