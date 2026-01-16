import java.util.*;

public class LongestContiguousArithmeticProgression {

    public LongestContiguousArithmeticProgression() {

    }

    public int getLongestArithmeticSubarray(List<Integer> deviation) {
        int n = deviation.size();
        if (n <= 2) return n;

        long[] a = new long[n];
        for (int i = 0; i < n; i++) a[i] = deviation.get(i);

        // left[i] = length of the longest arithmetic subarray ending at i (no changes)
        int[] left = new int[n];
        left[0] = 1;
        left[1] = 2;
        for (int i = 2; i < n; i++) {
            long d1 = a[i] - a[i - 1];
            long d2 = a[i - 1] - a[i - 2];
            left[i] = (d1 == d2) ? (left[i - 1] + 1) : 2;
        }

        // right[i] = length of the longest arithmetic subarray starting at i (no changes)
        int[] right = new int[n];
        right[n - 1] = 1;
        right[n - 2] = 2;
        for (int i = n - 3; i >= 0; i--) {
            long d1 = a[i + 1] - a[i];
            long d2 = a[i + 2] - a[i + 1];
            right[i] = (d1 == d2) ? (right[i + 1] + 1) : 2;
        }

        int ans = 1;
        for (int v : left) ans = Math.max(ans, v); // no change

        // Change first or last element to extend a progression by 1
        ans = Math.max(ans, right[1] + 1);
        ans = Math.max(ans, left[n - 2] + 1);

        // Try changing each middle element
        for (int i = 1; i <= n - 2; i++) {
            // Extend the arithmetic run on the left or on the right by changing a[i]
            ans = Math.max(ans, left[i - 1] + 1);
            ans = Math.max(ans, right[i + 1] + 1);

            // Try to "merge" left run ending at i-1 and right run starting at i+1
            // by changing a[i] so that both sides share the same common difference d.
            long num = a[i + 1] - a[i - 1];
            if ((num & 1L) == 0L) { // must be even to make integer d
                long d = num / 2;

                int l = 1; // at least include a[i-1]
                if (i >= 2 && (a[i - 1] - a[i - 2]) == d) {
                    l = left[i - 1];
                }

                int r = 1; // at least include a[i+1]
                if (i + 2 < n && (a[i + 2] - a[i + 1]) == d) {
                    r = right[i + 1];
                }

                ans = Math.max(ans, l + 1 + r); // left part + changed element + right part
            }
        }

        return Math.min(ans, n);
    }
}
