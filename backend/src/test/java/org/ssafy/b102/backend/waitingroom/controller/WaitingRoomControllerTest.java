package org.ssafy.b102.backend.waitingroom.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomCreateResponse;
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
}
