package com.lld.parkinglot.model;

import java.util.List;

/**
 * Each vehicle type knows which spot sizes it fits in, smallest (best fit) first.
 * Adding a new type (e.g. EV, BUS) is one line here plus a rate in the pricing config: no new subclasses.
 */
public enum VehicleType {
    BIKE(List.of(SpotType.SMALL, SpotType.MEDIUM, SpotType.LARGE)),
    CAR(List.of(SpotType.MEDIUM, SpotType.LARGE)),
    TRUCK(List.of(SpotType.LARGE));

    private final List<SpotType> fittingSpots;

    VehicleType(List<SpotType> fittingSpots) { this.fittingSpots = fittingSpots; }

    /** Spot sizes this vehicle can use, smallest first. */
    public List<SpotType> fittingSpots() { return fittingSpots; }

    public boolean fitsIn(SpotType spotType) { return fittingSpots.contains(spotType); }
}
