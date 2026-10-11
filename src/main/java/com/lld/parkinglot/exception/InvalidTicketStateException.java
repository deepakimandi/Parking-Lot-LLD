package com.lld.parkinglot.exception;

/** E.g. ticket already paid, or being checked out at another gate. */
public class InvalidTicketStateException extends ParkingException {
    public InvalidTicketStateException(String ticketId, com.lld.parkinglot.model.TicketStatus status) { super("Ticket " + ticketId + " is " + status); }
}
