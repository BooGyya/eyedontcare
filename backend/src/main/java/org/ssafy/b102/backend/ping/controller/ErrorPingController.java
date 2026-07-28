package org.ssafy.b102.backend.ping.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.ping.dto.ErrorPingRequest;
import org.ssafy.b102.backend.ping.dto.PingResponse;
import org.ssafy.b102.backend.ping.service.ErrorPingService;
import org.ssafy.b102.backend.ping.service.PingService;

@Profile("dev")
@RestController
@RequestMapping("/api/ping/errors")
public class ErrorPingController {

	private final ErrorPingService errorPingService;
	private final PingService pingService;

	public ErrorPingController(ErrorPingService errorPingService, PingService pingService) {
		this.errorPingService = errorPingService;
		this.pingService = pingService;
	}

	@GetMapping("/business")
	public ResponseEntity<ApiResponse<PingResponse>> businessError() {
		errorPingService.throwBusinessException();
		return ResponseEntity.ok(ApiResponse.success(pingService.ping()));
	}

	@PostMapping("/validation")
	public ResponseEntity<ApiResponse<PingResponse>> validationError(
		@Valid @RequestBody ErrorPingRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(pingService.ping()));
	}

	@GetMapping("/unexpected")
	public ResponseEntity<ApiResponse<PingResponse>> unexpectedError() {
		errorPingService.throwUnexpectedException();
		return ResponseEntity.ok(ApiResponse.success(pingService.ping()));
	}
}
