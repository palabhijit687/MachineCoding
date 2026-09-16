# Snake and Ladder

Simulation question. The code is easy; the marks are in how many of the fuzzy rules you
notice and ask about, and whether the board validates itself.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out snakeandladder.Main
```

## Clarifying questions I would ask first

1. **Board size** - always 100, or configurable? *(assumed: configurable, min 10 cells)*
2. **Do players start on cell 1 or off the board?** *(assumed: position 0, they enter with the first roll)*
3. **Exact finish?** Does rolling past the last cell win, stay put, or bounce back? *(assumed: must land exactly, an overshoot means no move. Made it a config flag)*
4. **Does rolling a 6 give another turn?** *(assumed: no by default, config flag to enable)*
5. **One dice or several?** *(assumed: configurable dice count and sides)*
6. **Can a ladder land you on a snake head?** *(assumed: yes, jumps chain until you land on a plain cell)*
7. **Game ends at the first winner, or does everyone finish for a ranking?** *(assumed: first winner by default, config flag for full ranking)*
8. **Board validity** - two snakes from the same cell? a snake on the last cell? *(assumed: all rejected)*
9. **Number of players** *(assumed: 2 or more)*

## Approach

```
model/    Jump (+JumpType), Board, Player, GameConfig, TurnResult
dice/     Dice (interface) -> RandomDice | FixedSequenceDice
service/  Game  (turn loop, jump chaining, ranking)
```

Key decisions and why:

- **One `Jump` class, not `Snake` + `Ladder`.** The only difference is direction, so the
  type is derived from `end > start`. Two classes would duplicate every rule. Static
  factories `Jump.snake(head, tail)` and `Jump.ladder(bottom, top)` keep the call site
  readable *and* validate the direction.
- **Jumps in a `Map<startCell, Jump>`.** Resolving a landing is O(1). Keeping two lists
  and scanning them per turn is the version I would not write.
- **Board validates itself at construction**: nothing starts on cell 1, nothing starts on
  or ends on the winning cell, no two jumps from the same cell, nothing outside 1..N.
  The game loop then has no defensive checks in it.
- **`Dice` behind an interface.** `FixedSequenceDice` makes the demo deterministic, so I
  can show a ladder, a snake bite, an overshoot and a win in exactly 8 turns.
  `RandomDice` takes a seed for the same reason. Multi-dice support lives here, and
  `maxValue()` is what the "extra turn on max roll" rule reads.
- **All rule variations in `GameConfig`** (exact finish, extra turn, play-until-all-finish,
  max turns) rather than branching inside the loop or subclassing `Game`.
- **Jump chaining is a loop with a visited set** and a hop cap, so a ladder landing on a
  snake resolves correctly and a pathological board cannot hang the game.
- **Turn order is an `ArrayDeque`**: rotate to the back normally, push to the front for an
  extra turn, remove entirely when a player finishes in ranking mode.
- **`playTurn()` returns a `TurnResult`** (roll, from, to, jumps taken, flags) instead of
  printing. The service stays testable and `Main` owns the formatting.
- **`maxTurns` safety valve** so a bad board or rule combination ends instead of spinning.

## Edge cases handled (see the demo output)

- Ladder climb, snake bite, and a **chained jump** (5→8 by ladder, immediately 8→2 by snake)
- Overshooting the last cell with exact-finish on -> player does not move at all
- Overshooting with exact-finish off -> clamped to the last cell, counts as a win
- Extra turn on a max roll - the same player keeps the dice
- Three players with a full ranking, including the last player getting their rank
  automatically once everyone else has finished
- Playing a turn after the game is over -> `IllegalStateException`
- Board smaller than 10 cells
- Jump starting on cell 1, starting on the winning cell, or ending on the winning cell
- Two jumps starting from the same cell
- Jump pointing outside the board
- `Jump.snake` pointing up, `Jump.ladder` pointing down, a jump to the same cell
- Fewer than 2 players, duplicate player names, blank player name
- Dice with fewer than 2 sides, empty scripted sequence

## Extensions I would mention if time was left

- **Bonus/penalty cells** (skip a turn, swap positions, teleport) - the `applyJumps` step
  generalises into a `CellEffect` interface applied after landing.
- **Random board generation** with a guarantee that the game terminates.
- **Interactive console** or a turn-by-turn API for a UI; `TurnResult` is already the
  event payload a UI would render.
- **Persistence/replay:** the dice sequence plus the player order fully determines a game,
  so storing the rolls is enough to replay it.
- **Statistics:** run 10k seeded games to measure average turns per board, which is a
  neat way to compare board designs.
