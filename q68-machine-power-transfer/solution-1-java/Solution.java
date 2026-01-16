import java.util.*;

public class MaximizeMachineStrength {

    public MaximizeMachineStrength() {
    }

    public long getStrength(List<String> powers) {
        if (powers == null || powers.isEmpty()) return 0L;

        long globalMin = Long.MAX_VALUE;
        long sumSecond = 0L;
        long minSecond = Long.MAX_VALUE;

        for (String csv : powers) {
            long[] mins = twoMinFromCsv(csv); // mins[0] = smallest, mins[1] = 2nd smallest (can be equal)
            long min1 = mins[0];
            long min2 = mins[1];

            if (min1 < globalMin) globalMin = min1;
            sumSecond += min2;
            if (min2 < minSecond) minSecond = min2;
        }

        // Max = global minimum must appear in at least one machine (as its strength),
        // and every other machine can achieve at most its original 2nd minimum.
        return globalMin + (sumSecond - minSecond);
    }

    // Parses a comma-separated list of longs (whitespace ignored) and returns:
    // [smallest, secondSmallest] (secondSmallest can equal smallest if duplicates exist)
    private static long[] twoMinFromCsv(String csv) {
        if (csv == null) {
            throw new IllegalArgumentException("powers[i] cannot be null");
        }

        long min1 = Long.MAX_VALUE;
        long min2 = Long.MAX_VALUE;

        int n = csv.length();
        int i = 0;
        int count = 0;

        while (i < n) {
            // Skip separators and whitespace
            while (i < n) {
                char c = csv.charAt(i);
                if (c == ',' || c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
            if (i >= n) break;

            // Optional sign
            boolean neg = false;
            char c = csv.charAt(i);
            if (c == '+' || c == '-') {
                neg = (c == '-');
                i++;
            }

            if (i >= n || !Character.isDigit(csv.charAt(i))) {
                throw new IllegalArgumentException("Invalid number token in: " + csv);
            }

            long val = 0L;
            while (i < n) {
                c = csv.charAt(i);
                if (c >= '0' && c <= '9') {
                    val = val * 10L + (c - '0');
                    i++;
                } else {
                    break;
                }
            }

            long x = neg ? -val : val;
            count++;

            if (x < min1) {
                min2 = min1;
                min1 = x;
            } else if (x < min2) {
                min2 = x;
            }
            // Loop continues; next iteration will skip commas/whitespace again
        }

        if (count < 2) {
            throw new IllegalArgumentException("Each machine must have at least 2 power units: " + csv);
        }

        return new long[] { min1, min2 };
    }
}
