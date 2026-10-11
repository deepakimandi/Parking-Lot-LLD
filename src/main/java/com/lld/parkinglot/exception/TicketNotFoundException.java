package com.lld.parkinglot.exception;


public class TicketNotFoundException extends ParkingException {
    public TicketNotFoundException(String ticketId) { super("Unknown ticket " + ticketId); }
}
