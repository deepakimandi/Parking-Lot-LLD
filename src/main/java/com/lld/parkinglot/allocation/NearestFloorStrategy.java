package com.lld.parkinglot.allocation;

import com.lld.parkinglot.model.ParkingFloor;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.SpotType;
import com.lld.parkinglot.model.Vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Lowest floor first; on that floor, the smallest fitting spot. Drivers walk less,
 * but bikes may take car spots on lower floors while bike spots upstairs sit empty.
 */
public class NearestFloorStrategy implements SpotAllocationStrategy {
    @Override
    public Optional<ParkingSpot> allocate(List<ParkingFloor> floors, Vehicle vehicle) {
        for (ParkingFloor floor : floors) {
            for (SpotType type : vehicle.type().fittingSpots()) {
                Optional<ParkingSpot> spot = floor.tryAllocate(type, vehicle);
                if (spot.isPresent()) return spot;
            }
        }
        return Optional.empty();
    }
}
