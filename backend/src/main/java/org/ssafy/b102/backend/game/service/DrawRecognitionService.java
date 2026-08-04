package org.ssafy.b102.backend.game.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.ssafy.b102.backend.game.config.GmsProperties;
import org.ssafy.b102.backend.game.dto.request.RecognizeDrawingRequest;
import org.ssafy.b102.backend.game.dto.response.RecognizeDrawingResponse;
import org.ssafy.b102.backend.game.exception.GameErrorCode;
import org.ssafy.b102.backend.global.error.BusinessException;

/**
 * 눈으로 그린 그림을 SSAFY GMS(OpenAI 호환 Vision)로 채점한다.
 *
 * <p>이미지(data URL)와 제시어·후보를 gpt-4o Vision에 넘겨 "무엇을 그렸는지"를 JSON으로 받아
 * 프론트 계약({@link RecognizeDrawingResponse})으로 변환한다. API 키가 없으면 미설정 오류,
 * GMS 호출/파싱이 실패하면 인식 실패 오류로 응답한다(조용히 가짜 성공으로 넘기지 않는다).
 */
@Service
public class DrawRecognitionService {

	private static final Logger log =
		LoggerFactory.getLogger(DrawRecognitionService.class);

	private static final String SYSTEM_PROMPT =
		"너는 사용자가 눈으로 그린 낙서를 보고 무엇을 그렸는지 판별하는 채점자야. "
			+ "반드시 지정된 JSON 형식으로만 답하고, 다른 말은 하지 마.";

	private final GmsProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public DrawRecognitionService(
		GmsProperties properties,
		ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.objectMapper = objectMapper;

		SimpleClientHttpRequestFactory requestFactory =
			new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(properties.baseUri())
			.build();
	}

	public RecognizeDrawingResponse recognize(RecognizeDrawingRequest request) {
		if (!properties.isConfigured()) {
			throw new BusinessException(
				GameErrorCode.DRAWING_RECOGNITION_NOT_CONFIGURED);
		}

		Recognition recognition = requestRecognition(request);

		boolean isTarget = recognition.isTarget() != null
			? recognition.isTarget()
			: normalize(recognition.label()).equals(normalize(request.prompt()));

		return new RecognizeDrawingResponse(
			recognition.label() == null ? "" : recognition.label(),
			clampConfidence(recognition.confidence()),
			isTarget,
			recognition.reason() == null ? "" : recognition.reason(),
			request.candidates(),
			properties.model()
		);
	}

	private Recognition requestRecognition(RecognizeDrawingRequest request) {
		String imageDataUrl = request.imageDataUrl();
		log.info("GMS 그림 인식 요청: model={} imageLen={} prefix={}",
			properties.model(),
			imageDataUrl == null ? 0 : imageDataUrl.length(),
			imageDataUrl == null
				? "null"
				: imageDataUrl.substring(0, Math.min(45, imageDataUrl.length())));

		Map<String, Object> body = Map.of(
			"model", properties.model(),
			"temperature", 0,
			"response_format", Map.of("type", "json_object"),
			"messages", List.of(
				Map.of("role", "system", "content", SYSTEM_PROMPT),
				Map.of("role", "user", "content", List.of(
					Map.of("type", "text", "text", userPrompt(request)),
					Map.of(
						"type", "image_url",
						"image_url", Map.of("url", request.imageDataUrl())
					)
				))
			)
		);

		try {
			ChatCompletionResponse response = restClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.header(
					HttpHeaders.AUTHORIZATION,
					"Bearer " + properties.apiKey()
				)
				.body(body)
				.retrieve()
				.body(ChatCompletionResponse.class);

			String content = response == null ? null : response.firstContent();
			if (content == null || content.isBlank()) {
				throw new BusinessException(
					GameErrorCode.DRAWING_RECOGNITION_FAILED);
			}
			return objectMapper.readValue(content, Recognition.class);
		} catch (BusinessException exception) {
			throw exception;
		} catch (RestClientException | tools.jackson.core.JacksonException exception) {
			if (exception instanceof RestClientResponseException responseException) {
				log.warn("GMS 그림 인식 실패: status={} body={}",
					responseException.getStatusCode(),
					responseException.getResponseBodyAsString());
			} else {
				log.warn("GMS 그림 인식 실패", exception);
			}
			throw new BusinessException(
				GameErrorCode.DRAWING_RECOGNITION_FAILED);
		}
	}

	private static String userPrompt(RecognizeDrawingRequest request) {
		return "제시어는 '" + request.prompt() + "'야. "
			+ "사용자가 눈으로 그린 이 그림이 다음 후보 중 무엇에 가장 가까운지 판단해: "
			+ String.join(", ", request.candidates()) + ". "
			+ "반드시 이 JSON 형식으로만 답해: "
			+ "{\"label\": 후보 중 하나, \"confidence\": 0~1 사이 숫자, "
			+ "\"isTarget\": 제시어와 같으면 true 아니면 false, "
			+ "\"reason\": 판단 근거 한국어 한 문장}.";
	}

	private static double clampConfidence(Double confidence) {
		if (confidence == null) {
			return 0;
		}
		return Math.min(Math.max(confidence, 0), 1);
	}

	private static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return text.trim().toLowerCase().replaceAll("\\s+", "");
	}

	/** GMS가 응답 content에 담아 주는 판정 JSON. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Recognition(
		String label,
		Double confidence,
		Boolean isTarget,
		String reason
	) {
	}

	/** OpenAI 호환 chat completions 응답(필요한 필드만). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ChatCompletionResponse(List<Choice> choices, String model) {

		String firstContent() {
			if (choices == null || choices.isEmpty()) {
				return null;
			}
			Choice choice = choices.get(0);
			return choice == null || choice.message() == null
				? null
				: choice.message().content();
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		private record Choice(Message message) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		private record Message(String content) {
		}
	}
}
