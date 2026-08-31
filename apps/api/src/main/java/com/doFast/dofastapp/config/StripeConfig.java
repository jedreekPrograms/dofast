package com.doFast.dofastapp.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private final String secretKey;

    public StripeConfig(@Value("${stripe.secret.key}") String secretKey) {
        this.secretKey = secretKey;
    }

    @PostConstruct
    public void init() {
        StripeApiVersionContract.verifySdkVersion();
        Stripe.apiKey = secretKey;
    }
}
