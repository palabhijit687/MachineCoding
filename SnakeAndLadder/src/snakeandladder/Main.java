package snakeandladder;

import snakeandladder.dice.Dice;
import snakeandladder.dice.FixedSequenceDice;
import snakeandladder.dice.RandomDice;
import snakeandladder.exception.InvalidBoardException;
import snakeandladder.model.Board;
import snakeandladder.model.GameConfig;
import snakeandladder.model.Jump;
import snakeandladder.model.Player;
import snakeandladder.model.TurnResult;
import snakeandladder.service.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * The dice is scripted for most scenarios so every rule can be demonstrated
 * exactly. The last two use a seeded random dice - still reproducible.
 */
public class Main {

    public static void main(String[] args) {
        smallBoard();
        scriptedGame();
        extraTurnRule();
        chainedJump();
        nonExactFinish();
        rankingWithThreePlayers();
        fullRandomGame();
        invalidBoards();
    }

    /** 30 cell board used by the scripted scenarios. */
    private static Board smallBoardInstance() {
        return new Board(30, List.of(
                Jump.ladder(3, 22),
                Jump.ladder(5, 8),
                Jump.ladder(11, 26),
                Jump.snake(17, 4),
                Jump.snake(19, 7),
                Jump.snake(21, 9),
                Jump.snake(27, 1)));
    }

    private static void smallBoard() {
        System.out.println("=== 1. the board ===");
        smallBoardInstance().printSummary();
        System.out.println();
    }

    private static void scriptedGame() {
        System.out.println("=== 2. scripted game: ladder, snake, overshoot, win ===");
        Board board = smallBoardInstance();
        Dice dice = new FixedSequenceDice(3, 5, 5, 3, 6, 6, 4, 4);
        Game game = new Game(board, dice,
                List.of(new Player("Amit"), new Player("Bhavna")), GameConfig.defaults());

        for (TurnResult turn : game.play()) {
            System.out.println("  " + turn);
        }
        System.out.println("  winner: " + game.getWinner().getName()
                + " in " + game.getTurnCount() + " turns\n");
    }

    private static void extraTurnRule() {
        System.out.println("=== 3. house rule: max roll gives another turn ===");
        Game game = new Game(smallBoardInstance(),
                new FixedSequenceDice(6, 6, 2, 4),
                List.of(new Player("Amit"), new Player("Bhavna")),
                GameConfig.defaults().extraTurnOnMaxRoll(true));

        for (int i = 0; i < 4; i++) {
            System.out.println("  " + game.playTurn());
        }
        System.out.println("  -> Amit rolled 6 twice so he kept the dice, then Bhavna got a turn\n");
    }

    private static void chainedJump() {
        System.out.println("=== 4. chained jump: ladder lands on a snake head ===");
        Board board = new Board(20, List.of(Jump.ladder(5, 8), Jump.snake(8, 2)));
        board.printSummary();
        Game game = new Game(board, new FixedSequenceDice(5, 1),
                List.of(new Player("Amit"), new Player("Bhavna")), GameConfig.defaults());
        System.out.println("  " + game.playTurn());
        System.out.println("  -> climbed 5->8, immediately bitten 8->2\n");
    }

    private static void nonExactFinish() {
        System.out.println("=== 5. house rule: overshoot still wins (exact finish off) ===");
        Board board = new Board(20, List.of(Jump.ladder(4, 9)));
        Game game = new Game(board, new FixedSequenceDice(18, 5, 18),
                List.of(new Player("Amit"), new Player("Bhavna")),
                GameConfig.defaults().exactFinishRequired(false));
        for (TurnResult turn : game.play()) {
            System.out.println("  " + turn);
        }
        System.out.println("  -> 18 + 18 overshoots 20 but is clamped to the last cell\n");
    }

    private static void rankingWithThreePlayers() {
        System.out.println("=== 6. three players, play on for a full ranking ===");
        Game game = new Game(smallBoardInstance(),
                new RandomDice(1, 6, 42L),
                List.of(new Player("Amit"), new Player("Bhavna"), new Player("Chetan")),
                GameConfig.defaults().playUntilAllFinish(true));
        game.play();
        System.out.println("  finished in " + game.getTurnCount() + " turns");
        System.out.print(game.standings());
        System.out.println();
    }

    private static void fullRandomGame() {
        System.out.println("=== 7. standard 100 cell board, seeded dice ===");
        Board board = new Board(100, standardJumps());
        board.printSummary();
        Game game = new Game(board, new RandomDice(1, 6, 7L),
                List.of(new Player("Amit"), new Player("Bhavna")), GameConfig.defaults());
        List<TurnResult> history = game.play();

        System.out.println("  last 5 turns:");
        history.subList(Math.max(0, history.size() - 5), history.size())
                .forEach(t -> System.out.println("    " + t));
        System.out.println("  winner: " + game.getWinner().getName()
                + " after " + game.getTurnCount() + " turns");

        long jumpsUsed = history.stream().mapToLong(t -> t.getJumpsTaken().size()).sum();
        long overshoots = history.stream().filter(TurnResult::isBlockedByExactFinish).count();
        System.out.println("  jumps taken: " + jumpsUsed + ", blocked overshoots: " + overshoots + "\n");
    }

    private static void invalidBoards() {
        System.out.println("=== 8. bad boards and bad setup ===");

        expectInvalidBoard("board too small", () -> new Board(5, List.of()));
        expectInvalidBoard("jump starting on cell 1",
                () -> new Board(30, List.of(Jump.ladder(1, 10))));
        expectInvalidBoard("jump starting on the last cell",
                () -> new Board(30, List.of(Jump.snake(30, 5))));
        expectInvalidBoard("jump ending on the last cell",
                () -> new Board(30, List.of(Jump.ladder(10, 30))));
        expectInvalidBoard("two jumps from the same cell",
                () -> new Board(30, List.of(Jump.ladder(10, 20), Jump.snake(10, 2))));
        expectInvalidBoard("jump outside the board",
                () -> new Board(30, List.of(Jump.ladder(10, 45))));

        expectIllegalArgument("snake pointing upwards", () -> Jump.snake(5, 15));
        expectIllegalArgument("ladder pointing downwards", () -> Jump.ladder(15, 5));
        expectIllegalArgument("jump to the same cell", () -> new Jump(7, 7));

        Board board = smallBoardInstance();
        Dice dice = new FixedSequenceDice(1);
        expectIllegalArgument("single player",
                () -> new Game(board, dice, List.of(new Player("Solo")), GameConfig.defaults()));
        expectIllegalArgument("duplicate player names",
                () -> new Game(board, dice,
                        List.of(new Player("Amit"), new Player("Amit")), GameConfig.defaults()));
        expectIllegalArgument("blank player name", () -> new Player("  "));
        expectIllegalArgument("dice with one side", () -> new RandomDice(1, 1, 0L));
        expectIllegalArgument("empty dice sequence", FixedSequenceDice::new);

        // playing on after the game is over
        Game finished = new Game(smallBoardInstance(), new FixedSequenceDice(3, 5, 5, 3, 6, 6, 4, 4),
                List.of(new Player("Amit"), new Player("Bhavna")), GameConfig.defaults());
        finished.play();
        try {
            finished.playTurn();
        } catch (IllegalStateException e) {
            System.out.println("  turn after game over: " + e.getMessage());
        }
    }

    private static List<Jump> standardJumps() {
        List<Jump> jumps = new ArrayList<>();
        jumps.add(Jump.ladder(2, 38));
        jumps.add(Jump.ladder(7, 14));
        jumps.add(Jump.ladder(8, 31));
        jumps.add(Jump.ladder(15, 26));
        jumps.add(Jump.ladder(21, 42));
        jumps.add(Jump.ladder(28, 84));
        jumps.add(Jump.ladder(36, 44));
        jumps.add(Jump.ladder(51, 67));
        jumps.add(Jump.ladder(71, 91));
        jumps.add(Jump.ladder(78, 98));
        jumps.add(Jump.snake(16, 6));
        jumps.add(Jump.snake(46, 25));
        jumps.add(Jump.snake(49, 11));
        jumps.add(Jump.snake(62, 19));
        jumps.add(Jump.snake(64, 60));
        jumps.add(Jump.snake(74, 53));
        jumps.add(Jump.snake(89, 68));
        jumps.add(Jump.snake(92, 88));
        jumps.add(Jump.snake(95, 75));
        jumps.add(Jump.snake(99, 80));
        return jumps;
    }

    private static void expectInvalidBoard(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": NOT rejected (bug)");
        } catch (InvalidBoardException e) {
            System.out.println("  " + label + ": " + e.getMessage());
        }
    }

    private static void expectIllegalArgument(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": NOT rejected (bug)");
        } catch (IllegalArgumentException e) {
            System.out.println("  " + label + ": " + e.getMessage());
        }
    }
}
