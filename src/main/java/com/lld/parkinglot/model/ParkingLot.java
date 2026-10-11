package com.lld.parkinglot.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The physical structure: floors and spots. Built once with a Builder, then the structure never changes
 * (only spot occupancy does). Deliberately NOT a Singleton: see the notes, section on Singleton.
 */
public final class ParkingLot {
    private final String name;
    private final List<ParkingFloor> floors;               // sorted: ground floor first
    private final Map<Integer, ParkingFloor> floorsByNumber = new HashMap<>();

    private ParkingLot(String name, List<ParkingFloor> floors) {
        this.name = name;
        List<ParkingFloor> sorted = new ArrayList<>(floors);
        sorted.sort(Comparator.comparingInt(ParkingFloor::floorNumber));
        this.floors = List.copyOf(sorted);
        for (ParkingFloor f : this.floors) floorsByNumber.put(f.floorNumber(), f);
    }

    public static Builder builder(String name) { return new Builder(name); }

    public String name() { return name; }
    public List<ParkingFloor> floors() { return floors; }

    public ParkingFloor floor(int floorNumber) {
        ParkingFloor f = floorsByNumber.get(floorNumber);
        if (f == null) throw new IllegalArgumentException("No floor " + floorNumber);
        return f;
    }

    public int freeCount(SpotType type) { return floors.stream().mapToInt(f -> f.freeCount(type)).sum(); }
    public int totalCount(SpotType type) { return floors.stream().mapToInt(f -> f.totalCount(type)).sum(); }

    /** Builder: readable setup of a multi-floor lot, with validation in one place. */
    public static final class Builder {
        private final String name;
        private final Map<Integer, ParkingFloor> floors = new HashMap<>();

        private Builder(String name) { this.name = name; }

        /** Adds a floor with the given number of small, medium and large spots, numbered 1..n nearest-first. */
        public Builder addFloor(int floorNumber, int small, int medium, int large) {
            if (floors.containsKey(floorNumber)) throw new IllegalArgumentException("Duplicate floor " + floorNumber);
            if (small < 0 || medium < 0 || large < 0) throw new IllegalArgumentException("Negative spot count");
            List<ParkingSpot> spots = new ArrayList<>();
            int n = 1;
            for (int i = 0; i < small; i++) spots.add(new ParkingSpot(floorNumber, n++, SpotType.SMALL));
            for (int i = 0; i < medium; i++) spots.add(new ParkingSpot(floorNumber, n++, SpotType.MEDIUM));
            for (int i = 0; i < large; i++) spots.add(new ParkingSpot(floorNumber, n++, SpotType.LARGE));
            floors.put(floorNumber, new ParkingFloor(floorNumber, spots));
            return this;
        }

        public ParkingLot build() {
            if (floors.isEmpty()) throw new IllegalStateException("A parking lot needs at least one floor");
            return new ParkingLot(name, new ArrayList<>(floors.values()));
        }
    }
}
