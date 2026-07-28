package org.ssafy.b102.backend.global.common.response;

import java.util.List;

public record ValidationErrorResponse(List<ValidationError> fieldErrors) {

    public ValidationErrorResponse {
        fieldErrors = fieldErrors == null
            ? List.of()
            : List.copyOf(fieldErrors);
    }
}
