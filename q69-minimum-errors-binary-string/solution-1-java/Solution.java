import java.util.ArrayList;
import java.util.List;

public class MinimumErrorsInBinaryString {

    private static final long MOD = 1_000_000_007L;

    public MinimumErrorsInBinaryString() {

    }

    /**
     * Minimizes:
     *   errors = x * (# of subsequences "01") + y * (# of subsequences "10")
     * where "01"/"10" are subsequences (i<j pairs), after replacing each '!' by '0' or '1'.
     *
     * Key idea:
     * - Each differing pair (i<j) contributes either x (if 0 then 1) or y (if 1 then 0).
     * - If x <= y, it's never beneficial (and often harmful) to have '!' assigned as 1 before a later '!' assigned as 0.
     *   So an optimal assignment exists where all '!' are: 0...0 then 1...1 (a split point).
     * - If x > y, an optimal assignment exists where all '!' are: 1...1 then 0...0 (a split point).
     *
     * We evaluate all split points efficiently by:
     * - Start with a baseline assignment (all '!' set to one value),
     * - Flip '!' one by one from left to right, updating total cost in O(1) per flip,
     * - Track the minimum.
     */
    public int getMinErrors(String errorString, int x, int y) {
        int n = errorString.length();
        if (n == 0) return 0;

        // If x <= y, we prefer "01" over "10", so optimal '!' pattern is 0...0 then 1...1.
        // We'll use baseline '!' = '1' (all ones), then flip prefix '!' from 1 -> 0.
        // If x > y, we prefer "10" over "01", so optimal '!' pattern is 1...1 then 0...0.
        // We'll use baseline '!' = '0' (all zeros), then flip prefix '!' from 0 -> 1.
        boolean prefer01 = (x <= y);
        char bangBase = prefer01 ? '1' : '0'; // baseline replacement for '!'

        // prefixOnes[i] = number of '1' in baseline assignment in positions [0, i)
        int[] prefixOnes = new int[n + 1];
        List<Integer> bangPositions = new ArrayList<>();

        long ones = 0;
        long zeros = 0;
        long cost = 0;

        // Build baseline: replace '!' with bangBase, compute baseline cost in one pass.
        for (int i = 0; i < n; i++) {
            char c = errorString.charAt(i);
            if (c == '!') {
                c = bangBase;
                bangPositions.add(i);
            }

            prefixOnes[i + 1] = prefixOnes[i] + (c == '1' ? 1 : 0);

            if (c == '0') {
                // This '0' forms "10" with all previous ones.
                cost += (long) y * ones;
                zeros++;
            } else { // c == '1'
                // This '1' forms "01" with all previous zeros.
                cost += (long) x * zeros;
                ones++;
            }
        }

        long minCost = cost;
        int k = bangPositions.size();
        long totalOnesBase = ones;

        // Flip '!' positions one by one (in order).
        // Step t (1..k) flips the t-th '!' in the string.
        for (int step = 1; step <= k; step++) {
            int pos = bangPositions.get(step - 1);
            int alreadyFlipped = step - 1; // flips applied before this position

            // Ones before pos in baseline assignment:
            long onesBeforeBase = prefixOnes[pos];

            // After alreadyFlipped flips:
            // - If prefer01: flips are 1->0, so total ones decrease by alreadyFlipped.
            // - Else: flips are 0->1, so total ones increase by alreadyFlipped.
            long onesTotalBefore = totalOnesBase + (prefer01 ? -alreadyFlipped : alreadyFlipped);

            // Ones before pos in current assignment (before flipping at pos):
            long onesBefore = onesBeforeBase + (prefer01 ? -alreadyFlipped : alreadyFlipped);
            long zerosBefore = (long) pos - onesBefore;

            // Current bit at pos before flip is the baseline bit (since we flip in order).
            int bitOld = (bangBase == '1') ? 1 : 0;

            long zerosTotalBefore = (long) n - onesTotalBefore;
            long onesAfter = onesTotalBefore - onesBefore - bitOld;
            long zerosAfter = zerosTotalBefore - zerosBefore - (1 - bitOld);

            long delta;
            if (prefer01) {
                // Flip 1 -> 0 at pos
                // Pairs with left:
                //   previous 0 with (pos=1) contributed x; becomes 0 => removes x per previous 0  => -x*zerosBefore
                //   previous 1 with (pos=1) contributed 0; becomes 0 => adds y per previous 1    => +y*onesBefore
                // Pairs with right:
                //   (pos=1) with later 0 contributed y; becomes 0 => removes y per later 0       => -y*zerosAfter
                //   (pos=1) with later 1 contributed 0; becomes 0 => adds x per later 1         => +x*onesAfter
                delta = (long) y * onesBefore - (long) x * zerosBefore
                      + (long) x * onesAfter  - (long) y * zerosAfter;
            } else {
                // Flip 0 -> 1 at pos
                // Pairs with left:
                //   previous 1 with (pos=0) contributed y; becomes 1 => removes y per previous 1 => -y*onesBefore
                //   previous 0 with (pos=0) contributed 0; becomes 1 => adds x per previous 0   => +x*zerosBefore
                // Pairs with right:
                //   (pos=0) with later 1 contributed x; becomes 1 => removes x per later 1      => -x*onesAfter
                //   (pos=0) with later 0 contributed 0; becomes 1 => adds y per later 0        => +y*zerosAfter
                delta = (long) x * zerosBefore - (long) y * onesBefore
                      + (long) y * zerosAfter  - (long) x * onesAfter;
            }

            cost += delta;
            if (cost < minCost) minCost = cost;
        }

        long ans = minCost % MOD;
        if (ans < 0) ans += MOD; // defensive
        return (int) ans;
    }
}
