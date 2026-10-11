package com.lld.parkinglot.exception;

/** Base for business errors. Unchecked: callers (gates) handle them at the edge. */
public class ParkingException extends RuntimeException {
    public ParkingException(String message) { super(message); }
}
