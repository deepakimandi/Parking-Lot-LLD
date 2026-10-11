package com.lld.parkinglot.payment;

import com.lld.parkinglot.model.PaymentMode;

import java.math.BigDecimal;

/** Simulated CASH payment. A real one would call a terminal / gateway SDK here. */
public class CashPaymentProcessor implements PaymentProcessor {
    @Override public PaymentMode mode() { return PaymentMode.CASH; }

    @Override
    public boolean charge(String reference, BigDecimal amount) {
        return amount.signum() >= 0;
    }
}
