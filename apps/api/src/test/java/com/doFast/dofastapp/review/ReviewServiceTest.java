package com.doFast.dofastapp.review;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.review.dto.ReviewRequest;
import com.doFast.dofastapp.review.entity.Review;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.review.service.ReviewService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private JobRepository jobRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private ReviewService reviewService;
    private User requester;
    private User worker;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, jobRepository, userRepository, notificationService);
        requester = user(1L, "requester");
        worker = user(2L, "worker");
    }

    @Test
    void requesterCanReviewWorkerAfterCompletedJob() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, requester)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 100L);
            return review;
        });

        var response = reviewService.addReview(request(10L, 5, "  Świetna realizacja  "), requester);

        assertEquals(worker.getId(), response.reviewedId());
        assertEquals(requester.getId(), response.reviewerId());
        assertEquals("Świetna realizacja", response.comment());
        verify(notificationService).notify(
                eq(worker),
                eq(NotificationType.REVIEW_RECEIVED),
                eq("Nowa opinia"),
                any(String.class),
                eq(job),
                eq(null)
        );
    }

    @Test
    void workerCanReviewRequesterAfterCompletedJob() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, worker)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 101L);
            return review;
        });

        var response = reviewService.addReview(request(10L, 4, null), worker);

        assertEquals(requester.getId(), response.reviewedId());
        assertEquals(worker.getId(), response.reviewerId());
    }

    @Test
    void outsiderCannotReviewJobParticipants() {
        Job job = job(JobStatus.DONE);
        User outsider = user(3L, "outsider");
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        assertThrows(
                ForbiddenOperationException.class,
                () -> reviewService.addReview(request(10L, 5, "Nie moja transakcja"), outsider)
        );
    }

    @Test
    void unfinishedJobCannotBeReviewed() {
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        assertThrows(
                ConflictException.class,
                () -> reviewService.addReview(request(10L, 5, null), requester)
        );
    }

    @Test
    void sameParticipantCannotReviewSameJobTwice() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, requester)).thenReturn(Optional.of(new Review()));

        assertThrows(
                ConflictException.class,
                () -> reviewService.addReview(request(10L, 5, null), requester)
        );
    }

    @Test
    void eligibilityTurnsOffAfterExistingReview() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, requester)).thenReturn(Optional.of(new Review()));

        var eligibility = reviewService.getEligibility(10L, requester);

        assertFalse(eligibility.eligible());
        assertEquals(worker.getId(), eligibility.counterpartId());
    }

    private ReviewRequest request(Long jobId, int rating, String comment) {
        ReviewRequest request = new ReviewRequest();
        request.setJobId(jobId);
        request.setRating(rating);
        request.setComment(comment);
        return request;
    }

    private Job job(JobStatus status) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Testowe zlecenie");
        job.setDescription("Opis");
        job.setStatus(status);
        job.setCreatedBy(requester);
        job.setTakenBy(worker);
        return job;
    }

    private User user(Long id, String nickname) {
        User user = new User(nickname + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
