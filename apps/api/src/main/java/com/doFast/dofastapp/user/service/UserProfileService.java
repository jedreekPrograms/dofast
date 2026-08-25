package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.user.dto.UserProfileResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.service.VerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final JobRepository jobRepository;
    private final VerificationService verificationService;

    public UserProfileService(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            JobRepository jobRepository,
            VerificationService verificationService
    ) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.jobRepository = jobRepository;
        this.verificationService = verificationService;
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));

        Double average = reviewRepository.findAverageRatingByReviewedId(userId);
        long reviewsCount = reviewRepository.countByReviewedId(userId);
        long completedAsRequester = jobRepository.countByStatusAndCreatedBy(JobStatus.DONE, user);
        long completedAsWorker = jobRepository.countByStatusAndTakenBy(JobStatus.DONE, user);

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                average != null ? Math.round(average * 10.0) / 10.0 : null,
                reviewsCount,
                completedAsRequester,
                completedAsWorker,
                completedAsRequester + completedAsWorker,
                verificationService.isVerified(userId)
        );
    }
}
