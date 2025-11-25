import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory RateLimiter supporting multiple strategies per resourceId.
 *
 * Strategies:
 *  - fixed-window-counter
 *  - sliding-window-counter
 *
 * Limits string format: "maxRequests,timePeriod"
 *   e.g. "5,2" => at most 5 requests every 2 seconds.
 */
public class RateLimiter {

    private final Map<String, RateLimitStrategy> strategies = new HashMap<>();

    public RateLimiter() {
    }

    /**
     * Configure or update a resource with a given strategy and limits.
     *
     * @param resourceId globally unique, non-blank id
     * @param strategy   "fixed-window-counter" or "sliding-window-counter"
     * @param limits     "maxRequests,timePeriod" (both >= 1)
     */
    public void addResource(String resourceId, String strategy, String limits) {
        String[] parts = limits.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("limits must be 'maxRequests,timePeriod'");
        }

        int maxRequests = Integer.parseInt(parts[0].trim());
        int timePeriod = Integer.parseInt(parts[1].trim());

        RateLimitStrategy strategyImpl;
        if ("fixed-window-counter".equals(strategy)) {
            strategyImpl = new FixedWindowCounterStrategy(maxRequests, timePeriod);
        } else if ("sliding-window-counter".equals(strategy)) {
            strategyImpl = new SlidingWindowCounterStrategy(maxRequests, timePeriod);
        } else {
            throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }

        // Replace any existing strategy (state is reset)
        strategies.put(resourceId, strategyImpl);
    }

    /**
     * Check if a request for this resource at given timestamp is allowed.
     *
     * @param resourceId valid, previously registered resourceId
     * @param timestamp  seconds, strictly increasing across all calls
     * @return true if allowed, false if blocked
     */
    public boolean isAllowed(String resourceId, int timestamp) {
        RateLimitStrategy strategy = strategies.get(resourceId);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown resourceId: " + resourceId);
        }
        return strategy.isAllowed(timestamp);
    }

    // ------------- Strategy SPI -------------

    private interface RateLimitStrategy {
        boolean isAllowed(int timestamp);
    }

    private abstract static class AbstractRateLimitStrategy implements RateLimitStrategy {
        protected final int maxRequests;
        protected final int timePeriod; // in seconds

        protected AbstractRateLimitStrategy(int maxRequests, int timePeriod) {
            this.maxRequests = maxRequests;
            this.timePeriod = timePeriod;
        }
    }

    // ------------- Fixed Window Counter -------------

    /**
     * Fixed Window:
     *   - Time divided into windows of size timePeriod: [0..timePeriod-1], [timePeriod..2*timePeriod-1], ...
     *   - Per window we count requests: if count >= maxRequests => block.
     */
    private static class FixedWindowCounterStrategy extends AbstractRateLimitStrategy {

        private int currentWindow = -1;
        private int currentCount = 0;

        public FixedWindowCounterStrategy(int maxRequests, int timePeriod) {
            super(maxRequests, timePeriod);
        }

        @Override
        public boolean isAllowed(int timestamp) {
            int windowId = timestamp / timePeriod; // e.g. timePeriod=5 -> [0..4]=0, [5..9]=1, ...

            if (windowId != currentWindow) {
                currentWindow = windowId;
                currentCount = 0;
            }

            if (currentCount < maxRequests) {
                currentCount++;
                return true;
            }
            return false;
        }
    }

    // ------------- Sliding Window Counter (log-based) -------------

    /**
     * Sliding Window (log-based):
     *   - Keep timestamps of recent *allowed* requests in a deque.
     *   - For a new request at t, remove all entries < (t - timePeriod + 1).
     *   - If remaining size < maxRequests, allow and record t; else block.
     */
    private static class SlidingWindowCounterStrategy extends AbstractRateLimitStrategy {

        private final Deque<Integer> timestamps = new ArrayDeque<>();

        public SlidingWindowCounterStrategy(int maxRequests, int timePeriod) {
            super(maxRequests, timePeriod);
        }

        @Override
        public boolean isAllowed(int timestamp) {
            int windowStart = timestamp - timePeriod + 1; // inclusive

            // Drop events that are outside the current sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.removeFirst();
            }

            // Now timestamps.size() = number of requests in [windowStart..timestamp]
            if (timestamps.size() < maxRequests) {
                timestamps.addLast(timestamp); // record this allowed request
                return true;
            }

            // Over the limit in this sliding window
            return false;
        }
    }
}
