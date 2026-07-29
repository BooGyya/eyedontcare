package org.ssafy.b102.backend.auth.kakao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.global.error.BusinessException;

@Component
public class KakaoOAuthClient {

    private static final String AUTHORIZATION_CODE =
        "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient restClient;
    private final KakaoProperties properties;

    @Autowired
    public KakaoOAuthClient(KakaoProperties properties) {
        this(
            createRestClient(properties),
            properties
        );
    }

    KakaoOAuthClient(
        RestClient restClient,
        KakaoProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient createRestClient(
        KakaoProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
            properties.connectTimeout()
        );
        requestFactory.setReadTimeout(
            properties.readTimeout()
        );

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

    public KakaoUserIdentity authenticate(
        String authorizationCode
    ) {
        try {
            String accessToken =
                exchangeAccessToken(authorizationCode);
            Long providerUserId = getProviderUserId(accessToken);

            return new KakaoUserIdentity(
                providerUserId.toString()
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw authenticationFailed();
            }

            throw kakaoServerError();
        } catch (ResourceAccessException exception) {
            throw kakaoServerError();
        } catch (RestClientException exception) {
            throw kakaoServerError();
        }
    }

    private String exchangeAccessToken(
        String authorizationCode
    ) {
        MultiValueMap<String, String> form =
            new LinkedMultiValueMap<>();

        form.add("grant_type", AUTHORIZATION_CODE);
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        form.add("client_secret", properties.clientSecret());

        KakaoTokenResponse response = restClient
            .post()
            .uri(properties.tokenUri())
            .contentType(
                MediaType.APPLICATION_FORM_URLENCODED
            )
            .body(form)
            .retrieve()
            .body(KakaoTokenResponse.class);

        if (
            response == null ||
            response.accessToken() == null ||
            response.accessToken().isBlank()
        ) {
            throw authenticationFailed();
        }

        return response.accessToken();
    }

    private Long getProviderUserId(String accessToken) {
        KakaoUserInfoResponse response = restClient
            .get()
            .uri(properties.userInfoUri())
            .header(
                HttpHeaders.AUTHORIZATION,
                BEARER_PREFIX + accessToken
            )
            .retrieve()
            .body(KakaoUserInfoResponse.class);

        if (response == null || response.id() == null) {
            throw authenticationFailed();
        }

        return response.id();
    }

    private BusinessException authenticationFailed() {
        return new BusinessException(
            AuthErrorCode.KAKAO_AUTHENTICATION_FAILED
        );
    }

    private BusinessException kakaoServerError() {
        return new BusinessException(
            AuthErrorCode.KAKAO_SERVER_ERROR
        );
    }
}
