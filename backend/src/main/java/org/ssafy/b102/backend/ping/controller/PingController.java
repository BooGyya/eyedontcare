package org.ssafy.b102.backend.ping.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.ping.dto.PingResponse;
import org.ssafy.b102.backend.ping.service.PingService;

@RestController
@RequestMapping("/api/ping")
public class PingController {

	private final PingService pingService;

	public PingController(PingService pingService) {
		this.pingService = pingService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PingResponse>> ping() {
		return ResponseEntity.ok(ApiResponse.success(pingService.ping()));
	}
}
