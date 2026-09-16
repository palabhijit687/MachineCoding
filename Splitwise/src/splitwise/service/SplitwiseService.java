package splitwise.service;

import splitwise.exception.InvalidSplitException;
import splitwise.exception.UserNotFoundException;
import splitwise.model.Expense;
import splitwise.model.Group;
import splitwise.model.Money;
import splitwise.model.Split;
import splitwise.model.SplitType;
import splitwise.model.Transaction;
import splitwise.model.User;
import splitwise.split.SplitStrategy;
import splitwise.split.SplitStrategyFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The facade the caller uses: register users, create groups, add expenses, read
 * balances, settle up.
 */
public class SplitwiseService {

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Transaction> settlements = new ArrayList<>();
    private final BalanceSheet balanceSheet = new BalanceSheet();
    private final DebtSimplifier simplifier = new DebtSimplifier();
    private final AtomicInteger expenseSequence = new AtomicInteger();

    // ---------- setup ----------

    public User addUser(String id, String name, String email) {
        if (users.containsKey(id)) {
            throw new IllegalArgumentException("user " + id + " already exists");
        }
        User user = new User(id, name, email);
        users.put(id, user);
        return user;
    }

    public Group createGroup(String id, String name, List<String> memberIds) {
        if (groups.containsKey(id)) {
            throw new IllegalArgumentException("group " + id + " already exists");
        }
        Group group = new Group(id, name);
        for (String memberId : memberIds) {
            requireUser(memberId);
            group.addMember(memberId);
        }
        groups.put(id, group);
        return group;
    }

    public void addToGroup(String groupId, String userId) {
        requireUser(userId);
        requireGroup(groupId).addMember(userId);
    }

    // ---------- expenses ----------

    /** Equal split across everyone given. */
    public Expense addEqualExpense(String description, double amount, String paidBy,
                                   List<String> participantIds, String groupId) {
        return addExpense(description, amount, paidBy, participantIds,
                SplitType.EQUAL, null, groupId);
    }

    public Expense addExpense(String description, double amount, String paidBy,
                              List<String> participantIds, SplitType splitType,
                              List<Double> values, String groupId) {
        validateExpense(description, amount, paidBy, participantIds, groupId);

        SplitStrategy strategy = SplitStrategyFactory.forType(splitType);
        List<Split> splits = strategy.split(amount, participantIds, values);

        String expenseId = "EXP-" + expenseSequence.incrementAndGet();
        Expense expense = new Expense(expenseId, description, amount, paidBy,
                splits, splitType, groupId);
        expenses.add(expense);

        // everyone except the payer now owes the payer their share
        for (Split split : splits) {
            if (!split.getUserId().equals(paidBy)) {
                balanceSheet.addDebt(split.getUserId(), paidBy, split.getAmount());
            }
        }
        return expense;
    }

    /** Partial or full payback. Cannot pay more than what is actually owed. */
    public Transaction settle(String fromUserId, String toUserId, double amount) {
        requireUser(fromUserId);
        requireUser(toUserId);
        if (fromUserId.equals(toUserId)) {
            throw new InvalidSplitException("cannot settle with yourself");
        }
        if (amount <= 0) {
            throw new InvalidSplitException("settlement amount must be positive");
        }
        double owed = balanceSheet.amountOwed(fromUserId, toUserId);
        if (Money.isZero(owed) || owed < 0) {
            throw new InvalidSplitException(nameOf(fromUserId) + " does not owe "
                    + nameOf(toUserId) + " anything");
        }
        if (Money.toCents(amount) > Money.toCents(owed)) {
            throw new InvalidSplitException(nameOf(fromUserId) + " owes only "
                    + Money.format(owed) + ", cannot settle " + Money.format(amount));
        }

        balanceSheet.addDebt(fromUserId, toUserId, -amount);
        Transaction transaction = new Transaction(fromUserId, toUserId, Money.round2(amount));
        settlements.add(transaction);
        return transaction;
    }

    /** Settle everything the user owes the other, in one shot. */
    public Transaction settleFully(String fromUserId, String toUserId) {
        double owed = balanceSheet.amountOwed(fromUserId, toUserId);
        return settle(fromUserId, toUserId, Money.round2(owed));
    }

    // ---------- reads ----------

    public List<String> statementFor(String userId) {
        requireUser(userId);
        return balanceSheet.statementFor(userId, namesById());
    }

    public double netBalance(String userId) {
        requireUser(userId);
        return balanceSheet.netBalance(userId);
    }

    public List<Transaction> allDues() {
        return balanceSheet.allDues();
    }

    /** Minimum set of transfers that clears everything. */
    public List<Transaction> simplifiedSettlements() {
        return simplifier.simplify(balanceSheet.netBalances());
    }

    public List<Expense> expensesFor(String userId) {
        requireUser(userId);
        return expenses.stream()
                .filter(e -> e.getPaidByUserId().equals(userId)
                        || e.getSplits().stream().anyMatch(s -> s.getUserId().equals(userId)))
                .toList();
    }

    public List<Expense> groupExpenses(String groupId) {
        requireGroup(groupId);
        return expenses.stream()
                .filter(e -> groupId.equals(e.getGroupId()))
                .toList();
    }

    public double totalPaidBy(String userId) {
        requireUser(userId);
        return Money.round2(expenses.stream()
                .filter(e -> e.getPaidByUserId().equals(userId))
                .mapToDouble(Expense::getAmount)
                .sum());
    }

    public List<Expense> getExpenses() {
        return List.copyOf(expenses);
    }

    public List<Transaction> getSettlements() {
        return List.copyOf(settlements);
    }

    public String nameOf(String userId) {
        User user = users.get(userId);
        return user == null ? userId : user.getName();
    }

    public Map<String, String> namesById() {
        Map<String, String> names = new HashMap<>();
        users.forEach((id, user) -> names.put(id, user.getName()));
        return names;
    }

    // ---------- validation ----------

    private void validateExpense(String description, double amount, String paidBy,
                                 List<String> participantIds, String groupId) {
        if (description == null || description.isBlank()) {
            throw new InvalidSplitException("description is required");
        }
        if (amount <= 0) {
            throw new InvalidSplitException("expense amount must be positive");
        }
        if (participantIds == null || participantIds.isEmpty()) {
            throw new InvalidSplitException("an expense needs at least one participant");
        }
        Set<String> unique = new HashSet<>(participantIds);
        if (unique.size() != participantIds.size()) {
            throw new InvalidSplitException("a participant is listed twice");
        }
        requireUser(paidBy);
        participantIds.forEach(this::requireUser);

        if (groupId != null) {
            Group group = requireGroup(groupId);
            if (!group.hasMember(paidBy)) {
                throw new InvalidSplitException(nameOf(paidBy) + " is not in group " + group.getName());
            }
            for (String participant : participantIds) {
                if (!group.hasMember(participant)) {
                    throw new InvalidSplitException(nameOf(participant)
                            + " is not in group " + group.getName());
                }
            }
        }
    }

    private void requireUser(String userId) {
        if (!users.containsKey(userId)) {
            throw new UserNotFoundException("no user with id " + userId);
        }
    }

    private Group requireGroup(String groupId) {
        Group group = groups.get(groupId);
        if (group == null) {
            throw new IllegalArgumentException("no group with id " + groupId);
        }
        return group;
    }
}
