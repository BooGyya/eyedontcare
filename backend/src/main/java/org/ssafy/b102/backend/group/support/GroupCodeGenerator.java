package org.ssafy.b102.backend.group.support;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 소모임 입장 코드 생성기.
 *
 * <p>혼동하기 쉬운 문자를 제외한 대문자·숫자 6자리를 만든다. 유일성 검증(중복 재시도)은
 * 서비스가 담당하며, 여기서는 후보 코드만 만든다.
 */
@Component
public class GroupCodeGenerator {

	private static final char[] ALPHABET =
		"ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int CODE_LENGTH = 6;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder builder = new StringBuilder(CODE_LENGTH);
		for (int index = 0; index < CODE_LENGTH; index++) {
			builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return builder.toString();
	}
}
