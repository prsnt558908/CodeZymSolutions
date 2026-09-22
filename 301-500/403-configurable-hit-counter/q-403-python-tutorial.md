# Configurable Time Window Hit Counter in Python

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
- `getHits(timestamp)`: pop timestamps off the front while they are too old (`timestamp - front >= windowSizeSeconds`), then the queue's length is the answer.

Python's `collections.deque` is built for exactly this. A plain list can also act like a queue, but removing from the front of a list is O(n) because every remaining item has to shift over. A `deque` removes from either end in O(1), which is what keeps this fast.

```python
from collections import deque


class HitCounter:
    def __init__(self, windowSizeSeconds):
        self.windowSizeSeconds = windowSizeSeconds
        self.hit_timestamps = deque()

    # Record one hit by remembering its timestamp.
    def hit(self, timestamp):
        self.hit_timestamps.append(timestamp)

    # Drop hits that fell outside the window, then report how many remain.
    def getHits(self, timestamp):
        while self.hit_timestamps and timestamp - self.hit_timestamps[0] >= self.windowSizeSeconds:
            self.hit_timestamps.popleft()
        return len(self.hit_timestamps)
```

The `hit` method runs in O(1) time. The `getHits` method runs in amortized O(1) time, since each hit is added once and removed once over its lifetime.

The catch is memory. If a single second receives a million hits, we store a million separate entries for it, even though they will all expire together at the exact same moment. That does not scale well when traffic can spike hard on one timestamp, which is exactly the scenario the problem's follow-up question is asking about.

## Solution 2: Group Hits By Timestamp

Instead of storing every hit separately, we can store one entry per distinct timestamp, along with a count of how many hits happened at that timestamp. If a new hit shares the timestamp with the most recent entry, we just increase that entry's count instead of adding a new one.

We keep two deques that move together, one holding the distinct timestamps and one holding the matching counts. We also keep a running total of hits currently inside the window, so `getHits` does not need to add up the counts every time. It just adjusts the running total whenever an entry is evicted.

Two plain deques are enough here because timestamps only ever grow. We never need to search or reorder anything, so there is no need to reach for a dictionary or a sorted structure. This keeps memory proportional to the number of distinct seconds that actually received a hit, not the number of hits.

```python
from collections import deque


class HitCounter:
    def __init__(self, windowSizeSeconds):
        self.windowSizeSeconds = windowSizeSeconds
        self.timestamps = deque()  # distinct timestamps, oldest first
        self.counts = deque()      # hit count for the matching timestamp
        self.total_hits = 0        # hits currently inside the window

    def hit(self, timestamp):
        if self.timestamps and self.timestamps[-1] == timestamp:
            # Same second as the last recorded hit, bump its count.
            self.counts[-1] += 1
        else:
            # A new second, start a fresh entry.
            self.timestamps.append(timestamp)
            self.counts.append(1)
        self.total_hits += 1

    def getHits(self, timestamp):
        # Evict entries that fell outside the window and shrink the running total.
        while self.timestamps and timestamp - self.timestamps[0] >= self.windowSizeSeconds:
            self.timestamps.popleft()
            self.total_hits -= self.counts.popleft()
        return self.total_hits
```

The `hit` method is still O(1), and `getHits` is still amortized O(1). The difference is memory. Storage now grows with the number of distinct timestamps inside the window, not the total hit count. A burst of a million hits on one second becomes a single entry with count 1,000,000, instead of a million entries. This is the improvement the follow-up question is asking for.