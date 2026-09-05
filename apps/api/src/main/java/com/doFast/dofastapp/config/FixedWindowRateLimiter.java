package com.doFast.dofastapp.config;

import java.time.Instant;

interface FixedWindowRateLimiter {

    Decision register(String key, int costUnits, Instant now);

    record Decision(boolean allowed, long retryAfterSeconds) {}
}
