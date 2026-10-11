package com.lld.parkinglot.gate;

import com.lld.parkinglot.model.Ticket;
import com.lld.parkinglot.model.Vehicle;
import com.lld.parkinglot.service.ParkingService;

/** A physical entry gate. Many gates share one ParkingService; each gate is effectively its own thread. */
public class EntryGate {
    private final String id;
    private final ParkingService service;

    public EntryGate(String id, ParkingService service) {
        this.id = id;
        this.service = service;
    }

    /** Issues a ticket and opens the barrier, or throws (lot full / already inside) and keeps it closed. */
    public Ticket enter(Vehicle vehicle) { return service.park(vehicle, id); }

    public String id() { return id; }
}
