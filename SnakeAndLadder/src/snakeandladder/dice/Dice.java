package snakeandladder.dice;

/**
 * Behind an interface for the same reason the clock was in the rate limiter: a
 * random dice makes the demo and the tests non deterministic.
 */
public interface Dice {

    int roll();

    /** Highest possible roll - needed for the "extra turn on max" rule. */
    int maxValue();
}
