package parkinglot.model;

import java.time.LocalDateTime;

public class Ticket {

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;

    private LocalDateTime exitTime;
    private double amount;

    public Ticket(String ticketId, Vehicle vehicle, ParkingSpot spot, LocalDateTime entryTime) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = entryTime;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isClosed() {
        return exitTime != null;
    }

    public void close(LocalDateTime exitTime, double amount) {
        this.exitTime = exitTime;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Ticket{" + ticketId + ", " + vehicle + ", spot=" + spot
                + ", floor=" + spot.getFloorNumber() + ", in=" + entryTime + "}";
    }
}
