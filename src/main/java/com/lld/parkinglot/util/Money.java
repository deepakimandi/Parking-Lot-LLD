package com.lld.parkinglot.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers: BigDecimal, scale 2, never double. */
public final class Money {
    private Money() {}

    public static BigDecimal of(String v) { return round(new BigDecimal(v)); }
    public static BigDecimal of(long v) { return round(BigDecimal.valueOf(v)); }
    public static BigDecimal round(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal times(BigDecimal amount, long factor) { return round(amount.multiply(BigDecimal.valueOf(factor))); }
}
