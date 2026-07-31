package org.ssafy.b102.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.ssafy.b102.backend.matchmaking.support.MatchmakingRandomRematchAdapter;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequester;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private ObjectProvider<RandomRematchRequester> randomRematchRequesterProvider;

	@Test
	void contextLoads() {
		assertThat(randomRematchRequesterProvider.getIfAvailable())
			.isInstanceOf(MatchmakingRandomRematchAdapter.class);
	}
}
