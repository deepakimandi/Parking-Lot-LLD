package com.lld.parkinglot.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A Clock you can move forward. Every time-dependent class takes a java.time.Clock, so tests and the demo
 * can "park for 3 hours" instantly instead of sleeping.
 */
public class MutableClock extends Clock {
    private volatile Instant now;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    public void advance(Duration d) { now = now.plus(d); }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(now, z); }
    @Override public Instant instant() { return now; }
}
