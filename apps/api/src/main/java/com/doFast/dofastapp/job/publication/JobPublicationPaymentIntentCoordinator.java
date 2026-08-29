package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.publication.dto.JobPublicationResponse;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class JobPublicationPaymentIntentCoordinator {

    private final JobPublicationService publicationService;
    private final JobPublicationPaymentIntentService paymentIntentService;

    public JobPublicationPaymentIntentCoordinator(
            JobPublicationService publicationService,
            JobPublicationPaymentIntentService paymentIntentService
    ) {
        this.publicationService = publicationService;
        this.paymentIntentService = paymentIntentService;
    }

    public CreatePaymentIntentResponse create(Long publicationId, User currentUser) {
        JobPublicationResponse publication = publicationService.get(publicationId, currentUser);
        if (!publication.paymentRequired()) {
            throw new ConflictException("Ta publikacja nie oczekuje już na płatność");
        }
        return paymentIntentService.create(publicationId, currentUser);
    }
}
