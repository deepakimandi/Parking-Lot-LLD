package com.lld.parkinglot.util;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe, readable ids like "T-1", "T-2". AtomicLong.incrementAndGet() never hands out the same number twice. */
public final class IdGenerator {
    private final String prefix;
    private final AtomicLong counter = new AtomicLong();

    public IdGenerator(String prefix) { this.prefix = prefix; }

    public String next() { return prefix + "-" + counter.incrementAndGet(); }
}
