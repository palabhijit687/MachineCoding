package tictactoe.model;

/**
 * N x N grid. Size is configurable because "make it 4x4" is the first follow-up
 * the interviewer asks.
 *
 * The board only stores state and validates coordinates - it does not know the
 * rules of winning. That lives in the strategy.
 */
public class Board {

    private final int size;
    private final Symbol[][] grid;
    private int filledCells;

    public Board(int size) {
        if (size < 3) {
            throw new IllegalArgumentException("board size must be at least 3");
        }
        this.size = size;
        this.grid = new Symbol[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                grid[r][c] = Symbol.EMPTY;
            }
        }
    }

    public int getSize() {
        return size;
    }

    public boolean isInside(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public boolean isFree(int row, int col) {
        return grid[row][col] == Symbol.EMPTY;
    }

    public Symbol get(int row, int col) {
        return grid[row][col];
    }

    public void place(int row, int col, Symbol symbol) {
        grid[row][col] = symbol;
        filledCells++;
    }

    public void clear(int row, int col) {
        grid[row][col] = Symbol.EMPTY;
        filledCells--;
    }

    public boolean isFull() {
        return filledCells == size * size;
    }

    public int getFilledCells() {
        return filledCells;
    }

    /** Prints with row/column headers so the demo output is readable. */
    public void print() {
        System.out.print("     ");
        for (int c = 0; c < size; c++) {
            System.out.print(c + "  ");
        }
        System.out.println();
        for (int r = 0; r < size; r++) {
            System.out.print("  " + r + "  ");
            for (int c = 0; c < size; c++) {
                System.out.print(grid[r][c].getDisplay() + "  ");
            }
            System.out.println();
        }
    }
}
