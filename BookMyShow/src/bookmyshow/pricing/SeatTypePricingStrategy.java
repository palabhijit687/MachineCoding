package bookmyshow.pricing;

import bookmyshow.model.Seat;
import bookmyshow.model.SeatType;
import bookmyshow.model.Show;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

/**
 * Show base price x a multiplier per seat type, plus a weekend surcharge.
 *
 * Behind the strategy interface so surge pricing, discounts or coupon logic can be
 * swapped in without touching the booking flow.
 */
public class SeatTypePricingStrategy implements PricingStrategy {

    private final Map<SeatType, Double> multipliers = new EnumMap<>(SeatType.class);
    private final double weekendSurcharge;

    public SeatTypePricingStrategy() {
        this(1.2);
    }

    public SeatTypePricingStrategy(double weekendMultiplier) {
        multipliers.put(SeatType.REGULAR, 1.0);
        multipliers.put(SeatType.PREMIUM, 1.5);
        multipliers.put(SeatType.RECLINER, 2.5);
        this.weekendSurcharge = weekendMultiplier;
    }

    @Override
    public double priceFor(Show show, Seat seat) {
        double price = show.getBasePrice() * multipliers.getOrDefault(seat.getType(), 1.0);
        DayOfWeek day = show.getStartTime().getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            price *= weekendSurcharge;
        }
        return Math.round(price * 100) / 100.0;
    }
}
