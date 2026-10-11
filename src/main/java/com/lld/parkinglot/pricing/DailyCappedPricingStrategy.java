package com.lld.parkinglot.pricing;

import com.lld.parkinglot.model.VehicleType;
import com.lld.parkinglot.util.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hourly, but each 24-hour block costs at most the daily cap. Example (car, Rs 40/h, cap Rs 300):
 * 3h = 120; 10h = 300 (capped); 26h = 300 + 80 = 380.
 */
public class DailyCappedPricingStrategy implements PricingStrategy {
    private final Map<VehicleType, BigDecimal> hourlyRates;
    private final Map<VehicleType, BigDecimal> dailyCaps;

    public DailyCappedPricingStrategy(Map<VehicleType, BigDecimal> hourlyRates, Map<VehicleType, BigDecimal> dailyCaps) {
        this.hourlyRates = new EnumMap<>(hourlyRates);
        this.dailyCaps = new EnumMap<>(dailyCaps);
        for (VehicleType t : VehicleType.values()) {
            if (!this.hourlyRates.containsKey(t) || !this.dailyCaps.containsKey(t)) {
                throw new IllegalArgumentException("Missing rate or cap for " + t);
            }
        }
    }

    @Override
    public BigDecimal calculateFee(VehicleType vehicleType, Duration parkedFor) {
        long hours = PricingStrategy.billableHours(parkedFor);
        BigDecimal cap = dailyCaps.get(vehicleType);
        BigDecimal fullDays = Money.times(cap, hours / 24);
        BigDecimal rest = Money.times(hourlyRates.get(vehicleType), hours % 24).min(cap);
        return Money.round(fullDays.add(rest));
    }
}
