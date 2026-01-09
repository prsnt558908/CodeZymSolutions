import java.util.*;

public class MaximumMexArray {

    public MaximumMexArray() {
    }

    public List<Integer> getMaxArray(List<Integer> data_packets) {
        int n = data_packets.size();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = data_packets.get(i);

        // MEX of any set of size <= n is <= n (n+1 is safe upper bound to track)
        int maxVal = n + 1;

        // We only track frequencies for values in [0..maxVal].
        // Any value > maxVal can be ignored because it can never affect mex.
        int[] freq = new int[maxVal + 1];

        for (int x : a) {
            if (x >= 0 && x <= maxVal) {
                freq[x]++;
            }
        }

        // missing holds all v in [0..maxVal] that are missing in the current suffix
        TreeSet<Integer> missing = new TreeSet<>();
        for (int v = 0; v <= maxVal; v++) missing.add(v);
        for (int v = 0; v <= maxVal; v++) {
            if (freq[v] > 0) missing.remove(v);
        }

        List<Integer> result = new ArrayList<>();

        int[] seenAt = new int[maxVal + 1]; // timestamp array for segment-local "seen"
        int segId = 1;

        int i = 0; // start index of current suffix
        while (i < n) {
            int mex = missing.first();

            if (mex == 0) {
                // If 0 is absent in the suffix, every prefix has mex 0.
                // Lexicographically maximum => produce the longest result => take k=1 repeatedly.
                while (i < n) {
                    result.add(0);
                    int x = a[i];
                    if (x >= 0 && x <= maxVal) {
                        freq[x]--;
                        if (freq[x] == 0) missing.add(x);
                    }
                    i++;
                }
                break;
            }

            // Smallest prefix starting at i that contains all numbers 0..mex-1
            segId++;
            int need = mex;
            int j = i;

            while (need > 0) {
                int v = a[j];
                if (v >= 0 && v < mex && seenAt[v] != segId) {
                    seenAt[v] = segId;
                    need--;
                }
                j++;
            }

            // This segment's mex is mex
            result.add(mex);

            // Remove a[i..j-1] from the suffix and update freq/missing (only for tracked range)
            while (i < j) {
                int x = a[i];
                if (x >= 0 && x <= maxVal) {
                    freq[x]--;
                    if (freq[x] == 0) missing.add(x);
                }
                i++;
            }
        }

        return result;
    }
}
