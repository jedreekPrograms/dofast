package com.doFast.dofastapp.review.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.review.dto.ReviewEligibilityResponse;
import com.doFast.dofastapp.review.dto.ReviewRequest;
import com.doFast.dofastapp.review.dto.ReviewResponse;
import com.doFast.dofastapp.review.entity.Review;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserBlockService userBlockService;

    public ReviewService(
            ReviewRepository reviewRepository,
            JobRepository jobRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            UserBlockService userBlockService
    ) {
        this.reviewRepository = reviewRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.userBlockService = userBlockService;
    }

    @Transactional
    public ReviewResponse addReview(ReviewRequest request, User reviewer) {
        Job job = jobRepository.findByIdForUpdate(request.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));

        if (job.getStatus() != JobStatus.DONE) {
            throw new ConflictException("Opinię można wystawić dopiero po zakończeniu zlecenia");
        }

        User reviewed = counterpart(job, reviewer);

        if (reviewRepository.findByJobAndReviewer(job, reviewer).isPresent()) {
            throw new ConflictException("Wystawiłeś już opinię za to zlecenie");
        }

        Review review = new Review();
        review.setJob(job);
        review.setReviewer(reviewer);
        review.setReviewed(reviewed);
        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));
        review.setCreatedAt(LocalDateTime.now());

        Review saved;
        try {
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Wystawiłeś już opinię za to zlecenie");
        }

        // A block created after a completed transaction must not erase marketplace accountability
        // or give either participant a way to suppress an otherwise eligible review. The review is
        // therefore persisted normally, while the direct REVIEW_RECEIVED notification is suppressed
        // so blocking still prevents a new notification channel between the two accounts.
        if (!userBlockService.isInteractionBlocked(reviewer, reviewed)) {
            notificationService.notify(
                    reviewed,
                    NotificationType.REVIEW_RECEIVED,
                    "Nowa opinia",
                    reviewer.getNickname() + " wystawił(a) Ci ocenę " + saved.getRating() + "/5.",
                    job,
                    null
            );
        }

        return toResponse(saved);
    }

    public ReviewEligibilityResponse getEligibility(Long jobId, User currentUser) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));

        User reviewed = counterpart(job, currentUser);
        boolean alreadyReviewed = reviewRepository.findByJobAndReviewer(job, currentUser).isPresent();

        return new ReviewEligibilityResponse(
                job.getId(),
                job.getStatus() == JobStatus.DONE && !alreadyReviewed,
                alreadyReviewed,
                reviewed.getId(),
                reviewed.getNickname()
        );
    }

    public PageResponse<ReviewResponse> getReceivedReviews(Long userId, int page, int size) {
        User reviewed = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));

        Page<Review> result = reviewRepository.findByReviewedOrderByCreatedAtDescIdDesc(
                reviewed,
                PageRequest.of(page, size)
        );
        List<ReviewResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.from(result, content);
    }

    private User counterpart(Job job, User reviewer) {
        if (sameUser(job.getCreatedBy(), reviewer)) {
            if (job.getTakenBy() == null) {
                throw new ConflictException("Zlecenie nie ma wykonawcy do ocenienia");
            }
            return job.getTakenBy();
        }
        if (sameUser(job.getTakenBy(), reviewer)) {
            return job.getCreatedBy();
        }
        throw new ForbiddenOperationException("Tylko strony zlecenia mogą wystawić opinię");
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getJob().getId(),
                review.getJob().getTitle(),
                review.getReviewer().getId(),
                review.getReviewer().getNickname(),
                review.getReviewed().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
