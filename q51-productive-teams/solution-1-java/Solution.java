import java.util.Arrays;

public class CountProductiveTeams {

    public CountProductiveTeams() {
    }

    /**
     * Counts number of quadruples (x, y, z, w) such that:
     *  - x < y < z < w
     *  - level[x] < level[z]
     *  - level[y] > level[w]
     *
     * Time:  O(n^2)
     * Space: O(n)
     *
     * NOTE: For n up to 3000, the count can exceed int range.
     * If you want full correctness for worst-case constraints, change return type to long.
     */
    public int countProductiveTeams(int[] level) {
        int n = level.length;
        int[] rank = compressToRanks(level); // ranks in [1..n], preserves < and >

        int[] freq = new int[n + 2]; // freq[valueRank] among indices x < y
        int[] pref = new int[n + 2]; // prefix sums of freq
        long ans = 0;

        for (int y = 0; y < n; y++) {
            // Build prefix sums so we can query: #x<y with rank[x] < someRank in O(1)
            pref[0] = 0;
            for (int v = 1; v <= n; v++) {
                pref[v] = pref[v - 1] + freq[v];
            }

            int rankY = rank[y];

            // Scan z from right to left, maintaining:
            // c = #w in (z..n-1] such that rank[w] < rankY  (i.e., level[y] > level[w])
            int c = 0;
            for (int z = n - 2; z >= y + 1; z--) {
                int addRank = rank[z + 1];
                if (addRank < rankY) c++;

                // left = #x < y such that rank[x] < rank[z]  (i.e., level[x] < level[z])
                int left = pref[rank[z] - 1];

                ans += (long) left * c;
            }

            // Add current y value into prefix multiset for next iterations
            freq[rankY]++;
        }

        // If your platform expects exact results for full constraints, change return type to long.
        return (int) ans;
    }

    // Coordinate compression to ranks 1..n (preserves comparisons, works even if values aren't exactly 1..n)
    private int[] compressToRanks(int[] a) {
        int n = a.length;
        int[] sorted = a.clone();
        Arrays.sort(sorted);

        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            int pos = Arrays.binarySearch(sorted, a[i]); // values are distinct, so exact hit
            rank[i] = pos + 1; // 1..n
        }
        return rank;
    }
}
