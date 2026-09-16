package bookmyshow.pricing;

import bookmyshow.model.Seat;
import bookmyshow.model.Show;

public interface PricingStrategy {
    double priceFor(Show show, Seat seat);
}
