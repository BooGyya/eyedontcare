package org.ssafy.b102.backend.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.global.error.BusinessException;

class KakaoOAuthClientTest {

    private static final String TOKEN_URI =
        "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI =
        "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer
            .bindTo(builder)
            .build();

        client = new KakaoOAuthClient(
            builder.build(),
            new KakaoProperties(
                "client-id",
                "client-secret",
                "http://localhost/callback",
                TOKEN_URI,
                USER_INFO_URI,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
            )
        );
    }

    @Test
    void 인가_코드를_교환하고_카카오_사용자_ID를_반환한다() {
        server.expect(once(), requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "access_token": "kakao-access-token"
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        server.expect(once(), requestTo(USER_INFO_URI))
            .andExpect(method(HttpMethod.GET))
            .andExpect(
                header(
                    "Authorization",
                    "Bearer kakao-access-token"
                )
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": 123456789
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        KakaoUserIdentity identity =
            client.authenticate("authorization-code");

        assertThat(identity.providerUserId())
            .isEqualTo("123456789");
        server.verify();
    }

    @Test
    void 잘못된_인가_코드는_AUTH_006이다() {
        server.expect(once(), requestTo(TOKEN_URI))
            .andRespond(withResourceNotFound());

        assertErrorCode(
            AuthErrorCode.KAKAO_AUTHENTICATION_FAILED
        );
        server.verify();
    }

    @Test
    void 토큰_응답에_Access_Token이_없으면_AUTH_006이다() {
        server.expect(once(), requestTo(TOKEN_URI))
            .andRespond(
                withSuccess("{}", MediaType.APPLICATION_JSON)
            );

        assertErrorCode(
            AuthErrorCode.KAKAO_AUTHENTICATION_FAILED
        );
        server.verify();
    }

    @Test
    void 카카오_서버_오류는_AUTH_007이다() {
        server.expect(once(), requestTo(TOKEN_URI))
            .andRespond(withServerError());

        assertErrorCode(AuthErrorCode.KAKAO_SERVER_ERROR);
        server.verify();
    }

    private void assertErrorCode(AuthErrorCode errorCode) {
        assertThatThrownBy(
            () -> client.authenticate("authorization-code")
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(
                ((BusinessException) exception).getErrorCode()
            ).isEqualTo(errorCode));
    }
}
