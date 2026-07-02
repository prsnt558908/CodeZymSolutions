# Low Level Design of Splitwise Expense Sharing App in Java

Problem Statement: https://codezym.com/question/12-design-splitwise-expense-sharing-app

In this solution, we design a lightweight Splitwise-like ledger. The core idea is simple: instead of storing every small transaction between users, we maintain one **net balance** for each user. If a user's balance is positive, that user should receive money. If it is negative, that user owes money. Finally, we simplify all balances by matching debtors with creditors using a greedy approach.

This solution does not use a heavy design pattern. It mainly uses a clean ledger-based design with greedy settlement. The important part is to keep the internal state simple and deterministic.

---

## What Are We Building?

We need to support a basic expense-sharing system where:

- Users can be registered.
- An expense can be recorded for a group of users.
- The total expense is split equally among all members.
- The system should show who owes whom.
- Duplicate expenses should be ignored.
- Final balances should be simplified.

For example, if Alice paid 300 for Alice, Bob, and Charlie, then each person's share is 100.

So:

- Alice paid 300 but her share was 100, so Alice should receive 200.
- Bob paid 0 but his share was 100, so Bob owes 100.
- Charlie paid 0 but his share was 100, so Charlie owes 100.

The final output can be:

```text
Bob owes Alice: 100.00
Charlie owes Alice: 100.00
```

---

## Main Idea

The main idea is to maintain a map like this:

```java
Map<String, Long> netCents;
```

Here:

- Key is the `userId`.
- Value is the user's net balance in cents.
- Positive value means the user should receive money.
- Negative value means the user owes money.

We use cents instead of floating-point numbers because money calculations should not depend on `double` precision.

For example:

```text
100.50 rupees = 10050 cents
```

This avoids rounding errors.

---

## Why Store Amounts in Cents?

Money should not be stored using floating-point values like `double`.

For example, calculations like this can sometimes produce precision issues:

```java
0.1 + 0.2
```

So this solution stores everything in integer cents using `long`.

```java
long cents = amount * 100L;
```

This guarantees exact two-decimal formatting while printing balances.

---

## Important Data Structures

### 1. `Set<String> users`

```java
private final Set<String> users = new HashSet<>();
```

This stores all registered users.

We need this because while recording an expense, we must make sure that every member in the expense is already registered.

---

### 2. `Map<String, Long> netCents`

```java
private final Map<String, Long> netCents = new HashMap<>();
```

This is the most important data structure in the solution.

It stores the net balance of each user.

Example:

```text
Alice -> 20000
Bob   -> -10000
Charlie -> -10000
```

This means:

- Alice should receive 200.00
- Bob owes 100.00
- Charlie owes 100.00

---

### 3. `Set<Integer> seenExpenseIds`

```java
private final Set<Integer> seenExpenseIds = new HashSet<>();
```

This is used to ignore duplicate expenses.

If the same `expenseId` is passed again, the system simply ignores it.

This prevents the same expense from being counted multiple times.

---

## How Register User Works

```java
public void registerUser(String userId, String displayName)
```

This method adds a user to the system.

The `displayName` is accepted by the method, but this implementation only uses `userId` for calculations and output.

The method is idempotent, meaning if the same user is registered multiple times, it does not break anything.

```java
if (users.add(userId)) {
    netCents.putIfAbsent(userId, 0L);
}
```

If the user is new, we initialize their balance as `0`.

---

## How Record Expense Works

```java
public void recordExpense(int expenseId, List<String> members, List<Integer> paid)
```

This method records one expense.

The input contains:

- `expenseId`: unique id of the expense
- `members`: users involved in the expense
- `paid`: how much each member paid

For example:

```java
members = ["Alice", "Bob", "Charlie"]
paid = [300, 0, 0]
```

This means Alice paid 300, Bob paid 0, and Charlie paid 0.

---

## Step 1: Ignore Duplicate Expense

```java
if (!seenExpenseIds.add(expenseId)) return;
```

If the expense id already exists, the method returns immediately.

This avoids duplicate counting.

---

## Step 2: Validate Input

```java
if (members == null || paid == null || members.size() == 0 || members.size() != paid.size()) return;
```

This makes sure:

- Member list is not null.
- Paid list is not null.
- There is at least one member.
- Both lists have the same size.

If any condition fails, the expense is ignored.

---

## Step 3: Check All Users Are Registered

```java
for (String uid : members) {
    if (!users.contains(uid)) return;
}
```

Every user in the expense must be registered first.

If even one user is missing, the expense is ignored.

---

## Step 4: Convert Paid Amounts to Cents

```java
long cents = pi * 100L;
```

Each payment is converted into cents.

The solution also checks that:

- Paid amount is not null.
- Paid amount is not negative.
- At least one member paid a positive amount.

```java
if (!anyPositive) return;
```

This prevents invalid zero-value expenses.

---

## Step 5: Split Total Equally

```java
long shareFloor = totalCents / n;
int remainder = (int) (totalCents % n);
```

The total amount is split equally among all members.

But sometimes the amount may not divide perfectly.

For example:

```text
100 cents split among 3 people
```

Each person gets:

```text
33 cents
```

But 1 cent remains.

So this solution distributes the remaining cents deterministically.

---

## Step 6: Deterministic Remainder Distribution

```java
Arrays.sort(idx, Comparator.comparing(members::get));
```

The users are sorted lexicographically by user id.

The first `remainder` users get one extra cent.

This makes the result predictable and consistent.

Without this rule, different runs could assign the extra cent to different users.

---

## Step 7: Update Net Balance

```java
long delta = paidCents[i] - share;
```

For each user:

```text
delta = amount paid - actual share
```

If `delta` is positive, the user should receive money.

If `delta` is negative, the user owes money.

Then we update the user's net balance:

```java
netCents.put(uid, netCents.getOrDefault(uid, 0L) + delta);
```

---

## How List Balances Works

```java
public List<String> listBalances()
```

This method converts net balances into simplified debtor-to-creditor transactions.

It first separates users into two groups:

```java
List<Entry> debtors = new ArrayList<>();
List<Entry> creditors = new ArrayList<>();
```

- Debtors have negative balance.
- Creditors have positive balance.

Then both lists are sorted by user id.

```java
debtors.sort(Comparator.comparing(a -> a.id));
creditors.sort(Comparator.comparing(a -> a.id));
```

After sorting, the solution greedily matches debtors with creditors.

---

## Greedy Settlement

The greedy settlement works like this:

```java
long pay = Math.min(d.cents, c.cents);
```

The debtor pays as much as possible to the current creditor.

Then we reduce both balances:

```java
d.cents -= pay;
c.cents -= pay;
```

If a debtor has paid everything, move to the next debtor.

If a creditor has received everything, move to the next creditor.

This continues until all balances are settled.

---

## Example

Suppose the net balances are:

```text
Alice   -> +200.00
Bob     -> -100.00
Charlie -> -100.00
```

Then:

```text
Bob owes Alice: 100.00
Charlie owes Alice: 100.00
```

Suppose the net balances are:

```text
Alice -> +50.00
Bob   -> +70.00
Ram   -> -120.00
```

Then:

```text
Ram owes Alice: 50.00
Ram owes Bob: 70.00
```

---

## Output Format

The output format is:

```text
<debtor-id> owes <creditor-id>: <amount>
```

Example:

```text
Bob owes Alice: 100.00
```

The amount is always printed with exactly two decimal places.

This is handled by:

```java
private static String formatLine(String debtor, String creditor, long cents)
```

---

## Why This Solution Is Simple and Efficient

This solution is efficient because it does not store all pairwise debts.

Instead, it only stores one net balance per user.

For each expense, we update each member once.

For final balance listing, we separate debtors and creditors and match them greedily.

If there are `U` users and `M` members in an expense:

- Register user: `O(1)` average
- Record expense: `O(M log M)` because members are sorted for remainder distribution
- List balances: `O(U log U)` because debtors and creditors are sorted

---

## Full Java Code

```java
import java.util.*;

/**
 * SplitBook — lightweight Splitwise-like ledger with full internal netting.
 *
 * Implementation notes:
 * - All math is done in integer cents (long) to guarantee 2-decimal precision.
 * - For an expense, total cents is split as evenly as possible:
 *      shareFloor = totalCents / n, remainder = totalCents % n.
 *   The first 'remainder' members (by lexicographic userId) get +1 cent to keep
 *   the sum of assigned shares equal to the total (deterministic tie-breaking).
 * - Net balance per user is maintained: positive => user is owed (creditor),
 *   negative => user owes (debtor).
 * - listBalances() matches debtors to creditors in lexicographic order and
 *   emits "<debtor> owes <creditor>: <amount>" lines already sorted by
 *   debtor id, then creditor id.
 */
public class SplitBook {

    // Registered users.
    // HashSet gives O(1) average lookup while validating expense members.
    private final Set<String> users = new HashSet<>();

    // Net balance of each user in cents.
    // Positive value means the user should receive money.
    // Negative value means the user owes money.
    private final Map<String, Long> netCents = new HashMap<>();

    // Used to ignore duplicate expense ids.
    private final Set<Integer> seenExpenseIds = new HashSet<>();

    public SplitBook() { }

    /**
     * Add a user.
     *
     * This method is idempotent:
     * registering the same user multiple times does not change the result.
     */
    public void registerUser(String userId, String displayName) {
        if (userId == null) return;

        if (users.add(userId)) {
            netCents.putIfAbsent(userId, 0L);
        }
    }

    /**
     * Record an expense.
     *
     * members[i] paid paid[i] amount.
     *
     * The total amount is split equally among all members.
     * The split is done in cents to avoid floating-point precision issues.
     *
     * The method ignores:
     * - duplicate expense ids
     * - invalid input
     * - unregistered users
     * - expenses where nobody paid anything
     */
    public void recordExpense(int expenseId, List<String> members, List<Integer> paid) {
        // Duplicate expense id? Ignore.
        if (!seenExpenseIds.add(expenseId)) return;

        // Basic input checks.
        if (members == null || paid == null || members.size() == 0 || members.size() != paid.size()) return;

        final int n = members.size();

        // All members must be registered.
        for (String uid : members) {
            if (!users.contains(uid)) return;
        }

        // Convert payments to cents and check that at least one user paid a positive amount.
        long totalCents = 0L;
        boolean anyPositive = false;
        long[] paidCents = new long[n];

        for (int i = 0; i < n; i++) {
            Integer pi = paid.get(i);

            // Invalid amount. Ignore this expense.
            if (pi == null || pi < 0) return;

            if (pi > 0) anyPositive = true;

            // Convert currency units to cents.
            long cents = pi * 100L;

            paidCents[i] = cents;
            totalCents += cents;
        }

        // At least one member must have paid something.
        if (!anyPositive) return;

        // Equal split in cents.
        long shareFloor = totalCents / n;
        int remainder = (int) (totalCents % n);

        // Determine which members get the extra 1 cent.
        // Sorting by user id makes the behavior deterministic.
        Integer[] idx = new Integer[n];

        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }

        Arrays.sort(idx, Comparator.comparing(members::get));

        boolean[] plusOne = new boolean[n];

        for (int k = 0; k < remainder; k++) {
            plusOne[idx[k]] = true;
        }

        // Apply each user's net balance change.
        for (int i = 0; i < n; i++) {
            long share = shareFloor + (plusOne[i] ? 1 : 0);

            // Positive delta means this user should receive money.
            // Negative delta means this user owes money.
            long delta = paidCents[i] - share;

            String uid = members.get(i);

            netCents.put(uid, netCents.getOrDefault(uid, 0L) + delta);
        }
    }

    /**
     * Return simplified debtor-to-creditor balances.
     *
     * Output format:
     * "<debtor-id> owes <creditor-id>: <amount>"
     *
     * Amount is printed with exactly two decimal places.
     */
    public List<String> listBalances() {
        // Separate users into debtors and creditors.
        List<Entry> debtors = new ArrayList<>();
        List<Entry> creditors = new ArrayList<>();

        for (Map.Entry<String, Long> e : netCents.entrySet()) {
            long v = e.getValue();

            if (v == 0L) continue;

            if (v < 0) {
                // Convert negative balance into positive amount owed.
                debtors.add(new Entry(e.getKey(), -v));
            } else {
                // Positive balance means this user should receive money.
                creditors.add(new Entry(e.getKey(), v));
            }
        }

        // Sort lexicographically by userId.
        debtors.sort(Comparator.comparing(a -> a.id));
        creditors.sort(Comparator.comparing(a -> a.id));

        // Greedily match debtors with creditors.
        List<String> out = new ArrayList<>();

        int i = 0;
        int j = 0;

        while (i < debtors.size() && j < creditors.size()) {
            Entry d = debtors.get(i);
            Entry c = creditors.get(j);

            long pay = Math.min(d.cents, c.cents);

            out.add(formatLine(d.id, c.id, pay));

            d.cents -= pay;
            c.cents -= pay;

            if (d.cents == 0) i++;
            if (c.cents == 0) j++;
        }

        return out;
    }

    // Helper class used while settling balances.
    private static final class Entry {
        final String id;
        long cents;

        Entry(String id, long cents) {
            this.id = id;
            this.cents = cents;
        }
    }

    // Format cents as normal currency with exactly two decimal places.
    private static String formatLine(String debtor, String creditor, long cents) {
        long units = cents / 100;
        long pennies = cents % 100;

        return debtor + " owes " + creditor + ": " + units + "." + (pennies < 10 ? "0" : "") + pennies;
    }
}
```

---

## Final Summary

This Splitwise design works by keeping only the final net balance of each user.

Instead of storing many individual debts, it stores:

```text
Who should receive money?
Who owes money?
How much?
```

Then it uses greedy matching to generate simplified transactions.

This keeps the solution clean, deterministic, and easy to explain in a low-level design interview.
