package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.user.dto.UserProfileResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    public UserProfileService(UserRepository userRepository, ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    public UserProfileResponse getProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Double avg = reviewRepository.findAverageRatingByReviewedId(userId);
        Long count = reviewRepository.countByReviewedId(userId);

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                avg != null ? Math.round(avg * 10.0) / 10.0 : null,
                count
        );
    }
}
