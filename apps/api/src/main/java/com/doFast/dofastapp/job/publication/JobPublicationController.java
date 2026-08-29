package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.job.publication.dto.CreateJobPublicationRequest;
import com.doFast.dofastapp.job.publication.dto.JobPublicationResponse;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/jobs/publications")
public class JobPublicationController {

    private final JobPublicationService publicationService;
    private final JobPublicationRecoveryCoordinator recoveryCoordinator;
    private final JobPublicationPaymentIntentCoordinator paymentIntentCoordinator;

    public JobPublicationController(
            JobPublicationService publicationService,
            JobPublicationRecoveryCoordinator recoveryCoordinator,
            JobPublicationPaymentIntentCoordinator paymentIntentCoordinator
    ) {
        this.publicationService = publicationService;
        this.recoveryCoordinator = recoveryCoordinator;
        this.paymentIntentCoordinator = paymentIntentCoordinator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPublicationResponse create(
            @RequestBody @Valid CreateJobPublicationRequest request,
            @AuthenticationPrincipal User user
    ) {
        return publicationService.create(request, user);
    }

    @GetMapping("/pending")
    public List<JobPublicationResponse> getPending(@AuthenticationPrincipal User user) {
        return recoveryCoordinator.getRecoverable(user);
    }

    @GetMapping("/{id}")
    public JobPublicationResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return publicationService.get(id, user);
    }

    @PostMapping("/{id}/payment-intent")
    public CreatePaymentIntentResponse createPaymentIntent(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return paymentIntentCoordinator.create(id, user);
    }

    @PostMapping("/{id}/cancel")
    public JobPublicationResponse cancel(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return publicationService.cancel(id, user);
    }
}
