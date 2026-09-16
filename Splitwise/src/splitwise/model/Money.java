package splitwise.model;

/**
 * Money helpers.
 *
 * Doubles cannot represent 0.1 exactly, so every split is computed in paise/cents
 * (long) and converted back at the end. This is the difference between a split that
 * adds up and one that is off by a paisa - the thing interviewers actually check.
 */
public final class Money {

    private Money() {
    }

    public static long toCents(double amount) {
        return Math.round(amount * 100);
    }

    public static double fromCents(long cents) {
        return cents / 100.0;
    }

    public static double round2(double amount) {
        return Math.round(amount * 100) / 100.0;
    }

    /** Treat anything under half a paisa as zero, so settled balances disappear. */
    public static boolean isZero(double amount) {
        return Math.abs(amount) < 0.005;
    }

    public static String format(double amount) {
        return String.format("%.2f", amount);
    }
}
