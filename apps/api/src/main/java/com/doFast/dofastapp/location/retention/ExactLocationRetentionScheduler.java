package com.doFast.dofastapp.location.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExactLocationRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExactLocationRetentionScheduler.class);

    private final ExactLocationRetentionService service;

    public ExactLocationRetentionScheduler(ExactLocationRetentionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${dofast.privacy.exact-location.cleanup-interval-ms:3600000}")
    public void cleanup() {
        ExactLocationRetentionService.CleanupResult result = service.cleanup();
        if (result.purgedJobs() > 0 || result.deletedRouteQuotes() > 0) {
            log.info(
                    "Exact location retention cleanup purged {} terminal jobs and deleted {} expired route quotes",
                    result.purgedJobs(),
                    result.deletedRouteQuotes()
            );
        }
    }
}
