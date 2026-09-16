package bookmyshow.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Screen {

    private final String id;
    private final String name;
    private final Map<String, Seat> seatsById = new LinkedHashMap<>();

    public Screen(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void addSeat(Seat seat) {
        if (seatsById.containsKey(seat.getId())) {
            throw new IllegalArgumentException("duplicate seat " + seat.getId() + " in screen " + name);
        }
        seatsById.put(seat.getId(), seat);
    }

    public Seat getSeat(String seatId) {
        return seatsById.get(seatId);
    }

    public boolean hasSeat(String seatId) {
        return seatsById.containsKey(seatId);
    }

    public List<Seat> getSeats() {
        return new ArrayList<>(seatsById.values());
    }

    public int seatCount() {
        return seatsById.size();
    }
}
