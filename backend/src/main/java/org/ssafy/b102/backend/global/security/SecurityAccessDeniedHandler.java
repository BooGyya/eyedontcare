package org.ssafy.b102.backend.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityAccessDeniedHandler
    implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter responseWriter;

    public SecurityAccessDeniedHandler(
        SecurityErrorResponseWriter responseWriter
    ) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception
    ) throws IOException {
        responseWriter.write(
            response,
            SecurityErrorCode.ACCESS_DENIED
        );
    }
}
