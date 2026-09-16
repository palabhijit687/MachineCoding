package snakeandladder.dice;

import java.util.Random;

/**
 * Supports multiple dice, since "now play with 2 dice" is a standard follow-up.
 * Takes an optional seed so a "random" game is still reproducible.
 */
public class RandomDice implements Dice {

    private final int diceCount;
    private final int sides;
    private final Random random;

    public RandomDice() {
        this(1, 6, new Random());
    }

    public RandomDice(int diceCount, int sides, long seed) {
        this(diceCount, sides, new Random(seed));
    }

    private RandomDice(int diceCount, int sides, Random random) {
        if (diceCount < 1 || sides < 2) {
            throw new IllegalArgumentException("need at least 1 dice with 2 or more sides");
        }
        this.diceCount = diceCount;
        this.sides = sides;
        this.random = random;
    }

    @Override
    public int roll() {
        int total = 0;
        for (int i = 0; i < diceCount; i++) {
            total += random.nextInt(sides) + 1;
        }
        return total;
    }

    @Override
    public int maxValue() {
        return diceCount * sides;
    }
}
