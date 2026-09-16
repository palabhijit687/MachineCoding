package snakeandladder.model;

/**
 * One snake or one ladder. Modelling both with a single class instead of two
 * subclasses, because the only difference is the direction - a snake goes down, a
 * ladder goes up. The type is derived, not stored twice.
 */
public class Jump {

    private final int start;
    private final int end;

    public Jump(int start, int end) {
        if (start == end) {
            throw new IllegalArgumentException("a jump cannot start and end on the same cell " + start);
        }
        this.start = start;
        this.end = end;
    }

    public static Jump snake(int head, int tail) {
        if (tail >= head) {
            throw new IllegalArgumentException("a snake must go down: " + head + " -> " + tail);
        }
        return new Jump(head, tail);
    }

    public static Jump ladder(int bottom, int top) {
        if (top <= bottom) {
            throw new IllegalArgumentException("a ladder must go up: " + bottom + " -> " + top);
        }
        return new Jump(bottom, top);
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public JumpType getType() {
        return end > start ? JumpType.LADDER : JumpType.SNAKE;
    }

    @Override
    public String toString() {
        return getType() + " " + start + "->" + end;
    }
}
