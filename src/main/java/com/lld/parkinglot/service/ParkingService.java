package com.lld.parkinglot.service;

import com.lld.parkinglot.allocation.SpotAllocationStrategy;
import com.lld.parkinglot.events.EventPublisher;
import com.lld.parkinglot.events.ParkingEvent;
import com.lld.parkinglot.events.ParkingEventType;
import com.lld.parkinglot.exception.InvalidTicketStateException;
import com.lld.parkinglot.exception.ParkingFullException;
import com.lld.parkinglot.exception.PaymentFailedException;
import com.lld.parkinglot.exception.TicketNotFoundException;
import com.lld.parkinglot.exception.VehicleAlreadyParkedException;
import com.lld.parkinglot.model.ParkingLot;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.Payment;
import com.lld.parkinglot.model.PaymentMode;
import com.lld.parkinglot.model.PaymentStatus;
import com.lld.parkinglot.model.Receipt;
import com.lld.parkinglot.model.Ticket;
import com.lld.parkinglot.model.TicketStatus;
import com.lld.parkinglot.model.Vehicle;
import com.lld.parkinglot.payment.PaymentService;
import com.lld.parkinglot.pricing.PricingStrategy;
import com.lld.parkinglot.repository.TicketRepository;
import com.lld.parkinglot.util.IdGenerator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade for the two use cases: park (entry gate) and checkout (exit gate).
 * Holds no lock of its own: every shared piece of state is updated atomically where it lives
 * (plate set, free-spot pool, spot CAS, ticket CAS), so gates never wait on each other.
 */
public class ParkingService {
    private final ParkingLot lot;
    private final SpotAllocationStrategy allocationStrategy;
    private final PricingStrategy pricingStrategy;
    private final PaymentService paymentService;
    private final TicketRepository tickets;
    private final EventPublisher events;
    private final Clock clock;
    private final IdGenerator ticketIds = new IdGenerator("T");
    /** Plates currently inside. add() is atomic, so the same plate can't enter at two gates at once. */
    private final Set<String> platesInside = ConcurrentHashMap.newKeySet();

    public ParkingService(ParkingLot lot, SpotAllocationStrategy allocationStrategy, PricingStrategy pricingStrategy,
                          PaymentService paymentService, TicketRepository tickets, EventPublisher events, Clock clock) {
        this.lot = lot;
        this.allocationStrategy = allocationStrategy;
        this.pricingStrategy = pricingStrategy;
        this.paymentService = paymentService;
        this.tickets = tickets;
        this.events = events;
        this.clock = clock;
    }

    /** Entry: reserve the plate, take a spot, issue a ticket. */
    public Ticket park(Vehicle vehicle, String entryGateId) {
        String plate = vehicle.licensePlate();
        if (!platesInside.add(plate)) throw new VehicleAlreadyParkedException(plate);

        ParkingSpot spot;
        try {
            spot = allocationStrategy.allocate(lot.floors(), vehicle)
                    .orElseThrow(() -> new ParkingFullException(vehicle.type()));
        } catch (RuntimeException e) {
            platesInside.remove(plate);            // undo the reservation, so the car can try again later
            throw e;
        }

        Ticket ticket = new Ticket(ticketIds.next(), vehicle, spot, clock.instant(), entryGateId);
        tickets.save(ticket);
        events.publish(new ParkingEvent(ParkingEventType.VEHICLE_PARKED, ticket, ticket.entryTime()));
        return ticket;
    }

    /** What the driver would pay if they left now (shown at a pay-station before choosing a payment mode). */
    public BigDecimal quote(String ticketId) {
        Ticket ticket = find(ticketId);
        if (ticket.status() != TicketStatus.ACTIVE) throw new InvalidTicketStateException(ticketId, ticket.status());
        return pricingStrategy.calculateFee(ticket.vehicle().type(), Duration.between(ticket.entryTime(), clock.instant()));
    }

    /** Exit: compute fee, take payment, free the spot. No payment, no exit. */
    public Receipt checkout(String ticketId, PaymentMode mode, String exitGateId) {
        Ticket ticket = find(ticketId);
        if (!ticket.tryBeginCheckout()) {                       // only one gate can check out a ticket
            throw new InvalidTicketStateException(ticketId, ticket.status());
        }

        Instant exitTime = clock.instant();
        Duration parkedFor = Duration.between(ticket.entryTime(), exitTime);
        Payment payment;
        try {
            BigDecimal fee = pricingStrategy.calculateFee(ticket.vehicle().type(), parkedFor);
            payment = paymentService.pay(ticket.id(), fee, mode);
        } catch (RuntimeException e) {
            ticket.abortCheckout();
            throw e;
        }
        if (payment.status() != PaymentStatus.SUCCESS) {
            ticket.abortCheckout();                             // stays ACTIVE; the spot stays occupied
            throw new PaymentFailedException(ticketId, mode);
        }

        ticket.markPaid(payment, exitTime);
        lot.floor(ticket.spot().floorNumber()).release(ticket.spot(), ticket.vehicle());
        platesInside.remove(ticket.vehicle().licensePlate());
        events.publish(new ParkingEvent(ParkingEventType.VEHICLE_EXITED, ticket, exitTime));

        return new Receipt(ticket.id(), ticket.vehicle().licensePlate(), ticket.spot().id(), ticket.entryTime(),
                exitTime, parkedFor, payment.amount(), mode, payment.id());
    }

    public Ticket find(String ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
    }
}
