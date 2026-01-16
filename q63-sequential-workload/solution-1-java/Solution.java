import java.util.*;

public class MaximumSequentialWorkloadPerMachine {

    public MaximumSequentialWorkloadPerMachine() {
    }

    public List<Long> getMaxWorkloadDone(List<Integer> performance, List<Integer> workload) {
        int m = performance.size();
        int n = workload.size();

        // Prefix sums of workload as long
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + (long) workload.get(i);
        }

        // Segment tree over workload values for range maximum queries.
        // We use it to find the first index j where workload[j] > capacity in O(log n).
        int size = 1;
        while (size < n) size <<= 1;

        int[] seg = new int[2 * size];
        // Initialize leaves
        for (int i = 0; i < n; i++) {
            seg[size + i] = workload.get(i);
        }
        // Remaining leaves are already 0 (workload values are >= 0), OK.
        for (int i = size - 1; i >= 1; i--) {
            seg[i] = Math.max(seg[2 * i], seg[2 * i + 1]);
        }

        List<Long> ans = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            int cap = performance.get(i);
            int firstFail = firstIndexGreaterThan(seg, size, n, cap); // first j with workload[j] > cap, or n
            ans.add(prefix[firstFail]);
        }
        return ans;
    }

    /**
     * Returns the smallest index j in [0, n) such that workload[j] > cap.
     * If no such j exists, returns n.
     *
     * Segment tree stores range maximums. We descend to find the first position whose value exceeds cap.
     */
    private int firstIndexGreaterThan(int[] seg, int base, int n, int cap) {
        if (n == 0) return 0;
        if (seg[1] <= cap) return n; // whole array max <= cap => all jobs can be processed

        int idx = 1;
        int l = 0, r = base; // base is power-of-two size
        while (idx < base) {
            int left = idx * 2;
            int mid = (l + r) >>> 1;

            if (seg[left] > cap) {
                idx = left;
                r = mid;
            } else {
                idx = left + 1;
                l = mid;
            }
        }
        int pos = idx - base;
        return (pos < n) ? pos : n;
    }
}
