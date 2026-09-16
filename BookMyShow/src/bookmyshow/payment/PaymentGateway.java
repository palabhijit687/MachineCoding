package bookmyshow.payment;

/**
 * Stubbed out - a real gateway is an external call. What matters for the design is
 * that payment can FAIL, and a failure must release the held seats.
 */
public interface PaymentGateway {
    boolean charge(String bookingId, double amount);
}
