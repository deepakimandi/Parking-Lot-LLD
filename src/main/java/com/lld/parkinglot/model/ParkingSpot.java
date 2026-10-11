package com.lld.parkinglot.model;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One physical spot. Its only mutable state is "who is parked here", held in an AtomicReference so that
 * occupy/release are compare-and-set operations: two threads can never both occupy the same spot.
 */
public final class ParkingSpot implements Comparable<ParkingSpot> {
    private final String id;
    private final int floorNumber;
    private final int number;
    private final SpotType type;
    private final AtomicReference<Vehicle> parkedVehicle = new AtomicReference<>();

    public ParkingSpot(int floorNumber, int number, SpotType type) {
        this.floorNumber = floorNumber;
        this.number = number;
        this.type = type;
        this.id = "F" + floorNumber + "-" + type.name().charAt(0) + String.format("%02d", number);
    }

    /** CAS null -> vehicle. Returns false if someone else is already here. */
    public boolean tryOccupy(Vehicle vehicle) {
        if (!vehicle.type().fitsIn(type)) {
            throw new IllegalArgumentException(vehicle.type() + " does not fit in a " + type + " spot");
        }
        return parkedVehicle.compareAndSet(null, vehicle);
    }

    /** CAS vehicle -> null. Only the vehicle that is parked here can free the spot. */
    public boolean release(Vehicle vehicle) {
        return parkedVehicle.compareAndSet(vehicle, null);
    }

    public boolean isFree() { return parkedVehicle.get() == null; }
    public Optional<Vehicle> parkedVehicle() { return Optional.ofNullable(parkedVehicle.get()); }

    public String id() { return id; }
    public int floorNumber() { return floorNumber; }
    public int number() { return number; }
    public SpotType type() { return type; }

    /** Lower number = closer to the ramp/lift. Used to hand out the nearest free spot first. */
    @Override
    public int compareTo(ParkingSpot other) {
        int c = Integer.compare(number, other.number);
        return c != 0 ? c : id.compareTo(other.id);
    }

    @Override public String toString() { return id; }
}
