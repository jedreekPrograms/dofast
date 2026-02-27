package com.doFast.dofastapp.payment.dto;

import java.math.BigDecimal;

public class CreatePaymentIntentRequest {

    private BigDecimal amount;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
