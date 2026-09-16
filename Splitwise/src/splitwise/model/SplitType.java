package splitwise.model;

public enum SplitType {
    /** Divide equally; the leftover paise go to the first few participants. */
    EQUAL,
    /** Caller gives the exact amount per participant; must add up to the total. */
    EXACT,
    /** Caller gives a percentage per participant; must add up to 100. */
    PERCENT
}
