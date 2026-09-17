# Parking Lot System Design

## 1. Clarifying Questions

1. Multiple Vehicle Types & Spot Compatibility:
    - Does the parking lot support different vehicle types (e.g., Motorcycles, Cars, Trucks)?
    - Can a smaller vehicle park in a larger spot if normal spots are full, or is it strictly 1-to-1 matching?

2. Capacity & Levels:
    - Is it a single-level open ground parking lot or a multi-floor building?
    - Do we need to track entry/exit per floor or globally?

3. Entry and Exit Points:
    - Are there multiple entry and exit gates operating concurrently, or just a single gate?

4. Pricing & Payment Model:
    - Is the fee flat, hourly, or variable based on vehicle type and duration?
    - Is payment processed at exit, or via pre-paid ticketing/automated booth?

5. Ticket Allocation Strategy:
    - How should an available spot be picked (e.g., lowest floor first, closest to entrance, first available index)?

---

## 2. Requirements

### Functional Requirements
- The system must support assigning an available parking spot matching the vehicle type.
- The system must issue a ticket upon vehicle entry with spot details and entry timestamp.
- The system must calculate the fee upon exit based on total parked duration and vehicle type rate.
- The system must free up the spot when a vehicle leaves and mark it available for subsequent entries.
- The system should decline entry when all suitable spots are occupied.

### Non-Functional Requirements
- Simplicity: Clean, beginner-friendly object-oriented abstractions.
- Maintainability: Modularity between models, state definitions, and business logic.
- Extensibility: Easy addition of new vehicle types or spot tiers without rewriting core controller logic.

---

## 3. Enums

public enum VehicleType {
MOTORCYCLE,
CAR,
TRUCK
}

public enum SpotType {
SMALL,
MEDIUM,
LARGE
}

public enum TicketStatus {
ACTIVE,
PAID
}

---

## 4. Models

public class Vehicle {
private String licensePlate;
private VehicleType vehicleType;

    public Vehicle(String licensePlate, VehicleType vehicleType) {
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}

public class ParkingSpot {
private int spotId;
private SpotType spotType;
private boolean isAvailable;
private Vehicle parkedVehicle;

    public ParkingSpot(int spotId, SpotType spotType) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.isAvailable = true;
        this.parkedVehicle = null;
    }

    public int getSpotId() {
        return spotId;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public Vehicle getParkedVehicle() {
        return parkedVehicle;
    }

    public void assignVehicle(Vehicle vehicle) {
        this.parkedVehicle = vehicle;
        this.isAvailable = false;
    }

    public void vacate() {
        this.parkedVehicle = null;
        this.isAvailable = true;
    }
}

import java.time.LocalDateTime;

public class Ticket {
private String ticketId;
private ParkingSpot parkingSpot;
private Vehicle vehicle;
private LocalDateTime entryTime;
private LocalDateTime exitTime;
private double fee;
private TicketStatus status;

    public Ticket(String ticketId, ParkingSpot parkingSpot, Vehicle vehicle) {
        this.ticketId = ticketId;
        this.parkingSpot = parkingSpot;
        this.vehicle = vehicle;
        this.entryTime = LocalDateTime.now();
        this.status = TicketStatus.ACTIVE;
    }

    public String getTicketId() {
        return ticketId;
    }

    public ParkingSpot getParkingSpot() {
        return parkingSpot;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public double getFee() {
        return fee;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}

---

## 5. Controller

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ParkingLotController {
private List<ParkingSpot> spots;
private Map<String, Ticket> activeTickets;

    public ParkingLotController(int smallSpots, int mediumSpots, int largeSpots) {
        this.spots = new ArrayList<>();
        this.activeTickets = new HashMap<>();

        int idCounter = 1;
        for (int i = 0; i < smallSpots; i++) {
            spots.add(new ParkingSpot(idCounter++, SpotType.SMALL));
        }
        for (int i = 0; i < mediumSpots; i++) {
            spots.add(new ParkingSpot(idCounter++, SpotType.MEDIUM));
        }
        for (int i = 0; i < largeSpots; i++) {
            spots.add(new ParkingSpot(idCounter++, SpotType.LARGE));
        }
    }

    private SpotType getRequiredSpotType(VehicleType vehicleType) {
        switch (vehicleType) {
            case MOTORCYCLE:
                return SpotType.SMALL;
            case CAR:
                return SpotType.MEDIUM;
            case TRUCK:
                return SpotType.LARGE;
            default:
                return SpotType.MEDIUM;
        }
    }

    private double getHourlyRate(VehicleType vehicleType) {
        switch (vehicleType) {
            case MOTORCYCLE:
                return 10.0;
            case CAR:
                return 20.0;
            case TRUCK:
                return 40.0;
            default:
                return 20.0;
        }
    }

    public Ticket parkVehicle(Vehicle vehicle) {
        SpotType requiredType = getRequiredSpotType(vehicle.getVehicleType());

        ParkingSpot allocatedSpot = null;
        for (ParkingSpot spot : spots) {
            if (spot.isAvailable() && spot.getSpotType() == requiredType) {
                allocatedSpot = spot;
                break;
            }
        }

        if (allocatedSpot == null) {
            return null;
        }

        allocatedSpot.assignVehicle(vehicle);

        String ticketId = UUID.randomUUID().toString().substring(0, 8);
        Ticket ticket = new Ticket(ticketId, allocatedSpot, vehicle);
        activeTickets.put(ticketId, ticket);

        return ticket;
    }

    public Ticket unparkVehicle(String ticketId) {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null || ticket.getStatus() != TicketStatus.ACTIVE) {
            return null;
        }

        LocalDateTime exitTime = LocalDateTime.now();
        ticket.setExitTime(exitTime);

        long hours = Duration.between(ticket.getEntryTime(), exitTime).toHours();
        long billableHours = Math.max(1, hours);

        double rate = getHourlyRate(ticket.getVehicle().getVehicleType());
        double totalFee = billableHours * rate;

        ticket.setFee(totalFee);
        ticket.setStatus(TicketStatus.PAID);

        ticket.getParkingSpot().vacate();
        activeTickets.remove(ticketId);

        return ticket;
    }

    public int getAvailableSpotsCount() {
        int count = 0;
        for (ParkingSpot spot : spots) {
            if (spot.isAvailable()) {
                count++;
            }
        }
        return count;
    }
}

---

## 6. Main Class

public class Main {
public static void main(String[] args) {
ParkingLotController parkingLot = new ParkingLotController(2, 2, 1);

        System.out.println("Available spots: " + parkingLot.getAvailableSpotsCount());

        Vehicle car = new Vehicle("KA-01-AB-1234", VehicleType.CAR);
        Vehicle bike = new Vehicle("KA-02-XY-9876", VehicleType.MOTORCYCLE);

        Ticket carTicket = parkingLot.parkVehicle(car);
        System.out.println("Car Ticket ID: " + carTicket.getTicketId() + " assigned to Spot: " + carTicket.getParkingSpot().getSpotId());

        Ticket bikeTicket = parkingLot.parkVehicle(bike);
        System.out.println("Bike Ticket ID: " + bikeTicket.getTicketId() + " assigned to Spot: " + bikeTicket.getParkingSpot().getSpotId());

        System.out.println("Available spots: " + parkingLot.getAvailableSpotsCount());

        Ticket processedTicket = parkingLot.unparkVehicle(carTicket.getTicketId());
        System.out.println("Car Unparked. Fee: $" + processedTicket.getFee());

        System.out.println("Available spots: " + parkingLot.getAvailableSpotsCount());
    }
}