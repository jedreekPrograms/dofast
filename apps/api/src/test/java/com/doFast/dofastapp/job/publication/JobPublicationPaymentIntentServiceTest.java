package com.doFast.dofastapp.job.publication;

import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class JobPublicationPaymentIntentServiceTest {

    @Test
    void automaticPaymentMethodsAllowStripeEligibleRedirectMethods() throws ReflectiveOperationException {
        PaymentIntentCreateParams.AutomaticPaymentMethods methods =
                JobPublicationPaymentIntentService.automaticPaymentMethods();

        assertThat(readField(methods, "enabled")).isEqualTo(Boolean.TRUE);
        assertThat(readField(methods, "allowRedirects"))
                .as("doFast must not disable redirect-based payment methods supported by the return flow")
                .isNull();
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
