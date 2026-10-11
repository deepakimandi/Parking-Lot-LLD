package com.lld.parkinglot.payment;

import com.lld.parkinglot.exception.UnsupportedPaymentModeException;
import com.lld.parkinglot.model.Payment;
import com.lld.parkinglot.model.PaymentMode;
import com.lld.parkinglot.model.PaymentStatus;
import com.lld.parkinglot.util.IdGenerator;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Picks the processor for the requested mode (a registry instead of if/else or a switch) and records the attempt.
 * Adding a new mode (e.g. FASTag wallet) = one new PaymentProcessor class registered at startup.
 */
public class PaymentService {
    private final Map<PaymentMode, PaymentProcessor> processors = new EnumMap<>(PaymentMode.class);
    private final IdGenerator ids = new IdGenerator("PAY");
    private final Clock clock;

    public PaymentService(Collection<PaymentProcessor> processors, Clock clock) {
        for (PaymentProcessor p : processors) this.processors.put(p.mode(), p);
        this.clock = clock;
    }

    public Payment pay(String ticketId, BigDecimal amount, PaymentMode mode) {
        PaymentProcessor processor = processors.get(mode);
        if (processor == null) throw new UnsupportedPaymentModeException(mode);
        boolean ok;
        try {
            ok = processor.charge(ticketId, amount);
        } catch (RuntimeException e) {
            ok = false;                       // a gateway error is just a failed payment for the driver
        }
        return new Payment(ids.next(), ticketId, amount, mode, ok ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, clock.instant());
    }
}
