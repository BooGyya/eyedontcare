package org.ssafy.b102.backend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.user.repository.UserRepository;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final SecurityErrorResponseWriter responseWriter;

    public JwtAuthenticationFilter(
        JwtTokenProvider jwtTokenProvider,
        UserRepository userRepository,
        SecurityErrorResponseWriter responseWriter
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(
            HttpHeaders.AUTHORIZATION
        );

        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            rejectInvalidToken(response);
            return;
        }

        String token = authorization.substring(
            BEARER_PREFIX.length()
        );

        Optional<Long> userId =
            jwtTokenProvider.parseAccessTokenUserId(token);

        if (
            userId.isEmpty() ||
            !userRepository.existsByIdAndDeletedAtIsNull(
                userId.get()
            )
        ) {
            rejectInvalidToken(response);
            return;
        }

        AuthenticatedUser principal =
            new AuthenticatedUser(userId.get());

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
            );

        authentication.setDetails(
            new WebAuthenticationDetailsSource()
                .buildDetails(request)
        );

        SecurityContext context =
            SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private void rejectInvalidToken(
        HttpServletResponse response
    ) throws IOException {
        SecurityContextHolder.clearContext();
        responseWriter.write(
            response,
            SecurityErrorCode.INVALID_ACCESS_TOKEN
        );
    }
}
