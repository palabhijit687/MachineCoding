package tictactoe.model;

/**
 * Kept as an enum instead of a raw char so an invalid symbol cannot exist.
 * EMPTY is the "no piece here" marker, which avoids null checks on the board.
 */
public enum Symbol {
    X('X'),
    O('O'),
    /** Third symbol so a 3 player game works without touching anything else. */
    Z('Z'),
    EMPTY('-');

    private final char display;

    Symbol(char display) {
        this.display = display;
    }

    public char getDisplay() {
        return display;
    }
}
