package splitwise.model;

import java.util.List;

public class Expense {

    private final String id;
    private final String description;
    private final double amount;
    private final String paidByUserId;
    private final List<Split> splits;
    private final SplitType splitType;
    private final String groupId;   // null for a direct friend expense

    public Expense(String id, String description, double amount, String paidByUserId,
                   List<Split> splits, SplitType splitType, String groupId) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.paidByUserId = paidByUserId;
        this.splits = List.copyOf(splits);
        this.splitType = splitType;
        this.groupId = groupId;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    public String getPaidByUserId() {
        return paidByUserId;
    }

    public List<Split> getSplits() {
        return splits;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public String toString() {
        return id + " '" + description + "' " + Money.format(amount)
                + " paid by " + paidByUserId + " " + splitType + " " + splits;
    }
}
