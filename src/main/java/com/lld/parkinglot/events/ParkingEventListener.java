package com.lld.parkinglot.events;

/** Observer. Display boards, SMS, analytics, audit log... each is one listener. */
@FunctionalInterface
public interface ParkingEventListener {
    void onEvent(ParkingEvent event);
}
