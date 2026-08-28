package com.doFast.dofastapp.job.publication;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobPublicationExpiryScheduler {

    private static final int MAX_PER_TICK = 25;

    private final JobPublicationService publicationService;

    public JobPublicationExpiryScheduler(JobPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @Scheduled(fixedDelayString = "${dofast.job-publications.expiry-interval-ms:60000}")
    public void expirePendingPublications() {
        for (int index = 0; index < MAX_PER_TICK; index++) {
            if (!publicationService.expireOne()) {
                return;
            }
        }
    }
}
