package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.user.entity.User;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void jobPublicationPaymentMetadataContainsOnlySettlementReferences() throws ReflectiveOperationException {
        User owner = mock(User.class);
        JobPublication publication = mock(JobPublication.class);
        when(owner.getId()).thenReturn(42L);
        when(publication.getId()).thenReturn(77L);
        when(publication.getUser()).thenReturn(owner);
        when(publication.getPaymentAmount()).thenReturn(new BigDecimal("125.50"));
        when(publication.getCurrency()).thenReturn("PLN");

        PaymentIntentCreateParams params = JobPublicationPaymentIntentService.paymentIntentParams(publication);

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) readField(params, "metadata");
        assertThat(metadata).containsExactlyInAnyOrderEntriesOf(Map.of(
                "userId", "42",
                "purpose", JobPublicationPaymentIntentService.PURPOSE,
                "jobPublicationId", "77"
        ));
        assertThat(metadata).doesNotContainKey("topUpRequestId");
        assertThat(readField(params, "amount")).isEqualTo(12_550L);
        assertThat(readField(params, "currency")).isEqualTo("pln");
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
