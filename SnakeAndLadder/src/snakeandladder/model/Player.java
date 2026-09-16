package snakeandladder.model;

public class Player {

    private final String name;
    private int position = 0;   // 0 = not on the board yet
    private int rank;           // 0 until they finish

    public Player(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("player name is required");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public boolean hasFinished() {
        return rank > 0;
    }

    @Override
    public String toString() {
        return name + "@" + position;
    }
}
