package splitwise.split;

import splitwise.exception.InvalidSplitException;
import splitwise.model.Money;
import splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;

/**
 * Percent split. Percentages must add up to 100.
 *
 * Rounding each share independently can leave the total a paisa short (e.g. 33.33%
 * three times), so the residual is dropped on the largest share - noticeable to
 * nobody, and the expense reconciles exactly.
 */
public class PercentSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> split(double amount, List<String> participantIds, List<Double> values) {
        if (values == null || values.size() != participantIds.size()) {
            throw new InvalidSplitException("a percent split needs one percentage per participant");
        }

        double totalPercent = 0;
        for (Double value : values) {
            if (value == null || value < 0) {
                throw new InvalidSplitException("percentages cannot be negative");
            }
            totalPercent += value;
        }
        if (Math.abs(totalPercent - 100.0) > 0.001) {
            throw new InvalidSplitException("percentages add up to "
                    + Money.format(totalPercent) + ", they must add up to 100");
        }

        long totalCents = Money.toCents(amount);
        long[] cents = new long[participantIds.size()];
        long assigned = 0;
        int largestIndex = 0;

        for (int i = 0; i < participantIds.size(); i++) {
            cents[i] = Math.round(totalCents * values.get(i) / 100.0);
            assigned += cents[i];
            if (values.get(i) > values.get(largestIndex)) {
                largestIndex = i;
            }
        }
        cents[largestIndex] += totalCents - assigned; // absorb the rounding residual

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < participantIds.size(); i++) {
            splits.add(new Split(participantIds.get(i), Money.fromCents(cents[i])));
        }
        return splits;
    }
}
