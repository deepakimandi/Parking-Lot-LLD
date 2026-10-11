package com.lld.parkinglot.allocation;

import com.lld.parkinglot.model.ParkingFloor;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.SpotType;
import com.lld.parkinglot.model.Vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Smallest fitting spot anywhere in the lot first, then the next size up.
 * A bike goes to the 3rd floor's SMALL spot before taking a ground-floor LARGE spot, which keeps
 * big spots for big vehicles. (Default.)
 */
public class BestFitStrategy implements SpotAllocationStrategy {
    @Override
    public Optional<ParkingSpot> allocate(List<ParkingFloor> floors, Vehicle vehicle) {
        for (SpotType type : vehicle.type().fittingSpots()) {
            for (ParkingFloor floor : floors) {
                Optional<ParkingSpot> spot = floor.tryAllocate(type, vehicle);
                if (spot.isPresent()) return spot;
            }
        }
        return Optional.empty();
    }
}
