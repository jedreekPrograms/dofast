package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.job.publication.dto.CreateJobPublicationRequest;
import com.doFast.dofastapp.job.publication.dto.JobPublicationResponse;
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

@RestController
@RequestMapping("/jobs/publications")
public class JobPublicationController {

    private final JobPublicationService publicationService;

    public JobPublicationController(JobPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPublicationResponse create(
            @RequestBody @Valid CreateJobPublicationRequest request,
            @AuthenticationPrincipal User user
    ) {
        return publicationService.create(request, user);
    }

    @GetMapping("/{id}")
    public JobPublicationResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return publicationService.get(id, user);
    }

    @PostMapping("/{id}/cancel")
    public JobPublicationResponse cancel(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return publicationService.cancel(id, user);
    }
}
