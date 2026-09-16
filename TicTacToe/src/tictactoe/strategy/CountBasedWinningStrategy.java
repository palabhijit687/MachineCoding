package tictactoe.strategy;

import tictactoe.model.Board;
import tictactoe.model.Move;
import tictactoe.model.Symbol;

/**
 * O(1) per move. Instead of scanning, keep a running count per symbol for every
 * row, every column, the diagonal and the anti diagonal. A move wins when one of
 * its counters reaches the board size.
 *
 * Trade off: it is stateful, so one instance belongs to exactly one game, and
 * isWinningMove must be called exactly once per move. undoMove rolls the counters
 * back so the undo feature still works.
 */
public class CountBasedWinningStrategy implements WinningStrategy {

    private final int size;
    private final int[][] rowCount;      // [symbol][row]
    private final int[][] colCount;      // [symbol][col]
    private final int[] diagCount;       // [symbol]
    private final int[] antiDiagCount;   // [symbol]

    public CountBasedWinningStrategy(int size) {
        this.size = size;
        // one slot per symbol, so adding a third player needs no change here
        int symbols = Symbol.values().length;
        this.rowCount = new int[symbols][size];
        this.colCount = new int[symbols][size];
        this.diagCount = new int[symbols];
        this.antiDiagCount = new int[symbols];
    }

    @Override
    public boolean isWinningMove(Board board, Move move) {
        int s = index(move.getPlayer().getSymbol());
        int row = move.getRow();
        int col = move.getCol();

        rowCount[s][row]++;
        colCount[s][col]++;
        if (row == col) {
            diagCount[s]++;
        }
        if (row + col == size - 1) {
            antiDiagCount[s]++;
        }

        return rowCount[s][row] == size
                || colCount[s][col] == size
                || diagCount[s] == size
                || antiDiagCount[s] == size;
    }

    @Override
    public void undoMove(Board board, Move move) {
        int s = index(move.getPlayer().getSymbol());
        int row = move.getRow();
        int col = move.getCol();

        rowCount[s][row]--;
        colCount[s][col]--;
        if (row == col) {
            diagCount[s]--;
        }
        if (row + col == size - 1) {
            antiDiagCount[s]--;
        }
    }

    private int index(Symbol symbol) {
        return symbol.ordinal();
    }

    @Override
    public String name() {
        return "CountBasedWinningStrategy (O(1) per move, stateful)";
    }
}
