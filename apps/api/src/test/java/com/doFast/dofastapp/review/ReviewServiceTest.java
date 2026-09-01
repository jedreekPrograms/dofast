package com.doFast.dofastapp.review;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.review.dto.ReviewRequest;
import com.doFast.dofastapp.review.entity.Review;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.review.service.ReviewService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private JobRepository jobRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserBlockService userBlockService;

    private ReviewService reviewService;
    private User requester;
    private User worker;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository,
                jobRepository,
                userRepository,
                notificationService,
                userBlockService
        );
        requester = user(1L, "requester");
        worker = user(2L, "worker");
    }

    @Test
    void requesterCanReviewWorkerAfterCompletedJob() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantByIdForUpdate(10L, requester.getId())).thenReturn(Optional.of(job));
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
    void blockedParticipantsKeepReviewAccountabilityWithoutDirectNotification() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantByIdForUpdate(10L, requester.getId())).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, requester)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 102L);
            return review;
        });
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(true);

        var response = reviewService.addReview(request(10L, 2, "Ocena po zakończonej transakcji"), requester);

        assertEquals(worker.getId(), response.reviewedId());
        assertEquals(2, response.rating());
        verify(reviewRepository).saveAndFlush(any(Review.class));
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void workerCanReviewRequesterAfterCompletedJob() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantByIdForUpdate(10L, worker.getId())).thenReturn(Optional.of(job));
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
    void outsiderCannotEnumerateCompletedJobThroughReviewCreation() {
        User outsider = user(3L, "outsider");
        when(jobRepository.findParticipantByIdForUpdate(10L, outsider.getId())).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> reviewService.addReview(request(10L, 5, "Nie moja transakcja"), outsider)
        );

        verify(jobRepository, never()).findByIdForUpdate(10L);
        verify(reviewRepository, never()).findByJobAndReviewer(any(), any());
    }

    @Test
    void unfinishedJobCannotBeReviewed() {
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findParticipantByIdForUpdate(10L, requester.getId())).thenReturn(Optional.of(job));

        assertThrows(
                ConflictException.class,
                () -> reviewService.addReview(request(10L, 5, null), requester)
        );
    }

    @Test
    void sameParticipantCannotReviewSameJobTwice() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantByIdForUpdate(10L, requester.getId())).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, requester)).thenReturn(Optional.of(new Review()));

        assertThrows(
                ConflictException.class,
                () -> reviewService.addReview(request(10L, 5, null), requester)
        );
    }

    @Test
    void eligibilityTurnsOffAfterExistingReview() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantById(10L, requester.getId())).thenReturn(Optional.of(job));
        when(reviewRepository.findByJobAndReviewer(job, requester)).thenReturn(Optional.of(new Review()));

        var eligibility = reviewService.getEligibility(10L, requester);

        assertFalse(eligibility.eligible());
        assertEquals(worker.getId(), eligibility.counterpartId());
    }

    @Test
    void outsiderCannotEnumerateCompletedJobThroughReviewEligibility() {
        User outsider = user(3L, "outsider");
        when(jobRepository.findParticipantById(10L, outsider.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.getEligibility(10L, outsider));

        verify(jobRepository, never()).findById(10L);
        verify(reviewRepository, never()).findByJobAndReviewer(any(), any());
    }

    @Test
    void activeUserReviewsRemainPublicThroughActiveScopedLookup() {
        when(userRepository.findByIdAndStatus(worker.getId(), UserStatus.ACTIVE)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByReviewedOrderByCreatedAtDescIdDesc(eq(worker), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var response = reviewService.getReceivedReviews(worker.getId(), 0, 10);

        assertEquals(0, response.totalElements());
        verify(userRepository, never()).findById(worker.getId());
    }

    @Test
    void suspendedOrMissingUserReviewsAreHiddenBeforeReviewLookup() {
        worker.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByIdAndStatus(worker.getId(), UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> reviewService.getReceivedReviews(worker.getId(), 0, 10)
        );

        verify(userRepository, never()).findById(worker.getId());
        verify(reviewRepository, never()).findByReviewedOrderByCreatedAtDescIdDesc(any(), any());
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
