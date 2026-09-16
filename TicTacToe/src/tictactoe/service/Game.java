package tictactoe.service;

import tictactoe.exception.InvalidMoveException;
import tictactoe.model.Board;
import tictactoe.model.GameStatus;
import tictactoe.model.Move;
import tictactoe.model.Player;
import tictactoe.strategy.WinningStrategy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Drives one game: whose turn it is, whether a move is legal, and when the game
 * ends. Supports more than two players since that is a common follow-up - the turn
 * order is just a rotating queue.
 */
public class Game {

    private final Board board;
    private final Deque<Player> turnOrder = new ArrayDeque<>();
    private final List<Move> moveHistory = new ArrayList<>();
    private final WinningStrategy winningStrategy;

    private GameStatus status = GameStatus.IN_PROGRESS;
    private Player winner;

    public Game(int boardSize, List<Player> players, WinningStrategy winningStrategy) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("at least 2 players are required");
        }
        long distinctSymbols = players.stream().map(Player::getSymbol).distinct().count();
        if (distinctSymbols != players.size()) {
            throw new IllegalArgumentException("every player needs a distinct symbol");
        }
        this.board = new Board(boardSize);
        this.turnOrder.addAll(players);
        this.winningStrategy = winningStrategy;
    }

    public Player currentPlayer() {
        return turnOrder.peekFirst();
    }

    public GameStatus getStatus() {
        return status;
    }

    public Player getWinner() {
        return winner;
    }

    public Board getBoard() {
        return board;
    }

    public List<Move> getMoveHistory() {
        return List.copyOf(moveHistory);
    }

    /** Plays a move for whoever's turn it is. */
    public GameStatus makeMove(int row, int col) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new InvalidMoveException("game is already over (" + status + ")");
        }
        if (!board.isInside(row, col)) {
            throw new InvalidMoveException("(" + row + "," + col + ") is outside the board");
        }
        if (!board.isFree(row, col)) {
            throw new InvalidMoveException("(" + row + "," + col + ") is already taken by "
                    + board.get(row, col));
        }

        Player player = turnOrder.pollFirst();
        Move move = new Move(player, row, col);
        board.place(row, col, player.getSymbol());
        moveHistory.add(move);

        if (winningStrategy.isWinningMove(board, move)) {
            status = GameStatus.WIN;
            winner = player;
            turnOrder.addFirst(player); // keep the winner as "current" for display
            return status;
        }
        if (board.isFull()) {
            status = GameStatus.DRAW;
            turnOrder.addLast(player);
            return status;
        }
        turnOrder.addLast(player); // back of the queue, next player's turn
        return status;
    }

    /** Undo the last move. Also rewinds the turn order and the game status. */
    public Move undoLastMove() {
        if (moveHistory.isEmpty()) {
            throw new InvalidMoveException("nothing to undo");
        }
        Move last = moveHistory.remove(moveHistory.size() - 1);
        board.clear(last.getRow(), last.getCol());
        winningStrategy.undoMove(board, last);

        // the player who made that move must play again
        if (status == GameStatus.WIN) {
            turnOrder.pollFirst();           // winner was pushed to the front
        } else {
            turnOrder.pollLast();            // they were rotated to the back
        }
        turnOrder.addFirst(last.getPlayer());

        status = GameStatus.IN_PROGRESS;
        winner = null;
        return last;
    }

    public String resultText() {
        return switch (status) {
            case IN_PROGRESS -> "in progress, next turn: " + currentPlayer();
            case WIN -> winner + " wins in " + moveHistory.size() + " moves";
            case DRAW -> "draw after " + moveHistory.size() + " moves";
        };
    }
}
