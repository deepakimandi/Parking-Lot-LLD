package com.lld.parkinglot.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable record of one payment attempt (successful or not). */
public record Payment(String id, String ticketId, BigDecimal amount, PaymentMode mode, PaymentStatus status, Instant at) {}
