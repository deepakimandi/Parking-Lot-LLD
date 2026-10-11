package com.lld.parkinglot;

import com.lld.parkinglot.allocation.BestFitStrategy;
import com.lld.parkinglot.allocation.NearestFloorStrategy;
import com.lld.parkinglot.allocation.SpotAllocationStrategy;
import com.lld.parkinglot.events.ParkingEventType;
import com.lld.parkinglot.exception.InvalidTicketStateException;
import com.lld.parkinglot.exception.ParkingException;
import com.lld.parkinglot.exception.ParkingFullException;
import com.lld.parkinglot.exception.PaymentFailedException;
import com.lld.parkinglot.exception.TicketNotFoundException;
import com.lld.parkinglot.exception.UnsupportedPaymentModeException;
import com.lld.parkinglot.exception.VehicleAlreadyParkedException;
import com.lld.parkinglot.gate.EntryGate;
import com.lld.parkinglot.gate.ExitGate;
import com.lld.parkinglot.model.ParkingLot;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.PaymentMode;
import com.lld.parkinglot.model.Receipt;
import com.lld.parkinglot.model.SpotType;
import com.lld.parkinglot.model.Ticket;
import com.lld.parkinglot.model.TicketStatus;
import com.lld.parkinglot.model.Vehicle;
import com.lld.parkinglot.model.VehicleType;
import com.lld.parkinglot.payment.CashPaymentProcessor;
import com.lld.parkinglot.payment.PaymentProcessor;
import com.lld.parkinglot.pricing.DailyCappedPricingStrategy;
import com.lld.parkinglot.pricing.HourlyPricingStrategy;
import com.lld.parkinglot.pricing.PricingStrategy;
import com.lld.parkinglot.util.Money;
import com.lld.parkinglot.util.MutableClock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free tests (plain main + assertions) so the project runs with just a JDK.
 * Run: ./run.sh test
 */
public class ParkingLotTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        run("fit rules: bike->SMALL first, car never in SMALL, truck only LARGE", ParkingLotTests::fitRules);
        run("best fit vs nearest floor: different spot for the same bike", ParkingLotTests::strategies);
        run("lot full -> ParkingFullException, and the plate can try again later", ParkingLotTests::lotFull);
        run("same plate twice (normalised) is rejected while inside", ParkingLotTests::duplicatePlate);
        run("pricing: min 1 hour, any started hour billed, daily cap", ParkingLotTests::pricing);
        run("checkout: fee, receipt, spot and plate freed, board updated", ParkingLotTests::checkout);
        run("paid ticket cannot be used again; unknown ticket rejected", ParkingLotTests::ticketReuse);
        run("payment failure: barrier stays closed, ticket ACTIVE, retry with cash works", ParkingLotTests::paymentFailure);
        run("unsupported payment mode is rejected and the ticket stays ACTIVE", ParkingLotTests::unsupportedMode);
        run("a failing listener never breaks parking (Observer isolation)", ParkingLotTests::listenerIsolation);
        run("200 vehicles, 8 gates, 50 spots: exactly 50 tickets, no spot shared", ParkingLotTests::concurrentEntry);
        run("same plate at 10 gates at once: exactly one ticket", ParkingLotTests::concurrentSamePlate);
        run("same ticket at 2 exit gates at once: charged exactly once", ParkingLotTests::concurrentDoubleExit);
        run("churn: 16 threads park/exit 2,000 times; never two vehicles in a spot", ParkingLotTests::churn);

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- helpers

    private static MutableClock clock() {
        return new MutableClock(Instant.parse("2026-10-11T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
    }

    private static ParkingLotApplication app(ParkingLot lot, MutableClock clock) {
        return ParkingLotApplication.withDefaults(lot, clock);
    }

    private static Vehicle car(String plate) { return new Vehicle(plate, VehicleType.CAR); }
    private static Vehicle bike(String plate) { return new Vehicle(plate, VehicleType.BIKE); }
    private static Vehicle truck(String plate) { return new Vehicle(plate, VehicleType.TRUCK); }

    // ---------------------------------------------------------------- tests

    static void fitRules() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 1, 1, 1).build();
        EntryGate in = app(lot, clock()).entryGate("G");
        check(in.enter(bike("B1")).spot().type() == SpotType.SMALL, "bike takes SMALL first");
        check(in.enter(bike("B2")).spot().type() == SpotType.MEDIUM, "2nd bike overflows to MEDIUM");
        check(in.enter(car("C1")).spot().type() == SpotType.LARGE, "car overflows to LARGE");
        expect(ParkingFullException.class, () -> in.enter(truck("T1")));

        ParkingSpot small = new ParkingSpot(0, 1, SpotType.SMALL);
        expect(IllegalArgumentException.class, () -> small.tryOccupy(car("X")));
    }

    static void strategies() {
        // Floor 0 has only a MEDIUM spot, floor 1 has a SMALL spot.
        ParkingLot lot1 = ParkingLot.builder("L").addFloor(0, 0, 1, 0).addFloor(1, 1, 0, 0).build();
        ParkingLot lot2 = ParkingLot.builder("L").addFloor(0, 0, 1, 0).addFloor(1, 1, 0, 0).build();
        SpotAllocationStrategy best = new BestFitStrategy();
        SpotAllocationStrategy nearest = new NearestFloorStrategy();
        ParkingSpot a = best.allocate(lot1.floors(), bike("B")).orElseThrow();
        ParkingSpot b = nearest.allocate(lot2.floors(), bike("B")).orElseThrow();
        check(a.floorNumber() == 1 && a.type() == SpotType.SMALL, "best fit keeps the car spot free: " + a);
        check(b.floorNumber() == 0 && b.type() == SpotType.MEDIUM, "nearest floor uses the ground-floor car spot: " + b);
    }

    static void lotFull() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 1, 0).build();
        ParkingLotApplication app = app(lot, clock());
        EntryGate in = app.entryGate("G");
        ExitGate out = app.exitGate("X");
        Ticket t = in.enter(car("C1"));
        expect(ParkingFullException.class, () -> in.enter(car("C2")));
        out.exit(t.id(), PaymentMode.CASH);
        check(in.enter(car("C2")).spot().id().equals(t.spot().id()), "C2 gets the freed spot (plate was released after full)");
    }

    static void duplicatePlate() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 5, 0).build();
        ParkingLotApplication app = app(lot, clock());
        EntryGate in = app.entryGate("G");
        Ticket t = in.enter(car("KA 01 AB 1234"));
        expect(VehicleAlreadyParkedException.class, () -> in.enter(car("ka01ab-1234")));
        check(lot.freeCount(SpotType.MEDIUM) == 4, "rejected entry took no spot");
        app.exitGate("X").exit(t.id(), PaymentMode.CASH);
        in.enter(car("KA01AB1234"));       // allowed again after leaving
    }

    static void pricing() {
        Map<VehicleType, BigDecimal> rates = ParkingLotApplication.defaultHourlyRates();
        PricingStrategy hourly = new HourlyPricingStrategy(rates);
        checkMoney(hourly.calculateFee(VehicleType.CAR, Duration.ZERO), "40.00");
        checkMoney(hourly.calculateFee(VehicleType.CAR, Duration.ofMinutes(60)), "40.00");
        checkMoney(hourly.calculateFee(VehicleType.CAR, Duration.ofMinutes(61)), "80.00");
        checkMoney(hourly.calculateFee(VehicleType.CAR, Duration.ofSeconds(3601)), "80.00");
        checkMoney(hourly.calculateFee(VehicleType.BIKE, Duration.ofMinutes(150)), "60.00");
        checkMoney(hourly.calculateFee(VehicleType.TRUCK, Duration.ofHours(2)), "200.00");

        Map<VehicleType, BigDecimal> caps = Map.of(VehicleType.BIKE, Money.of(100), VehicleType.CAR, Money.of(300),
                VehicleType.TRUCK, Money.of(800));
        PricingStrategy capped = new DailyCappedPricingStrategy(rates, caps);
        checkMoney(capped.calculateFee(VehicleType.CAR, Duration.ofHours(3)), "120.00");
        checkMoney(capped.calculateFee(VehicleType.CAR, Duration.ofHours(10)), "300.00");
        checkMoney(capped.calculateFee(VehicleType.CAR, Duration.ofHours(26)), "380.00");
        checkMoney(capped.calculateFee(VehicleType.CAR, Duration.ofHours(48)), "600.00");
        expect(IllegalArgumentException.class, () -> new HourlyPricingStrategy(Map.of(VehicleType.CAR, Money.of(40))));
    }

    static void checkout() {
        MutableClock clock = clock();
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 2, 0).build();
        ParkingLotApplication app = app(lot, clock);
        Ticket t = app.entryGate("G").enter(car("C1"));
        check(app.displayBoard().current().contains("MEDIUM  1/2"), "board shows 1/2 after entry:\n" + app.displayBoard().current());

        clock.advance(Duration.ofMinutes(130));
        ExitGate out = app.exitGate("X");
        checkMoney(out.showFee(t.id()), "120.00");
        Receipt r = out.exit(t.id(), PaymentMode.CARD);
        checkMoney(r.amount(), "120.00");
        check(r.parkedFor().equals(Duration.ofMinutes(130)), "duration on receipt");
        check(t.status() == TicketStatus.PAID && t.payment().isPresent() && t.exitTime().isPresent(), "ticket PAID");
        check(t.spot().isFree() && lot.freeCount(SpotType.MEDIUM) == 2, "spot back in the pool");
        check(app.displayBoard().current().contains("MEDIUM  2/2"), "board refreshed by the exit event");
    }

    static void ticketReuse() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 2, 0).build();
        ParkingLotApplication app = app(lot, clock());
        Ticket t = app.entryGate("G").enter(car("C1"));
        ExitGate out = app.exitGate("X");
        out.exit(t.id(), PaymentMode.CASH);
        expect(InvalidTicketStateException.class, () -> out.exit(t.id(), PaymentMode.CASH));
        expect(InvalidTicketStateException.class, () -> out.showFee(t.id()));
        expect(TicketNotFoundException.class, () -> out.exit("T-999", PaymentMode.CASH));
    }

    static void paymentFailure() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 1, 0).build();
        PaymentProcessor decliningCard = new PaymentProcessor() {
            public PaymentMode mode() { return PaymentMode.CARD; }
            public boolean charge(String ref, BigDecimal amount) { return false; }
        };
        PaymentProcessor brokenUpi = new PaymentProcessor() {
            public PaymentMode mode() { return PaymentMode.UPI; }
            public boolean charge(String ref, BigDecimal amount) { throw new RuntimeException("gateway timeout"); }
        };
        ParkingLotApplication app = new ParkingLotApplication(lot, new BestFitStrategy(),
                new HourlyPricingStrategy(ParkingLotApplication.defaultHourlyRates()),
                List.of(decliningCard, brokenUpi, new CashPaymentProcessor()), clock());
        Ticket t = app.entryGate("G").enter(car("C1"));
        ExitGate out = app.exitGate("X");
        expect(PaymentFailedException.class, () -> out.exit(t.id(), PaymentMode.CARD));
        expect(PaymentFailedException.class, () -> out.exit(t.id(), PaymentMode.UPI));
        check(t.status() == TicketStatus.ACTIVE, "ticket back to ACTIVE");
        check(!t.spot().isFree(), "car still occupies its spot");
        out.exit(t.id(), PaymentMode.CASH);
        check(t.status() == TicketStatus.PAID && t.spot().isFree(), "cash worked");
    }

    static void unsupportedMode() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 1, 0).build();
        ParkingLotApplication app = new ParkingLotApplication(lot, new BestFitStrategy(),
                new HourlyPricingStrategy(ParkingLotApplication.defaultHourlyRates()),
                List.of(new CashPaymentProcessor()), clock());
        Ticket t = app.entryGate("G").enter(car("C1"));
        expect(UnsupportedPaymentModeException.class, () -> app.exitGate("X").exit(t.id(), PaymentMode.UPI));
        check(t.status() == TicketStatus.ACTIVE, "ticket stays ACTIVE");
    }

    static void listenerIsolation() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 2, 0).build();
        ParkingLotApplication app = app(lot, clock());
        List<ParkingEventType> seen = new ArrayList<>();
        app.events().subscribe(e -> { throw new RuntimeException("SMS service down"); });
        app.events().subscribe(e -> seen.add(e.type()));
        java.io.PrintStream err = System.err;
        System.setErr(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        try {
            Ticket t = app.entryGate("G").enter(car("C1"));
            app.exitGate("X").exit(t.id(), PaymentMode.CASH);
        } finally {
            System.setErr(err);
        }
        check(seen.equals(List.of(ParkingEventType.VEHICLE_PARKED, ParkingEventType.VEHICLE_EXITED)), "events: " + seen);
    }

    static void concurrentEntry() throws Exception {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 25, 0).addFloor(1, 0, 20, 5).build();
        ParkingLotApplication app = app(lot, clock());
        ConcurrentLinkedQueue<Ticket> tickets = new ConcurrentLinkedQueue<>();
        AtomicInteger full = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            EntryGate gate = app.entryGate("G" + (i % 8));
            Vehicle v = car("CAR" + i);
            jobs.add(() -> {
                try { tickets.add(gate.enter(v)); } catch (ParkingFullException e) { full.incrementAndGet(); }
                return null;
            });
        }
        runTogether(8, jobs);
        Set<String> spots = ConcurrentHashMap.newKeySet();
        for (Ticket t : tickets) {
            check(spots.add(t.spot().id()), "spot handed out twice: " + t.spot());
            check(t.spot().parkedVehicle().orElseThrow() == t.vehicle(), "spot holds the ticket's vehicle");
        }
        check(tickets.size() == 50, "tickets = " + tickets.size());
        check(full.get() == 150, "turned away = " + full.get());
        check(lot.freeCount(SpotType.MEDIUM) == 0 && lot.freeCount(SpotType.LARGE) == 0, "no free spots left");
    }

    static void concurrentSamePlate() throws Exception {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 20, 0).build();
        ParkingLotApplication app = app(lot, clock());
        AtomicInteger ok = new AtomicInteger(), rejected = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EntryGate gate = app.entryGate("G" + i);
            jobs.add(() -> {
                try { gate.enter(car("KA01AB1234")); ok.incrementAndGet(); }
                catch (VehicleAlreadyParkedException e) { rejected.incrementAndGet(); }
                return null;
            });
        }
        runTogether(10, jobs);
        check(ok.get() == 1 && rejected.get() == 9, "ok=" + ok + " rejected=" + rejected);
        check(lot.freeCount(SpotType.MEDIUM) == 19, "only one spot taken");
    }

    static void concurrentDoubleExit() throws Exception {
        for (int round = 0; round < 50; round++) {
            ParkingLot lot = ParkingLot.builder("L").addFloor(0, 0, 1, 0).build();
            AtomicInteger charges = new AtomicInteger();
            PaymentProcessor slowCash = new PaymentProcessor() {
                public PaymentMode mode() { return PaymentMode.CASH; }
                public boolean charge(String ref, BigDecimal amount) {
                    charges.incrementAndGet();
                    try { Thread.sleep(2); } catch (InterruptedException ignored) { }
                    return true;
                }
            };
            ParkingLotApplication app = new ParkingLotApplication(lot, new BestFitStrategy(),
                    new HourlyPricingStrategy(ParkingLotApplication.defaultHourlyRates()), List.of(slowCash), clock());
            Ticket t = app.entryGate("G").enter(car("C1"));
            AtomicInteger ok = new AtomicInteger(), rejected = new AtomicInteger();
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int g = 0; g < 2; g++) {
                ExitGate gate = app.exitGate("X" + g);
                jobs.add(() -> {
                    try { gate.exit(t.id(), PaymentMode.CASH); ok.incrementAndGet(); }
                    catch (InvalidTicketStateException e) { rejected.incrementAndGet(); }
                    return null;
                });
            }
            runTogether(2, jobs);
            check(ok.get() == 1 && rejected.get() == 1, "round " + round + ": ok=" + ok + " rejected=" + rejected);
            check(charges.get() == 1, "round " + round + ": charged " + charges + " times");
            check(lot.freeCount(SpotType.MEDIUM) == 1, "spot released exactly once");
        }
    }

    static void churn() throws Exception {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 5, 5, 2).addFloor(1, 5, 5, 2).build();
        ParkingLotApplication app = app(lot, clock());
        AtomicInteger parks = new AtomicInteger(), fulls = new AtomicInteger();
        VehicleType[] types = VehicleType.values();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int w = 0; w < 16; w++) {
            final int worker = w;
            EntryGate in = app.entryGate("IN" + w);
            ExitGate out = app.exitGate("OUT" + w);
            jobs.add(() -> {
                for (int i = 0; i < 125; i++) {
                    Vehicle v = new Vehicle("W" + worker + "V" + i, types[(worker + i) % types.length]);
                    try {
                        Ticket t = in.enter(v);
                        parks.incrementAndGet();
                        if (t.spot().parkedVehicle().orElse(null) != v) throw new AssertionError("spot stolen: " + t.spot());
                        Thread.yield();
                        out.exit(t.id(), PaymentMode.CASH);
                    } catch (ParkingFullException e) {
                        fulls.incrementAndGet();
                    }
                }
                return null;
            });
        }
        runTogether(16, jobs);
        check(parks.get() + fulls.get() == 2000, "all attempts accounted for");
        for (SpotType type : SpotType.values()) {
            check(lot.freeCount(type) == lot.totalCount(type), type + ": all spots free at the end");
        }
        lot.floors().forEach(f -> f.spots().forEach(s -> check(s.isFree(), s + " left occupied")));
    }

    // ---------------------------------------------------------------- tiny test framework

    /** Releases all jobs at the same moment; a deadlock/hang shows up as a failure instead of hanging the run. */
    private static void runTogether(int threads, List<Callable<Void>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> job : jobs) futures.add(pool.submit(() -> { start.await(); return job.call(); }));
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(20, TimeUnit.SECONDS)) throw new AssertionError("timed out (deadlock?)");
        for (var f : futures) f.get();       // rethrows any assertion failure from a worker
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static void run(String name, ThrowingRunnable test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable e) {
            failed++;
            Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
            System.out.println("FAIL  " + name + "\n      " + cause);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkMoney(BigDecimal actual, String expected) {
        check(actual.compareTo(new BigDecimal(expected)) == 0 && actual.scale() == 2, "expected " + expected + " got " + actual);
    }

    private static void expect(Class<? extends Throwable> type, ThrowingRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + e);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
