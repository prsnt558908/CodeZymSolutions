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

    // Registered users & net balances (in cents)
    private final Set<String> users = new HashSet<>();
    private final Map<String, Long> netCents = new HashMap<>();

    // To ignore duplicate expense ids if ever repeated
    private final Set<Integer> seenExpenseIds = new HashSet<>();

    public SplitBook() { }

    /** Add a user. Idempotent. */
    public void registerUser(String userId, String displayName) {
        if (userId == null) return;
        if (users.add(userId)) {
            netCents.putIfAbsent(userId, 0L);
        }
    }

    /**
     * Record an expense:
     * - members[i] paid paid[i] (integer currency units)
     * - Splits total equally across members with exact-cents distribution.
     * - Ignores if expenseId already used, or invalid inputs, or any user missing.
     */
    public void recordExpense(int expenseId, List<String> members, List<Integer> paid) {
        // Duplicate expense id? Ignore.
        if (!seenExpenseIds.add(expenseId)) return;

        // Basic input checks
        if (members == null || paid == null || members.size() == 0 || members.size() != paid.size()) return;

        final int n = members.size();

        // All members must be registered
        for (String uid : members) {
            if (!users.contains(uid)) return;
        }

        // Convert payments to cents; ensure at least one positive
        long totalCents = 0L;
        boolean anyPositive = false;
        long[] paidCents = new long[n];
        for (int i = 0; i < n; i++) {
            Integer pi = paid.get(i);
            if (pi == null || pi < 0) return; // invalid; ignore expense
            if (pi > 0) anyPositive = true;
            long cents = pi * 100L; // safe (pi ≤ 1e9 -> cents ≤ 1e11)
            paidCents[i] = cents;
            totalCents += cents;
        }
        if (!anyPositive) return; // spec says at least one paid > 0

        // Equal split in cents with deterministic distribution of remainder
        long shareFloor = totalCents / n;
        int remainder = (int) (totalCents % n);

        // Determine which indices get the +1 cent (lexicographic by userId)
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparing(members::get));

        boolean[] plusOne = new boolean[n];
        for (int k = 0; k < remainder; k++) {
            plusOne[idx[k]] = true;
        }

        // Apply deltas to each member's net
        for (int i = 0; i < n; i++) {
            long share = shareFloor + (plusOne[i] ? 1 : 0);
            long delta = paidCents[i] - share; // positive => they are owed, negative => they owe
            String uid = members.get(i);
            netCents.put(uid, netCents.getOrDefault(uid, 0L) + delta);
        }
    }

    /**
     * Return simplified debtor->creditor balances, sorted by debtor id then creditor id.
     * Format: "<debtor-id> owes <creditor-id>: <amount>" with exactly two decimals.
     */
    public List<String> listBalances() {
        // Separate debtors (negative net) and creditors (positive net)
        List<Entry> debtors = new ArrayList<>();
        List<Entry> creditors = new ArrayList<>();
        for (Map.Entry<String, Long> e : netCents.entrySet()) {
            long v = e.getValue();
            if (v == 0L) continue;
            if (v < 0) debtors.add(new Entry(e.getKey(), -v));  // amount owed
            else       creditors.add(new Entry(e.getKey(), v)); // amount to receive
        }

        // Sort lexicographically by userId
        debtors.sort(Comparator.comparing(a -> a.id));
        creditors.sort(Comparator.comparing(a -> a.id));

        // Greedy match: debtor → creditor, ensures already-sorted output
        List<String> out = new ArrayList<>();
        int i = 0, j = 0;
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

    // ---- helpers ----

    private static final class Entry {
        final String id;
        long cents;
        Entry(String id, long cents) { this.id = id; this.cents = cents; }
    }

    private static String formatLine(String debtor, String creditor, long cents) {
        long units = cents / 100;
        long pennies = cents % 100;
        return debtor + " owes " + creditor + ": " + units + "." + (pennies < 10 ? "0" : "") + pennies;
    }
}
