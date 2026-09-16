package snakeandladder.model;

/**
 * The house rules. Everyone plays snake and ladder slightly differently, so I put
 * the variations in one config object instead of hardcoding one interpretation.
 */
public class GameConfig {

    /** Must land exactly on the last cell; an overshoot means you do not move. */
    private boolean exactFinishRequired = true;

    /** Rolling the maximum value gives another turn. */
    private boolean extraTurnOnMaxRoll = false;

    /** Keep playing after the first winner, to produce a full ranking. */
    private boolean playUntilAllFinish = false;

    /** Safety valve so a broken board cannot loop forever. */
    private int maxTurns = 500;

    public static GameConfig defaults() {
        return new GameConfig();
    }

    public GameConfig exactFinishRequired(boolean value) {
        this.exactFinishRequired = value;
        return this;
    }

    public GameConfig extraTurnOnMaxRoll(boolean value) {
        this.extraTurnOnMaxRoll = value;
        return this;
    }

    public GameConfig playUntilAllFinish(boolean value) {
        this.playUntilAllFinish = value;
        return this;
    }

    public GameConfig maxTurns(int value) {
        this.maxTurns = value;
        return this;
    }

    public boolean isExactFinishRequired() {
        return exactFinishRequired;
    }

    public boolean isExtraTurnOnMaxRoll() {
        return extraTurnOnMaxRoll;
    }

    public boolean isPlayUntilAllFinish() {
        return playUntilAllFinish;
    }

    public int getMaxTurns() {
        return maxTurns;
    }
}
