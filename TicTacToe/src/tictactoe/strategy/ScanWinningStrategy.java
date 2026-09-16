package tictactoe.strategy;

import tictactoe.model.Board;
import tictactoe.model.Move;
import tictactoe.model.Symbol;

/**
 * The straightforward version: after a move, scan only the affected row, column
 * and (if relevant) the two diagonals. O(n) per move, no state to maintain.
 *
 * This is what I would write first. It is easy to prove correct and easy to undo.
 */
public class ScanWinningStrategy implements WinningStrategy {

    @Override
    public boolean isWinningMove(Board board, Move move) {
        int n = board.getSize();
        int row = move.getRow();
        int col = move.getCol();
        Symbol symbol = move.getPlayer().getSymbol();

        boolean rowWin = true;
        boolean colWin = true;
        for (int i = 0; i < n; i++) {
            if (board.get(row, i) != symbol) rowWin = false;
            if (board.get(i, col) != symbol) colWin = false;
        }
        if (rowWin || colWin) {
            return true;
        }

        if (row == col) {
            boolean diagWin = true;
            for (int i = 0; i < n; i++) {
                if (board.get(i, i) != symbol) {
                    diagWin = false;
                    break;
                }
            }
            if (diagWin) return true;
        }

        if (row + col == n - 1) {
            boolean antiWin = true;
            for (int i = 0; i < n; i++) {
                if (board.get(i, n - 1 - i) != symbol) {
                    antiWin = false;
                    break;
                }
            }
            if (antiWin) return true;
        }
        return false;
    }

    @Override
    public String name() {
        return "ScanWinningStrategy (O(n) per move, stateless)";
    }
}
