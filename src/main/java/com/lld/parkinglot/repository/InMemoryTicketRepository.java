package com.lld.parkinglot.repository;

import com.lld.parkinglot.model.Ticket;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTicketRepository implements TicketRepository {
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    @Override public void save(Ticket ticket) { tickets.put(ticket.id(), ticket); }
    @Override public Optional<Ticket> findById(String ticketId) { return Optional.ofNullable(tickets.get(ticketId)); }
}
