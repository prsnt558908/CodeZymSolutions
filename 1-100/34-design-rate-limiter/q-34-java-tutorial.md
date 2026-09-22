
# Design a Rate Limiter in Java

#### Problem Statement

[https://codezym.com/question/34-design-rate-limiter](https://codezym.com/question/34-design-rate-limiter)


The main idea is to keep one independent rate-limiting strategy for each resource. A fixed window needs only a counter, while a sliding window needs a queue of accepted request timestamps. The **Strategy pattern** fits this problem because both algorithms answer the same question, but count requests differently. A small factory helper creates the chosen strategy, and a map connects each resource ID to its strategy. This keeps request handling simple and makes adding another strategy straightforward.

## 1. Understand the Rules

We need to implement two methods.

- `addResource(resourceId, strategy, limits)` configures a resource. For example, `"2,5"` means at most two accepted requests per five-second window.
- `isAllowed(resourceId, timestamp)` checks whether the resource can accept a request at the supplied time.

Each resource has its own limits and request history. Requests to one resource must not consume another resource's quota.

There are three details that determine the result.

1. **Only accepted requests consume quota.** A rejected request must not be added to the sliding-window history.
2. **Updating a resource resets its state.** Every call to `addResource` replaces the previous configuration and history, even when the strategy name stays the same.
3. **The two strategies use different window boundaries.** Fixed windows start at multiples of the configured period. Sliding windows move with each request.

The statement guarantees valid resource IDs, supported strategy names, positive limits, and increasing timestamps. We use the supplied timestamp directly instead of reading the system clock.

The name `"sliding-window-counter"` can be misleading here. The statement explicitly asks for a **log-based sliding window** that stores individual timestamps. We therefore implement that exact behavior, rather than an estimate based on two fixed-window counters.

## 2. Start With a Simple Approach

A straightforward solution stores every accepted request timestamp in a list for each resource.

For every new request, we scan that list and count the accepted requests that belong to the relevant window. If the count is below the limit, we accept the request and append its timestamp.

This works, but it keeps checking old history. After a resource has accepted `H` requests, a check can take `O(H)` time. Keeping all history also uses `O(H)` space.

We can improve this by looking at what each strategy actually needs.

- A fixed window needs only the current window ID and its accepted-request count.
- A sliding window needs only the accepted timestamps that have not expired.

## 3. Fixed Window: Keep a Window ID and a Counter

Suppose the period is five seconds. The fixed windows are `[0, 5)`, `[5, 10)`, `[10, 15)`, and so on.

For integer timestamps, these contain seconds `0..4`, `5..9`, and `10..14`.

We find the window with `Math.floorDiv(timestamp, timePeriod)`. With a period of five, timestamp `4` belongs to window `0`, while timestamp `5` belongs to window `1`.

The strategy stores two changing values.

- `currentWindow` identifies the window of the last checked request.
- `acceptedCount` records how many requests were accepted in that window.

For each request, we follow these steps.

1. Calculate its window ID.
2. If this is a different window, save the new ID and reset the count to zero.
3. If the count already equals the limit, reject the request.
4. Otherwise, increment the count and accept it.

For limits `"2,5"`, requests at times `1` and `2` are accepted. A request at `4` is rejected because the same window is full. A request at `5` is accepted because it belongs to the next window.

**The first request does not decide where a fixed window starts.** Even if a resource receives its first request at time `4`, its current window still ends just before time `5`.

Fixed windows can allow bursts around a boundary. With limits `"2,5"`, requests at `3`, `4`, `5`, and `6` can all succeed. The first two use one window, and the next two use another. That is expected behavior for this strategy.

## 4. Sliding Window: Keep a Queue of Accepted Timestamps

For a request at time `t` and a period of `W`, the active window is `(t - W, t]`. With integer timestamps, this is the same as `[t - W + 1, t]`.

This means an old timestamp expires when it is **less than or equal to `t - W`**. A request accepted exactly `W` seconds ago no longer consumes quota.

Because timestamps arrive in increasing order, accepted timestamps are already sorted. We do not need a sorted map or a sorting step. A normal first-in, first-out queue is enough.

We use `Deque<Integer>` backed by `ArrayDeque`. Although a deque supports both ends, this solution uses it as a queue.

- Add accepted timestamps at the back with `addLast`.
- Read the oldest timestamp with `peekFirst`.
- Remove expired timestamps from the front with `removeFirst`.

For each request, first remove all expired timestamps. If the remaining queue size equals the limit, reject the request. Otherwise, add its timestamp and accept it.

Consider limits `"2,3"` with a fresh resource.

1. At time `6`, the window is `[4, 6]`. The queue is empty, so accept the request. The queue becomes `[6]`.
2. At time `7`, the window is `[5, 7]`. Timestamp `6` is still active, so accept the request. The queue becomes `[6, 7]`.
3. At time `8`, the window is `[6, 8]`. Both stored requests are active, so reject the request. The queue stays `[6, 7]`.
4. At time `9`, the window is `[7, 9]`. Timestamp `6` expires because `6 <= 9 - 3`. Remove it and accept `9`. The queue becomes `[7, 9]`.

The rejected request at time `8` never enters the queue. Adding it would incorrectly block later requests.

We clean up a resource's queue when that resource receives a request. No background cleanup process is needed. An idle resource may temporarily retain expired timestamps, but they are removed before its next decision. The queue never holds more than its configured maximum number of accepted requests.

## 5. Organize the Code With the Strategy Pattern

`RateLimiter` owns a `HashMap<String, RateLimitStrategy>`. The key is a resource ID, and the value contains both that resource's configuration and its current state. A map gives us direct access to the requested resource without scanning other resources.

`RateLimitStrategy` is a small interface with one method, `isAllowed(timestamp)`. It lets `RateLimiter` delegate the decision without knowing how the chosen algorithm counts requests.

`FixedWindowCounter` implements the interface with a counter. `SlidingWindowLog` implements it with a timestamp queue. Each configured resource gets a fresh strategy object, so resources using the same algorithm still have separate state.

The `createStrategy` helper chooses which object to create. This is a simple factory helper. A separate factory class hierarchy would add little value for these two creation choices.

To add another algorithm, implement `RateLimitStrategy` and add a creation case in `createStrategy`. The public `isAllowed` method and the existing strategy implementations stay unchanged. The factory helper is the one place that needs to know the supported strategy names.

Updating a resource also becomes simple. `addResource` creates a new strategy object and puts it under the same resource ID. Replacing the map value automatically discards the old counter or queue from that resource's active state.

The helper types are nested inside `RateLimiter` because they are implementation details. The public API remains exactly the one supplied in the starter code.

## 6. Why the Solution Is Correct

For a fixed window, `acceptedCount` starts at zero whenever a new window is encountered. It increases only when a request is accepted, and we reject requests once it reaches the maximum. Therefore, it always equals the number of accepted requests in the current fixed window and never exceeds the limit.

For a sliding window, the queue contains accepted requests in timestamp order. All expired timestamps are at the front, so removing timestamps at or before the cutoff leaves exactly the previous accepted requests in the active window. We accept the new request only when adding it would keep the count within the limit.

The map keeps each resource's state separate. Replacing a resource's strategy gives it empty state, which satisfies the reset rule.

## 7. Time and Space Complexity

Let `M` be a resource's maximum request count, `R` the number of configured resources, and `L` the length of the limits string.

**Adding or updating a resource** takes `O(L)` time to parse the limits, plus expected `O(1)` time to create and store its strategy. The sliding queue starts empty, so configuring a large limit does not immediately allocate space for that many timestamps.

**Fixed-window checks** take expected `O(1)` time, including the map lookup, and use `O(1)` state per resource.

**Sliding-window checks** take amortized `O(1)` time, including expected map lookup time. Each accepted timestamp is added once and removed at most once. Queue insertion is also amortized `O(1)`. A single call can take `O(M)` time if it removes many expired timestamps or grows the backing array. Space is `O(M)` per sliding-window resource.

Across all resources, total space is `O(R + sum of M over sliding-window resources)`.


## 9. Complete Java Solution


```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class RateLimiter {
    private final Map<String, RateLimitStrategy> resources;

    public RateLimiter() {
        resources = new HashMap<>();
    }

    public void addResource(String resourceId, String strategy, String limits) {
        String[] parts = limits.split(",");
        int maxRequests = Integer.parseInt(parts[0].trim());
        int timePeriod = Integer.parseInt(parts[1].trim());

        // A fresh strategy also resets the state of an existing resource.
        resources.put(resourceId, createStrategy(strategy, maxRequests, timePeriod));
    }

    public boolean isAllowed(String resourceId, int timestamp) {
        // The problem guarantees that this resource has been configured.
        return resources.get(resourceId).isAllowed(timestamp);
    }

    private static RateLimitStrategy createStrategy(
            String strategy, int maxRequests, int timePeriod) {
        switch (strategy) {
            case "fixed-window-counter":
                return new FixedWindowCounter(maxRequests, timePeriod);
            case "sliding-window-counter":
                return new SlidingWindowLog(maxRequests, timePeriod);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    private interface RateLimitStrategy {
        boolean isAllowed(int timestamp);
    }

    private static final class FixedWindowCounter implements RateLimitStrategy {
        private final int maxRequests;
        private final int timePeriod;
        private long currentWindow = Long.MIN_VALUE;
        private int acceptedCount;

        private FixedWindowCounter(int maxRequests, int timePeriod) {
            this.maxRequests = maxRequests;
            this.timePeriod = timePeriod;
        }

        @Override
        public boolean isAllowed(int timestamp) {
            // Windows are aligned to time zero, not to the first request.
            long requestWindow = Math.floorDiv(timestamp, timePeriod);

            if (requestWindow != currentWindow) {
                currentWindow = requestWindow;
                acceptedCount = 0;
            }

            if (acceptedCount >= maxRequests) {
                return false;
            }

            acceptedCount++;
            return true;
        }
    }

    private static final class SlidingWindowLog implements RateLimitStrategy {
        private final int maxRequests;
        private final int timePeriod;
        private final Deque<Integer> acceptedTimes = new ArrayDeque<>();

        private SlidingWindowLog(int maxRequests, int timePeriod) {
            this.maxRequests = maxRequests;
            this.timePeriod = timePeriod;
        }

        @Override
        public boolean isAllowed(int timestamp) {
            // Active timestamps satisfy timestamp - timePeriod < oldTime.
            long cutoff = (long) timestamp - timePeriod;
            while (!acceptedTimes.isEmpty() && acceptedTimes.peekFirst() <= cutoff) {
                acceptedTimes.removeFirst();
            }

            if (acceptedTimes.size() >= maxRequests) {
                return false;
            }

            // Rejected requests are never added to the history.
            acceptedTimes.addLast(timestamp);
            return true;
        }
    }
}
```
