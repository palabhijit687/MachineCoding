package bookmyshow.payment;

public class FakePaymentGateway implements PaymentGateway {

    private final boolean succeed;

    public FakePaymentGateway(boolean succeed) {
        this.succeed = succeed;
    }

    @Override
    public boolean charge(String bookingId, double amount) {
        return succeed;
    }
}
