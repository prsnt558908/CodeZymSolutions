import java.util.*;

public class MaxMinMedianSubsequences {

    public MaxMinMedianSubsequences() {
    }

    // Returns [maxMedian, minMedian]
    public int[] getMaxMinMedian(int[] values, int k) {
        if (values == null) {
            throw new IllegalArgumentException("values is null");
        }
        int n = values.length;
        if (k < 1 || k > n) {
            throw new IllegalArgumentException("k must be in [1, n]");
        }

        int[] a = Arrays.copyOf(values, n);
        Arrays.sort(a);

        // min median is the (k-1)/2-th element in globally sorted array
        int minMedian = a[(k - 1) / 2];

        // max median is the (floor(k/2)+1)-th largest element
        int needOnRight = (k / 2) + 1;      // floor(k/2) + 1
        int maxMedian = a[n - needOnRight]; // (needOnRight)-th largest

        return new int[] { maxMedian, minMedian };
    }
}
