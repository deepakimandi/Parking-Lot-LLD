package com.lld.parkinglot;

import com.lld.parkinglot.exception.ParkingException;
import com.lld.parkinglot.gate.EntryGate;
import com.lld.parkinglot.gate.ExitGate;
import com.lld.parkinglot.model.ParkingLot;
import com.lld.parkinglot.model.PaymentMode;
import com.lld.parkinglot.model.Receipt;
import com.lld.parkinglot.model.Ticket;
import com.lld.parkinglot.model.Vehicle;
import com.lld.parkinglot.model.VehicleType;
import com.lld.parkinglot.util.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** End-to-end demo. Run with ./run.sh */
public class Main {
    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-11T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
        ParkingLot lot = ParkingLot.builder("City Mall")
                .addFloor(0, 2, 3, 1)      // ground floor: 2 small, 3 medium, 1 large
                .addFloor(1, 2, 3, 0)
                .build();
        ParkingLotApplication app = ParkingLotApplication.withDefaults(lot, clock);
        EntryGate north = app.entryGate("ENTRY-N");
        ExitGate south = app.exitGate("EXIT-S");

        section("1. Board at opening");
        System.out.print(app.displayBoard().current());

        section("2. Vehicles enter (best fit: smallest spot that fits, nearest first)");
        Ticket car = north.enter(new Vehicle("KA 01 AB 1234", VehicleType.CAR));
        Ticket bike = north.enter(new Vehicle("KA02CD5678", VehicleType.BIKE));
        Ticket truck = north.enter(new Vehicle("MH12TR0001", VehicleType.TRUCK));
        List.of(car, bike, truck).forEach(t -> System.out.println("  " + t));
        System.out.print(app.displayBoard().current());

        section("3. Rule checks");
        attempt("same car again (plate normalised)", () -> north.enter(new Vehicle("ka01ab1234", VehicleType.CAR)));
        attempt("second truck (only LARGE spot is taken)", () -> north.enter(new Vehicle("MH12TR0002", VehicleType.TRUCK)));

        section("4. 2h 10m later the car leaves: billed 3 hours x Rs 40");
        clock.advance(Duration.ofMinutes(130));
        System.out.println("  fee shown at gate: Rs " + south.showFee(car.id()));
        Receipt receipt = south.exit(car.id(), PaymentMode.UPI);
        System.out.println("  " + receipt);
        attempt("same ticket at the exit again", () -> south.exit(car.id(), PaymentMode.CASH));

        section("5. Bike leaves after 20 minutes more: billed 3 hours x Rs 20 (any started hour counts)");
        clock.advance(Duration.ofMinutes(20));
        System.out.println("  " + south.exit(bike.id(), PaymentMode.CASH));
        System.out.print(app.displayBoard().current());

        section("6. Rush hour: 40 cars race through 4 entry gates for the free car-sized spots");
        int freeForCars = lot.freeCount(com.lld.parkinglot.model.SpotType.MEDIUM) + lot.freeCount(com.lld.parkinglot.model.SpotType.LARGE);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger full = new AtomicInteger();
        ConcurrentLinkedQueue<Ticket> issued = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 40; i++) {
            EntryGate gate = app.entryGate("ENTRY-" + (i % 4));
            String plate = "DL" + (1000 + i);
            pool.submit(() -> {
                start.await();
                try { issued.add(gate.enter(new Vehicle(plate, VehicleType.CAR))); }
                catch (ParkingException e) { full.incrementAndGet(); }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        long distinctSpots = issued.stream().map(t -> t.spot().id()).distinct().count();
        System.out.printf("  free car-capable spots: %d | tickets issued: %d | distinct spots: %d | turned away: %d%n",
                freeForCars, issued.size(), distinctSpots, full.get());
        System.out.print(app.displayBoard().current());
    }

    private static void section(String title) { System.out.println("\n--- " + title + " ---"); }

    private static void attempt(String what, Runnable r) {
        try {
            r.run();
            System.out.println("  " + what + ": allowed (unexpected!)");
        } catch (ParkingException e) {
            System.out.println("  " + what + ": rejected -> " + e.getMessage());
        }
    }
}
