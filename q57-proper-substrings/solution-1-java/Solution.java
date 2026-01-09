import java.util.Arrays;

public class StringSortOperations {

    public StringSortOperations() {
    }

    /**
     * Minimum operations to make strValue sorted (non-decreasing) by repeatedly:
     *  - picking any proper substring (not the entire string)
     *  - sorting that substring
     *
     * Key facts (for n >= 3):
     *  - Answer is always in {0,1,2,3}.
     *  - 0 if already sorted.
     *  - 1 if the first/last mismatch vs globally-sorted string does NOT span the whole string.
     *  - Otherwise (mismatch spans whole string), answer is 2 if after sorting prefix [0..n-2] OR suffix [1..n-1]
     *    the string becomes "1-operation-away"; else 3.
     */
    public int getMinimumOperations(String strValue) {
        char[] s = strValue.toCharArray();
        int n = s.length;

        // Build target = fully sorted version of s (counting sort, since only 'a'..'z')
        char[] target = buildGloballySorted(s);

        if (Arrays.equals(s, target)) {
            return 0;
        }

        int[] b0 = mismatchBounds(s, target);
        int l0 = b0[0], r0 = b0[1];

        // If mismatches are contained within a proper segment, one sort of that segment fixes everything.
        if (!(l0 == 0 && r0 == n - 1)) {
            return 1;
        }

        // Full-string mismatch: cannot sort the entire string in one operation.
        // Try to do it in 2 by making it "1-away" after sorting a length-(n-1) prefix or suffix.

        // Candidate 1: sort prefix [0..n-2]
        char[] cand1 = sortPrefixExceptLast(s);
        if (isOneOperationAway(cand1, target)) {
            return 2;
        }

        // Candidate 2: sort suffix [1..n-1]
        char[] cand2 = sortSuffixExceptFirst(s);
        if (isOneOperationAway(cand2, target)) {
            return 2;
        }

        // Otherwise, needs 3 (and 3 always suffices for n>=3).
        return 3;
    }

    // ---------- helpers ----------

    private static char[] buildGloballySorted(char[] s) {
        int[] cnt = new int[26];
        for (char ch : s) cnt[ch - 'a']++;

        char[] out = new char[s.length];
        int idx = 0;
        for (int c = 0; c < 26; c++) {
            int k = cnt[c];
            char ch = (char) ('a' + c);
            while (k-- > 0) out[idx++] = ch;
        }
        return out;
    }

    /**
     * Returns {firstMismatchIndex, lastMismatchIndex}.
     * Assumes a and b have same length and are not equal.
     */
    private static int[] mismatchBounds(char[] a, char[] b) {
        int n = a.length;
        int l = 0;
        while (l < n && a[l] == b[l]) l++;
        int r = n - 1;
        while (r >= 0 && a[r] == b[r]) r--;
        return new int[]{l, r};
    }

    /**
     * True if current can be turned into target by sorting ONE proper substring.
     * (i.e., current == target OR mismatches vs target are contained in a proper segment)
     */
    private static boolean isOneOperationAway(char[] current, char[] target) {
        if (Arrays.equals(current, target)) return true;

        int[] b = mismatchBounds(current, target);
        int l = b[0], r = b[1];
        return !(l == 0 && r == current.length - 1); // if full span, one operation would require sorting whole string (not allowed)
    }

    /** Sort prefix [0..n-2] (proper substring), keep last char as-is. */
    private static char[] sortPrefixExceptLast(char[] s) {
        int n = s.length;
        char[] out = new char[n];

        int[] cnt = new int[26];
        for (int i = 0; i <= n - 2; i++) cnt[s[i] - 'a']++;

        int idx = 0;
        for (int c = 0; c < 26; c++) {
            int k = cnt[c];
            char ch = (char) ('a' + c);
            while (k-- > 0) out[idx++] = ch;
        }

        out[n - 1] = s[n - 1];
        return out;
    }

    /** Keep first char as-is, sort suffix [1..n-1] (proper substring). */
    private static char[] sortSuffixExceptFirst(char[] s) {
        int n = s.length;
        char[] out = new char[n];
        out[0] = s[0];

        int[] cnt = new int[26];
        for (int i = 1; i <= n - 1; i++) cnt[s[i] - 'a']++;

        int idx = 1;
        for (int c = 0; c < 26; c++) {
            int k = cnt[c];
            char ch = (char) ('a' + c);
            while (k-- > 0) out[idx++] = ch;
        }

        return out;
    }
}
