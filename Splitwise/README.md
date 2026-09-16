# Splitwise

Expense sharing app. The interesting parts are the split strategies, keeping the money
arithmetic exact, and "settle up" (minimum cash flow).

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out splitwise.Main
```

## Clarifying questions I would ask first

1. **Which split types?** Equal, exact amount, percentage, shares? *(assumed: EQUAL, EXACT, PERCENT)*
2. **Groups, or just friend-to-friend?** *(assumed: both - `groupId` is optional on an expense)*
3. **Is the payer always part of the split?** *(assumed: not necessarily - one person can pay for others without taking a share)*
4. **Can multiple people pay for one expense?** *(assumed: single payer. Multi-payer is a listed extension)*
5. **How should balances be shown** - per pair, or one net number per person? *(assumed: both, plus a "simplify debts" view)*
6. **Settlements** - full only or partial too? *(assumed: partial allowed, cannot exceed what is owed)*
7. **Currency** - single or multi? *(assumed: single. Multi-currency needs an FX rate on each expense)*
8. **What about rounding** when an amount does not divide evenly? *(assumed: nothing may be lost - the leftover paise are assigned to the first participants)*
9. **Editing/deleting an expense** in scope? *(assumed: no, mentioned as an extension since it needs balance reversal)*

## Approach

```
model/    User, Group, Expense, Split, SplitType, Transaction, Money
split/    SplitStrategy (interface) -> Equal | Exact | Percent, + factory
service/  SplitwiseService (facade), BalanceSheet (who owes whom), DebtSimplifier
```

Key decisions and why:

- **All money maths in paise (`long`), not doubles.** `Money.toCents/fromCents` wrap it.
  Splitting 100 three ways as doubles gives 33.33 × 3 = 99.99 and a paisa vanishes.
  `EqualSplitStrategy` divides the cents and hands the remainder to the first few
  participants: **33.34, 33.33, 33.33**. The demo asserts the parts add back to the total.
- **`PercentSplitStrategy` absorbs the rounding residual on the largest share,** so a
  33.33/33.33/33.34 split of 100 still reconciles exactly.
- **One strategy per split type behind `SplitStrategy`.** Each one owns its own validation
  (exact amounts must sum to the total, percentages must sum to 100). Adding a SHARES
  split ("Divya counts as 2") is a new class and one enum value.
- **`BalanceSheet` stores a nested map `balances[a][b]`**, positive meaning *a owes b*, and
  **always writes both directions** in `addDebt`. That removes the classic bug where the
  answer depends on which way round you look it up. Reading a pair is O(1).
- **Settlement is just a negative debt** (`addDebt(from, to, -amount)`), so partial paybacks
  need no special case.
- **`DebtSimplifier` implements minimum cash flow** with two heaps: repeatedly match the
  largest creditor with the largest debtor and settle the smaller side. Each round zeroes
  out at least one person, so n people need at most n-1 transfers. In the demo it turns
  **5 raw dues into 2 transfers**. I would say out loud that greedy is not provably optimal
  in every case (the exact problem is NP-hard) - it is what the real product does too.
- **Net balance derived, not stored.** One source of truth, so a per-pair view and a
  per-person view can never disagree.
- **Service is the only validation point** - unknown users, non-members, duplicates and
  bad amounts are rejected before anything touches the balance sheet.

## Edge cases handled (see the demo output)

- Equal split that does not divide evenly -> no paisa lost, verified in the output
- Percent split needing a rounding fix -> still sums to the total exactly
- Payer's own share correctly excluded from what they are owed
- Exact amounts that do not add up to the total -> rejected with both numbers in the message
- Percentages that do not add up to 100 -> rejected
- Wrong number of values for the participant count -> rejected
- Negative split amount, zero/negative expense amount, blank description -> rejected
- Duplicate participant in one expense, empty participant list -> rejected
- Unknown user, unknown group -> rejected
- Participant who is not a member of the group the expense is filed under -> rejected
- Partial settlement, then settling the remainder; pair disappears from the statement
- Settling with yourself, a negative settlement, settling when nothing is owed, and
  settling more than owed -> all rejected
- Duplicate user id / group id -> rejected
- Circular debts (A→B→C→A) collapse correctly in the simplified view

## Extensions I would mention if time was left

- **Edit/delete an expense:** store the deltas each expense applied so they can be reversed,
  or rebuild the balance sheet by replaying the expense log (event sourcing). Replay is
  simpler to reason about and gives an audit trail for free.
- **Multi-payer expenses:** `paidBy` becomes a list of contributions; the split logic is
  unchanged, only the debt attribution loop changes.
- **Per-group ledgers.** Right now one global balance sheet is kept and simplification is
  global. Real Splitwise simplifies per group, which means a `BalanceSheet` per group plus
  one for non-group expenses.
- **Multi-currency:** store the currency and an FX rate on the expense, and keep balances
  per currency; never sum different currencies into one net number.
- **Notifications and reminders** on new expenses via an observer on `addExpense`.
- **Persistence and concurrency:** users, groups and expenses behind repositories; per-pair
  row locking (or per-group) so two simultaneous expenses cannot corrupt a balance.
