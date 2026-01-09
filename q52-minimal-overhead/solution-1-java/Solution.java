import java.util.*;

public class MinimizeTotalOverhead {

    public MinimizeTotalOverhead() {
    }

    private static class Node {
        int count; // current total count of this letter
        int idx;   // 0..25

        Node(int count, int idx) {
            this.count = count;
            this.idx = idx;
        }
    }

    public String determineOptimalSamples(String samples) {
        int n = samples.length();
        int[] fixed = new int[26];
        int q = 0;

        // Count fixed letters and number of '?'
        for (int i = 0; i < n; i++) {
            char ch = samples.charAt(i);
            if (ch == '?') {
                q++;
            } else {
                fixed[ch - 'a']++;
            }
        }

        // Greedily distribute q additions to minimize sum of overhead increments.
        // Tie-break by smaller letter for lexicographically smallest optimal result.
        PriorityQueue<Node> pq = new PriorityQueue<>((a, b) -> {
            if (a.count != b.count) return Integer.compare(a.count, b.count);
            return Integer.compare(a.idx, b.idx);
        });

        int[] total = Arrays.copyOf(fixed, 26);
        for (int i = 0; i < 26; i++) {
            pq.add(new Node(total[i], i));
        }

        for (int t = 0; t < q; t++) {
            Node cur = pq.poll();
            cur.count++;
            total[cur.idx]++;   // keep array in sync
            pq.add(cur);
        }

        // How many of each letter to place into '?' positions
        int[] need = new int[26];
        for (int i = 0; i < 26; i++) {
            need[i] = total[i] - fixed[i];
        }

        // Build lexicographically smallest string using the chosen multiset for '?'
        StringBuilder sb = new StringBuilder(n);
        int ptr = 0; // smallest letter with remaining quota
        for (int i = 0; i < n; i++) {
            char ch = samples.charAt(i);
            if (ch != '?') {
                sb.append(ch);
            } else {
                while (ptr < 26 && need[ptr] == 0) ptr++;
                // ptr must be valid because total '?' = sum(need)
                sb.append((char) ('a' + ptr));
                need[ptr]--;
            }
        }

        return sb.toString();
    }
}
