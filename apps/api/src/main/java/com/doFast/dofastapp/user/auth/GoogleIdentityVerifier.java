package com.doFast.dofastapp.user.auth;

public interface GoogleIdentityVerifier {
    GoogleIdentity verify(String credential);
}