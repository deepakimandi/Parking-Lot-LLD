package com.lld.parkinglot.model;

import java.util.Objects;

/**
 * Immutable value object. Car/Bike/Truck subclasses would add no behaviour, so a record + enum is enough.
 * The plate is normalised ("ka 01 ab 1234" == "KA01AB1234") so the same car can't sneak in twice.
 */
public record Vehicle(String licensePlate, VehicleType type) {
    public Vehicle {
        Objects.requireNonNull(licensePlate, "licensePlate");
        Objects.requireNonNull(type, "type");
        licensePlate = licensePlate.replaceAll("[\\s-]", "").toUpperCase();
        if (licensePlate.isEmpty()) throw new IllegalArgumentException("licensePlate is blank");
    }
}
