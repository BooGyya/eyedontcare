package org.ssafy.b102.backend.gameresult.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;
import org.ssafy.b102.backend.gameresult.entity.Participant;

/**
 * 내 경기 기록 목록 응답.
 *
 * <p>{@code page}는 1부터 시작한다. Spring Data의 0-based 인덱스는 서비스에서 변환한다.
 */
public record MyGameResultPageResponse(
	List<MyGameResultResponse> content,
	int page,
	int size,
	long totalElements
) {

	public MyGameResultPageResponse {
		content = content == null ? List.of() : List.copyOf(content);
	}

	public static MyGameResultPageResponse from(Page<Participant> page) {
		return new MyGameResultPageResponse(
			page.getContent().stream().map(MyGameResultResponse::from).toList(),
			page.getNumber() + 1,
			page.getSize(),
			page.getTotalElements()
		);
	}
}
