public class WorkroomRemoteness {

    public WorkroomRemoteness() {
    }

    /**
     * Returns the maximum "remoteness" among all valid assignments.
     *
     * Interpretation consistent with the examples:
     * - skill = developers (length n)
     * - room  = rooms (length m), where skill is a subsequence of room
     * - choose increasing indices p[0..n-1] in room such that room[p[i]] == skill[i]
     * - remoteness of an assignment = max over i of (p[i+1] - p[i] - 1)
     *
     * Approach:
     * - left[i]  = earliest index in room where skill[0..i] can be matched (greedy from left)
     * - right[i] = latest index in room where skill[i..n-1] can be matched (greedy from right)
     * - For adjacent pair (i, i+1), best possible gap is right[i+1] - left[i] - 1
     *   because any valid assignment must satisfy p[i] >= left[i] and p[i+1] <= right[i+1].
     */
    public int getMaximumRemoteness(String skill, String room) {
        int n = skill.length();
        if (n <= 1) return 0;

        int m = room.length();
        int[] left = new int[n];
        int[] right = new int[n];

        // Build earliest positions for each developer (prefix matching)
        int j = 0;
        for (int i = 0; i < n; i++) {
            char need = skill.charAt(i);
            while (j < m && room.charAt(j) != need) j++;
            // Under the intended constraints, this always succeeds.
            left[i] = j;
            j++;
        }

        // Build latest positions for each developer (suffix matching)
        j = m - 1;
        for (int i = n - 1; i >= 0; i--) {
            char need = skill.charAt(i);
            while (j >= 0 && room.charAt(j) != need) j--;
            right[i] = j;
            j--;
        }

        // Maximize the gap between consecutive assigned developers
        int ans = 0;
        for (int i = 0; i < n - 1; i++) {
            int gap = right[i + 1] - left[i] - 1;
            if (gap > ans) ans = gap;
        }
        return ans;
    }
}
