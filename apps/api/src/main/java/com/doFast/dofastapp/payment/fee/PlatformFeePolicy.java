package com.doFast.dofastapp.payment.fee;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PlatformFeePolicy {

    private static final int MONEY_SCALE = 2;
    private static final int MAX_BASIS_POINTS = 1000;
    private static final BigDecimal BASIS_POINT_DIVISOR = new BigDecimal("10000");

    private final int basisPoints;

    public PlatformFeePolicy(@Value("${dofast.payments.platform-fee-basis-points:100}") int basisPoints) {
        validateBasisPoints(basisPoints);
        this.basisPoints = basisPoints;
    }

    public int currentBasisPoints() {
        return basisPoints;
    }

    public BigDecimal currentPercent() {
        return BigDecimal.valueOf(basisPoints)
                .movePointLeft(2)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public PlatformFeeQuote quoteCurrent(BigDecimal grossAmount) {
        return quote(grossAmount, basisPoints);
    }

    public PlatformFeeQuote quote(BigDecimal grossAmount, int snapshotBasisPoints) {
        validateBasisPoints(snapshotBasisPoints);
        BigDecimal gross = normalizeMoney(grossAmount);
        if (gross.signum() <= 0) {
            throw new IllegalArgumentException("Gross settlement amount must be positive");
        }

        BigDecimal fee = gross
                .multiply(BigDecimal.valueOf(snapshotBasisPoints))
                .divide(BASIS_POINT_DIVISOR, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal payout = gross.subtract(fee).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (payout.signum() <= 0) {
            throw new IllegalArgumentException("Platform fee cannot consume the whole settlement");
        }
        return new PlatformFeeQuote(gross, fee, payout, snapshotBasisPoints);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Settlement amount is required");
        }
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Settlement amount must use at most two decimal places", ex);
        }
    }

    private void validateBasisPoints(int value) {
        if (value < 0 || value > MAX_BASIS_POINTS) {
            throw new IllegalArgumentException("Platform fee basis points must be between 0 and " + MAX_BASIS_POINTS);
        }
    }
}
