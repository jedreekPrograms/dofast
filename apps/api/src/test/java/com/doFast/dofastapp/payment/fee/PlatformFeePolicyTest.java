package com.doFast.dofastapp.payment.fee;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformFeePolicyTest {

    @Test
    void quotesOnePercentWithCurrencyRounding() {
        PlatformFeePolicy policy = new PlatformFeePolicy(100);

        PlatformFeeQuote quote = policy.quoteCurrent(new BigDecimal("33.50"));

        assertEquals(new BigDecimal("33.50"), quote.grossAmount());
        assertEquals(new BigDecimal("0.34"), quote.platformFeeAmount());
        assertEquals(new BigDecimal("33.16"), quote.workerPayoutAmount());
        assertEquals(100, quote.basisPoints());
        assertEquals(new BigDecimal("1.00"), policy.currentPercent());
    }

    @Test
    void quoteCanUseHistoricalSnapshotInsteadOfCurrentRate() {
        PlatformFeePolicy policy = new PlatformFeePolicy(100);

        PlatformFeeQuote quote = policy.quote(new BigDecimal("40.00"), 250);

        assertEquals(new BigDecimal("1.00"), quote.platformFeeAmount());
        assertEquals(new BigDecimal("39.00"), quote.workerPayoutAmount());
        assertEquals(250, quote.basisPoints());
    }

    @Test
    void rejectsUnsafeConfiguredRate() {
        assertThrows(IllegalArgumentException.class, () -> new PlatformFeePolicy(1001));
        assertThrows(IllegalArgumentException.class, () -> new PlatformFeePolicy(-1));
    }
}
