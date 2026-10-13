# Design a TTL (Time to Live) Cache in Python

#### Problem Statement
[https://codezym.com/question/132-design-time-to-live-cache](https://codezym.com/question/132-design-time-to-live-cache)

---

We need to build a cache where every key lives only for a limited time. Once that time is over, the key is gone.


The core idea is simple. For every key, we store the exact time at which it expires. In this problem, time never goes backwards. So an expired entry can never become valid again. This means we can safely delete it. If the same key is added again later, it simply gets a fresh entry.


The final solution uses two simple data structures:

- A **dictionary** (`dict`) to find any key quickly.
- A **min heap** (Python's `heapq` module) sorted by expiry time. The key that expires first is always on top.


Before every operation, we pop expired entries from the top of the heap and remove those keys from the dictionary. After this cleanup, the dictionary holds only active keys. So `get` is a plain dictionary lookup, and the active key count is just the dictionary size.


No design pattern is needed here. The hard part is picking the right data structures, not organizing many classes. A dictionary plus a min heap is the simplest and fastest solution.

---

## Do We Need a Design Pattern?

Two patterns can look useful at first.


**Strategy pattern:** We could hide the expiry rule behind a separate class, so it can be swapped later. But this problem has only one rule: a fixed TTL from insert time. A separate class for a single rule adds code but no value. It also does nothing for the real challenge, which is counting active keys fast.


**Decorator pattern:** We could build a plain key-value store and wrap it with a TTL layer. But there is no existing store to wrap. TTL is the whole purpose of this class. The extra layer would only make the code longer.


So we keep it simple: one `TTLCache` class and one small `CacheEntry` class.

---

## Solution 1: Simple Dictionary (Brute Force)

### Idea

For every key, we save its value and its expiry time:

`expiry_time = currentTime + ttlSeconds`


A key is valid while `currentTime < expiry_time`. It is expired once `currentTime >= expiry_time`.


- `put`: save a new entry. If the key already exists, the new entry replaces the old one.
- `get`: look up the key. If it is missing or expired, return `""`.
- `getActiveKeyCount`: loop over all entries and count the ones that are not expired.


### Why `CacheEntry`?

Each key has two things attached to it: a value and an expiry time. `CacheEntry` keeps them together, so the dictionary can store one object per key.


### Code

```python
class CacheEntry:
    """Value of a key along with the time at which it expires."""

    def __init__(self, value, expiry_time):
        self.value = value
        self.expiry_time = expiry_time  # the key is valid while currentTime < expiry_time


class TTLCache:
    def __init__(self):
        # key -> its value and expiry time
        self.entries = {}

    def put(self, key, value, ttlSeconds, currentTime):
        # Replaces the old entry if the key already exists
        self.entries[key] = CacheEntry(value, currentTime + ttlSeconds)

    def get(self, key, currentTime):
        entry = self.entries.get(key)
        if entry is None or entry.expiry_time <= currentTime:
            return ""
        return entry.value

    def getActiveKeyCount(self, currentTime):
        # Checks every key, one by one. This is the slow part.
        count = 0
        for entry in self.entries.values():
            if entry.expiry_time > currentTime:
                count += 1
        return count
```


### Problems With This Solution

1. **Counting is slow.** `getActiveKeyCount` checks every key on every call. With `n` keys, one call costs O(n). With up to 10^5 calls, this can reach billions of steps.
2. **Memory keeps growing.** Expired keys are never deleted. They stay in the dictionary and keep using memory.


Both problems have the same cause: we never remove expired keys. Let's fix that.

---

## Solution 2: Dictionary + Min Heap (Optimal)

### Key Observation

`currentTime` never goes backwards. If an entry has expired at time 20, it is also expired at time 21, 22 and forever after. A later `put` on the same key does not bring it back. It creates a brand new entry.


So it is safe to **delete expired entries from the dictionary**. If the dictionary holds only active keys, then:

- `get` is a simple dictionary lookup.
- `getActiveKeyCount` is just `len(self.entries)`.


The only question left: how do we find expired keys fast, without scanning the whole dictionary?


### Finding Expired Keys Fast: Min Heap

We also push every entry into a min heap, sorted by expiry time. Python's `heapq` module turns a normal list into a min heap. The entry that expires first is always at index 0.


Before every operation, we run a cleanup:

1. Look at the item on top of the heap.
2. If it has expired (`expiry_time <= currentTime`), pop it and delete its key from the dictionary.
3. Repeat until the top item is still valid, or the heap is empty.


We stop at the first valid item. Valid keys further down the heap are never touched.


**Why not a simple queue?** Keys do not expire in the order they are added. A key added at time 1 with TTL 100 expires long after a key added at time 2 with TTL 3. The heap always gives us the soonest expiry, no matter the insert order.


**Why is each heap item a tuple `(expiry_time, key)`?** Python compares tuples by their first value, so the soonest expiry stays on top. The key is there so that, after popping, we know which key to delete from the dictionary.


### Handling Updates

There is one catch. When a key is updated, its old item is still sitting in the heap. Removing an item from the middle of a heap is slow.


So we leave it there. Every time we pop an item, we double check with the dictionary:

- If the key's **latest** entry in the dictionary has also expired, delete the key.
- Otherwise, the popped item is old. The key was either updated with a new expiry time, or it is already deleted. We just drop the popped item.


This way, an old heap item can never delete a key that is still valid.


### Dry Run (Example 3)

| Call | What happens | Dictionary | Heap |
|---|---|---|---|
| `put(A, apple, 5, 10)` | A expires at 15. | A: apple, 15 | (15, A) |
| `put(A, apricot, 10, 12)` | Nothing has expired yet. A now expires at 22. | A: apricot, 22 | (15, A), (22, A) |
| `get(A, 16)` | Pop (15, A). Dictionary says A expires at 22, which is still ahead. So this heap item is old. Drop it. Return `"apricot"`. | A: apricot, 22 | (22, A) |
| `get(A, 22)` | Pop (22, A). Dictionary says A expires at 22, which is now. So A has expired. Delete it. Return `""`. | empty | empty |


### Code

```python
import heapq


class CacheEntry:
    """Value of a key along with the time at which it expires."""

    def __init__(self, value, expiry_time):
        self.value = value
        self.expiry_time = expiry_time  # the key is valid while currentTime < expiry_time


class TTLCache:
    def __init__(self):
        # key -> latest entry of that key. After cleanup, only active keys remain here.
        self.entries = {}
        # Min heap of (expiry_time, key) tuples. The soonest expiry is always at index 0.
        self.expiry_heap = []

    def put(self, key, value, ttlSeconds, currentTime):
        self.remove_expired(currentTime)
        expiry_time = currentTime + ttlSeconds
        # Replaces the old entry of this key, if any.
        # The old heap item stays in the heap and gets dropped when it reaches the top.
        self.entries[key] = CacheEntry(value, expiry_time)
        heapq.heappush(self.expiry_heap, (expiry_time, key))

    def get(self, key, currentTime):
        self.remove_expired(currentTime)
        entry = self.entries.get(key)
        if entry is None:
            return ""
        return entry.value

    def getActiveKeyCount(self, currentTime):
        self.remove_expired(currentTime)
        return len(self.entries)

    def remove_expired(self, current_time):
        """Deletes every key that has expired at current_time.

        Time never goes backwards, so an expired entry can never become valid again.
        """
        while self.expiry_heap and self.expiry_heap[0][0] <= current_time:
            expiry_time, key = heapq.heappop(self.expiry_heap)
            latest = self.entries.get(key)
            # Delete only if the key's latest entry has expired.
            # Otherwise this heap item is an old entry of an updated or already deleted key.
            if latest is not None and latest.expiry_time <= current_time:
                del self.entries[key]
```


### Complexity

| Operation | Solution 1 | Solution 2 |
|---|---|---|
| `put` | O(1) | O(log n) |
| `get` | O(1) | O(1) + cleanup |
| `getActiveKeyCount` | O(n) | O(1) + cleanup |


Here `n` is the number of stored entries.


One cleanup may pop many items. But every item enters the heap once and leaves it at most once. So over all calls, cleanup adds only O(log n) per `put`.


**Space:** the heap may keep old items of updated keys until their expiry time comes. So it can hold up to one item per `put` call.