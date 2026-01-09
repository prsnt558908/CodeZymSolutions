import java.util.*;

public class TransactionLogProcessor {

    public TransactionLogProcessor() {

    }

    public List<String> processLogs(List<String> logs, int threshold) {
        // Count transactions per user id (as integer key for numeric sorting).
        Map<Integer, Integer> count = new HashMap<>();

        for (String log : logs) {
            if (log == null) continue;

            // Logs may have leading/multiple spaces; trim and split on whitespace.
            String s = log.trim();
            if (s.isEmpty()) continue;

            String[] parts = s.split("\\s+");
            // Expected: sender recipient amount
            if (parts.length < 2) continue; // defensive: malformed line

            int sender = Integer.parseInt(parts[0]);
            int recipient = Integer.parseInt(parts[1]);

            // Sender always counts once
            count.put(sender, count.getOrDefault(sender, 0) + 1);

            // Recipient counts once unless it's the same as sender (self-transaction counts once total)
            if (recipient != sender) {
                count.put(recipient, count.getOrDefault(recipient, 0) + 1);
            }
        }

        // Collect qualifying user ids
        List<Integer> ids = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : count.entrySet()) {
            if (e.getValue() >= threshold) {
                ids.add(e.getKey());
            }
        }

        // Sort by numeric value ascending
        Collections.sort(ids);

        // Convert back to strings
        List<String> result = new ArrayList<>(ids.size());
        for (int id : ids) {
            result.add(String.valueOf(id));
        }
        return result;
    }
}
