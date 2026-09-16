package parkinglot.pricing;

import parkinglot.model.Ticket;
import parkinglot.model.VehicleType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Simple hourly pricing: rate per vehicle type, every started hour is charged.
 * Minimum charge is 1 hour.
 */
public class HourlyPricingStrategy implements PricingStrategy {

    private final Map<VehicleType, Double> ratePerHour = new EnumMap<>(VehicleType.class);

    public HourlyPricingStrategy() {
        ratePerHour.put(VehicleType.BIKE, 10.0);
        ratePerHour.put(VehicleType.CAR, 20.0);
        ratePerHour.put(VehicleType.TRUCK, 40.0);
    }

    public HourlyPricingStrategy(Map<VehicleType, Double> rates) {
        ratePerHour.putAll(rates);
    }

    @Override
    public double calculate(Ticket ticket, LocalDateTime exitTime) {
        long minutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();
        if (minutes < 0) {
            throw new IllegalArgumentException("exit time cannot be before entry time");
        }
        long hours = Math.max(1, (long) Math.ceil(minutes / 60.0));
        double rate = ratePerHour.getOrDefault(ticket.getVehicle().getType(), 20.0);
        return hours * rate;
    }
}
