package org.ssafy.b102.backend.global.common.response;

public record ValidationError(
    String field,
    String reason
) {
}
