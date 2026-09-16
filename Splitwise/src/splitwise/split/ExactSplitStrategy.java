package splitwise.split;

import splitwise.exception.InvalidSplitException;
import splitwise.model.Money;
import splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;

/**
 * Caller gives the exact amount for each participant. The only real job here is
 * refusing splits that do not add up to the total.
 */
public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> split(double amount, List<String> participantIds, List<Double> values) {
        if (values == null || values.size() != participantIds.size()) {
            throw new InvalidSplitException("an exact split needs one amount per participant");
        }

        long totalCents = Money.toCents(amount);
        long sumCents = 0;
        for (Double value : values) {
            if (value == null || value < 0) {
                throw new InvalidSplitException("split amounts cannot be negative");
            }
            sumCents += Money.toCents(value);
        }
        if (sumCents != totalCents) {
            throw new InvalidSplitException("split amounts add up to "
                    + Money.format(Money.fromCents(sumCents)) + " but the expense is "
                    + Money.format(amount));
        }

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < participantIds.size(); i++) {
            splits.add(new Split(participantIds.get(i), Money.round2(values.get(i))));
        }
        return splits;
    }
}
