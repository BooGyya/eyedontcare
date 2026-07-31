package org.ssafy.b102.backend.waitingroom.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InviteCodeGeneratorTest {

	@ParameterizedTest
	@CsvSource({
		"0, 0000",
		"123, 0123",
		"9999, 9999"
	})
	void preservesFourDigitFormat(int number, String expected) {
		InviteCodeGenerator generator = new InviteCodeGenerator(() -> number);

		assertThat(generator.generate()).isEqualTo(expected);
	}
}
