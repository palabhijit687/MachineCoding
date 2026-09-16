package splitwise.split;

import splitwise.model.SplitType;

public class SplitStrategyFactory {

    private SplitStrategyFactory() {
    }

    public static SplitStrategy forType(SplitType type) {
        return switch (type) {
            case EQUAL -> new EqualSplitStrategy();
            case EXACT -> new ExactSplitStrategy();
            case PERCENT -> new PercentSplitStrategy();
        };
    }
}
