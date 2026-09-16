package splitwise.split;

import splitwise.model.Split;

import java.util.List;

/**
 * Turns "1000 rupees shared by these people" into a concrete per-person amount.
 * One implementation per split type, so adding SHARES later is a new class.
 */
public interface SplitStrategy {

    /**
     * @param amount        total expense amount
     * @param participantIds who the expense is split across
     * @param values        exact amounts or percentages; ignored for an equal split
     */
    List<Split> split(double amount, List<String> participantIds, List<Double> values);
}
