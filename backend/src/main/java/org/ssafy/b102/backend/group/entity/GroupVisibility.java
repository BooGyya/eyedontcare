package org.ssafy.b102.backend.group.entity;

/**
 * 소모임 공개 범위.
 *
 * <p>{@code PUBLIC}은 목록·검색에 노출되고, {@code PRIVATE}은 코드로만 입장할 수 있다.
 */
public enum GroupVisibility {
	PUBLIC,
	PRIVATE
}
