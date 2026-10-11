package com.lld.parkinglot.pricing;

import com.lld.parkinglot.model.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;

/** Strategy: how much does a stay cost? Swappable per lot (mall vs airport) without touching ParkingService. */
public interface PricingStrategy {
    BigDecimal calculateFee(VehicleType vehicleType, Duration parkedFor);

    /** Any started hour is billed; minimum one hour. 0-60 min = 1h, 61 min = 2h. */
    static long billableHours(Duration parkedFor) {
        if (parkedFor.isNegative()) throw new IllegalArgumentException("Negative duration");
        long seconds = parkedFor.toSeconds() + (parkedFor.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, (seconds + 3599) / 3600);
    }
}
