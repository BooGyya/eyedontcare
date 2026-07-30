package org.ssafy.b102.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.ssafy.b102.backend.global.security.JwtAuthenticationFilter;
import org.ssafy.b102.backend.global.security.SecurityAccessDeniedHandler;
import org.ssafy.b102.backend.global.security.SecurityAuthenticationEntryPoint;
import org.ssafy.b102.backend.global.security.SecurityErrorResponseWriter;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.user.repository.UserRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CorsConfigurationSource corsConfigurationSource,
        JwtTokenProvider jwtTokenProvider,
        UserRepository userRepository,
        SecurityErrorResponseWriter responseWriter,
        SecurityAuthenticationEntryPoint authenticationEntryPoint,
        SecurityAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
            new JwtAuthenticationFilter(
                jwtTokenProvider,
                userRepository,
                responseWriter
            );

        http
            .cors(cors -> cors.configurationSource(
                corsConfigurationSource
            ))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/login/kakao",
                    "/api/v1/auth/reissue",
                    "/api/v1/match/join",
                    "/api/v1/game-results",
                    "/api/v1/waiting-rooms",
                    "/api/v1/waiting-rooms/join",
                    "/api/v1/waiting-rooms/{roomId}/leave"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/v1/match/cancel"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/games",
                    "/api/v1/games/{gameId}"
                ).permitAll()
                .requestMatchers("/api/ping/**").permitAll()
                // WebSocket handshake만 공개하며, 실제 JWT 인증은 별도 작업에서 구현한다.
                .requestMatchers("/ws/match").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
