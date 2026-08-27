package com.doFast.dofastapp.job.search.alert;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobPublicationAlertWorker {

    private final JobPublicationAlertProcessor processor;

    public JobPublicationAlertWorker(JobPublicationAlertProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${dofast.saved-search-alerts.poll-ms:5000}")
    public void processPendingPublications() {
        processor.processBatch();
    }
}
