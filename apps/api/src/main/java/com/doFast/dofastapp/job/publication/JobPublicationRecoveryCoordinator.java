package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.publication.dto.JobPublicationResponse;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobPublicationRecoveryCoordinator {

    private final JobPublicationRepository publicationRepository;
    private final JobPublicationService publicationService;

    public JobPublicationRecoveryCoordinator(
            JobPublicationRepository publicationRepository,
            JobPublicationService publicationService
    ) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
    }

    @Transactional
    public List<JobPublicationResponse> getRecoverable(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby wznowić publikację");
        }

        LocalDateTime now = LocalDateTime.now();
        while (true) {
            JobPublication expired = publicationRepository
                    .findFirstByUser_IdAndStatusAndExpiresAtBeforeOrderByExpiresAtAscIdAsc(
                            currentUser.getId(),
                            JobPublicationStatus.PAYMENT_REQUIRED,
                            now
                    )
                    .orElse(null);
            if (expired == null) {
                break;
            }
            publicationService.expireIfNecessary(expired, now);
        }

        return publicationRepository
                .findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                        currentUser.getId(),
                        JobPublicationStatus.PAYMENT_REQUIRED,
                        now
                )
                .stream()
                .map(publicationService::toResponse)
                .toList();
    }
}
