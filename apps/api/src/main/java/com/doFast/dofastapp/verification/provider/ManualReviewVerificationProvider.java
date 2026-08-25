package com.doFast.dofastapp.verification.provider;

import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ManualReviewVerificationProvider implements VerificationProvider {

    public static final String PROVIDER_CODE = "MANUAL_REVIEW";

    @Override
    public VerificationSubmission startVerification(User user) {
        return new VerificationSubmission(PROVIDER_CODE, null);
    }
}
