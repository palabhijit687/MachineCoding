package cab.strategy;

import cab.model.Driver;
import cab.model.Location;

import java.util.List;
import java.util.Optional;

/**
 * Which of the nearby drivers should get the ride. The candidate list is already
 * filtered by availability, vehicle type and radius - this only ranks it.
 */
public interface DriverMatchingStrategy {

    Optional<Driver> pick(List<Driver> candidates, Location pickup);

    String name();
}
