package com.lld.parkinglot.payment;

import com.lld.parkinglot.model.PaymentMode;

import java.math.BigDecimal;

/** Strategy (and the Adapter seam for real gateways): one implementation per payment mode. */
public interface PaymentProcessor {
    PaymentMode mode();

    /** Charges the amount. Returns true on success. May also throw; the caller treats that as a failure. */
    boolean charge(String reference, BigDecimal amount);
}
