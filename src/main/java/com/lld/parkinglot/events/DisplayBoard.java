package com.lld.parkinglot.events;

import com.lld.parkinglot.model.ParkingFloor;
import com.lld.parkinglot.model.ParkingLot;
import com.lld.parkinglot.model.SpotType;

/**
 * The board at the entrance: free spots per floor and type. It is an Observer, so it refreshes when a vehicle
 * enters or leaves instead of polling. It only reads lock-free counts, so it never slows the gates down.
 */
public class DisplayBoard implements ParkingEventListener {
    private final ParkingLot lot;
    private volatile String current;

    public DisplayBoard(ParkingLot lot) {
        this.lot = lot;
        this.current = render();
    }

    @Override
    public void onEvent(ParkingEvent event) { current = render(); }

    public String current() { return current; }

    public String render() {
        StringBuilder sb = new StringBuilder("=== " + lot.name() + " : free spots ===\n");
        for (ParkingFloor f : lot.floors()) {
            sb.append(String.format("Floor %d |", f.floorNumber()));
            for (SpotType t : SpotType.values()) {
                sb.append(String.format(" %-6s %2d/%-2d |", t, f.freeCount(t), f.totalCount(t)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
