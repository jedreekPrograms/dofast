package com.doFast.dofastapp.review.repository;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.review.entity.Review;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByJob(Job job);

    List<Review> findByReviewed(User reviewed);
}
