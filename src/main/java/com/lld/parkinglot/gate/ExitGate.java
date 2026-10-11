package com.lld.parkinglot.gate;

import com.lld.parkinglot.model.PaymentMode;
import com.lld.parkinglot.model.Receipt;
import com.lld.parkinglot.service.ParkingService;

import java.math.BigDecimal;

public class ExitGate {
    private final String id;
    private final ParkingService service;

    public ExitGate(String id, ParkingService service) {
        this.id = id;
        this.service = service;
    }

    public BigDecimal showFee(String ticketId) { return service.quote(ticketId); }

    /** Takes payment; the barrier opens only if this returns a receipt. */
    public Receipt exit(String ticketId, PaymentMode mode) { return service.checkout(ticketId, mode, id); }

    public String id() { return id; }
}
