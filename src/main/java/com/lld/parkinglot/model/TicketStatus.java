package com.lld.parkinglot.model;

/** ACTIVE -> CHECKOUT_IN_PROGRESS -> PAID ; CHECKOUT_IN_PROGRESS -> ACTIVE if payment fails. */
public enum TicketStatus {
    ACTIVE,
    CHECKOUT_IN_PROGRESS,
    PAID
}
