package com.lld.parkinglot.allocation;

import com.lld.parkinglot.model.ParkingFloor;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.Vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Strategy: which free spot does a vehicle get? Implementations must return a spot that is already
 * OCCUPIED by this vehicle (find + take in one step), so callers can't race between "found" and "taken".
 */
public interface SpotAllocationStrategy {
    Optional<ParkingSpot> allocate(List<ParkingFloor> floors, Vehicle vehicle);
}
