package cab.strategy;

import cab.model.Driver;
import cab.model.Location;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Shortest pickup distance wins. Ties broken by rating, then id for determinism. */
public class NearestDriverMatchingStrategy implements DriverMatchingStrategy {

    @Override
    public Optional<Driver> pick(List<Driver> candidates, Location pickup) {
        return candidates.stream()
                .min(Comparator
                        .comparingDouble((Driver d) -> d.getCurrentLocation().distanceKmTo(pickup))
                        .thenComparing(Comparator.comparingDouble(Driver::getRating).reversed())
                        .thenComparing(Driver::getId));
    }

    @Override
    public String name() {
        return "NearestDriver";
    }
}
