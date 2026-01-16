import java.util.*;

public class WarehouseDispatchCredits {

    public WarehouseDispatchCredits() {
    }

    /**
     * For each warehouse with initial inventory x:
     * - Turns normally remove (dispatch1 + dispatch2) in a full round (you then coworker).
     * - Let a = dispatch1, b = dispatch2, t = a + b.
     * - After removing some number of full rounds, the remaining is rem in [1..t].
     *   If rem <= a, you empty on your turn with 0 skips.
     *   Otherwise, you must force consecutive "your" turns at the end by skipping coworker turns.
     *   Each skip gives you one extra dispatch of 'a' before coworker can act.
     *   Minimum skips needed = ceil(rem / a) - 1 = floor((rem - 1) / a).
     *
     * Since each warehouse credit is worth 1 and costs "skipsNeeded", the global optimum is:
     * take all warehouses with smallest skipsNeeded until the skip budget is exhausted.
     */
    public int maxCredits(List<Integer> inventory, int dispatch1, int dispatch2, int skips) {
        long a = (long) dispatch1;
        long b = (long) dispatch2;
        long k = (long) skips;
        long t = a + b;

        int n = inventory.size();
        long[] costs = new long[n];

        for (int i = 0; i < n; i++) {
            long x = inventory.get(i).longValue();

            long rem = x % t;       // 0..t-1
            if (rem == 0) rem = t;  // treat multiples of t as rem = t (losing without skips)

            // Minimum skips needed for this warehouse:
            // ceil(rem/a)-1 == floor((rem-1)/a)
            long need = (rem - 1) / a;
            costs[i] = need;
        }

        Arrays.sort(costs);

        long used = 0L;
        int credits = 0;
        for (long need : costs) {
            if (used + need <= k) {
                used += need;
                credits++;
            } else {
                break;
            }
        }

        return credits;
    }
}
