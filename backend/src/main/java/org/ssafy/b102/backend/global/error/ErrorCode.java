package org.ssafy.b102.backend.global.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	HttpStatus status();

	String code();

	String message();
}
