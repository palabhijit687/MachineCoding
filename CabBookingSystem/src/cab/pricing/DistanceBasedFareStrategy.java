package cab.pricing;

import cab.model.VehicleType;

import java.util.EnumMap;
import java.util.Map;

/**
 * fare = (base + perKm * km + perMinute * minutes) * surge
 *
 * Surge is a field rather than a separate class so the demo can show the same ride
 * priced normally and at 1.8x.
 */
public class DistanceBasedFareStrategy implements FareStrategy {

    private static class Rate {
        final double base;
        final double perKm;
        final double perMinute;

        Rate(double base, double perKm, double perMinute) {
            this.base = base;
            this.perKm = perKm;
            this.perMinute = perMinute;
        }
    }

    private final Map<VehicleType, Rate> rates = new EnumMap<>(VehicleType.class);
    private final double surgeMultiplier;

    public DistanceBasedFareStrategy() {
        this(1.0);
    }

    public DistanceBasedFareStrategy(double surgeMultiplier) {
        if (surgeMultiplier < 1.0) {
            throw new IllegalArgumentException("surge multiplier cannot be below 1.0");
        }
        this.surgeMultiplier = surgeMultiplier;
        rates.put(VehicleType.BIKE, new Rate(20, 8, 1));
        rates.put(VehicleType.SEDAN, new Rate(50, 12, 2));
        rates.put(VehicleType.SUV, new Rate(80, 18, 3));
    }

    @Override
    public double calculate(VehicleType type, double distanceKm, long durationMinutes) {
        Rate rate = rates.get(type);
        double fare = rate.base + rate.perKm * distanceKm + rate.perMinute * durationMinutes;
        return Math.round(fare * surgeMultiplier * 100) / 100.0;
    }

    @Override
    public String describe(VehicleType type) {
        Rate rate = rates.get(type);
        return type + ": base " + rate.base + " + " + rate.perKm + "/km + "
                + rate.perMinute + "/min"
                + (surgeMultiplier > 1.0 ? " x" + surgeMultiplier + " surge" : "");
    }
}
