package com.lld.parkinglot;

import com.lld.parkinglot.allocation.BestFitStrategy;
import com.lld.parkinglot.allocation.SpotAllocationStrategy;
import com.lld.parkinglot.events.DisplayBoard;
import com.lld.parkinglot.events.EventPublisher;
import com.lld.parkinglot.gate.EntryGate;
import com.lld.parkinglot.gate.ExitGate;
import com.lld.parkinglot.model.ParkingLot;
import com.lld.parkinglot.model.VehicleType;
import com.lld.parkinglot.payment.CardPaymentProcessor;
import com.lld.parkinglot.payment.CashPaymentProcessor;
import com.lld.parkinglot.payment.PaymentProcessor;
import com.lld.parkinglot.payment.PaymentService;
import com.lld.parkinglot.payment.UpiPaymentProcessor;
import com.lld.parkinglot.pricing.HourlyPricingStrategy;
import com.lld.parkinglot.pricing.PricingStrategy;
import com.lld.parkinglot.repository.InMemoryTicketRepository;
import com.lld.parkinglot.service.ParkingService;
import com.lld.parkinglot.util.Money;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Composition root: the ONE place that picks concrete classes and wires them together (manual dependency
 * injection). Everything else depends on interfaces. One app = one lot; this replaces a Singleton.
 */
public class ParkingLotApplication {
    private final ParkingLot lot;
    private final ParkingService parkingService;
    private final DisplayBoard displayBoard;
    private final EventPublisher events = new EventPublisher();

    public ParkingLotApplication(ParkingLot lot, SpotAllocationStrategy allocation, PricingStrategy pricing,
                                 List<PaymentProcessor> processors, Clock clock) {
        this.lot = lot;
        PaymentService payments = new PaymentService(processors, clock);
        this.parkingService = new ParkingService(lot, allocation, pricing, payments,
                new InMemoryTicketRepository(), events, clock);
        this.displayBoard = new DisplayBoard(lot);
        events.subscribe(displayBoard);
    }

    /** Defaults: best-fit allocation, hourly pricing (bike 20, car 40, truck 100 per hour), cash/card/UPI. */
    public static ParkingLotApplication withDefaults(ParkingLot lot, Clock clock) {
        return new ParkingLotApplication(lot, new BestFitStrategy(), new HourlyPricingStrategy(defaultHourlyRates()),
                List.of(new CashPaymentProcessor(), new CardPaymentProcessor(), new UpiPaymentProcessor()), clock);
    }

    public static Map<VehicleType, java.math.BigDecimal> defaultHourlyRates() {
        return Map.of(VehicleType.BIKE, Money.of(20), VehicleType.CAR, Money.of(40), VehicleType.TRUCK, Money.of(100));
    }

    public EntryGate entryGate(String id) { return new EntryGate(id, parkingService); }
    public ExitGate exitGate(String id) { return new ExitGate(id, parkingService); }

    public ParkingLot lot() { return lot; }
    public ParkingService parkingService() { return parkingService; }
    public DisplayBoard displayBoard() { return displayBoard; }
    public EventPublisher events() { return events; }
}
