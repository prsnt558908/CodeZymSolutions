import java.util.*;

public class SecurityRiskCalculator {

    private static final long MOD = 1_000_000_007L;

    public SecurityRiskCalculator() {
    }

    /**
     * Sum over all subarrays: (length of subarray) * (maximum element in subarray), modulo 1e9+7.
     *
     * Idea:
     * For each index i, count all subarrays where security[i] is the "chosen" maximum (tie-broken consistently),
     * and add security[i] * (sum of lengths of those subarrays).
     *
     * Tie-break rule (to avoid double counting with equal values):
     * - Left boundary uses previous STRICTLY greater element (>)
     * - Right boundary uses next GREATER OR EQUAL element (>=)
     * This assigns each subarray to the rightmost occurrence among equal maxima.
     */
    public int calculateTotalRisk(List<Integer> security) {
        int n = security.size();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = security.get(i);

        int[] prevGreater = new int[n];      // index of previous element > a[i], or -1
        int[] nextGe = new int[n];           // index of next element >= a[i], or n

        // prevGreater: monotonic decreasing stack (by value), pop while <= current
        ArrayDeque<Integer> st = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            while (!st.isEmpty() && a[st.peek()] <= a[i]) st.pop();
            prevGreater[i] = st.isEmpty() ? -1 : st.peek();
            st.push(i);
        }

        // nextGe: scan from right, pop while < current so top is >= current
        st.clear();
        for (int i = n - 1; i >= 0; i--) {
            while (!st.isEmpty() && a[st.peek()] < a[i]) st.pop();
            nextGe[i] = st.isEmpty() ? n : st.peek();
            st.push(i);
        }

        long ans = 0;
        for (int i = 0; i < n; i++) {
            long L = i - (long) prevGreater[i];   // number of choices for left end
            long R = (long) nextGe[i] - i;        // number of choices for right end

            // Sum of lengths over all subarrays where i is the chosen maximum:
            // sum_{x=1..L} sum_{y=1..R} (x + y - 1)
            // = (L*(L+1)/2)*R + (R*(R+1)/2)*L - L*R
            long triL = (L * (L + 1) / 2) % MOD;
            long triR = (R * (R + 1) / 2) % MOD;

            long term1 = (triL * (R % MOD)) % MOD;
            long term2 = (triR * (L % MOD)) % MOD;
            long term3 = ((L % MOD) * (R % MOD)) % MOD;

            long lenSum = (term1 + term2 - term3) % MOD;
            if (lenSum < 0) lenSum += MOD;

            long contrib = ((a[i] % MOD) * lenSum) % MOD;
            ans += contrib;
            ans %= MOD;
        }

        return (int) ans;
    }
}
