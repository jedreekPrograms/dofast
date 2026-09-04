package com.doFast.dofastapp.config;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class InMemoryFixedWindowRateLimiter {

    private static final long CLEANUP_INTERVAL = 256;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong registrationsSinceCleanup = new AtomicLong();
    private final Object admissionLock = new Object();
    private final int maxCostUnits;
    private final long windowSeconds;
    private final int maxEntries;

    InMemoryFixedWindowRateLimiter(int maxCostUnits, long windowSeconds, int maxEntries) {
        if (maxCostUnits < 1 || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid in-memory fixed-window rate-limit configuration");
        }
        this.maxCostUnits = maxCostUnits;
        this.windowSeconds = windowSeconds;
        this.maxEntries = maxEntries;
    }

    Decision register(String key, int costUnits, Instant now) {
        if (key == null || key.isBlank() || costUnits < 1) {
            throw new IllegalArgumentException("Rate-limit key and cost must be present");
        }
        Objects.requireNonNull(now, "now");

        if (registrationsSinceCleanup.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanup(now);
        }
        if (!admitKey(key, now)) {
            return new Decision(false, windowSeconds);
        }

        long epochSecond = now.getEpochSecond();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || epochSecond - current.startedAtEpochSecond() >= windowSeconds) {
                return new Window(epochSecond, costUnits);
            }
            long updatedCost = current.costUnits() > Long.MAX_VALUE - costUnits
                    ? Long.MAX_VALUE
                    : current.costUnits() + costUnits;
            return new Window(current.startedAtEpochSecond(), updatedCost);
        });

        if (window.costUnits() <= maxCostUnits) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, windowSeconds - (epochSecond - window.startedAtEpochSecond()));
        return new Decision(false, retryAfter);
    }

    private boolean admitKey(String key, Instant now) {
        if (windows.containsKey(key)) {
            return true;
        }
        synchronized (admissionLock) {
            if (windows.containsKey(key)) {
                return true;
            }
            if (windows.size() >= maxEntries) {
                cleanup(now);
            }
            if (windows.size() >= maxEntries) {
                return false;
            }
            windows.put(key, new Window(now.getEpochSecond(), 0));
            return true;
        }
    }

    private void cleanup(Instant now) {
        long cutoff = now.getEpochSecond() - windowSeconds;
        windows.entrySet().removeIf(entry -> entry.getValue().startedAtEpochSecond() <= cutoff);
    }

    private record Window(long startedAtEpochSecond, long costUnits) {}

    record Decision(boolean allowed, long retryAfterSeconds) {}
}
