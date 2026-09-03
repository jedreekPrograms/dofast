package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
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
import com.doFast.dofastapp.job.dto.JobRoutePointResponse;
import com.doFast.dofastapp.job.dto.JobRouteResponse;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.entity.JobRouteStop;
import com.doFast.dofastapp.job.expense.JobExpenseService;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutbox;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.dto.LocationResponse;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobService {

    private static final String EXACT_LOCATION_ACCESS_DENIED =
            "Dokładna lokalizacja jest dostępna tylko dla stron aktywnego zlecenia";
    private static final String JOB_NOT_FOUND = "Zlecenie nie istnieje";

    private final JobRepository jobRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final RouteQuoteService routeQuoteService;
    private final LiveTrackingService liveTrackingService;
    private final JobPublicationOutboxRepository jobPublicationOutboxRepository;
    private final UserBlockService userBlockService;
    private final JobExpenseService expenseService;

    @Autowired
    public JobService(
            JobRepository jobRepository,
            JobCategoryRepository jobCategoryRepository,
            TransactionService transactionService,
            NotificationService notificationService,
            RouteQuoteService routeQuoteService,
            LiveTrackingService liveTrackingService,
            JobPublicationOutboxRepository jobPublicationOutboxRepository,
            UserBlockService userBlockService,
            JobExpenseService expenseService
    ) {
        this.jobRepository = jobRepository;
        this.jobCategoryRepository = jobCategoryRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.routeQuoteService = routeQuoteService;
        this.liveTrackingService = liveTrackingService;
        this.jobPublicationOutboxRepository = jobPublicationOutboxRepository;
        this.userBlockService = userBlockService;
        this.expenseService = expenseService;
    }

    JobService(
            JobRepository jobRepository,
            JobCategoryRepository jobCategoryRepository,
            TransactionService transactionService,
            NotificationService notificationService,
            RouteQuoteService routeQuoteService,
            LiveTrackingService liveTrackingService,
            JobPublicationOutboxRepository jobPublicationOutboxRepository
    ) {
        this(
                jobRepository,
                jobCategoryRepository,
                transactionService,
                notificationService,
                routeQuoteService,
                liveTrackingService,
                jobPublicationOutboxRepository,
                null,
                null
        );
    }

    @Transactional
    public JobResponse createJob(JobRequest request, User user) {
        requireActorId(user);
        JobCategory category = jobCategoryRepository.findByIdAndActiveTrue(request.getCategoryId())
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

        Job job = new Job();
        job.setTitle(request.getTitle().trim());
        job.setDescription(request.getDescription().trim());
        job.setPrice(request.getPrice());
        job.setExpenseBudget(request.getExpenseBudget());
        job.setAssignmentMode(assignmentMode);
        job.setPriceNegotiationEnabled(request.isPriceNegotiationEnabled());
        job.setStatus(JobStatus.OPEN);
        job.setCategory(category);
        job.setCreatedBy(user);
        configureFulfillment(job, category, request, user);

        Job saved = jobRepository.save(job);
        transactionService.holdMoney(saved);
        if (expenseService != null) {
            expenseService.holdBudget(saved);
        }
        jobPublicationOutboxRepository.save(new JobPublicationOutbox(saved));
        return toResponse(saved);
    }

    public PageResponse<JobResponse> getOpenJobs(String query, BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("Minimalna cena nie może być większa od maksymalnej");
        }

        String normalizedQuery = normalizeSearchQuery(query);
        PageRequest pageable = discoveryPage(page, size);
        Page<Job> result = jobRepository.findOpenJobs(JobStatus.OPEN, normalizedQuery, minPrice, maxPrice, pageable);
        return toPageResponse(result);
    }

    public PageResponse<JobResponse> getOpenJobs(
            String query,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size
    ) {
        String normalizedCategory = normalizeCategorySlug(categorySlug);
        if (normalizedCategory.isEmpty()) {
            return getOpenJobs(query, minPrice, maxPrice, page, size);
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("Minimalna cena nie może być większa od maksymalnej");
        }

        String normalizedQuery = normalizeSearchQuery(query);
        Page<Job> result = jobRepository.findOpenJobsByCategory(
                JobStatus.OPEN,
                normalizedQuery,
                normalizedCategory,
                minPrice,
                maxPrice,
                discoveryPage(page, size)
        );
        return toPageResponse(result);
    }

    public List<NearbyJobResponse> getNearbyJobs(double latitude, double longitude, int radiusMeters, int limit) {
        return jobRepository.findNearbyOpenJobs(latitude, longitude, radiusMeters, limit)
                .stream()
                .map(match -> new NearbyJobResponse(
                        match.getId(), match.getTitle(), match.getDescription(), match.getPrice(),
                        JobStatus.valueOf(match.getStatus()), match.getLocationLabel(), match.getDestinationLabel(),
                        match.getRouteDistanceMeters(), match.getRouteDurationSeconds(),
                        Math.round(match.getDistanceMeters()), match.getCreatedAt()
                ))
                .toList();
    }

    public JobResponse getJob(Long jobId) { return toResponse(getJobForRead(jobId)); }

    public LocationResponse getExactLocation(Long jobId, User currentUser) {
        Job job = getJobForExactLocationRead(jobId, currentUser);
        assertCanAccessExactLocation(job, currentUser);
        Point point = job.getLocation();
        if (point == null) {
            throw new ResourceNotFoundException("Dokładna lokalizacja zlecenia nie jest dostępna");
        }
        return new LocationResponse(point.getY(), point.getX(), exactOriginLabel(job));
    }

    public JobRouteResponse getExactRoute(Long jobId, User currentUser) {
        Job job = getJobForExactLocationRead(jobId, currentUser);
        assertCanAccessExactLocation(job, currentUser);
        if (job.getLocation() == null || job.getDestinationLocation() == null) {
            throw new ResourceNotFoundException("Dokładna trasa zlecenia nie jest dostępna");
        }
        return new JobRouteResponse(
                pointResponse(job.getLocation(), exactOriginLabel(job)),
                job.getRouteStops().stream()
                        .map(stop -> pointResponse(stop.getLocation(), exactStopLabel(stop)))
                        .toList(),
                pointResponse(job.getDestinationLocation(), exactDestinationLabel(job)),
                job.getRouteDistanceMeters(),
                job.getRouteDurationSeconds(),
                job.getRouteEncodedPolyline(),
                job.getRouteProvider(),
                job.getRouteComputedAt()
        );
    }

    @Transactional
    public JobResponse acceptJob(Long jobId, User currentUser) {
        requireActorId(currentUser);
        Job job = getOpenInstantJobForUpdate(jobId);
        if (sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Nie możesz przyjąć własnego zlecenia");
        }
        if (userBlockService != null && userBlockService.isInteractionBlocked(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Nie możesz przyjąć tego zlecenia");
        }
        job.assignTo(currentUser, LocalDateTime.now());
        Job saved = jobRepository.save(job);
        if (usesLiveTracking(saved)) {
            liveTrackingService.initializeForAcceptedJob(saved);
        }
        notificationService.notify(saved.getCreatedBy(), NotificationType.JOB_ACCEPTED, "Zlecenie zostało przyjęte",
                currentUser.getNickname() + " przyjął zlecenie „" + saved.getTitle() + "”", saved, null);
        return toResponse(saved);
    }

    public List<JobResponse> getMyJobs(User user) {
        requireActorId(user);
        return jobRepository.findByCreatedByOrTakenByOrderByCreatedAtDesc(user, user).stream().map(this::toResponse).toList();
    }

    @Transactional
    public JobResponse requestCompletion(Long jobId, User currentUser) {
        Long actorId = requireActorId(currentUser);
        Job job = jobRepository.findAssignedWorkerByIdForUpdate(jobId, actorId)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ConflictException("Zlecenie nie jest w trakcie realizacji");
        }
        job.requestCompletion(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        notificationService.notify(saved.getCreatedBy(), NotificationType.COMPLETION_REQUESTED,
                "Wykonawca zgłosił zakończenie",
                "Potwierdź wykonanie zlecenia „" + saved.getTitle() + "” albo otwórz spór.", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public JobResponse confirmCompletion(Long jobId, User currentUser) {
        Long actorId = requireActorId(currentUser);
        Job job = jobRepository.findByIdAndCreatedByIdForUpdate(jobId, actorId)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.COMPLETION_REQUESTED) {
            throw new ConflictException("Wykonawca nie zgłosił jeszcze wykonania zlecenia");
        }
        if (job.getTakenBy() == null) throw new ConflictException("Zlecenie nie ma przypisanego wykonawcy");
        job.complete(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        jobRepository.flush();
        liveTrackingService.stopAndClear(saved.getId());
        transactionService.releaseMoney(saved, saved.getTakenBy());
        if (expenseService != null) {
            expenseService.settleOnCompletion(saved);
        }
        notificationService.notify(saved.getTakenBy(), NotificationType.JOB_COMPLETED, "Zlecenie potwierdzone",
                "Zlecenie „" + saved.getTitle() + "” zostało zakończone, a środki zwolnione.", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public JobResponse cancelJob(Long jobId, User currentUser) {
        Long actorId = requireActorId(currentUser);
        Job job = jobRepository.findByIdAndCreatedByIdForUpdate(jobId, actorId)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Można anulować tylko zlecenie, którego nikt jeszcze nie przyjął");
        }
        job.cancel(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        transactionService.refundMoney(saved);
        if (expenseService != null) {
            expenseService.refundAll(saved);
        }
        return toResponse(saved);
    }

    private void configureFulfillment(Job job, JobCategory category, JobRequest request, User user) {
        if (category.getFulfillmentMode() == FulfillmentMode.ON_SITE) {
            configureOnSiteJob(job, request);
            return;
        }
        configurePointToPointJob(job, request, user);
    }

    private void configureOnSiteJob(Job job, JobRequest request) {
        if (request.getRouteQuoteId() != null) {
            throw new BusinessException("Zlecenie wykonywane na miejscu nie może korzystać z trasy A → B");
        }
        RoutePointRequest location = request.getLocation();
        if (location == null) {
            throw new BusinessException("Wskaż miejsce wykonania usługi");
        }
        String privateLabel = normalizeOptional(location.privateLabel());
        if (privateLabel == null) {
            throw new BusinessException("Podaj dokładny adres wykonania usługi");
        }

        job.setLocation(GeoPointFactory.from(location.latitude(), location.longitude()));
        job.setLocationLabel(location.publicLabel().trim());
        job.setLocationPrivateLabel(privateLabel);
    }

    private void configurePointToPointJob(Job job, JobRequest request, User user) {
        if (request.getLocation() != null) {
            throw new BusinessException("Zlecenie transportowe powinno korzystać z wyznaczonej trasy A → B");
        }
        if (request.getRouteQuoteId() == null) {
            throw new BusinessException("Wyznacz trasę przed opublikowaniem zlecenia");
        }

        RouteQuote quote = routeQuoteService.consume(request.getRouteQuoteId(), user);
        job.setLocation(quote.getOrigin());
        job.setLocationLabel(quote.getOriginPublicLabel());
        job.setLocationPrivateLabel(quote.getOriginPrivateLabel());
        job.setDestinationLocation(quote.getDestination());
        job.setDestinationLabel(quote.getDestinationPublicLabel());
        job.setDestinationPrivateLabel(quote.getDestinationPrivateLabel());
        quote.getStops().forEach(stop -> job.addRouteStop(
                stop.getLocation(),
                stop.getPublicLabel(),
                stop.getPrivateLabel(),
                stop.getPlaceId()
        ));
        job.setRouteDistanceMeters(quote.getDistanceMeters());
        job.setRouteDurationSeconds(quote.getDurationSeconds());
        job.setRouteEncodedPolyline(quote.getEncodedPolyline());
        job.setRouteProvider(quote.getProvider());
        job.setRouteComputedAt(quote.getCreatedAt());
        job.setRouteQuote(quote);
    }

    private boolean usesLiveTracking(Job job) {
        return job.getCategory() != null && job.getCategory().getFulfillmentMode() == FulfillmentMode.POINT_TO_POINT;
    }

    private Job getJobForRead(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));
    }

    private Job getJobForExactLocationRead(Long jobId, User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ForbiddenOperationException(EXACT_LOCATION_ACCESS_DENIED);
        }
        return jobRepository.findParticipantById(jobId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenOperationException(EXACT_LOCATION_ACCESS_DENIED));
    }

    private Job getOpenInstantJobForUpdate(Long jobId) {
        return jobRepository.findByIdAndStatusAndAssignmentModeForUpdate(
                        jobId,
                        JobStatus.OPEN,
                        JobAssignmentMode.INSTANT
                )
                .orElseGet(() -> {
                    Job publicOpenJob = jobRepository.findByIdAndStatus(jobId, JobStatus.OPEN).orElse(null);
                    if (publicOpenJob != null && publicOpenJob.getAssignmentMode() != JobAssignmentMode.INSTANT) {
                        throw new ConflictException("To zlecenie wymaga wyboru wykonawcy spośród propozycji");
                    }
                    throw new ResourceNotFoundException(JOB_NOT_FOUND);
                });
    }

    private Long requireActorId(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ResourceNotFoundException(JOB_NOT_FOUND);
        }
        return currentUser.getId();
    }

    private void assertCanAccessExactLocation(Job job, User currentUser) {
        if (!canAccessExactLocation(job, currentUser)) {
            throw new ForbiddenOperationException(EXACT_LOCATION_ACCESS_DENIED);
        }
    }

    private boolean canAccessExactLocation(Job job, User currentUser) {
        boolean requester = sameUser(job.getCreatedBy(), currentUser);
        boolean assignedWorker = sameUser(job.getTakenBy(), currentUser);
        boolean activeAcceptedJob = job.getStatus() == JobStatus.IN_PROGRESS
                || job.getStatus() == JobStatus.COMPLETION_REQUESTED
                || job.getStatus() == JobStatus.DISPUTED;
        boolean requesterPreAssignment = requester && job.getStatus() == JobStatus.OPEN;
        return requesterPreAssignment || (activeAcceptedJob && (requester || assignedWorker));
    }

    private String exactOriginLabel(Job job) {
        return job.getLocationPrivateLabel() != null ? job.getLocationPrivateLabel() : job.getLocationLabel();
    }

    private String exactDestinationLabel(Job job) {
        return job.getDestinationPrivateLabel() != null ? job.getDestinationPrivateLabel() : job.getDestinationLabel();
    }

    private String exactStopLabel(JobRouteStop stop) {
        return stop.getPrivateLabel() != null ? stop.getPrivateLabel() : stop.getPublicLabel();
    }

    private JobRoutePointResponse pointResponse(Point point, String label) {
        return new JobRoutePointResponse(point.getY(), point.getX(), label);
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearchQuery(String value) {
        if (value == null) return "";
        return value.trim();
    }

    private String normalizeCategorySlug(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }

    private PageRequest discoveryPage(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private PageResponse<JobResponse> toPageResponse(Page<Job> result) {
        List<JobResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return PageResponse.from(result, content);
    }

    private JobResponse toResponse(Job job) {
        return JobResponseMapper.toResponse(job);
    }
}
