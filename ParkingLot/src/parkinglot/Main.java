package parkinglot;

import parkinglot.exception.InvalidTicketException;
import parkinglot.exception.ParkingLotFullException;
import parkinglot.model.ParkingFloor;
import parkinglot.model.ParkingSpot;
import parkinglot.model.SpotType;
import parkinglot.model.Ticket;
import parkinglot.model.Vehicle;
import parkinglot.model.VehicleType;
import parkinglot.pricing.HourlyPricingStrategy;
import parkinglot.service.ParkingLot;

import java.time.LocalDateTime;

/**
 * Driver that walks through the happy path + the edge cases the interviewer
 * usually asks for.
 */
public class Main {

    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot("Phoenix Mall Parking", new HourlyPricingStrategy());

        // floor 1: 2 small, 2 medium, 1 large | floor 2: 1 small, 1 medium
        lot.addFloor(buildFloor(1, 2, 2, 1));
        lot.addFloor(buildFloor(2, 1, 1, 0));

        System.out.println(lot.availability());

        LocalDateTime now = LocalDateTime.of(2026, 9, 16, 10, 0);

        System.out.println("--- parking vehicles ---");
        Ticket bikeTicket = lot.park(new Vehicle("KA01AB1234", VehicleType.BIKE), now);
        Ticket carTicket = lot.park(new Vehicle("KA02CD5678", VehicleType.CAR), now);
        Ticket truckTicket = lot.park(new Vehicle("KA03EF9012", VehicleType.TRUCK), now);
        System.out.println(bikeTicket);
        System.out.println(carTicket);
        System.out.println(truckTicket);
        System.out.println();
        System.out.println(lot.availability());

        System.out.println("--- exit after some time ---");
        Ticket closedBike = lot.unpark(bikeTicket.getTicketId(), now.plusMinutes(30));
        System.out.println("bike parked 30 min  -> pay " + closedBike.getAmount());

        Ticket closedCar = lot.unpark(carTicket.getTicketId(), now.plusHours(2).plusMinutes(10));
        System.out.println("car parked 2h10m    -> pay " + closedCar.getAmount());

        Ticket closedTruck = lot.unpark(truckTicket.getTicketId(), now.plusHours(3));
        System.out.println("truck parked 3h     -> pay " + closedTruck.getAmount());
        System.out.println();
        System.out.println(lot.availability());

        System.out.println("--- edge cases ---");

        // 1. same ticket twice
        try {
            lot.unpark(closedBike.getTicketId(), now.plusHours(4));
        } catch (InvalidTicketException e) {
            System.out.println("double exit blocked: " + e.getMessage());
        }

        // 2. unknown ticket
        try {
            lot.unpark("T-9999");
        } catch (InvalidTicketException e) {
            System.out.println("unknown ticket blocked: " + e.getMessage());
        }

        // 3. same vehicle parked twice
        Vehicle duplicate = new Vehicle("KA05XY1111", VehicleType.CAR);
        lot.park(duplicate, now);
        try {
            lot.park(duplicate, now);
        } catch (IllegalStateException e) {
            System.out.println("duplicate entry blocked: " + e.getMessage());
        }

        // 4. lot full for trucks (only 1 large spot, fill it then try again)
        lot.park(new Vehicle("KA06TR2222", VehicleType.TRUCK), now);
        try {
            lot.park(new Vehicle("KA07TR3333", VehicleType.TRUCK), now);
        } catch (ParkingLotFullException e) {
            System.out.println("lot full blocked: " + e.getMessage());
        }

        System.out.println();
        System.out.println(lot.availability());
    }

    private static ParkingFloor buildFloor(int floorNo, int small, int medium, int large) {
        ParkingFloor floor = new ParkingFloor(floorNo);
        int counter = 1;
        for (int i = 0; i < small; i++) {
            floor.addSpot(new ParkingSpot("F" + floorNo + "-S" + counter++, SpotType.SMALL, floorNo));
        }
        for (int i = 0; i < medium; i++) {
            floor.addSpot(new ParkingSpot("F" + floorNo + "-M" + counter++, SpotType.MEDIUM, floorNo));
        }
        for (int i = 0; i < large; i++) {
            floor.addSpot(new ParkingSpot("F" + floorNo + "-L" + counter++, SpotType.LARGE, floorNo));
        }
        return floor;
    }
}
