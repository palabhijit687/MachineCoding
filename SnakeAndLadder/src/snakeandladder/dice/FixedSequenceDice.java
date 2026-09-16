package snakeandladder.dice;

/**
 * Test dice - returns a scripted sequence, then repeats it. Lets me demo an exact
 * scenario (climb this ladder, get bitten by that snake, overshoot the last cell)
 * without hoping the random generator cooperates.
 */
public class FixedSequenceDice implements Dice {

    private final int[] sequence;
    private final int maxValue;
    private int index;

    public FixedSequenceDice(int... sequence) {
        if (sequence == null || sequence.length == 0) {
            throw new IllegalArgumentException("sequence cannot be empty");
        }
        this.sequence = sequence;
        int max = 6;
        for (int value : sequence) {
            max = Math.max(max, value);
        }
        this.maxValue = max;
    }

    @Override
    public int roll() {
        int value = sequence[index % sequence.length];
        index++;
        return value;
    }

    @Override
    public int maxValue() {
        return maxValue;
    }
}
