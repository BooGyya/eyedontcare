package org.ssafy.b102.backend.waitingroom.support;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.IntSupplier;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

	private static final int CODE_BOUND = 10_000;

	private final IntSupplier numberSupplier;

	public InviteCodeGenerator() {
		SecureRandom secureRandom = new SecureRandom();
		this.numberSupplier = () -> secureRandom.nextInt(CODE_BOUND);
	}

	InviteCodeGenerator(IntSupplier numberSupplier) {
		this.numberSupplier = numberSupplier;
	}

	public String generate() {
		int number = numberSupplier.getAsInt();
		if (number < 0 || number >= CODE_BOUND) {
			throw new IllegalStateException("Invite code number is out of range");
		}
		return String.format(Locale.ROOT, "%04d", number);
	}
}
