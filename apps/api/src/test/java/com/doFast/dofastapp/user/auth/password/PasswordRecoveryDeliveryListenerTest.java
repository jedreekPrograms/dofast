package com.doFast.dofastapp.user.auth.password;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryDeliveryListenerTest {

    @Test
    void listenerDelegatesRawTokenToMailer() {
        PasswordRecoveryMailer mailer = mock(PasswordRecoveryMailer.class);
        PasswordRecoveryDeliveryListener listener = new PasswordRecoveryDeliveryListener(mailer);
        PasswordRecoveryDeliveryRequested event = new PasswordRecoveryDeliveryRequested(
                9L,
                "user@example.com",
                "raw-reset-token"
        );

        listener.deliver(event);

        verify(mailer).sendResetLink("user@example.com", "raw-reset-token");
    }

    @Test
    void deliveryFailureIsContainedAndDoesNotEscapeAsyncBoundary() {
        PasswordRecoveryMailer mailer = mock(PasswordRecoveryMailer.class);
        when(mailer.toString()).thenReturn("mailer");
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp unavailable"))
                .when(mailer).sendResetLink("user@example.com", "raw-reset-token");
        PasswordRecoveryDeliveryListener listener = new PasswordRecoveryDeliveryListener(mailer);

        assertDoesNotThrow(() -> listener.deliver(new PasswordRecoveryDeliveryRequested(
                9L,
                "user@example.com",
                "raw-reset-token"
        )));
    }
}
