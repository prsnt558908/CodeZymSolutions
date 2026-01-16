import java.util.*;

public class MaximumStorageEfficiency {

    public MaximumStorageEfficiency() {
    }

    public int getMaximumStorageEfficiency(List<Integer> numSegments, long m) {
        // We want to maximize the minimum segments in any storage unit.
        // If we target a minimum value x, then task with s segments can be split into at most floor(s / x) units
        // (each unit has at least x segments). We need total units >= m.
        int minSeg = Integer.MAX_VALUE;
        for (int s : numSegments) {
            minSeg = Math.min(minSeg, s);
        }

        long lo = 1;
        long hi = minSeg; // answer cannot exceed min(numSegments)
        long ans = 1;

        while (lo <= hi) {
            long mid = lo + ((hi - lo) >>> 1);

            long units = 0;
            for (int s : numSegments) {
                units += (s / mid);
                if (units >= m) { // early stop to avoid overflow and extra work
                    break;
                }
            }

            if (units >= m) {
                ans = mid;      // feasible, try bigger minimum
                lo = mid + 1;
            } else {
                hi = mid - 1;   // not feasible, try smaller minimum
            }
        }

        return (int) ans;
    }
}
