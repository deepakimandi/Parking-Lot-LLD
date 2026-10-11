package com.lld.parkinglot.exception;


public class PaymentFailedException extends ParkingException {
    public PaymentFailedException(String ticketId, com.lld.parkinglot.model.PaymentMode mode) { super(mode + " payment failed for ticket " + ticketId + "; the barrier stays closed"); }
}
