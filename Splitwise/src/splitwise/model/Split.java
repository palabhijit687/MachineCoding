package splitwise.model;

/** How much one user owes for one expense. */
public class Split {

    private final String userId;
    private final double amount;

    public Split(String userId, double amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public String getUserId() {
        return userId;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return userId + ":" + Money.format(amount);
    }
}
