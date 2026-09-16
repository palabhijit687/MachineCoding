package cab.strategy;

import cab.model.Driver;
import cab.model.Location;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Best rating within the radius wins, distance only as a tie break. Shows why the
 * ranking belongs behind an interface - same candidate list, different winner.
 */
public class HighestRatedDriverMatchingStrategy implements DriverMatchingStrategy {

    @Override
    public Optional<Driver> pick(List<Driver> candidates, Location pickup) {
        return candidates.stream()
                .max(Comparator
                        .comparingDouble(Driver::getRating)
                        .thenComparing(Comparator.comparingDouble(
                                (Driver d) -> d.getCurrentLocation().distanceKmTo(pickup)).reversed())
                        .thenComparing(Comparator.comparing(Driver::getId).reversed()));
    }

    @Override
    public String name() {
        return "HighestRatedDriver";
    }
}
