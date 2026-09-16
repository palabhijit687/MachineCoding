package bookmyshow.model;

/**
 * A physical seat in a screen. Immutable - "is it free?" depends on the show, not on
 * the seat, so status is deliberately NOT stored here.
 */
public class Seat {

    private final String id;      // e.g. "A1"
    private final String row;
    private final int number;
    private final SeatType type;

    public Seat(String row, int number, SeatType type) {
        this.row = row;
        this.number = number;
        this.type = type;
        this.id = row + number;
    }

    public String getId() {
        return id;
    }

    public String getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }

    public SeatType getType() {
        return type;
    }

    @Override
    public String toString() {
        return id;
    }
}
