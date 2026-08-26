package com.doFast.dofastapp.user.auth.apple;

import org.springframework.stereotype.Service;

@Service
public class AppleSignInService {

    private final AppleLoginChallengeService challengeService;
    private final AppleTokenClient tokenClient;
    private final AppleIdentityTokenVerifier identityTokenVerifier;

    public AppleSignInService(
            AppleLoginChallengeService challengeService,
            AppleTokenClient tokenClient,
            AppleIdentityTokenVerifier identityTokenVerifier
    ) {
        this.challengeService = challengeService;
        this.tokenClient = tokenClient;
        this.identityTokenVerifier = identityTokenVerifier;
    }

    public AppleIdentity authenticate(AppleLoginRequest request) {
        challengeService.consume(request.challengeId(), request.state(), request.nonce());
        AppleTokenResponse tokenResponse = tokenClient.exchangeAuthorizationCode(request.code());
        return identityTokenVerifier.verify(
                tokenResponse.idToken(),
                request.nonce(),
                displayName(request.firstName(), request.lastName())
        );
    }

    private String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isBlank() ? null : combined;
    }
}