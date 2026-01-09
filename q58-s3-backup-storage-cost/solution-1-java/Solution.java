import java.util.*;

public class MinimumStorageCost {

    public MinimumStorageCost() {
    }

    public long getMinimumStorageCost(int n, int encCost, int flatCost, int[] sensitiveFiles) {
        // Mark sensitive files (treat input as a set; duplicates don't increase X).
        boolean[] isSensitive = new boolean[n + 1];
        if (sensitiveFiles != null) {
            for (int v : sensitiveFiles) {
                if (v >= 1 && v <= n) {
                    isSensitive[v] = true;
                }
            }
        }

        // Prefix count of sensitive files for O(1) segment queries.
        int[] pref = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            pref[i] = pref[i - 1] + (isSensitive[i] ? 1 : 0);
        }

        return solve(1, n, pref, (long) encCost, (long) flatCost);
    }

    private long solve(int l, int r, int[] pref, long encCost, long flatCost) {
        int len = r - l + 1;
        int x = pref[r] - pref[l - 1]; // number of sensitive files in [l..r]

        long wholeCost = (x == 0) ? flatCost : (long) len * (long) x * encCost;

        // Only even-sized batches can be split.
        if ((len & 1) == 1) {
            return wholeCost;
        }

        int mid = l + (len / 2) - 1;
        long splitCost = solve(l, mid, pref, encCost, flatCost) + solve(mid + 1, r, pref, encCost, flatCost);

        return Math.min(wholeCost, splitCost);
    }
}
