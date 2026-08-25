package com.doFast.dofastapp.review.repository;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.review.entity.Review;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByJobAndReviewer(Job job, User reviewer);

    Page<Review> findByReviewedOrderByCreatedAtDescIdDesc(User reviewed, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewed.id = :userId")
    Double findAverageRatingByReviewedId(Long userId);

    long countByReviewedId(Long userId);
}
