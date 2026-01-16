import java.util.*;

public class ReplaceValuesAfterEachTransaction {

    public ReplaceValuesAfterEachTransaction() {
        // no-op
    }

    public List<Long> getSumsAfterTransactions(List<Long> entries, List<List<Long>> transactions) {
        // Count how many times each value appears in the current "entries"
        Map<Long, Long> freq = new HashMap<>();
        long sum = 0L;

        for (Long vObj : entries) {
            long v = vObj; // auto-unbox
            sum += v;
            freq.put(v, freq.getOrDefault(v, 0L) + 1L);
        }

        List<Long> ans = new ArrayList<>(transactions.size());

        for (List<Long> t : transactions) {
            long oldV = t.get(0);
            long newV = t.get(1);

            // If nothing changes, sum stays the same
            if (oldV == newV) {
                ans.add(sum);
                continue;
            }

            Long cntObj = freq.get(oldV);
            if (cntObj == null || cntObj == 0L) {
                ans.add(sum);
                continue;
            }

            long cnt = cntObj;

            // Update sum: replacing cnt occurrences of oldV with newV
            // delta = (newV - oldV) * cnt
            sum += (newV - oldV) * cnt;

            // Move counts from oldV to newV
            freq.remove(oldV);
            freq.put(newV, freq.getOrDefault(newV, 0L) + cnt);

            ans.add(sum);
        }

        return ans;
    }
}
