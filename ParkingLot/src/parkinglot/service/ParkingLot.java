package parkinglot.service;

import parkinglot.exception.InvalidTicketException;
import parkinglot.exception.ParkingLotFullException;
import parkinglot.model.ParkingFloor;
import parkinglot.model.ParkingSpot;
import parkinglot.model.SpotType;
import parkinglot.model.Ticket;
import parkinglot.model.Vehicle;
import parkinglot.pricing.PricingStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entry point of the system. Keeps it simple:
 *  - park(vehicle)  -> Ticket
 *  - unpark(ticket) -> Ticket with amount
 *
 * All mutating methods are synchronized so two entry gates cannot grab the same
 * spot. A real system would lock per floor / use a DB row lock, I mentioned that
 * as a follow-up instead of building it.
 */
public class ParkingLot {

    private final String name;
    private final List<ParkingFloor> floors = new ArrayList<>();
    private final Map<String, Ticket> activeTickets = new HashMap<>();
    private final Map<String, Ticket> closedTickets = new HashMap<>();
    private final PricingStrategy pricingStrategy;
    private final AtomicLong ticketCounter = new AtomicLong(1000);

    public ParkingLot(String name, PricingStrategy pricingStrategy) {
        this.name = name;
        this.pricingStrategy = pricingStrategy;
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public synchronized Ticket park(Vehicle vehicle) {
        return park(vehicle, LocalDateTime.now());
    }

    /** entryTime is a parameter so the demo/tests can control the clock. */
    public synchronized Ticket park(Vehicle vehicle, LocalDateTime entryTime) {
        if (isAlreadyParked(vehicle.getNumber())) {
            throw new IllegalStateException("vehicle " + vehicle.getNumber() + " is already inside");
        }
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAndOccupy(vehicle);
            if (spot != null) {
                String ticketId = "T-" + ticketCounter.incrementAndGet();
                Ticket ticket = new Ticket(ticketId, vehicle, spot, entryTime);
                activeTickets.put(ticketId, ticket);
                return ticket;
            }
        }
        throw new ParkingLotFullException("no free spot for " + vehicle);
    }

    public synchronized Ticket unpark(String ticketId) {
        return unpark(ticketId, LocalDateTime.now());
    }

    public synchronized Ticket unpark(String ticketId, LocalDateTime exitTime) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            if (closedTickets.containsKey(ticketId)) {
                throw new InvalidTicketException("ticket " + ticketId + " is already closed");
            }
            throw new InvalidTicketException("unknown ticket " + ticketId);
        }
        double amount = pricingStrategy.calculate(ticket, exitTime);
        ticket.close(exitTime, amount);
        findFloor(ticket.getSpot().getFloorNumber()).release(ticket.getSpot());
        closedTickets.put(ticketId, ticket);
        return ticket;
    }

    public synchronized Ticket getTicket(String ticketId) {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            ticket = closedTickets.get(ticketId);
        }
        if (ticket == null) {
            throw new InvalidTicketException("unknown ticket " + ticketId);
        }
        return ticket;
    }

    /** Display board at the entry gate. */
    public synchronized String availability() {
        StringBuilder sb = new StringBuilder(name + " availability\n");
        for (ParkingFloor floor : floors) {
            sb.append("  floor ").append(floor.getFloorNumber()).append(" -> ");
            for (SpotType type : SpotType.values()) {
                sb.append(type).append(": ")
                        .append(floor.freeCount(type)).append("/").append(floor.totalCount(type))
                        .append("  ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private boolean isAlreadyParked(String vehicleNumber) {
        return activeTickets.values().stream()
                .anyMatch(t -> t.getVehicle().getNumber().equals(vehicleNumber));
    }

    private ParkingFloor findFloor(int floorNumber) {
        return floors.stream()
                .filter(f -> f.getFloorNumber() == floorNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("floor " + floorNumber + " not found"));
    }
}
