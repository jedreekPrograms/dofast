package com.doFast.dofastapp.payment.service;

import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class StripePaymentServicePaymentMethodsTest {

    @Test
    void automaticPaymentMethodsAllowStripeEligibleRedirectMethods() throws ReflectiveOperationException {
        PaymentIntentCreateParams.AutomaticPaymentMethods methods =
                StripePaymentService.automaticPaymentMethods();

        assertThat(readField(methods, "enabled")).isEqualTo(Boolean.TRUE);
        assertThat(readField(methods, "allowRedirects"))
                .as("wallet top-ups must not disable redirect methods supported by the wallet return flow")
                .isNull();
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
