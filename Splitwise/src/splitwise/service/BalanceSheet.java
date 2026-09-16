package splitwise.service;

import splitwise.model.Money;
import splitwise.model.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Who owes whom. Stored as a nested map:
 *
 *   balances[a][b] > 0  ->  a owes b that much
 *   balances[a][b] < 0  ->  b owes a
 *
 * Both directions are always written together, so reading either side gives a
 * consistent answer and there is no "which way round is it stored?" bug.
 */
public class BalanceSheet {

    private final Map<String, Map<String, Double>> balances = new HashMap<>();

    /** Records that `from` owes `to` an extra `amount` (negative amount reverses it). */
    public void addDebt(String from, String to, double amount) {
        if (from.equals(to)) {
            return; // a user never owes themselves
        }
        balances.computeIfAbsent(from, k -> new HashMap<>())
                .merge(to, amount, Double::sum);
        balances.computeIfAbsent(to, k -> new HashMap<>())
                .merge(from, -amount, Double::sum);
    }

    /** Positive = from owes to. Negative = to owes from. */
    public double amountOwed(String from, String to) {
        return balances.getOrDefault(from, Map.of()).getOrDefault(to, 0.0);
    }

    /**
     * Net position of one user across everybody.
     * Positive = the user should receive money, negative = the user owes money.
     */
    public double netBalance(String userId) {
        double owedByUser = balances.getOrDefault(userId, Map.of()).values().stream()
                .mapToDouble(Double::doubleValue).sum();
        return Money.round2(-owedByUser);
    }

    /** Readable per user view, skipping settled pairs. */
    public List<String> statementFor(String userId, Map<String, String> namesById) {
        List<String> lines = new ArrayList<>();
        Map<String, Double> row = balances.getOrDefault(userId, Map.of());
        for (String other : new TreeSet<>(row.keySet())) {
            double amount = row.get(other);
            if (Money.isZero(amount)) {
                continue;
            }
            String otherName = namesById.getOrDefault(other, other);
            if (amount > 0) {
                lines.add(namesById.getOrDefault(userId, userId) + " owes "
                        + otherName + " " + Money.format(amount));
            } else {
                lines.add(otherName + " owes " + namesById.getOrDefault(userId, userId)
                        + " " + Money.format(-amount));
            }
        }
        if (lines.isEmpty()) {
            lines.add("no dues");
        }
        return lines;
    }

    /**
     * Every outstanding debt, listed once per pair in the direction it is owed.
     * Sorted by user id so the output is stable.
     */
    public List<Transaction> allDues() {
        List<Transaction> dues = new ArrayList<>();
        for (String from : new TreeSet<>(balances.keySet())) {
            Map<String, Double> row = balances.get(from);
            for (String to : new TreeSet<>(row.keySet())) {
                double amount = row.get(to);
                if (amount > 0 && !Money.isZero(amount)) {
                    dues.add(new Transaction(from, to, Money.round2(amount)));
                }
            }
        }
        return dues;
    }

    /** Net position of every user who has ever been involved. */
    public Map<String, Double> netBalances() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String user : new TreeSet<>(balances.keySet())) {
            double net = netBalance(user);
            if (!Money.isZero(net)) {
                result.put(user, net);
            }
        }
        return result;
    }
}
