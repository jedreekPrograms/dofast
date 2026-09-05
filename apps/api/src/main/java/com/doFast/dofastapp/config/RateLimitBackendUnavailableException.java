package com.doFast.dofastapp.config;

final class RateLimitBackendUnavailableException extends RuntimeException {

    RateLimitBackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    RateLimitBackendUnavailableException(String message) {
        super(message);
    }
}
