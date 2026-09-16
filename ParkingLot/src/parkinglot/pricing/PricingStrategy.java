package parkinglot.pricing;

import parkinglot.model.Ticket;

import java.time.LocalDateTime;

/**
 * Strategy pattern - the lot does not care how the money is calculated. Makes it
 * easy to plug in flat rate / day pass / weekend pricing later.
 */
public interface PricingStrategy {
    double calculate(Ticket ticket, LocalDateTime exitTime);
}
