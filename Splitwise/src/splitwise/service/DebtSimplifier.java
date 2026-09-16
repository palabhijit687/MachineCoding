package splitwise.service;

import splitwise.model.Money;
import splitwise.model.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * "Settle up" - the minimum cash flow problem.
 *
 * Only each person's NET position matters, not who ate whose pizza. Greedily match
 * the biggest creditor with the biggest debtor, settle the smaller of the two, and
 * repeat. Each round zeroes out at least one person, so with n people involved you
 * get at most n-1 transfers instead of a transfer per pair.
 *
 * This greedy version minimises the number of people left, not provably the number
 * of transactions in every case (that is NP-hard), which is the honest thing to say
 * out loud. In practice it is what Splitwise's "simplify debts" does.
 */
public class DebtSimplifier {

    private static class Node {
        final String userId;
        double amount;

        Node(String userId, double amount) {
            this.userId = userId;
            this.amount = amount;
        }
    }

    public List<Transaction> simplify(Map<String, Double> netBalances) {
        // biggest creditor first, biggest debtor first
        PriorityQueue<Node> creditors =
                new PriorityQueue<>(Comparator.comparingDouble((Node n) -> -n.amount));
        PriorityQueue<Node> debtors =
                new PriorityQueue<>(Comparator.comparingDouble(n -> n.amount));

        netBalances.forEach((userId, net) -> {
            if (Money.isZero(net)) {
                return;
            }
            if (net > 0) {
                creditors.add(new Node(userId, net));
            } else {
                debtors.add(new Node(userId, net));
            }
        });

        List<Transaction> settlements = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node creditor = creditors.poll();
            Node debtor = debtors.poll();

            double transfer = Math.min(creditor.amount, -debtor.amount);
            settlements.add(new Transaction(debtor.userId, creditor.userId, Money.round2(transfer)));

            creditor.amount = Money.round2(creditor.amount - transfer);
            debtor.amount = Money.round2(debtor.amount + transfer);

            if (!Money.isZero(creditor.amount)) {
                creditors.add(creditor);
            }
            if (!Money.isZero(debtor.amount)) {
                debtors.add(debtor);
            }
        }
        return settlements;
    }
}
