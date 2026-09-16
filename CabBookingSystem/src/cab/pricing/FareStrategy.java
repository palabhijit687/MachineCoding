package cab.pricing;

import cab.model.VehicleType;

/**
 * Fare calculation. Behind an interface because pricing is the thing product changes
 * every month - surge, night charge, subscriptions, promos.
 */
public interface FareStrategy {

    double calculate(VehicleType type, double distanceKm, long durationMinutes);

    String describe(VehicleType type);
}
