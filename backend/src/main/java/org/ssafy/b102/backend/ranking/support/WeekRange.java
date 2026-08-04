package org.ssafy.b102.backend.ranking.support;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 주간 랭킹 기간(KST 월요일 0시 ~ 다음 월요일 0시).
 *
 * <p>{@code start}(포함) ~ {@code end}(제외)로 이번 주 경기를 거른다. {@code weekStart}는
 * 응답용 주 시작 날짜(KST)다.
 */
public record WeekRange(Instant start, Instant end, LocalDate weekStart) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static WeekRange current(Instant now) {
		ZonedDateTime kstNow = now.atZone(KST);
		LocalDate monday = kstNow.toLocalDate()
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		ZonedDateTime start = monday.atStartOfDay(KST);
		ZonedDateTime end = start.plusWeeks(1);

		return new WeekRange(start.toInstant(), end.toInstant(), monday);
	}
}
