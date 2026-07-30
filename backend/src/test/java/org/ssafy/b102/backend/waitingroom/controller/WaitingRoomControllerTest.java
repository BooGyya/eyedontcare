package org.ssafy.b102.backend.waitingroom.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomCreateResponse;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomJoinResponse;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomParticipantResponse;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomService;

@ExtendWith(MockitoExtension.class)
class WaitingRoomControllerTest {

	private static final String PATH = "/api/v1/waiting-rooms";

	@Mock
	private WaitingRoomService waitingRoomService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders
			.standaloneSetup(new WaitingRoomController(waitingRoomService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void returnsCreatedResponseAndGuestIdentity() throws Exception {
		when(waitingRoomService.createInviteRoom(any(), any(), any()))
			.thenReturn(response(true));

		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameName\":\"EYEFIGHT\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("WAITING_ROOM_CREATE_SUCCESS"))
			.andExpect(jsonPath("$.message").value("초대방 생성이 완료되었습니다."))
			.andExpect(jsonPath("$.data.roomType").value("INVITE"))
			.andExpect(jsonPath("$.data.roomCode").value("0123"))
			.andExpect(jsonPath("$.data.participant.roomRole").value("HOST"))
			.andExpect(jsonPath("$.data.participant.isReady").value(false))
			.andExpect(jsonPath("$.data.guestSessionId").exists())
			.andExpect(jsonPath("$.data.guestNickname").value("게스트닉네임"));
	}

	@Test
	void memberResponseOmitsGuestFields() throws Exception {
		when(waitingRoomService.createInviteRoom(any(), any(), any()))
			.thenReturn(response(false));

		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameName\":\"BLINK\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.guestSessionId").doesNotExist())
			.andExpect(jsonPath("$.data.guestNickname").doesNotExist());
	}

	@Test
	void missingGameNameReturnsCommon001() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void malformedJsonReturnsCommon002() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameName\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-002"));
	}

	@Test
	void mapsWaitingRoomErrors() throws Exception {
		when(waitingRoomService.createInviteRoom(any(), any(), any()))
			.thenThrow(new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));

		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameName\":\"EYEFIGHT\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("WAITING-003"));
	}

	@Test
	void joinReturnsRoomSnapshot() throws Exception {
		when(waitingRoomService.joinInviteRoom(any(), any(), any()))
			.thenReturn(joinResponse());

		mockMvc.perform(post(PATH + "/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roomCode\":\"0123\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("WAITING_ROOM_JOIN_SUCCESS"))
			.andExpect(jsonPath("$.message").value("초대방 입장이 완료되었습니다."))
			.andExpect(jsonPath("$.data.roomId")
				.value("c93c76b2-7f78-4275-b8af-7cdd921bbb4f"))
			.andExpect(jsonPath("$.data.participants.length()").value(2))
			.andExpect(jsonPath("$.data.participants[0].slotNo").value(1))
			.andExpect(jsonPath("$.data.participants[1].slotNo").value(2))
			.andExpect(jsonPath("$.data.guestSessionId").exists())
			.andExpect(jsonPath("$.data.guestNickname").value("게스트닉네임"));
	}

	@Test
	void missingOrNullJoinCodeReturnsCommon001() throws Exception {
		mockMvc.perform(post(PATH + "/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));

		mockMvc.perform(post(PATH + "/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roomCode\":null}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "123", "12345", "12a3", " 123", "123 "})
	void invalidJoinCodeFormatReturnsCommon001(String roomCode) throws Exception {
		mockMvc.perform(post(PATH + "/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roomCode\":\"" + roomCode + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0000", "0123", "9999"})
	void validJoinCodeFormatReachesService(String roomCode) throws Exception {
		when(waitingRoomService.joinInviteRoom(any(), any(), any()))
			.thenReturn(joinResponse());

		mockMvc.perform(post(PATH + "/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roomCode\":\"" + roomCode + "\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void joinMapsMalformedAndDomainErrors() throws Exception {
		assertJoinError(CommonErrorCode.MALFORMED_JSON, 400, "COMMON-002");
		assertJoinError(WaitingRoomErrorCode.INVALID_INVITE_CODE, 404, "WAITING-004");
		assertJoinError(WaitingRoomErrorCode.WAITING_ROOM_FULL, 409, "WAITING-005");
		assertJoinError(WaitingRoomErrorCode.PARTICIPANT_ALREADY_JOINED, 409, "WAITING-006");
		assertJoinError(WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE, 409, "WAITING-007");
		assertJoinError(
			WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE,
			503,
			"WAITING-003"
		);
	}

	@Test
	void leaveReturnsSuccessWithoutData() throws Exception {
		mockMvc.perform(post(
				PATH + "/{roomId}/leave",
				"c93c76b2-7f78-4275-b8af-7cdd921bbb4f"
			))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("WAITING_ROOM_LEAVE_SUCCESS"))
			.andExpect(jsonPath("$.message").value("대기방 퇴장이 완료되었습니다."))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void invalidLeaveRoomIdReturnsCommon004() throws Exception {
		mockMvc.perform(post(PATH + "/not-a-uuid/leave"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-004"));
	}

	@Test
	void leaveMapsDomainErrors() throws Exception {
		assertLeaveError(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND, 404, "WAITING-008");
		assertLeaveError(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND, 404, "WAITING-009");
		assertLeaveError(
			WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE,
			503,
			"WAITING-003"
		);
	}

	private void assertLeaveError(
		org.ssafy.b102.backend.global.error.ErrorCode errorCode,
		int httpStatus,
		String code
	) throws Exception {
		reset(waitingRoomService);
		org.mockito.Mockito.doThrow(new BusinessException(errorCode))
			.when(waitingRoomService)
			.leave(any(), any(), any());

		mockMvc.perform(post(
				PATH + "/{roomId}/leave",
				"c93c76b2-7f78-4275-b8af-7cdd921bbb4f"
			))
			.andExpect(status().is(httpStatus))
			.andExpect(jsonPath("$.code").value(code));
	}

	private void assertJoinError(
		org.ssafy.b102.backend.global.error.ErrorCode errorCode,
		int httpStatus,
		String code
	) throws Exception {
		reset(waitingRoomService);
		when(waitingRoomService.joinInviteRoom(any(), any(), any()))
			.thenThrow(new BusinessException(errorCode));

		mockMvc.perform(post(PATH + "/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roomCode\":\"0123\"}"))
			.andExpect(status().is(httpStatus))
			.andExpect(jsonPath("$.code").value(code));
	}

	private WaitingRoomCreateResponse response(boolean guest) {
		UUID guestId = guest ? UUID.fromString("7e329e72-e8da-4c62-8282-754e7b5c0864") : null;
		return new WaitingRoomCreateResponse(
			UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f"),
			"INVITE",
			"EYEFIGHT",
			"0123",
			"WAITING",
			new WaitingRoomParticipantResponse(
				guest ? "GUEST:" + guestId : "USER:1",
				guest ? "게스트닉네임" : "회원닉네임",
				"HOST",
				1,
				false,
				"PENDING",
				Instant.parse("2026-07-30T04:00:00Z")
			),
			Instant.parse("2026-07-30T04:00:00Z"),
			guestId,
			guest ? "게스트닉네임" : null
		);
	}

	private WaitingRoomJoinResponse joinResponse() {
		UUID guestId = UUID.fromString("7e329e72-e8da-4c62-8282-754e7b5c0864");
		Instant createdAt = Instant.parse("2026-07-30T04:00:00Z");
		return new WaitingRoomJoinResponse(
			UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f"),
			"INVITE",
			"EYEFIGHT",
			"0123",
			"WAITING",
			List.of(
				new WaitingRoomParticipantResponse(
					"USER:1",
					"회원닉네임",
					"HOST",
					1,
					false,
					"PENDING",
					createdAt
				),
				new WaitingRoomParticipantResponse(
					"GUEST:" + guestId,
					"게스트닉네임",
					"PLAYER",
					2,
					false,
					"PENDING",
					createdAt.plusSeconds(60)
				)
			),
			createdAt,
			guestId,
			"게스트닉네임"
		);
	}
}
