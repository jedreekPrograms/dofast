package com.doFast.dofastapp.location.retention;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ExactLocationRetentionProperties {

    private final Duration retention;
    private final int batchSize;

    public ExactLocationRetentionProperties(
            @Value("${dofast.privacy.exact-location.retention-days:30}") int retentionDays,
            @Value("${dofast.privacy.exact-location.cleanup-batch-size:100}") int batchSize
    ) {
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("Exact location retention must be between 1 and 3650 days");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Exact location cleanup batch size must be between 1 and 1000");
        }
        this.retention = Duration.ofDays(retentionDays);
        this.batchSize = batchSize;
    }

    public Duration retention() {
        return retention;
    }

    public int batchSize() {
        return batchSize;
    }
}
