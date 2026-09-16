package bookmyshow.model;

import java.time.LocalDateTime;

/**
 * A movie playing on a screen at a time. `basePrice` is the REGULAR seat price; the
 * pricing strategy multiplies it per seat type.
 */
public class Show {

    private final String id;
    private final Movie movie;
    private final Theatre theatre;
    private final Screen screen;
    private final LocalDateTime startTime;
    private final double basePrice;

    public Show(String id, Movie movie, Theatre theatre, Screen screen,
                LocalDateTime startTime, double basePrice) {
        if (basePrice <= 0) {
            throw new IllegalArgumentException("base price must be positive");
        }
        this.id = id;
        this.movie = movie;
        this.theatre = theatre;
        this.screen = screen;
        this.startTime = startTime;
        this.basePrice = basePrice;
    }

    public String getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Theatre getTheatre() {
        return theatre;
    }

    public Screen getScreen() {
        return screen;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return startTime.plusMinutes(movie.getDurationMinutes());
    }

    public double getBasePrice() {
        return basePrice;
    }

    @Override
    public String toString() {
        return id + " " + movie.getTitle() + " @ " + theatre.getName()
                + " " + screen.getName() + " " + startTime.toLocalTime();
    }
}
