package snakeandladder.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What happened in one turn. Returning this instead of void keeps the game loop
 * out of the printing business and makes the turn easy to assert on in a test.
 */
public class TurnResult {

    private final Player player;
    private final int roll;
    private final int from;
    private int to;
    private final List<Jump> jumpsTaken = new ArrayList<>();
    private boolean blockedByExactFinish;
    private boolean won;
    private boolean extraTurn;

    public TurnResult(Player player, int roll, int from) {
        this.player = player;
        this.roll = roll;
        this.from = from;
        this.to = from;
    }

    public Player getPlayer() {
        return player;
    }

    public int getRoll() {
        return roll;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(int to) {
        this.to = to;
    }

    public List<Jump> getJumpsTaken() {
        return jumpsTaken;
    }

    public void addJump(Jump jump) {
        jumpsTaken.add(jump);
    }

    public boolean isBlockedByExactFinish() {
        return blockedByExactFinish;
    }

    public void setBlockedByExactFinish(boolean value) {
        this.blockedByExactFinish = value;
    }

    public boolean isWon() {
        return won;
    }

    public void setWon(boolean won) {
        this.won = won;
    }

    public boolean isExtraTurn() {
        return extraTurn;
    }

    public void setExtraTurn(boolean extraTurn) {
        this.extraTurn = extraTurn;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-8s rolled %d : %2d -> %2d", player.getName(), roll, from, to));
        if (blockedByExactFinish) {
            sb.append("  (overshoot, stays put)");
        }
        for (Jump jump : jumpsTaken) {
            sb.append("  [").append(jump).append("]");
        }
        if (extraTurn) {
            sb.append("  (extra turn)");
        }
        if (won) {
            sb.append("  *** WINS ***");
        }
        return sb.toString();
    }
}
