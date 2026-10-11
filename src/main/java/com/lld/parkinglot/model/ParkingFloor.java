package com.lld.parkinglot.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A floor keeps a pool of FREE spots per spot type. Allocation is pollFirst() on a concurrent sorted set:
 * atomic (two threads never get the same spot), lock-free, O(log n), and always the nearest free spot.
 * No more "scan all spots, then mark one occupied" (a check-then-act race).
 */
public final class ParkingFloor {
    private final int floorNumber;
    private final List<ParkingSpot> spots;
    private final Map<SpotType, ConcurrentSkipListSet<ParkingSpot>> freeSpots = new EnumMap<>(SpotType.class);

    public ParkingFloor(int floorNumber, List<ParkingSpot> spots) {
        this.floorNumber = floorNumber;
        this.spots = List.copyOf(spots);
        for (SpotType type : SpotType.values()) freeSpots.put(type, new ConcurrentSkipListSet<>());
        for (ParkingSpot spot : this.spots) {
            if (spot.floorNumber() != floorNumber) throw new IllegalArgumentException(spot + " is not on floor " + floorNumber);
            if (spot.isFree()) freeSpots.get(spot.type()).add(spot);
        }
    }

    /** Take the nearest free spot of this type, or empty if none. Safe to call from many threads. */
    public Optional<ParkingSpot> tryAllocate(SpotType type, Vehicle vehicle) {
        ConcurrentSkipListSet<ParkingSpot> pool = freeSpots.get(type);
        ParkingSpot spot;
        while ((spot = pool.pollFirst()) != null) {      // atomic: removes and returns, only one thread gets it
            if (spot.tryOccupy(vehicle)) return Optional.of(spot);
            // A spot in the free pool is always free, so we never get here. Defensive: skip it and try the next.
        }
        return Optional.empty();
    }

    /** Free the spot (CAS) and only then put it back in the pool, so a thread that polls it can always occupy it. */
    public void release(ParkingSpot spot, Vehicle vehicle) {
        if (!spot.release(vehicle)) {
            throw new IllegalStateException(vehicle.licensePlate() + " is not parked at " + spot);
        }
        freeSpots.get(spot.type()).add(spot);
    }

    /** Snapshot count (may be stale by the time you read it; fine for a display board). */
    public int freeCount(SpotType type) { return freeSpots.get(type).size(); }

    public int totalCount(SpotType type) {
        return (int) spots.stream().filter(s -> s.type() == type).count();
    }

    public int floorNumber() { return floorNumber; }
    public List<ParkingSpot> spots() { return Collections.unmodifiableList(spots); }
}
