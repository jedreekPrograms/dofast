package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.publication.dto.CreateJobPublicationRequest;
import com.doFast.dofastapp.job.publication.dto.JobPublicationResponse;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteResponse;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobPublicationService {

    private static final BigDecimal MIN_ONLINE_PAYMENT = new BigDecimal("1.00");
    private static final BigDecimal MAX_ONLINE_PAYMENT = new BigDecimal("10000.00");
    private static final int PAYMENT_WINDOW_MINUTES = 10;
    private static final int ROUTE_EXPIRY_SAFETY_SECONDS = 20;

    private final JobPublicationRepository publicationRepository;
    private final UserRepository userRepository;
    private final JobCategoryRepository categoryRepository;
    private final RouteQuoteService routeQuoteService;
    private final WalletService walletService;
    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public JobPublicationService(
            JobPublicationRepository publicationRepository,
            UserRepository userRepository,
            JobCategoryRepository categoryRepository,
            RouteQuoteService routeQuoteService,
            WalletService walletService,
            JobService jobService,
            ObjectMapper objectMapper
    ) {
        this.publicationRepository = publicationRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.routeQuoteService = routeQuoteService;
        this.walletService = walletService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobPublicationResponse create(CreateJobPublicationRequest request, User currentUser) {
        User lockedUser = userRepository.findByIdForUpdate(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));
        String requestKey = requestKey(lockedUser.getId(), request.requestId());
        String payload = serialize(request.job());
        String payloadHash = sha256(payload);

        JobPublication existing = publicationRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            if (!existing.getPayloadHash().equals(payloadHash)) {
                throw new ConflictException("Identyfikator publikacji został już użyty dla innego zlecenia");
            }
            return toResponse(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = validatePublishable(request.job(), lockedUser, now);
        BigDecimal totalAmount = money(request.job().getPrice());
        BigDecimal walletBalance = walletService.getBalanceForUpdate(lockedUser.getId());

        if (walletBalance.compareTo(totalAmount) >= 0) {
            JobResponse job = jobService.createJob(request.job(), lockedUser);
            JobPublication publication = new JobPublication();
            publication.initializePublished(
                    lockedUser,
                    requestKey,
                    payloadHash,
                    request.job().getCategoryId(),
                    request.job().getRouteQuoteId(),
                    totalAmount,
                    job.id(),
                    now
            );
            return toResponse(publicationRepository.save(publication));
        }

        BigDecimal reservedAmount = walletBalance.max(BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal missingAmount = totalAmount.subtract(reservedAmount);
        BigDecimal paymentAmount = missingAmount.max(MIN_ONLINE_PAYMENT).setScale(2, RoundingMode.UNNECESSARY);
        if (paymentAmount.compareTo(MAX_ONLINE_PAYMENT) > 0) {
            throw new BusinessException("Brakująca kwota przekracza limit pojedynczej płatności online 10 000,00 PLN");
        }

        if (reservedAmount.signum() > 0) {
            walletService.debit(
                    lockedUser.getId(),
                    reservedAmount,
                    WalletTransactionType.JOB_PUBLICATION_RESERVE,
                    null,
                    reserveOperationKey(requestKey)
            );
        }

        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                lockedUser,
                requestKey,
                payloadHash,
                payload,
                request.job().getCategoryId(),
                request.job().getRouteQuoteId(),
                totalAmount,
                reservedAmount,
                paymentAmount,
                now,
                expiresAt
        );
        return toResponse(publicationRepository.save(publication));
    }

    public JobPublicationResponse get(Long publicationId, User currentUser) {
        JobPublication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Publikacja nie istnieje"));
        assertOwner(publication, currentUser);
        return toResponse(publication);
    }

    public List<JobPublicationResponse> getRecoverable(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby wznowić publikację");
        }
        return publicationRepository
                .findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                        currentUser.getId(),
                        JobPublicationStatus.PAYMENT_REQUIRED,
                        LocalDateTime.now()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobPublicationResponse cancel(Long publicationId, User currentUser) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Publikacja nie istnieje"));
        assertOwner(publication, currentUser);
        if (publication.getStatus() == JobPublicationStatus.CANCELLED) {
            return toResponse(publication);
        }
        if (publication.getStatus() != JobPublicationStatus.PAYMENT_REQUIRED) {
            throw new ConflictException("Tej publikacji nie można już anulować");
        }

        restoreReservation(publication);
        publication.cancel(LocalDateTime.now());
        return toResponse(publicationRepository.save(publication));
    }

    @Transactional
    public boolean expireOne() {
        JobPublication publication = publicationRepository
                .findFirstByStatusAndExpiresAtBeforeOrderByExpiresAtAscIdAsc(
                        JobPublicationStatus.PAYMENT_REQUIRED,
                        LocalDateTime.now()
                )
                .orElse(null);
        if (publication == null) {
            return false;
        }
        restoreReservation(publication);
        publication.cancel(LocalDateTime.now());
        publicationRepository.save(publication);
        return true;
    }

    void restoreReservation(JobPublication publication) {
        if (publication.getWalletReservedAmount().signum() <= 0) {
            return;
        }
        walletService.credit(
                publication.getUser().getId(),
                publication.getWalletReservedAmount(),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                releaseOperationKey(publication.getId())
        );
    }

    JobPublicationResponse toResponse(JobPublication publication) {
        BigDecimal missing = publication.getTotalAmount()
                .subtract(publication.getWalletReservedAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.UNNECESSARY);
        boolean paymentRequired = publication.getStatus() == JobPublicationStatus.PAYMENT_REQUIRED;
        return new JobPublicationResponse(
                publication.getId(),
                publication.getStatus(),
                publication.getTotalAmount(),
                publication.getWalletReservedAmount(),
                missing,
                publication.getPaymentAmount(),
                publication.getCurrency(),
                publication.getPublishedJobId(),
                publication.getExpiresAt(),
                paymentRequired,
                paymentRequired
        );
    }

    private LocalDateTime validatePublishable(JobRequest request, User user, LocalDateTime now) {
        JobCategory category = categoryRepository.findByIdAndActiveTrue(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Wybrana kategoria zlecenia nie istnieje lub jest nieaktywna"));
        if (category.getParent() == null || category.getFulfillmentMode() == null) {
            throw new BusinessException("Wybierz konkretną podkategorię usługi");
        }

        JobAssignmentMode assignmentMode = request.getAssignmentMode() == null
                ? JobAssignmentMode.INSTANT
                : request.getAssignmentMode();
        if (assignmentMode == JobAssignmentMode.INSTANT && request.isPriceNegotiationEnabled()) {
            throw new BusinessException("Negocjacja ceny jest dostępna tylko dla zleceń z propozycjami");
        }

        if (category.getFulfillmentMode() == FulfillmentMode.ON_SITE) {
            validateOnSite(request);
            return now.plusMinutes(PAYMENT_WINDOW_MINUTES);
        }

        if (request.getLocation() != null || request.getRouteQuoteId() == null) {
            throw new BusinessException("Zlecenie transportowe wymaga aktualnej wyceny trasy A → B");
        }
        RouteQuoteResponse quote = routeQuoteService.getQuote(request.getRouteQuoteId(), user);
        LocalDateTime safeRouteExpiry = quote.expiresAt().minusSeconds(ROUTE_EXPIRY_SAFETY_SECONDS);
        LocalDateTime paymentWindowExpiry = now.plusMinutes(PAYMENT_WINDOW_MINUTES);
        LocalDateTime expiresAt = safeRouteExpiry.isBefore(paymentWindowExpiry)
                ? safeRouteExpiry
                : paymentWindowExpiry;
        if (!expiresAt.isAfter(now.plusSeconds(30))) {
            throw new ConflictException("Wycena trasy zaraz wygaśnie. Wyznacz trasę ponownie przed płatnością");
        }
        return expiresAt;
    }

    private void validateOnSite(JobRequest request) {
        if (request.getRouteQuoteId() != null) {
            throw new BusinessException("Zlecenie wykonywane na miejscu nie może korzystać z trasy A → B");
        }
        RoutePointRequest location = request.getLocation();
        if (location == null || location.privateLabel() == null || location.privateLabel().isBlank()) {
            throw new BusinessException("Podaj dokładny adres wykonania usługi");
        }
    }

    private void assertOwner(JobPublication publication, User currentUser) {
        if (currentUser == null || currentUser.getId() == null
                || !currentUser.getId().equals(publication.getUser().getId())) {
            throw new ForbiddenOperationException("Ta publikacja należy do innego użytkownika");
        }
    }

    private BigDecimal money(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Kwota może mieć maksymalnie dwa miejsca po przecinku");
        }
    }

    private String serialize(JobRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Nie udało się zapisać danych publikacji", ex);
        }
    }

    JobRequest deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, JobRequest.class);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Nie udało się odczytać danych publikacji", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String requestKey(Long userId, String clientRequestId) {
        return "job-publication:" + userId + ":" + clientRequestId.trim();
    }

    private String reserveOperationKey(String requestKey) {
        return requestKey + ":reserve";
    }

    private String releaseOperationKey(Long publicationId) {
        return "job-publication:" + publicationId + ":release";
    }
}
