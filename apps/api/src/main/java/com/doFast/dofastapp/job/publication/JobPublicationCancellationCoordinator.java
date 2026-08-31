package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.job.publication.dto.JobPublicationResponse;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class JobPublicationCancellationCoordinator {

    private final JobPublicationService publicationService;
    private final JobPublicationPaymentIntentCleanupService paymentIntentCleanupService;

    public JobPublicationCancellationCoordinator(
            JobPublicationService publicationService,
            JobPublicationPaymentIntentCleanupService paymentIntentCleanupService
    ) {
        this.publicationService = publicationService;
        this.paymentIntentCleanupService = paymentIntentCleanupService;
    }

    public JobPublicationResponse cancel(Long publicationId, User currentUser) {
        JobPublicationResponse response = publicationService.cancel(publicationId, currentUser);
        paymentIntentCleanupService.process(publicationId);
        return response;
    }
}
