package com.doFast.dofastapp.payment.refund;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundRequestRepository;
import com.doFast.dofastapp.payment.refund.service.StripeRefundRequestService;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeRefundRequestPrivacyTest {

    @Mock private StripeRefundRequestRepository refundRepository;
    @Mock private PaymentTransactionRepository paymentRepository;
    @Mock private StripePaymentDisputeRepository disputeRepository;
    @Mock private WalletService walletService;

    private StripeRefundRequestService service;

    @BeforeEach
    void setUp() {
        service = new StripeRefundRequestService(
                refundRepository,
                paymentRepository,
                disputeRepository,
                walletService
        );
    }

    @Test
    void outsiderCannotEnumerateSettledPaymentThroughRefundCreation() {
        when(refundRepository.findByUserIdAndRequestKey(8L, "private-probe")).thenReturn(Optional.empty());
        when(paymentRepository.findOwnedByStripePaymentIntentIdForUpdate("pi_private", 8L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.create(
                        8L,
                        new CreateStripeRefundRequest("private-probe", "pi_private", new BigDecimal("5.00"))
                )
        );

        assertEquals("Płatność Stripe nie istnieje", error.getMessage());
        verify(paymentRepository).findOwnedByStripePaymentIntentIdForUpdate("pi_private", 8L);
        verifyNoInteractions(disputeRepository, walletService);
    }

    @Test
    void missingActorCannotReachRefundPersistenceOrWalletState() {
        CreateStripeRefundRequest request = new CreateStripeRefundRequest(
                "missing-actor",
                "pi_private",
                new BigDecimal("5.00")
        );

        assertThrows(ForbiddenOperationException.class, () -> service.create(null, request));
        assertThrows(ForbiddenOperationException.class, () -> service.get(101L, null));

        verifyNoInteractions(refundRepository, paymentRepository, disputeRepository, walletService);
    }

    @Test
    void outsiderCannotEnumerateExistingRefundState() {
        when(refundRepository.findByIdAndUserId(101L, 8L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.get(101L, 8L)
        );

        assertEquals("Zwrot nie istnieje", error.getMessage());
        verify(refundRepository, never()).findById(101L);
    }

    @Test
    void ownerStillReadsRefundStateThroughScopedLookup() {
        LocalDateTime now = LocalDateTime.now();
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_owner",
                "owner-request",
                new BigDecimal("12.50"),
                "PLN",
                now
        );
        ReflectionTestUtils.setField(request, "id", 101L);
        when(refundRepository.findByIdAndUserId(101L, 7L)).thenReturn(Optional.of(request));

        var response = service.get(101L, 7L);

        assertEquals(101L, response.id());
        assertEquals("pi_owner", response.paymentIntentId());
        assertEquals(new BigDecimal("12.50"), response.amount());
        assertEquals(StripeRefundStatus.REQUESTED, response.status());
        verify(refundRepository, never()).findById(101L);
    }
}
