package com.lld.parkinglot.payment;

import com.lld.parkinglot.model.PaymentMode;

import java.math.BigDecimal;

/** Simulated CARD payment. A real one would call a terminal / gateway SDK here. */
public class CardPaymentProcessor implements PaymentProcessor {
    @Override public PaymentMode mode() { return PaymentMode.CARD; }

    @Override
    public boolean charge(String reference, BigDecimal amount) {
        return amount.signum() >= 0;
    }
}
