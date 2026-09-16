package splitwise.model;

/** "from pays to, this much" - used for settlements and for simplified debts. */
public class Transaction {

    private final String fromUserId;
    private final String toUserId;
    private final double amount;

    public Transaction(String fromUserId, String toUserId, double amount) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return fromUserId + " pays " + toUserId + " " + Money.format(amount);
    }
}
