package tictactoe;

import tictactoe.exception.InvalidMoveException;
import tictactoe.model.GameStatus;
import tictactoe.model.Move;
import tictactoe.model.Player;
import tictactoe.model.Symbol;
import tictactoe.service.Game;
import tictactoe.strategy.CountBasedWinningStrategy;
import tictactoe.strategy.ScanWinningStrategy;
import tictactoe.strategy.WinningStrategy;

import java.util.List;

/**
 * The moves are scripted so the run is deterministic. In a real round I would also
 * wire a Scanner loop for human input - the Game API does not change either way.
 */
public class Main {

    private static final Player ALICE = new Player("Alice", Symbol.X);
    private static final Player BOB = new Player("Bob", Symbol.O);
    private static final Player CHARLIE = new Player("Charlie", Symbol.Z);

    public static void main(String[] args) {
        rowWin();
        columnWin();
        diagonalWin();
        draw();
        fourByFourAntiDiagonal();
        threePlayers();
        undo();
        invalidMoves();
        strategiesAgree();
    }

    private static void rowWin() {
        System.out.println("=== 1. 3x3 row win (counter based strategy) ===");
        Game game = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));
        play(game, new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0, 2}});
        finish(game);
    }

    private static void columnWin() {
        System.out.println("=== 2. 3x3 column win (scan strategy) ===");
        Game game = new Game(3, List.of(ALICE, BOB), new ScanWinningStrategy());
        play(game, new int[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}, {2, 0}});
        finish(game);
    }

    private static void diagonalWin() {
        System.out.println("=== 3. 3x3 diagonal win ===");
        Game game = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));
        play(game, new int[][]{{0, 0}, {0, 1}, {1, 1}, {0, 2}, {2, 2}});
        finish(game);
    }

    private static void draw() {
        System.out.println("=== 4. 3x3 draw (board full, nobody wins) ===");
        Game game = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));
        play(game, new int[][]{
                {0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 0}, {1, 2}, {2, 1}, {2, 0}, {2, 2}});
        finish(game);
    }

    private static void fourByFourAntiDiagonal() {
        System.out.println("=== 5. 4x4 board, anti diagonal win (needs 4 in a line) ===");
        Game game = new Game(4, List.of(ALICE, BOB), new CountBasedWinningStrategy(4));
        play(game, new int[][]{{0, 3}, {0, 0}, {1, 2}, {0, 1}, {2, 1}, {0, 2}, {3, 0}});
        finish(game);
    }

    private static void threePlayers() {
        System.out.println("=== 6. three players on a 3x3 (turn order rotates) ===");
        Game game = new Game(3, List.of(ALICE, BOB, CHARLIE), new CountBasedWinningStrategy(3));
        play(game, new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 0}, {1, 1}, {1, 2}, {2, 0}});
        finish(game);
    }

    private static void undo() {
        System.out.println("=== 7. undo ===");
        Game game = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));
        play(game, new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0, 2}});
        System.out.println("  status: " + game.resultText());

        Move undone = game.undoLastMove();
        System.out.println("  undid " + undone + " -> " + game.resultText());
        game.getBoard().print();

        System.out.println("  Alice plays (2,2) instead:");
        game.makeMove(2, 2);
        System.out.println("  status: " + game.resultText());

        // Bob can now block the winning cell
        game.makeMove(0, 2);
        System.out.println("  Bob blocks (0,2) -> " + game.resultText());
        game.getBoard().print();
        System.out.println("  move log: " + game.getMoveHistory() + "\n");
    }

    private static void invalidMoves() {
        System.out.println("=== 8. invalid input ===");
        Game game = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));

        try {
            game.makeMove(5, 0);
        } catch (InvalidMoveException e) {
            System.out.println("  out of bounds: " + e.getMessage());
        }
        try {
            game.makeMove(-1, 2);
        } catch (InvalidMoveException e) {
            System.out.println("  negative index: " + e.getMessage());
        }

        game.makeMove(0, 0);
        try {
            game.makeMove(0, 0);
        } catch (InvalidMoveException e) {
            System.out.println("  occupied cell: " + e.getMessage());
        }

        Game finished = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));
        playQuietly(finished, new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0, 2}});
        try {
            finished.makeMove(2, 2);
        } catch (InvalidMoveException e) {
            System.out.println("  move after game over: " + e.getMessage());
        }

        Game fresh = new Game(3, List.of(ALICE, BOB), new CountBasedWinningStrategy(3));
        try {
            fresh.undoLastMove();
        } catch (InvalidMoveException e) {
            System.out.println("  undo with no moves: " + e.getMessage());
        }

        try {
            new Game(2, List.of(ALICE, BOB), new CountBasedWinningStrategy(2));
        } catch (IllegalArgumentException e) {
            System.out.println("  board too small: " + e.getMessage());
        }
        try {
            new Game(3, List.of(ALICE), new CountBasedWinningStrategy(3));
        } catch (IllegalArgumentException e) {
            System.out.println("  single player: " + e.getMessage());
        }
        try {
            new Game(3, List.of(ALICE, new Player("Copycat", Symbol.X)),
                    new CountBasedWinningStrategy(3));
        } catch (IllegalArgumentException e) {
            System.out.println("  duplicate symbols: " + e.getMessage());
        }
        try {
            new Player("Cheater", Symbol.EMPTY);
        } catch (IllegalArgumentException e) {
            System.out.println("  EMPTY as a player symbol: " + e.getMessage());
        }
        System.out.println();
    }

    /** Both strategies must agree on the same game - cheap correctness check. */
    private static void strategiesAgree() {
        System.out.println("=== 9. both strategies produce the same result ===");
        int[][] moves = {{0, 0}, {0, 1}, {1, 1}, {0, 2}, {2, 2}};

        WinningStrategy counter = new CountBasedWinningStrategy(3);
        WinningStrategy scan = new ScanWinningStrategy();

        Game g1 = new Game(3, List.of(ALICE, BOB), counter);
        Game g2 = new Game(3, List.of(ALICE, BOB), scan);
        for (int[] m : moves) {
            g1.makeMove(m[0], m[1]);
            g2.makeMove(m[0], m[1]);
        }
        System.out.println("  " + counter.name() + " -> " + g1.resultText());
        System.out.println("  " + scan.name() + " -> " + g2.resultText());
        boolean same = g1.getStatus() == g2.getStatus()
                && g1.getWinner().getName().equals(g2.getWinner().getName());
        System.out.println("  " + (same ? "PASS - same outcome" : "FAIL - strategies disagree"));
    }

    private static void play(Game game, int[][] moves) {
        for (int[] move : moves) {
            Player player = game.currentPlayer();
            GameStatus status = game.makeMove(move[0], move[1]);
            System.out.println("  " + player + " -> (" + move[0] + "," + move[1] + ")"
                    + (status == GameStatus.IN_PROGRESS ? "" : "   [" + status + "]"));
        }
    }

    /** Same as play() but without the per move log, for setting up a fixture. */
    private static void playQuietly(Game game, int[][] moves) {
        for (int[] move : moves) {
            game.makeMove(move[0], move[1]);
        }
    }

    private static void finish(Game game) {
        game.getBoard().print();
        System.out.println("  result: " + game.resultText() + "\n");
    }
}
