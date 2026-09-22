# Configurable Time Window Hit Counter in Java

#### Problem Statement
[https://codezym.com/question/403-configurable-hit-counter](https://codezym.com/question/403-configurable-hit-counter)

## Intuition

We need a counter that can answer "how many hits happened in the last N seconds" at any moment.

Since a hit stops mattering once it falls outside the window, the natural approach is a sliding window: new hits enter on one end while expired hits leave from the other.

That is exactly what a queue gives us for free. Hits always arrive in non-decreasing timestamp order, so the timestamps we store are already sorted, with the oldest one always at the front. Removing what has expired is just a matter of looking at the front and popping while it is too old.

The only real design decision is what we store per hit. Storing one entry per hit is the obvious first attempt, but the problem's own follow-up question hints at the weak spot: what if a huge number of hits land in the same second? We will start with the simple version, see where it struggles, then fix it.

## Solution 1: Store Every Hit

The simplest approach is to keep every hit timestamp in a queue, in the order it arrived.

- `hit(timestamp)`: push the timestamp onto the back of the queue.
- `getHits(timestamp)`: pop timestamps off the front while they are too old (`timestamp - front >= windowSizeSeconds`), then the queue's size is the answer.

A `Deque` (double ended queue) lets us add to the back and remove from the front in O(1), which is a perfect fit here. We do not need anything fancier like a sorted set, since the arrival order already gives us sorted order for free.

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class HitCounter {

    private final int windowSizeSeconds;
    private final Deque<Integer> hitTimestamps;

    public HitCounter(int windowSizeSeconds) {
        this.windowSizeSeconds = windowSizeSeconds;
        this.hitTimestamps = new ArrayDeque<>();
    }

    // Record one hit by remembering its timestamp.
    public void hit(int timestamp) {
        hitTimestamps.addLast(timestamp);
    }

    // Drop hits that fell outside the window, then report how many remain.
    public int getHits(int timestamp) {
        while (!hitTimestamps.isEmpty() && timestamp - hitTimestamps.peekFirst() >= windowSizeSeconds) {
            hitTimestamps.pollFirst();
        }
        return hitTimestamps.size();
    }
}
```

The `hit` method runs in O(1) time. The `getHits` method runs in amortized O(1) time, since each hit is added once and removed once over its lifetime.

The catch is memory. If a single second receives a million hits, we store a million separate integers for it, even though they will all expire together at the exact same moment. That does not scale well when traffic can spike hard on one timestamp, which is exactly the scenario the problem's follow-up question is asking about.

## Solution 2: Group Hits By Timestamp

Instead of storing every hit separately, we can store one entry per distinct timestamp, along with a count of how many hits happened at that timestamp. If a new hit shares the timestamp with the most recent entry, we just increase that entry's count instead of adding a new one.

We keep two deques that move together, one holding the distinct timestamps and one holding the matching counts. We also keep a running total of hits currently inside the window, so `getHits` does not need to add up the counts every time. It just adjusts the running total whenever an entry is evicted.

Two plain deques are enough here because timestamps only ever grow. We never need to search or reorder anything, so there is no need to reach for a map or a sorted structure. This keeps memory proportional to the number of distinct seconds that actually received a hit, not the number of hits.

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class HitCounter {

    private final int windowSizeSeconds;
    private final Deque<Integer> timestamps; // distinct timestamps, oldest first
    private final Deque<Integer> counts;     // hit count for the matching timestamp
    private int totalHits;                   // hits currently inside the window

    public HitCounter(int windowSizeSeconds) {
        this.windowSizeSeconds = windowSizeSeconds;
        this.timestamps = new ArrayDeque<>();
        this.counts = new ArrayDeque<>();
        this.totalHits = 0;
    }

    public void hit(int timestamp) {
        if (!timestamps.isEmpty() && timestamps.peekLast() == timestamp) {
            // Same second as the last recorded hit, bump its count.
            counts.addLast(counts.pollLast() + 1);
        } else {
            // A new second, start a fresh entry.
            timestamps.addLast(timestamp);
            counts.addLast(1);
        }
        totalHits++;
    }

    public int getHits(int timestamp) {
        // Evict entries that fell outside the window and shrink the running total.
        while (!timestamps.isEmpty() && timestamp - timestamps.peekFirst() >= windowSizeSeconds) {
            timestamps.pollFirst();
            totalHits -= counts.pollFirst();
        }
        return totalHits;
    }
}
```

The `hit` method is still O(1), and `getHits` is still amortized O(1). The difference is memory. Storage now grows with the number of distinct timestamps inside the window, not the total hit count. A burst of a million hits on one second becomes a single entry with count 1,000,000, instead of a million entries. This is the improvement the follow-up question is asking for.