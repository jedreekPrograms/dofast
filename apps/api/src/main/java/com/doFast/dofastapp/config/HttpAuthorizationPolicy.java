package com.doFast.dofastapp.config;

final class HttpAuthorizationPolicy {

    static final String[] PUBLIC_POST_PATHS = {
            "/users",
            "/users/login",
            "/users/login/google",
            "/users/login/apple",
            "/users/login/apple/challenge",
            "/users/session/refresh",
            "/users/session/logout",
            "/users/password/forgot",
            "/users/password/reset",
            "/users/email-verification/resend",
            "/users/email-verification/verify",
            "/webhooks/stripe"
    };

    static final String[] PUBLIC_GET_PATHS = {
            "/jobs",
            "/jobs/nearby",
            "/job-categories",
            "/users/*/profile",
            "/reviews/users/*"
    };

    static final String[] PUBLIC_TRANSPORT_PATHS = {
            "/ws",
            "/ws/**",
            "/ws-sockjs/**"
    };

    static final String[] PUBLIC_HEALTH_PATHS = {
            "/actuator/health",
            "/actuator/health/**"
    };

    static final String[] ADMIN_PATHS = {"/admin/**"};

    private HttpAuthorizationPolicy() {}
}
