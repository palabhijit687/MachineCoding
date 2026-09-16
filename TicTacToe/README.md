# Tic Tac Toe

Warm-up LLD question. Easy to get working, easy to get marked down on - the grading is
about whether the board, the rules and the turn order are separate things.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out tictactoe.Main
```

## Clarifying questions I would ask first

1. **Board size** - always 3x3 or configurable NxN? *(assumed: configurable, min 3)*
2. **Win condition** - N in a row on an NxN board, or always 3? *(assumed: N in a row, so the whole row/column/diagonal)*
3. **Number of players** - always 2? *(assumed: 2 or more, turn order rotates. Third symbol Z included)*
4. **Input** - interactive console or scripted? *(assumed: the `Game` API is what matters; the demo scripts the moves so the run is deterministic. A `Scanner` loop would call the same methods)*
5. **Undo** needed? *(assumed: yes, it is the usual follow-up)*
6. **Computer opponent / minimax** in scope? *(assumed: no, mentioned as an extension)*
7. **What happens on an illegal move** - reject and re-ask, or lose the turn? *(assumed: reject with an exception, the turn does not advance)*

## Approach

```
model/     Symbol (enum), Player, Move, Board, GameStatus
strategy/  WinningStrategy (interface) -> ScanWinningStrategy | CountBasedWinningStrategy
service/   Game  (turn order, validation, status)
```

Key decisions and why:

- **Board holds state only.** It validates coordinates and tracks how many cells are
  filled. It does not know what "winning" means, so changing the win rule never touches it.
- **Win check behind a `WinningStrategy` interface,** with two implementations:
  - `ScanWinningStrategy` - after a move, scan just that row, that column, and the
    diagonals if the move is on one. O(n) per move, stateless, trivially correct.
  - `CountBasedWinningStrategy` - keep a running count per symbol for each row, each
    column, the diagonal and the anti-diagonal. A counter hitting N means a win.
    **O(1) per move.** The tradeoff is that it is stateful: one instance per game, and
    `isWinningMove` must be called exactly once per move.
  Scenario 9 in the demo runs the same game through both and asserts they agree.
- **`filledCells` counter for the draw check** instead of scanning the board every turn.
- **Turn order is an `ArrayDeque`**: poll the front, push to the back. Two players or
  five, the code is identical - no `currentPlayerIndex` arithmetic to get wrong.
- **`Symbol` is an enum with an `EMPTY` member.** No nulls on the board, and a player
  cannot be constructed with `EMPTY`.
- **Undo works with the O(1) strategy** because `WinningStrategy` has an `undoMove` hook
  that rolls the counters back. A stateless strategy just ignores it (default method).
  Undo also rewinds the turn order and clears a `WIN` status.
- **`Move` history kept as a list** - powers undo, a replay log, and printing the game.

## Edge cases handled (see the demo output)

- Row, column, diagonal and anti-diagonal wins
- Full board with no winner -> `DRAW`
- 4x4 board where a win needs 4 in a line
- Three players sharing one board, turn order rotating correctly
- Undo a *winning* move: status returns to `IN_PROGRESS`, the same player replays, and
  the opponent can then block the cell
- Move outside the board / negative index -> `InvalidMoveException`
- Move on an occupied cell -> `InvalidMoveException`, turn does not advance
- Move after the game is over -> rejected
- Undo with no moves played -> rejected
- Board smaller than 3, fewer than 2 players, two players with the same symbol,
  `EMPTY` as a player symbol -> all rejected at construction

## Extensions I would mention if time was left

- **Computer player** behind a `PlayerStrategy` interface: random, then "block or win",
  then minimax with alpha-beta for 3x3.
- **K-in-a-row on an NxN board** (like Connect-N). The counter strategy needs
  per-direction run lengths from the last move instead of whole-line counts.
- **Console loop** with a `Scanner`, re-prompting on an invalid move, plus `undo` and
  `quit` commands. The `Game` API already supports all of it.
- **Persistence / resume**: the move list is enough to rebuild any game state.
- **Multiple concurrent games**: a `GameManager` keyed by game id. Each `Game` is
  single-threaded by nature, so a lock per game is enough.
