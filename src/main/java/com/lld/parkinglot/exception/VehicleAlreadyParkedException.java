package com.lld.parkinglot.exception;

/** The same plate cannot hold two active tickets. */
public class VehicleAlreadyParkedException extends ParkingException {
    public VehicleAlreadyParkedException(String plate) { super(plate + " is already inside the lot"); }
}
