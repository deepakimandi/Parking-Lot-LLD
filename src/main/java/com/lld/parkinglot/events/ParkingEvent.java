package com.lld.parkinglot.events;

import com.lld.parkinglot.model.Ticket;

import java.time.Instant;

public record ParkingEvent(ParkingEventType type, Ticket ticket, Instant at) {}
