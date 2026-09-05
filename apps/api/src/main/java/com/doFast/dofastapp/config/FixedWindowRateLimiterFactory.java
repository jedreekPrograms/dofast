package com.doFast.dofastapp.config;

@FunctionalInterface
interface FixedWindowRateLimiterFactory {

    FixedWindowRateLimiter create(String namespace, int maxCostUnits, long windowSeconds, int maxEntries);
}
