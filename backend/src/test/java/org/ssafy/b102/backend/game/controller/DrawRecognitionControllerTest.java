package org.ssafy.b102.backend.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.game.config.GmsProperties;
import org.ssafy.b102.backend.game.dto.request.RecognizeDrawingRequest;
import org.ssafy.b102.backend.game.dto.response.RecognizeDrawingResponse;
import org.ssafy.b102.backend.game.exception.GameErrorCode;
import org.ssafy.b102.backend.game.service.DrawRecognitionService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;

class DrawRecognitionControllerTest {

	@Test
	void 그림을_인식하면_200과_결과를_반환한다() throws Exception {
		StubService service = new StubService();
		service.response = new RecognizeDrawingResponse(
			"달", 0.92, true, "초승달 모양이 뚜렷하다.",
			List.of("달", "산", "하트"), "gpt-4o"
		);

		mockMvc(service)
			.perform(post("/api/v1/games/draw/recognize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"imageDataUrl":"data:image/png;base64,AAAA",
					 "prompt":"달","candidates":["달","산","하트"]}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("DRAWING_RECOGNIZED"))
			.andExpect(jsonPath("$.data.label").value("달"))
			.andExpect(jsonPath("$.data.isTarget").value(true))
			.andExpect(jsonPath("$.data.model").value("gpt-4o"));
	}

	@Test
	void 이미지가_없으면_COMMON_001이다() throws Exception {
		mockMvc(new StubService())
			.perform(post("/api/v1/games/draw/recognize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"달\",\"candidates\":[\"달\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void 미설정이면_503_GAME_003이다() throws Exception {
		StubService service = new StubService();
		service.toThrow = new BusinessException(
			GameErrorCode.DRAWING_RECOGNITION_NOT_CONFIGURED);

		mockMvc(service)
			.perform(post("/api/v1/games/draw/recognize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"imageDataUrl":"data:image/png;base64,AAAA",
					 "prompt":"달","candidates":["달"]}"""))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("GAME-003"));
	}

	@Test
	void GMS_호출_실패는_502_GAME_002이다() throws Exception {
		StubService service = new StubService();
		service.toThrow = new BusinessException(
			GameErrorCode.DRAWING_RECOGNITION_FAILED);

		mockMvc(service)
			.perform(post("/api/v1/games/draw/recognize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"imageDataUrl":"data:image/png;base64,AAAA",
					 "prompt":"달","candidates":["달"]}"""))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("GAME-002"));
	}

	private MockMvc mockMvc(DrawRecognitionService service) {
		return MockMvcBuilders
			.standaloneSetup(new DrawRecognitionController(service))
			.setValidator(validator())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static LocalValidatorFactoryBean validator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		return validator;
	}

	/** GMS를 실제로 부르지 않도록 recognize()만 스텁으로 대체한다(외부 호출 없이 컨트롤러만 검증). */
	private static class StubService extends DrawRecognitionService {

		private RecognizeDrawingResponse response;
		private BusinessException toThrow;

		private StubService() {
			super(
				new GmsProperties(
					"https://gms.example/v1/chat/completions",
					"test-key",
					"gpt-4o",
					Duration.ofSeconds(1),
					Duration.ofSeconds(1)
				),
				null
			);
		}

		@Override
		public RecognizeDrawingResponse recognize(
			RecognizeDrawingRequest request
		) {
			if (toThrow != null) {
				throw toThrow;
			}
			return response;
		}
	}
}
