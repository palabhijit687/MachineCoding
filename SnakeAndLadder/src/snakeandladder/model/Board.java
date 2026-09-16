package snakeandladder.model;

import snakeandladder.exception.InvalidBoardException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cells are 1..size. Jumps live in a map keyed by their start cell, so resolving
 * "did I land on something?" is O(1) instead of scanning a list of snakes and a
 * list of ladders.
 *
 * All the board rules are validated here at construction time, so the game loop
 * can assume the board is sane.
 */
public class Board {

    private final int size;
    private final Map<Integer, Jump> jumpsByStart = new HashMap<>();

    public Board(int size, List<Jump> jumps) {
        if (size < 10) {
            throw new InvalidBoardException("board must have at least 10 cells");
        }
        this.size = size;
        for (Jump jump : jumps) {
            addJump(jump);
        }
    }

    private void addJump(Jump jump) {
        int start = jump.getStart();
        int end = jump.getEnd();

        if (start < 1 || start > size || end < 1 || end > size) {
            throw new InvalidBoardException(jump + " goes outside the board (1.." + size + ")");
        }
        if (start == 1) {
            throw new InvalidBoardException("nothing can start on cell 1: " + jump);
        }
        if (start == size) {
            throw new InvalidBoardException("nothing can start on the winning cell: " + jump);
        }
        if (end == size) {
            throw new InvalidBoardException("a jump cannot end on the winning cell: " + jump);
        }
        if (jumpsByStart.containsKey(start)) {
            throw new InvalidBoardException("cell " + start + " already has "
                    + jumpsByStart.get(start) + ", cannot also add " + jump);
        }
        jumpsByStart.put(start, jump);
    }

    public int getSize() {
        return size;
    }

    /** Null when the cell is plain. */
    public Jump jumpAt(int cell) {
        return jumpsByStart.get(cell);
    }

    public List<Jump> getJumps(JumpType type) {
        List<Jump> result = new ArrayList<>();
        jumpsByStart.values().stream()
                .filter(j -> j.getType() == type)
                .sorted((a, b) -> Integer.compare(a.getStart(), b.getStart()))
                .forEach(result::add);
        return result;
    }

    public void printSummary() {
        System.out.println("  board 1.." + size);
        System.out.println("    ladders: " + getJumps(JumpType.LADDER));
        System.out.println("    snakes : " + getJumps(JumpType.SNAKE));
    }
}
