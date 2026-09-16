package bookmyshow.service;

import bookmyshow.model.Movie;
import bookmyshow.model.Show;
import bookmyshow.model.Theatre;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The browse side: cities, theatres, movies, shows. Kept separate from booking
 * because the read path and the write path have nothing in common.
 */
public class Catalog {

    private final Map<String, Movie> movies = new LinkedHashMap<>();
    private final Map<String, Theatre> theatres = new LinkedHashMap<>();
    private final Map<String, Show> shows = new LinkedHashMap<>();

    public void addMovie(Movie movie) {
        movies.put(movie.getId(), movie);
    }

    public void addTheatre(Theatre theatre) {
        theatres.put(theatre.getId(), theatre);
    }

    public void addShow(Show show) {
        if (shows.containsKey(show.getId())) {
            throw new IllegalArgumentException("show " + show.getId() + " already exists");
        }
        // a screen cannot run two overlapping shows
        for (Show existing : shows.values()) {
            if (existing.getScreen().getId().equals(show.getScreen().getId())
                    && overlaps(existing, show)) {
                throw new IllegalArgumentException("show clashes with " + existing.getId()
                        + " on screen " + show.getScreen().getName());
            }
        }
        shows.put(show.getId(), show);
    }

    private boolean overlaps(Show a, Show b) {
        return a.getStartTime().isBefore(b.getEndTime())
                && b.getStartTime().isBefore(a.getEndTime());
    }

    public Movie getMovie(String movieId) {
        return movies.get(movieId);
    }

    public Show getShow(String showId) {
        return shows.get(showId);
    }

    /** Movies that have at least one show in the city, optionally filtered by title. */
    public List<Movie> searchMovies(String city, String titleContains) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies.values()) {
            boolean titleMatches = titleContains == null || titleContains.isBlank()
                    || movie.getTitle().toLowerCase().contains(titleContains.toLowerCase());
            boolean playsInCity = shows.values().stream()
                    .anyMatch(s -> s.getMovie().getId().equals(movie.getId())
                            && s.getTheatre().getCity().equalsIgnoreCase(city));
            if (titleMatches && playsInCity) {
                result.add(movie);
            }
        }
        return result;
    }

    public List<Show> showsFor(String movieId, String city, LocalDate date) {
        return shows.values().stream()
                .filter(s -> s.getMovie().getId().equals(movieId))
                .filter(s -> s.getTheatre().getCity().equalsIgnoreCase(city))
                .filter(s -> date == null || s.getStartTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(Show::getStartTime))
                .toList();
    }

    public List<Show> allShows() {
        return List.copyOf(shows.values());
    }
}
