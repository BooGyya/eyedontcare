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
 * <p>이미지(data URL)와 후보 목록을 gpt-4o Vision에 넘겨 "무엇을 그렸는지"를 JSON으로 받아
 * 프론트 계약({@link RecognizeDrawingResponse})으로 변환한다. API 키가 없으면 미설정 오류,
 * GMS 호출/파싱이 실패하면 인식 실패 오류로 응답한다(조용히 가짜 성공으로 넘기지 않는다).
 *
 * <p><b>모델에게 제시어(정답)를 알려주지 않는다.</b> 예전엔 "제시어는 '{prompt}'야, 이 그림이
 * 맞는지 판단해"처럼 정답을 먼저 알려주고 확인을 요청했는데, 이러면 그림이 애매하거나 심지어
 * 텅 비어 있어도 모델이 "정답이라고 알려준 그거"를 그냥 그대로 돌려주는 편향(정답을 미리 아는
 * 채점자가 왜곡되기 쉬운 것과 같은 현상)이 생겨서, "아무거나 그려도/안 그려도 정답 처리"되는
 * 문제의 원인이 됐다. 그래서 지금은 모델에게 후보 목록만 주고 순수하게 "이 그림이 뭘로 보이는지"
 * 고르게 한 다음, 그 결과(label)와 실제 제시어(prompt)가 같은지는 백엔드가 직접 비교한다
 * ({@link #recognize}) — 모델이 스스로 매긴 isTarget 값은 신뢰하지 않는다.
 */
@Service
public class DrawRecognitionService {

	private static final Logger log =
		LoggerFactory.getLogger(DrawRecognitionService.class);

	/** 모델이 이 미만의 확신으로 답하면, label이 우연히 제시어와 같아도 정답으로 인정하지 않는다. */
	private static final double MIN_CONFIDENCE_TO_ACCEPT = 0.35;

	private static final String SYSTEM_PROMPT =
		"너는 사용자가 눈으로 그린 낙서를 보고 무엇을 그렸는지 냉정하게 분류하는 채점자야. "
			+ "그림이 흔들리거나 엉성해도 괜찮지만, 반드시 그림 자체만 보고 판단해. "
			+ "그림에 아무것도 그려져 있지 않거나, 후보 중 어느 것과도 뚜렷하게 비슷하지 않으면 "
			+ "label을 '알 수 없음'으로, confidence를 0.3 미만으로 답해 — 억지로 후보 중 하나를 "
			+ "골라서 맞춰주려 하지 마. 반드시 지정된 JSON 형식으로만 답하고, 다른 말은 하지 마.";

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
		double confidence = clampConfidence(recognition.confidence());

		// 모델에게 정답을 미리 안 알려줬으니(요청 프롬프트에 request.prompt()가 없다), 모델이
		// 스스로 매긴 isTarget 값은 이제 애초에 없다(모델에게 물어보지도 않는다) — label과
		// 제시어가 실제로 같은지는 여기서 백엔드가 직접 정규화 비교로 판정한다. confidence가
		// 너무 낮으면(모델 스스로 "잘 모르겠다"는 뜻) label이 우연히 같아도 정답으로 인정하지
		// 않는다 — 애매한 그림이 통과되는 걸 한 번 더 막는 안전장치다.
		boolean isTarget = confidence >= MIN_CONFIDENCE_TO_ACCEPT
			&& normalize(recognition.label()).equals(normalize(request.prompt()));

		return new RecognizeDrawingResponse(
			recognition.label() == null ? "" : recognition.label(),
			confidence,
			isTarget,
			recognition.reason() == null ? "" : recognition.reason(),
			request.candidates(),
			properties.model()
		);
	}

	private Recognition requestRecognition(RecognizeDrawingRequest request) {
		String imageDataUrl = request.imageDataUrl();
		log.debug("GMS 그림 인식 요청: model={} imageLen={} prefix={}",
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

		ChatCompletionResponse response = callGmsWithRetry(body);

		String content = response == null ? null : response.firstContent();
		if (content == null || content.isBlank()) {
			throw new BusinessException(GameErrorCode.DRAWING_RECOGNITION_FAILED);
		}
		try {
			return objectMapper.readValue(content, Recognition.class);
		} catch (tools.jackson.core.JacksonException exception) {
			log.warn("GMS 응답 파싱 실패", exception);
			throw new BusinessException(GameErrorCode.DRAWING_RECOGNITION_FAILED);
		}
	}

	/**
	 * GMS를 호출한다. 게이트웨이의 일시적 5xx·429는 한 번 재시도해 간헐적 502를 완화하고,
	 * 연결 실패·읽기 타임아웃은 이미 시간을 소모했으므로 재시도하지 않고 바로 실패 처리한다.
	 */
	private ChatCompletionResponse callGmsWithRetry(Map<String, Object> body) {
		int maxAttempts = 2;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return restClient.post()
					.contentType(MediaType.APPLICATION_JSON)
					.header(
						HttpHeaders.AUTHORIZATION,
						"Bearer " + properties.apiKey()
					)
					.body(body)
					.retrieve()
					.body(ChatCompletionResponse.class);
			} catch (RestClientResponseException exception) {
				boolean transientError =
					exception.getStatusCode().is5xxServerError()
						|| exception.getStatusCode().value() == 429;
				log.warn("GMS 호출 실패: status={} attempt={}/{} body={}",
					exception.getStatusCode(), attempt, maxAttempts,
					exception.getResponseBodyAsString());
				if (transientError && attempt < maxAttempts) {
					continue;
				}
				throw new BusinessException(GameErrorCode.DRAWING_RECOGNITION_FAILED);
			} catch (RestClientException exception) {
				log.warn("GMS 호출 실패(연결/타임아웃) attempt={}",
					attempt, exception);
				throw new BusinessException(GameErrorCode.DRAWING_RECOGNITION_FAILED);
			}
		}
		// 루프에서 항상 반환하거나 예외를 던지므로 도달하지 않는다. 방어적으로 둔다.
		throw new BusinessException(GameErrorCode.DRAWING_RECOGNITION_FAILED);
	}

	/**
	 * 제시어(정답)를 일부러 언급하지 않는다 — 모델이 그림 자체가 아니라 "정답이라고 알려준 말"에
	 * 이끌려 답하는 편향을 막기 위함이다. 어떤 게 정답인지는 백엔드({@link #recognize})가
	 * label과 실제 제시어를 비교해서 판정한다.
	 */
	private static String userPrompt(RecognizeDrawingRequest request) {
		return "사용자가 눈으로 그린 이 낙서 그림을 봐. "
			+ "다음 후보 목록 중 이 그림이 무엇에 가장 가깝게 보이는지 판단해: "
			+ String.join(", ", request.candidates()) + ". "
			+ "그림이 너무 엉성하거나 후보 중 확실히 비슷한 게 없으면 label을 '알 수 없음'으로 "
			+ "답해. 반드시 이 JSON 형식으로만 답해: "
			+ "{\"label\": 후보 중 하나 또는 '알 수 없음', \"confidence\": 0~1 사이 숫자, "
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
