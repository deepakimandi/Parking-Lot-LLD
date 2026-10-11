package com.lld.parkinglot.exception;

/** No spot that fits this vehicle. The driver is turned away at the gate. */
public class ParkingFullException extends ParkingException {
    public ParkingFullException(com.lld.parkinglot.model.VehicleType type) { super("No free spot for a " + type); }
}
