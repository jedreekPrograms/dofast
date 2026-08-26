package com.doFast.dofastapp.user.auth.apple;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AppleTokenClient {

    private static final String APPLE_ID_BASE_URL = "https://appleid.apple.com";

    private final RestClient restClient;
    private final AppleAuthConfiguration configuration;
    private final AppleClientSecretService clientSecretService;

    public AppleTokenClient(
            AppleAuthConfiguration configuration,
            AppleClientSecretService clientSecretService
    ) {
        this(RestClient.create(APPLE_ID_BASE_URL), configuration, clientSecretService);
    }

    AppleTokenClient(
            RestClient restClient,
            AppleAuthConfiguration configuration,
            AppleClientSecretService clientSecretService
    ) {
        this.restClient = restClient;
        this.configuration = configuration;
        this.clientSecretService = clientSecretService;
    }

    public AppleTokenResponse exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", configuration.clientId());
        form.add("client_secret", clientSecretService.createClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", configuration.redirectUri());

        try {
            AppleTokenResponse response = restClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);

            if (response == null || response.idToken() == null || response.idToken().isBlank()) {
                throw invalidCredential();
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw invalidCredential();
        }
    }

    private AuthenticationFailedException invalidCredential() {
        return new AuthenticationFailedException("Nieprawidłowa odpowiedź logowania Apple");
    }
}
