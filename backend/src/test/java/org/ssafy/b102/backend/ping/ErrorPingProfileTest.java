package org.ssafy.b102.backend.ping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.ssafy.b102.backend.ping.controller.ErrorPingController;
import org.ssafy.b102.backend.ping.controller.PingController;
import org.ssafy.b102.backend.ping.service.ErrorPingService;
import org.ssafy.b102.backend.ping.service.PingService;

class ErrorPingProfileTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(
			PingService.class,
			PingController.class,
			ErrorPingService.class,
			ErrorPingController.class
		);

	@Test
	void devProfileRegistersErrorPingBeans() {
		contextRunner.withPropertyValues("spring.profiles.active=dev")
			.run(context -> {
				assertThat(context).hasSingleBean(PingController.class);
				assertThat(context).hasSingleBean(PingService.class);
				assertThat(context).hasSingleBean(ErrorPingController.class);
				assertThat(context).hasSingleBean(ErrorPingService.class);
			});
	}

	@Test
	void prodProfileDoesNotRegisterErrorPingBeans() {
		contextRunner.withPropertyValues("spring.profiles.active=prod")
			.run(context -> {
				assertThat(context).hasSingleBean(PingController.class);
				assertThat(context).hasSingleBean(PingService.class);
				assertThat(context).doesNotHaveBean(ErrorPingController.class);
				assertThat(context).doesNotHaveBean(ErrorPingService.class);
			});
	}
}
