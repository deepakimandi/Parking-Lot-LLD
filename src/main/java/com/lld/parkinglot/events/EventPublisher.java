package com.lld.parkinglot.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subject in the Observer pattern. CopyOnWriteArrayList: many publishes from many gate threads, rare subscribes,
 * and iteration never throws ConcurrentModificationException. A failing listener never breaks parking.
 */
public class EventPublisher {
    private final List<ParkingEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(ParkingEventListener listener) { listeners.add(listener); }

    public void publish(ParkingEvent event) {
        for (ParkingEventListener l : listeners) {
            try {
                l.onEvent(event);
            } catch (RuntimeException e) {
                System.err.println("Listener failed on " + event.type() + ": " + e.getMessage());
            }
        }
    }
}
