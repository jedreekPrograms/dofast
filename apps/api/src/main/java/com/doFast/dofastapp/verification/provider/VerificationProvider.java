package com.doFast.dofastapp.verification.provider;

import com.doFast.dofastapp.user.entity.User;

public interface VerificationProvider {
    VerificationSubmission startVerification(User user);
}
