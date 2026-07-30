package org.ssafy.b102.backend.waitingroom.repository;

public interface WaitingRoomStore {

	CreateInviteRoomResult createInviteRoomAtomically(CreateInviteRoomCommand command);
}
