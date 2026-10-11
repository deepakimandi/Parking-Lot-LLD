package com.lld.parkinglot.exception;


public class UnsupportedPaymentModeException extends ParkingException {
    public UnsupportedPaymentModeException(com.lld.parkinglot.model.PaymentMode mode) { super("Payment mode not supported here: " + mode); }
}
