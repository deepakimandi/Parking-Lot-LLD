package com.lld.parkinglot.model;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issued at entry. Status changes are compare-and-set, so if the same ticket is presented at two exit gates
 * at once, only one of them can start checkout and the driver is charged once.
 */
public final class Ticket {
    private final String id;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final Instant entryTime;
    private final String entryGateId;
    private final AtomicReference<TicketStatus> status = new AtomicReference<>(TicketStatus.ACTIVE);
    private volatile Instant exitTime;
    private volatile Payment payment;

    public Ticket(String id, Vehicle vehicle, ParkingSpot spot, Instant entryTime, String entryGateId) {
        this.id = id;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = entryTime;
        this.entryGateId = entryGateId;
    }

    /** ACTIVE -> CHECKOUT_IN_PROGRESS. False if already paid or another gate is checking it out. */
    public boolean tryBeginCheckout() {
        return status.compareAndSet(TicketStatus.ACTIVE, TicketStatus.CHECKOUT_IN_PROGRESS);
    }

    /** CHECKOUT_IN_PROGRESS -> ACTIVE (payment failed; the driver may try another mode). */
    public void abortCheckout() {
        status.compareAndSet(TicketStatus.CHECKOUT_IN_PROGRESS, TicketStatus.ACTIVE);
    }

    /** CHECKOUT_IN_PROGRESS -> PAID. Fields are written before the CAS, so anyone who sees PAID sees them too. */
    public void markPaid(Payment payment, Instant exitTime) {
        this.payment = payment;
        this.exitTime = exitTime;
        if (!status.compareAndSet(TicketStatus.CHECKOUT_IN_PROGRESS, TicketStatus.PAID)) {
            throw new IllegalStateException("Ticket " + id + " is not in checkout");
        }
    }

    public String id() { return id; }
    public Vehicle vehicle() { return vehicle; }
    public ParkingSpot spot() { return spot; }
    public Instant entryTime() { return entryTime; }
    public String entryGateId() { return entryGateId; }
    public TicketStatus status() { return status.get(); }
    public Optional<Instant> exitTime() { return Optional.ofNullable(exitTime); }
    public Optional<Payment> payment() { return Optional.ofNullable(payment); }

    @Override
    public String toString() {
        return "Ticket[" + id + ", " + vehicle.licensePlate() + " " + vehicle.type() + " @ " + spot + ", " + status() + "]";
    }
}
