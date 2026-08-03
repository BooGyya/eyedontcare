package org.ssafy.b102.backend.group.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class GroupCodeGeneratorTest {

	private final GroupCodeGenerator generator = new GroupCodeGenerator();

	@RepeatedTest(50)
	void 코드는_허용된_문자로만_구성된_6자리다() {
		String code = generator.generate();

		assertThat(code).hasSize(6);
		assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
	}
}
