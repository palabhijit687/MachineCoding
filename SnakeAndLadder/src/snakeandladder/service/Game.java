package snakeandladder.service;

import snakeandladder.dice.Dice;
import snakeandladder.model.Board;
import snakeandladder.model.GameConfig;
import snakeandladder.model.Jump;
import snakeandladder.model.Player;
import snakeandladder.model.TurnResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The game loop. Turn order is a queue, and every rule variation comes from
 * GameConfig rather than an if-else buried in here.
 */
public class Game {

    /** Guard against a pathological board where jumps bounce forever. */
    private static final int MAX_JUMP_CHAIN = 20;

    private final Board board;
    private final Dice dice;
    private final GameConfig config;
    private final Deque<Player> turnOrder = new ArrayDeque<>();
    private final List<Player> finishers = new ArrayList<>();
    private final List<TurnResult> history = new ArrayList<>();

    private boolean finished;
    private int turnCount;

    public Game(Board board, Dice dice, List<Player> players, GameConfig config) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("at least 2 players are required");
        }
        long distinctNames = players.stream().map(Player::getName).distinct().count();
        if (distinctNames != players.size()) {
            throw new IllegalArgumentException("player names must be unique");
        }
        this.board = board;
        this.dice = dice;
        this.config = config;
        this.turnOrder.addAll(players);
    }

    public boolean isFinished() {
        return finished;
    }

    public Player getWinner() {
        return finishers.isEmpty() ? null : finishers.get(0);
    }

    public List<Player> getFinishers() {
        return List.copyOf(finishers);
    }

    public List<TurnResult> getHistory() {
        return List.copyOf(history);
    }

    public int getTurnCount() {
        return turnCount;
    }

    /** Plays one turn for whoever is at the front of the queue. */
    public TurnResult playTurn() {
        if (finished) {
            throw new IllegalStateException("game is already finished");
        }
        Player player = turnOrder.pollFirst();
        int roll = dice.roll();
        TurnResult result = new TurnResult(player, roll, player.getPosition());

        int target = player.getPosition() + roll;
        if (target > board.getSize()) {
            if (config.isExactFinishRequired()) {
                // overshoot - the player does not move at all
                result.setBlockedByExactFinish(true);
                target = player.getPosition();
            } else {
                target = board.getSize();
            }
        }

        target = applyJumps(target, result);
        player.setPosition(target);
        result.setTo(target);
        turnCount++;
        history.add(result);

        if (target == board.getSize()) {
            player.setRank(finishers.size() + 1);
            finishers.add(player);
            result.setWon(true);
            if (!config.isPlayUntilAllFinish()) {
                finished = true;
                turnOrder.addFirst(player);   // keep the winner visible as current
                return result;
            }
            // ranking mode: the player leaves the rotation
            if (turnOrder.size() == 1) {
                Player last = turnOrder.pollFirst();
                last.setRank(finishers.size() + 1);
                finishers.add(last);
                finished = true;
            } else if (turnOrder.isEmpty()) {
                finished = true;
            }
            return result;
        }

        if (config.isExtraTurnOnMaxRoll() && roll == dice.maxValue()) {
            result.setExtraTurn(true);
            turnOrder.addFirst(player);       // same player goes again
        } else {
            turnOrder.addLast(player);
        }
        return result;
    }

    /** Runs the whole game and returns every turn that was played. */
    public List<TurnResult> play() {
        while (!finished && turnCount < config.getMaxTurns()) {
            playTurn();
        }
        return getHistory();
    }

    /**
     * A ladder can drop you onto a snake head and vice versa, so jumps are applied
     * repeatedly. The visited set stops an impossible board from looping forever.
     */
    private int applyJumps(int cell, TurnResult result) {
        Set<Integer> visited = new HashSet<>();
        int current = cell;
        int hops = 0;
        while (hops++ < MAX_JUMP_CHAIN && visited.add(current)) {
            Jump jump = board.jumpAt(current);
            if (jump == null) {
                break;
            }
            result.addJump(jump);
            current = jump.getEnd();
        }
        return current;
    }

    public String standings() {
        StringBuilder sb = new StringBuilder();
        for (Player player : finishers) {
            sb.append("    ").append(player.getRank()).append(". ")
                    .append(player.getName()).append("\n");
        }
        for (Player player : turnOrder) {
            if (!player.hasFinished()) {
                sb.append("    -  ").append(player.getName())
                        .append(" (still at ").append(player.getPosition()).append(")\n");
            }
        }
        return sb.toString();
    }
}
