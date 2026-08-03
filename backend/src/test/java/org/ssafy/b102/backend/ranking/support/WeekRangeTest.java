package org.ssafy.b102.backend.ranking.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WeekRangeTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Test
	void 이번_주는_KST_월요일_0시에_시작해_7일간이다() {
		Instant now = Instant.parse("2026-08-05T10:00:00Z");

		WeekRange week = WeekRange.current(now);

		assertThat(week.weekStart().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(week.start())
			.isEqualTo(week.weekStart().atStartOfDay(KST).toInstant());
		assertThat(week.end())
			.isEqualTo(week.start().plus(java.time.Duration.ofDays(7)));
	}

	@Test
	void now는_이번_주_범위_안에_있다() {
		Instant now = Instant.parse("2026-08-05T10:00:00Z");

		WeekRange week = WeekRange.current(now);

		assertThat(now).isAfterOrEqualTo(week.start());
		assertThat(now).isBefore(week.end());
	}

	@Test
	void KST_월요일_0시_직전은_지난_주에_속한다() {
		WeekRange week = WeekRange.current(Instant.parse("2026-08-05T10:00:00Z"));
		Instant justBeforeMonday = week.start().minusNanos(1);

		WeekRange previous = WeekRange.current(justBeforeMonday);

		assertThat(previous.weekStart()).isEqualTo(week.weekStart().minusDays(7));
	}
}
