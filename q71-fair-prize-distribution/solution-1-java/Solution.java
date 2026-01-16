import java.util.*;

public class FairPrizeDistribution {

    public FairPrizeDistribution() {
    }

    /**
     * Returns the lexicographically smallest fair prize distribution.
     *
     * Fairness rules:
     * 1) Same score => same prize value (requires that value appears at least groupSize times in values).
     * 2) Higher score => strictly higher prize value.
     * 3) Each element in values can be used at most once (so a score group consuming k prizes needs k copies).
     *
     * Approach:
     * - Group participants by score (frequency per distinct score).
     * - Sort distinct scores ascending.
     * - Count occurrences of each distinct prize value (multiset), and sort by value.
     * - Greedily assign the smallest possible prize value to each score group in increasing score order,
     *   skipping prize values whose count is insufficient for that group size.
     *
     * This greedy is correct because choosing a larger prize value earlier can only reduce options later
     * (due to the strictly-increasing requirement), and the greedy assignment is coordinate-wise minimal
     * among all feasible assignments, which implies the resulting participant array is lexicographically smallest.
     */
    public List<Integer> findFairDistribution(List<Integer> points, List<Integer> values) {
        int n = points == null ? 0 : points.size();
        int m = values == null ? 0 : values.size();

        if (n == 0) return new ArrayList<>();
        if (m < n) return new ArrayList<>(); // not enough total prizes

        // 1) Frequency of each score
        Map<Integer, Integer> scoreFreq = new HashMap<>((int) (n / 0.75f) + 1);
        for (Integer p : points) {
            scoreFreq.put(p, scoreFreq.getOrDefault(p, 0) + 1);
        }

        // 2) Sort distinct scores
        int k = scoreFreq.size();
        int[] scores = new int[k];
        int idx = 0;
        for (Integer s : scoreFreq.keySet()) scores[idx++] = s;
        Arrays.sort(scores);

        int[] need = new int[k];
        for (int i = 0; i < k; i++) {
            need[i] = scoreFreq.get(scores[i]);
        }

        // 3) Sort prize values and compress into (uniqueValue, count)
        int[] vals = new int[m];
        idx = 0;
        for (Integer v : values) vals[idx++] = v;
        Arrays.sort(vals);

        int uniqueCount = 0;
        for (int i = 0; i < m; ) {
            uniqueCount++;
            int j = i + 1;
            while (j < m && vals[j] == vals[i]) j++;
            i = j;
        }

        int[] uniq = new int[uniqueCount];
        int[] cnt = new int[uniqueCount];
        int u = 0;
        for (int i = 0; i < m; ) {
            int v = vals[i];
            int j = i + 1;
            while (j < m && vals[j] == v) j++;
            uniq[u] = v;
            cnt[u] = j - i;
            u++;
            i = j;
        }

        // 4) Greedy assignment in increasing score order
        Map<Integer, Integer> scoreToPrize = new HashMap<>((int) (k / 0.75f) + 1);
        int p = 0; // pointer over uniq/cnt (increasing prize values)
        for (int i = 0; i < k; i++) {
            int req = need[i];
            while (p < uniqueCount && cnt[p] < req) p++;
            if (p == uniqueCount) return new ArrayList<>(); // impossible
            scoreToPrize.put(scores[i], uniq[p]);
            p++; // next score must use a strictly larger prize value
        }

        // 5) Build result in original participant order
        List<Integer> result = new ArrayList<>(n);
        for (Integer score : points) {
            result.add(scoreToPrize.get(score));
        }
        return result;
    }
}
