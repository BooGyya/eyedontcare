package org.ssafy.b102.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

class SecurityAccessDeniedHandlerTest {

    @Test
    void 접근_거부를_ApiResponse_형식으로_반환한다()
        throws Exception {

        SecurityErrorResponseWriter responseWriter =
            new SecurityErrorResponseWriter(
                JsonMapper.builder().build()
            );

        SecurityAccessDeniedHandler handler =
            new SecurityAccessDeniedHandler(responseWriter);

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        handler.handle(
            new MockHttpServletRequest(),
            response,
            new AccessDeniedException("denied")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType())
            .startsWith("application/json");
        assertThat(response.getContentAsString())
            .contains("\"code\":\"SECURITY-003\"")
            .contains("\"message\":\"접근 권한이 없습니다.\"")
            .doesNotContain("\"data\"");
    }
}
