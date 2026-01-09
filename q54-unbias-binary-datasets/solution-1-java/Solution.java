import java.util.*;

public class MinOperationsToUnbias {

    public MinOperationsToUnbias() {

    }

    /**
     * We can remove a suffix from data1 (by repeatedly deleting its rightmost char)
     * and remove a prefix from data2 (by repeatedly deleting its leftmost char).
     *
     * After operations, we merge (concatenate) the remaining strings.
     * Goal: #0 == #1 in the merged result, minimize operations.
     *
     * Trick: map '1' -> +1, '0' -> -1. Then unbiased means total sum == 0.
     * Remaining = prefix(data1) + suffix(data2).
     *
     * Time:  O(n)
     * Space: O(n)
     */
    public int minOperationsToUnbias(int n, String data1, String data2) {
        if (data1 == null) data1 = "";
        if (data2 == null) data2 = "";

        int m = Math.min(n, data1.length());
        int k = Math.min(n, data2.length());

        String a = data1.substring(0, m);
        String b = data2.substring(0, k);

        final int INF = 1_000_000_000;

        // bestJ[sum + offset] = minimal j such that suffix sum of b starting at j equals sum
        // j is also the number of deletions from the left of b.
        int offset = k;
        int[] bestJ = new int[2 * k + 1];
        Arrays.fill(bestJ, INF);

        // empty suffix (delete all k chars) has sum 0
        bestJ[offset] = k;

        int suffSum = 0;
        for (int j = k - 1; j >= 0; j--) {
            suffSum += (b.charAt(j) == '1') ? 1 : -1; // sum of b[j..k-1]
            int idx = suffSum + offset;
            if (idx >= 0 && idx < bestJ.length) {
                bestJ[idx] = Math.min(bestJ[idx], j);
            }
        }

        int ans = m + k; // always possible by deleting everything
        int prefSum = 0;

        // keep prefix length p from a => delete (m - p) from end of a
        for (int p = 0; p <= m; p++) {
            if (p > 0) {
                prefSum += (a.charAt(p - 1) == '1') ? 1 : -1;
            }
            int target = -prefSum; // need suffix sum of b to be -prefSum

            if (target < -k || target > k) continue;
            int j = bestJ[target + offset];
            if (j == INF) continue;

            int ops = (m - p) + j;
            ans = Math.min(ans, ops);
        }

        return ans;
    }
}
