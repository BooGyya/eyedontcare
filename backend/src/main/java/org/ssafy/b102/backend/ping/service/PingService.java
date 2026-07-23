package org.ssafy.b102.backend.ping.service;

import org.springframework.stereotype.Service;
import org.ssafy.b102.backend.ping.dto.PingResponse;

@Service
public class PingService {

	public PingResponse ping() {
		return new PingResponse("pong");
	}
}
