package com.lld.parkinglot.repository;

import com.lld.parkinglot.model.Ticket;

import java.util.Optional;

/** Repository: the service doesn't care whether tickets live in a map, MySQL or Redis. */
public interface TicketRepository {
    void save(Ticket ticket);
    Optional<Ticket> findById(String ticketId);
}
