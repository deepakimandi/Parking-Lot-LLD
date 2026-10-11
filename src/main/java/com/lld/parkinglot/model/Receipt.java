package com.lld.parkinglot.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** What the exit gate prints. */
public record Receipt(String ticketId, String licensePlate, String spotId, Instant entryTime, Instant exitTime,
                      Duration parkedFor, BigDecimal amount, PaymentMode mode, String paymentId) {
    @Override
    public String toString() {
        return String.format("Receipt[%s, %s, spot %s, %dh%02dm, Rs %s via %s]", ticketId, licensePlate, spotId,
                parkedFor.toHours(), parkedFor.toMinutesPart(), amount, mode);
    }
}
