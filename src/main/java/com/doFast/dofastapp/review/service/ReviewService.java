package com.doFast.dofastapp.review.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.review.dto.ReviewRequest;
import com.doFast.dofastapp.review.dto.ReviewResponse;
import com.doFast.dofastapp.review.entity.Review;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.user.entity.User;


public class ReviewService {

    private final ReviewRepository reviewRepository;
    private  final JobRepository jobRepository;

    public ReviewService(ReviewRepository reviewRepository, JobRepository jobRepository) {
        this.reviewRepository = reviewRepository;
        this.jobRepository = jobRepository;
    }

    public ReviewResponse addReview(ReviewRequest request, User reviewer) {

        Job job = jobRepository.findById(request.getJobId())
                .orElseThrow(() -> new BusinessException("Zlecenie nie istnieje"));

        if (job.getStatus() != JobStatus.DONE) {
            throw new BusinessException("Zlecenie nie jest zakończone");
        }

        if (job.getCreatedBy().getId().equals(reviewer.getId())) {
            throw new BusinessException("Tylko autor zlecenia może wystawić opinię");
        }

        if (job.getTakenBy() == null) {
            throw new BusinessException("Zlecenie nie ma wykonawcy");
        }

        if(reviewRepository.findByJob(job).isPresent()) {
            throw new BusinessException("Ocena dla tego zlecenia już istnieje");

        }

        Review review = new Review();
        review.setJob(job);
        review.setReviewer(reviewer);
        review.setReviewed(job.getTakenBy());
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        reviewRepository.save(review);

        return new ReviewResponse(
                review.getRating(),
                review.getComment()
        );
    }
}
