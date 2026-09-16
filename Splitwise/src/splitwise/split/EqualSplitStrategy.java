package splitwise.split;

import splitwise.model.Money;
import splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;

/**
 * Equal split done in paise so the parts always add back up to the total.
 *
 * 100 / 3 is not 33.33 three times (that is 99.99). The leftover paise are handed
 * to the first few participants: 33.34, 33.33, 33.33.
 */
public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> split(double amount, List<String> participantIds, List<Double> values) {
        int n = participantIds.size();
        long totalCents = Money.toCents(amount);
        long base = totalCents / n;
        long leftover = totalCents % n;

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long cents = base + (i < leftover ? 1 : 0);
            splits.add(new Split(participantIds.get(i), Money.fromCents(cents)));
        }
        return splits;
    }
}
