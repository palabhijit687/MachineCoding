package splitwise;

import splitwise.exception.InvalidSplitException;
import splitwise.exception.UserNotFoundException;
import splitwise.model.Expense;
import splitwise.model.Money;
import splitwise.model.Split;
import splitwise.model.SplitType;
import splitwise.model.Transaction;
import splitwise.service.SplitwiseService;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        SplitwiseService app = setup();
        equalSplit(app);
        unevenEqualSplit(app);
        exactSplit(app);
        percentSplit(app);
        statements(app);
        settlements(app);
        simplifyDebts();
        validation(app);
    }

    private static SplitwiseService setup() {
        System.out.println("=== 1. users and group ===");
        SplitwiseService app = new SplitwiseService();
        app.addUser("u1", "Abhijeet", "abhijeet@mail.com");
        app.addUser("u2", "Bhavna", "bhavna@mail.com");
        app.addUser("u3", "Chetan", "chetan@mail.com");
        app.addUser("u4", "Divya", "divya@mail.com");

        app.createGroup("g1", "Goa Trip", List.of("u1", "u2", "u3", "u4"));
        System.out.println("  group Goa Trip with 4 members: Abhijeet, Bhavna, Chetan, Divya\n");
        return app;
    }

    private static void equalSplit(SplitwiseService app) {
        System.out.println("=== 2. equal split (clean division) ===");
        Expense expense = app.addEqualExpense("Hotel", 1200, "u1",
                List.of("u1", "u2", "u3", "u4"), "g1");
        print(app, expense);
        System.out.println();
    }

    private static void unevenEqualSplit(SplitwiseService app) {
        System.out.println("=== 3. equal split that does not divide evenly ===");
        Expense expense = app.addEqualExpense("Auto ride", 100, "u2",
                List.of("u2", "u3", "u4"), "g1");
        print(app, expense);
        double sum = expense.getSplits().stream().mapToDouble(Split::getAmount).sum();
        System.out.println("  splits add up to " + Money.format(sum)
                + " -> " + (Money.toCents(sum) == Money.toCents(expense.getAmount())
                ? "PASS, no paisa lost" : "FAIL"));
        System.out.println();
    }

    private static void exactSplit(SplitwiseService app) {
        System.out.println("=== 4. exact split (Divya ate more) ===");
        Expense expense = app.addExpense("Dinner", 1000, "u3",
                List.of("u1", "u2", "u3", "u4"), SplitType.EXACT,
                List.of(200.0, 200.0, 200.0, 400.0), "g1");
        print(app, expense);
        System.out.println();
    }

    private static void percentSplit(SplitwiseService app) {
        System.out.println("=== 5. percent split ===");
        Expense rent = app.addExpense("Cab for the week", 5000, "u4",
                List.of("u1", "u2", "u4"), SplitType.PERCENT,
                List.of(50.0, 30.0, 20.0), "g1");
        print(app, rent);

        System.out.println("  a percent split that needs rounding (100 at 33.33/33.33/33.34):");
        Expense snacks = app.addExpense("Snacks", 100, "u1",
                List.of("u1", "u2", "u3"), SplitType.PERCENT,
                List.of(33.33, 33.33, 33.34), "g1");
        print(app, snacks);
        double sum = snacks.getSplits().stream().mapToDouble(Split::getAmount).sum();
        System.out.println("  splits add up to " + Money.format(sum)
                + " -> " + (Money.toCents(sum) == Money.toCents(snacks.getAmount())
                ? "PASS" : "FAIL"));
        System.out.println();
    }

    private static void statements(SplitwiseService app) {
        System.out.println("=== 6. balances ===");
        for (String userId : List.of("u1", "u2", "u3", "u4")) {
            System.out.println("  " + app.nameOf(userId)
                    + "  (net " + Money.format(app.netBalance(userId)) + ")");
            app.statementFor(userId).forEach(line -> System.out.println("      " + line));
        }
        System.out.println("  all outstanding dues (" + app.allDues().size() + " transfers):");
        app.allDues().forEach(t -> System.out.println("      " + readable(app, t)));
        System.out.println("  total paid by Abhijeet: " + Money.format(app.totalPaidBy("u1")));
        System.out.println("  expenses Chetan is part of: " + app.expensesFor("u3").size());
        System.out.println();
    }

    private static void settlements(SplitwiseService app) {
        System.out.println("=== 7. settling up ===");
        double owed = 0;
        for (Transaction due : app.allDues()) {
            if (due.getFromUserId().equals("u2") && due.getToUserId().equals("u1")) {
                owed = due.getAmount();
            }
        }
        System.out.println("  Bhavna owes Abhijeet " + Money.format(owed));

        Transaction partial = app.settle("u2", "u1", 100);
        System.out.println("  partial payment: " + readable(app, partial));
        System.out.println("  now: " + app.statementFor("u2").get(0));

        Transaction full = app.settleFully("u2", "u1");
        System.out.println("  clears the rest: " + readable(app, full));
        System.out.println("  Bhavna vs Abhijeet now:");
        app.statementFor("u2").forEach(line -> System.out.println("      " + line));
        System.out.println("  settlements recorded: " + app.getSettlements().size() + "\n");
    }

    /** Fresh service so the numbers are easy to follow. */
    private static void simplifyDebts() {
        System.out.println("=== 8. simplify debts (minimum cash flow) ===");
        SplitwiseService app = new SplitwiseService();
        app.addUser("a", "Amit", "a@mail.com");
        app.addUser("b", "Bhavna", "b@mail.com");
        app.addUser("c", "Chetan", "c@mail.com");
        app.addUser("d", "Divya", "d@mail.com");

        // a chain of expenses that creates a circular set of debts
        app.addEqualExpense("Breakfast", 300, "a", List.of("a", "b", "c"), null);
        app.addEqualExpense("Lunch", 300, "b", List.of("b", "c", "d"), null);
        app.addEqualExpense("Coffee", 200, "c", List.of("c", "d", "a"), null);
        app.addEqualExpense("Dinner", 400, "d", List.of("a", "b", "c", "d"), null);

        System.out.println("  raw dues (" + app.allDues().size() + " transfers):");
        app.allDues().forEach(t -> System.out.println("      " + readable(app, t)));

        System.out.println("  net position per person:");
        for (String userId : List.of("a", "b", "c", "d")) {
            double net = app.netBalance(userId);
            String label = net > 0 ? "should receive" : (net < 0 ? "should pay" : "is settled");
            System.out.println("      " + app.nameOf(userId) + " " + label + " "
                    + Money.format(Math.abs(net)));
        }

        List<Transaction> simplified = app.simplifiedSettlements();
        System.out.println("  simplified (" + simplified.size() + " transfers):");
        simplified.forEach(t -> System.out.println("      " + readable(app, t)));

        double totalMoved = simplified.stream().mapToDouble(Transaction::getAmount).sum();
        System.out.println("  cash moved: " + Money.format(totalMoved)
                + " across " + simplified.size() + " transfers instead of "
                + app.allDues().size() + "\n");
    }

    private static void validation(SplitwiseService app) {
        System.out.println("=== 9. validation ===");

        expect("exact split not adding up", () -> app.addExpense("Bad exact", 1000, "u1",
                List.of("u1", "u2"), SplitType.EXACT, List.of(400.0, 400.0), "g1"));

        expect("percentages not adding to 100", () -> app.addExpense("Bad percent", 1000, "u1",
                List.of("u1", "u2"), SplitType.PERCENT, List.of(40.0, 40.0), "g1"));

        expect("wrong number of values", () -> app.addExpense("Bad count", 1000, "u1",
                List.of("u1", "u2", "u3"), SplitType.EXACT, List.of(500.0, 500.0), "g1"));

        expect("negative split amount", () -> app.addExpense("Negative", 1000, "u1",
                List.of("u1", "u2"), SplitType.EXACT, List.of(1100.0, -100.0), "g1"));

        expect("zero expense amount", () -> app.addEqualExpense("Free", 0, "u1",
                List.of("u1", "u2"), "g1"));

        expect("blank description", () -> app.addEqualExpense("  ", 100, "u1",
                List.of("u1", "u2"), "g1"));

        expect("duplicate participant", () -> app.addEqualExpense("Dup", 100, "u1",
                List.of("u1", "u1", "u2"), "g1"));

        expect("no participants", () -> app.addEqualExpense("Nobody", 100, "u1",
                List.of(), "g1"));

        expect("unknown user", () -> app.addEqualExpense("Ghost", 100, "u1",
                List.of("u1", "u9"), "g1"));

        expect("unknown group", () -> app.addEqualExpense("No group", 100, "u1",
                List.of("u1", "u2"), "g9"));

        // Divya is in g1, so build a group without her to show the member check
        app.createGroup("g2", "Office Lunch", List.of("u1", "u2"));
        expect("participant not in the group", () -> app.addEqualExpense("Outsider", 100, "u1",
                List.of("u1", "u3"), "g2"));

        expect("settle with yourself", () -> app.settle("u1", "u1", 50));
        expect("settle a negative amount", () -> app.settle("u3", "u1", -50));
        expect("settle when nothing is owed", () -> app.settle("u1", "u3", 50));
        expect("settle more than owed", () -> app.settle("u3", "u1", 999999));
        expect("duplicate user id", () -> app.addUser("u1", "Clone", "clone@mail.com"));
        expect("duplicate group id", () -> app.createGroup("g1", "Copy", List.of("u1")));
    }

    private static void print(SplitwiseService app, Expense expense) {
        System.out.println("  " + expense.getDescription() + " " + Money.format(expense.getAmount())
                + " paid by " + app.nameOf(expense.getPaidByUserId())
                + " [" + expense.getSplitType() + "]");
        for (Split split : expense.getSplits()) {
            System.out.println("      " + app.nameOf(split.getUserId())
                    + " owes share " + Money.format(split.getAmount())
                    + (split.getUserId().equals(expense.getPaidByUserId()) ? "  (is the payer)" : ""));
        }
    }

    private static String readable(SplitwiseService app, Transaction t) {
        return app.nameOf(t.getFromUserId()) + " pays " + app.nameOf(t.getToUserId())
                + " " + Money.format(t.getAmount());
    }

    private static void expect(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": NOT rejected (bug)");
        } catch (InvalidSplitException | UserNotFoundException | IllegalArgumentException e) {
            System.out.println("  " + label + ": " + e.getMessage());
        }
    }
}
