package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional
public class JobPublicationPaymentIntentService {

    public static final String PURPOSE = "JOB_PUBLICATION";

    private final JobPublicationRepository publicationRepository;
    private final JobPublicationService publicationService;

    public JobPublicationPaymentIntentService(
            JobPublicationRepository publicationRepository,
            JobPublicationService publicationService
    ) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
    }

    public CreatePaymentIntentResponse create(Long publicationId, User currentUser) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Publikacja nie istnieje"));
        assertOwner(publication, currentUser);
        if (publication.getStatus() != JobPublicationStatus.PAYMENT_REQUIRED) {
            throw new ConflictException("Ta publikacja nie oczekuje już na płatność");
        }
        LocalDateTime now = LocalDateTime.now();
        if (publicationService.expireIfNecessary(publication, now)) {
            throw new ConflictException("Czas na opłacenie publikacji wygasł");
        }

        long amountInCents = publication.getPaymentAmount().movePointRight(2).longValueExact();
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(publication.getCurrency().toLowerCase(Locale.ROOT))
                .putMetadata("userId", publication.getUser().getId().toString())
                .putMetadata("purpose", PURPOSE)
                .putMetadata("jobPublicationId", publication.getId().toString())
                .putMetadata("topUpRequestId", "job-publication-" + publication.getId())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build()
                )
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("dofast:job-publication:" + publication.getId())
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            if (intent.getId() == null || intent.getId().isBlank()
                    || intent.getClientSecret() == null || intent.getClientSecret().isBlank()) {
                throw new PaymentProviderException("Stripe zwrócił niepełną odpowiedź dla publikacji zlecenia", null);
            }
            String existingIntent = publication.getStripePaymentIntentId();
            if (existingIntent != null && !existingIntent.equals(intent.getId())) {
                throw new ConflictException("Publikacja ma już inną płatność Stripe");
            }
            publication.attachStripePaymentIntent(intent.getId(), LocalDateTime.now());
            publicationRepository.save(publication);
            return new CreatePaymentIntentResponse(
                    intent.getId(),
                    intent.getClientSecret(),
                    publication.getPaymentAmount(),
                    publication.getCurrency()
            );
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się przygotować płatności za publikację", ex);
        }
    }

    private void assertOwner(JobPublication publication, User currentUser) {
        if (currentUser == null || currentUser.getId() == null
                || !currentUser.getId().equals(publication.getUser().getId())) {
            throw new ForbiddenOperationException("Ta publikacja należy do innego użytkownika");
        }
    }
}
