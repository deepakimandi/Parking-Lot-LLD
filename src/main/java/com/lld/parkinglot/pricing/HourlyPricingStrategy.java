package com.lld.parkinglot.pricing;

import com.lld.parkinglot.model.VehicleType;
import com.lld.parkinglot.util.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** fee = hourly rate for the vehicle type x billable hours. Rates are data (a rate card), not a switch statement. */
public class HourlyPricingStrategy implements PricingStrategy {
    private final Map<VehicleType, BigDecimal> hourlyRates;

    public HourlyPricingStrategy(Map<VehicleType, BigDecimal> hourlyRates) {
        this.hourlyRates = new EnumMap<>(hourlyRates);
        for (VehicleType t : VehicleType.values()) {
            if (!this.hourlyRates.containsKey(t)) throw new IllegalArgumentException("No hourly rate for " + t);
        }
    }

    @Override
    public BigDecimal calculateFee(VehicleType vehicleType, Duration parkedFor) {
        return Money.times(hourlyRates.get(vehicleType), PricingStrategy.billableHours(parkedFor));
    }
}
