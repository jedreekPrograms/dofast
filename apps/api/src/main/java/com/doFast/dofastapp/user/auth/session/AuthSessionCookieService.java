package com.doFast.dofastapp.user.auth.session;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthSessionCookieService {

    public static final String REFRESH_COOKIE = "dofast_refresh";
    public static final String CSRF_COOKIE = "dofast_csrf";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final AuthSessionProperties properties;
    private final AuthSessionSecrets secrets;

    public AuthSessionCookieService(AuthSessionProperties properties, AuthSessionSecrets secrets) {
        this.properties = properties;
        this.secrets = secrets;
    }

    public void write(HttpServletResponse response, AuthSessionGrant grant) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                REFRESH_COOKIE,
                grant.refreshToken(),
                true,
                properties.refreshTtl()
        ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                CSRF_COOKIE,
                grant.csrfToken(),
                false,
                properties.refreshTtl()
        ).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, "", true, Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(CSRF_COOKIE, "", false, Duration.ZERO).toString());
    }

    public AuthSessionCredentials requireCredentials(HttpServletRequest request) {
        String refreshToken = cookieValue(request, REFRESH_COOKIE);
        String csrfCookie = cookieValue(request, CSRF_COOKIE);
        String csrfHeader = request.getHeader(CSRF_HEADER);

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ForbiddenOperationException("Brak aktywnej sesji odświeżania");
        }
        if (csrfCookie == null || csrfCookie.isBlank() || csrfHeader == null || csrfHeader.isBlank()
                || !secrets.constantTimeEquals(csrfCookie, csrfHeader)) {
            throw new ForbiddenOperationException("Nieprawidłowy token CSRF");
        }
        return new AuthSessionCredentials(refreshToken, csrfHeader);
    }

    public AuthSessionCredentials optionalCredentials(HttpServletRequest request) {
        String refreshToken = cookieValue(request, REFRESH_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) return null;
        return requireCredentials(request);
    }

    private ResponseCookie buildCookie(String name, String value, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.cookieSecure())
                .sameSite(properties.sameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
