package tictactoe.strategy;

import tictactoe.model.Board;
import tictactoe.model.Move;

/**
 * How we decide someone has won. Behind an interface so I can start with the
 * obvious scan and swap in the O(1) counter version without touching Game.
 */
public interface WinningStrategy {

    /** Called after the move is placed on the board. */
    boolean isWinningMove(Board board, Move move);

    /** Called when a move is undone, so stateful strategies can roll back. */
    default void undoMove(Board board, Move move) {
    }

    String name();
}
