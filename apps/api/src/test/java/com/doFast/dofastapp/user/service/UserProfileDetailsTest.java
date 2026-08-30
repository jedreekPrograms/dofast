package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.review.repository.ReviewRepository;
import com.doFast.dofastapp.user.auth.GoogleIdentityVerifier;
import com.doFast.dofastapp.user.auth.email.EmailVerificationService;
import com.doFast.dofastapp.user.auth.session.AuthRefreshSessionRepository;
import com.doFast.dofastapp.user.dto.UpdateProfileRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserAuthIdentityRepository;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.service.VerificationService;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileDetailsTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private WalletService walletService;
    @Mock private UserAuthIdentityRepository authIdentityRepository;
    @Mock private GoogleIdentityVerifier googleIdentityVerifier;
    @Mock private AuthRefreshSessionRepository refreshSessionRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private ReviewRepository reviewRepository;
    @Mock private JobRepository jobRepository;
    @Mock private VerificationService verificationService;
    @Mock private UserServiceCategoryService userServiceCategoryService;

    @Test
    void ownerUpdateNormalizesOptionalPublicFields() {
        User user = user(7L, LocalDateTime.of(2025, 4, 12, 9, 30));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserService service = new UserService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                walletService,
                authIdentityRepository,
                googleIdentityVerifier,
                refreshSessionRepository,
                emailVerificationService
        );

        var response = service.updateProfile(
                user,
                new UpdateProfileRequest(
                        "  Solidny Wykonawca  ",
                        "  Pomagam w przeprowadzkach i transporcie.  ",
                        "   "
                )
        );

        assertEquals("Solidny Wykonawca", response.nickname());
        assertEquals("Pomagam w przeprowadzkach i transporcie.", response.bio());
        assertNull(response.publicLocation());
        assertEquals(response.bio(), user.getBio());
        assertNull(user.getPublicLocation());
    }

    @Test
    void publicProfileContainsOnlyExplicitProfileFieldsAndTrustSummary() {
        LocalDateTime memberSince = LocalDateTime.of(2025, 4, 12, 9, 30);
        User user = user(9L, memberSince);
        user.setBio("Montaż, drobne remonty i transport.");
        user.setPublicLocation("Wrocław i okolice");

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(reviewRepository.findAverageRatingByReviewedId(9L)).thenReturn(4.84);
        when(reviewRepository.countByReviewedId(9L)).thenReturn(17L);
        when(jobRepository.countByStatusAndCreatedBy(JobStatus.DONE, user)).thenReturn(4L);
        when(jobRepository.countByStatusAndTakenBy(JobStatus.DONE, user)).thenReturn(23L);
        when(verificationService.isVerified(9L)).thenReturn(true);
        when(userServiceCategoryService.getForUser(9L)).thenReturn(List.of());

        UserProfileService service = new UserProfileService(
                userRepository,
                reviewRepository,
                jobRepository,
                verificationService,
                userServiceCategoryService
        );

        var profile = service.getProfile(9L);

        assertEquals(9L, profile.userId());
        assertEquals("Użytkownik 9", profile.username());
        assertEquals("Montaż, drobne remonty i transport.", profile.bio());
        assertEquals("Wrocław i okolice", profile.publicLocation());
        assertEquals(memberSince, profile.memberSince());
        assertEquals(4.8, profile.averageRating());
        assertEquals(17L, profile.reviewsCount());
        assertEquals(27L, profile.completedJobsTotal());
        assertTrue(profile.identityVerified());
        assertTrue(profile.serviceCategories().isEmpty());
    }

    private User user(Long id, LocalDateTime createdAt) {
        User user = new User("private@example.com", "Użytkownik " + id);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        return user;
    }
}
