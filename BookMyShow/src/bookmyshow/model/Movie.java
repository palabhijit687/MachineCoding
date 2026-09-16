package bookmyshow.model;

public class Movie {

    private final String id;
    private final String title;
    private final String language;
    private final int durationMinutes;

    public Movie(String id, String title, String language, int durationMinutes) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.durationMinutes = durationMinutes;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    @Override
    public String toString() {
        return title + " (" + language + ", " + durationMinutes + "m)";
    }
}
