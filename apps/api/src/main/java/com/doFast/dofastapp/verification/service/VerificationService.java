package com.doFast.dofastapp.verification.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.dto.AdminVerificationEventResponse;
import com.doFast.dofastapp.verification.dto.AdminVerificationResponse;
import com.doFast.dofastapp.verification.dto.VerificationResponse;
import com.doFast.dofastapp.verification.entity.VerificationCase;
import com.doFast.dofastapp.verification.entity.VerificationEvent;
import com.doFast.dofastapp.verification.enums.VerificationDecision;
import com.doFast.dofastapp.verification.enums.VerificationEventType;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.provider.VerificationProvider;
import com.doFast.dofastapp.verification.provider.VerificationSubmission;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import com.doFast.dofastapp.verification.repository.VerificationEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class VerificationService {

    private final VerificationCaseRepository verificationCaseRepository;
    private final VerificationEventRepository verificationEventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final VerificationProvider verificationProvider;

    public VerificationService(
            VerificationCaseRepository verificationCaseRepository,
            VerificationEventRepository verificationEventRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            VerificationProvider verificationProvider
    ) {
        this.verificationCaseRepository = verificationCaseRepository;
        this.verificationEventRepository = verificationEventRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.verificationProvider = verificationProvider;
    }

    public VerificationResponse getCurrent(User user) {
        return verificationCaseRepository.findByUser_Id(user.getId())
                .map(this::toResponse)
                .orElseGet(VerificationResponse::notStarted);
    }

    public boolean isVerified(Long userId) {
        return verificationCaseRepository.existsByUser_IdAndStatus(userId, VerificationStatus.VERIFIED);
    }

    public long countPending() {
        return verificationCaseRepository.countByStatus(VerificationStatus.PENDING);
    }

    @Transactional
    public VerificationResponse requestVerification(User principal) {
        User user = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));

        LocalDateTime now = LocalDateTime.now();
        VerificationCase verification = verificationCaseRepository.findByUser_Id(user.getId()).orElse(null);

        if (verification == null) {
            VerificationSubmission submission = startProviderVerification(user);
            verification = new VerificationCase();
            verification.initialize(
                    user,
                    submission.provider(),
                    submission.providerReference(),
                    now
            );
            verification = verificationCaseRepository.save(verification);
            recordEvent(
                    verification,
                    user,
                    VerificationEventType.REQUESTED,
                    null,
                    VerificationStatus.PENDING,
                    null,
                    now
            );
            return toResponse(verification);
        }

        if (verification.getStatus() == VerificationStatus.PENDING) {
            return toResponse(verification);
        }
        if (verification.getStatus() == VerificationStatus.VERIFIED) {
            throw new ConflictException("Tożsamość jest już zweryfikowana");
        }
        if (verification.getStatus() != VerificationStatus.REJECTED
                && verification.getStatus() != VerificationStatus.REVOKED) {
            throw new ConflictException("Weryfikacja nie może zostać ponownie zgłoszona w aktualnym stanie");
        }

        VerificationSubmission submission = startProviderVerification(user);
        VerificationStatus previousStatus = verification.getStatus();
        verification.resubmit(
                submission.provider(),
                submission.providerReference(),
                now
        );
        verification = verificationCaseRepository.save(verification);
        recordEvent(
                verification,
                user,
                VerificationEventType.RESUBMITTED,
                previousStatus,
                VerificationStatus.PENDING,
                null,
                now
        );
        return toResponse(verification);
    }

    public PageResponse<AdminVerificationResponse> getAdminVerifications(
            VerificationStatus status,
            int page,
            int size
    ) {
        if (status == VerificationStatus.NOT_STARTED) {
            throw new BusinessException("Status NOT_STARTED nie jest zapisanym zgłoszeniem weryfikacyjnym");
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"))
        );
        Page<VerificationCase> verifications = status == null
                ? verificationCaseRepository.findAll(pageable)
                : verificationCaseRepository.findByStatus(status, pageable);

        List<AdminVerificationResponse> content = verifications.getContent()
                .stream()
                .map(this::toAdminResponse)
                .toList();
        return PageResponse.from(verifications, content);
    }

    public List<AdminVerificationEventResponse> getEvents(Long verificationId) {
        if (!verificationCaseRepository.existsById(verificationId)) {
            throw new ResourceNotFoundException("Weryfikacja nie istnieje");
        }
        return verificationEventRepository.findByVerification_IdOrderByCreatedAtAscIdAsc(verificationId)
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional
    public AdminVerificationResponse decide(
            Long verificationId,
            VerificationDecision decision,
            String reason,
            User admin
    ) {
        VerificationCase verification = verificationCaseRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Weryfikacja nie istnieje"));

        if (verification.getUser().getId().equals(admin.getId())) {
            throw new ForbiddenOperationException("Administrator nie może rozpatrywać własnej weryfikacji");
        }

        LocalDateTime now = LocalDateTime.now();
        VerificationStatus previousStatus = verification.getStatus();

        switch (decision) {
            case APPROVE -> approve(verification, admin, previousStatus, now);
            case REJECT -> reject(verification, admin, previousStatus, reason, now);
            case REVOKE -> revoke(verification, admin, previousStatus, reason, now);
        }

        return toAdminResponse(verificationCaseRepository.save(verification));
    }

    private VerificationSubmission startProviderVerification(User user) {
        VerificationSubmission submission = verificationProvider.startVerification(user);
        if (submission == null || submission.provider() == null || submission.provider().isBlank()) {
            throw new IllegalStateException("Verification provider returned invalid submission metadata");
        }
        if (submission.provider().length() > 32) {
            throw new IllegalStateException("Verification provider code is too long");
        }
        if (submission.providerReference() != null && submission.providerReference().length() > 255) {
            throw new IllegalStateException("Verification provider reference is too long");
        }
        return submission;
    }

    private void approve(
            VerificationCase verification,
            User admin,
            VerificationStatus previousStatus,
            LocalDateTime now
    ) {
        requireStatus(verification, VerificationStatus.PENDING, "Zatwierdzić można tylko oczekującą weryfikację");
        verification.approve(admin, now);
        recordEvent(
                verification,
                admin,
                VerificationEventType.APPROVED,
                previousStatus,
                VerificationStatus.VERIFIED,
                null,
                now
        );
        notificationService.notify(
                verification.getUser(),
                NotificationType.VERIFICATION_APPROVED,
                "Tożsamość zweryfikowana",
                "Twoja tożsamość została zweryfikowana. Na publicznym profilu pojawi się oznaczenie weryfikacji.",
                null,
                null
        );
    }

    private void reject(
            VerificationCase verification,
            User admin,
            VerificationStatus previousStatus,
            String reason,
            LocalDateTime now
    ) {
        requireStatus(verification, VerificationStatus.PENDING, "Odrzucić można tylko oczekującą weryfikację");
        String normalizedReason = requireReason(reason);
        verification.reject(admin, normalizedReason, now);
        recordEvent(
                verification,
                admin,
                VerificationEventType.REJECTED,
                previousStatus,
                VerificationStatus.REJECTED,
                normalizedReason,
                now
        );
        notificationService.notify(
                verification.getUser(),
                NotificationType.VERIFICATION_REJECTED,
                "Weryfikacja wymaga ponownego zgłoszenia",
                "Weryfikacja tożsamości nie została zaakceptowana. Szczegóły znajdziesz w ustawieniach weryfikacji.",
                null,
                null
        );
    }

    private void revoke(
            VerificationCase verification,
            User admin,
            VerificationStatus previousStatus,
            String reason,
            LocalDateTime now
    ) {
        requireStatus(verification, VerificationStatus.VERIFIED, "Cofnąć można tylko aktywną weryfikację");
        String normalizedReason = requireReason(reason);
        verification.revoke(admin, normalizedReason, now);
        recordEvent(
                verification,
                admin,
                VerificationEventType.REVOKED,
                previousStatus,
                VerificationStatus.REVOKED,
                normalizedReason,
                now
        );
        notificationService.notify(
                verification.getUser(),
                NotificationType.VERIFICATION_REVOKED,
                "Status weryfikacji został cofnięty",
                "Status weryfikacji tożsamości został cofnięty. Szczegóły znajdziesz w ustawieniach weryfikacji.",
                null,
                null
        );
    }

    private void requireStatus(VerificationCase verification, VerificationStatus required, String message) {
        if (verification.getStatus() != required) {
            throw new ConflictException(message);
        }
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 5) {
            throw new BusinessException("Powód decyzji musi mieć co najmniej 5 znaków");
        }
        if (normalized.length() > 500) {
            throw new BusinessException("Powód decyzji może mieć maksymalnie 500 znaków");
        }
        return normalized;
    }

    private void recordEvent(
            VerificationCase verification,
            User actor,
            VerificationEventType eventType,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String reason,
            LocalDateTime now
    ) {
        verificationEventRepository.save(new VerificationEvent(
                verification,
                actor,
                eventType,
                fromStatus,
                toStatus,
                reason,
                now
        ));
    }

    private VerificationResponse toResponse(VerificationCase verification) {
        boolean canRequest = verification.getStatus() == VerificationStatus.REJECTED
                || verification.getStatus() == VerificationStatus.REVOKED;
        return new VerificationResponse(
                verification.getId(),
                verification.getStatus(),
                verification.getRequestedAt(),
                verification.getReviewedAt(),
                verification.getVerifiedAt(),
                verification.getRevokedAt(),
                verification.getDecisionReason(),
                canRequest
        );
    }

    private AdminVerificationResponse toAdminResponse(VerificationCase verification) {
        return new AdminVerificationResponse(
                verification.getId(),
                verification.getUser().getId(),
                verification.getUser().getEmail(),
                verification.getUser().getNickname(),
                verification.getStatus(),
                verification.getProvider(),
                verification.getProviderReference(),
                verification.getRequestedAt(),
                verification.getReviewedAt(),
                verification.getVerifiedAt(),
                verification.getRevokedAt(),
                verification.getReviewedBy() != null ? verification.getReviewedBy().getId() : null,
                verification.getDecisionReason(),
                verification.getUpdatedAt()
        );
    }

    private AdminVerificationEventResponse toEventResponse(VerificationEvent event) {
        return new AdminVerificationEventResponse(
                event.getId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getActor().getId(),
                event.getActor().getNickname(),
                event.getReason(),
                event.getCreatedAt()
        );
    }
}
