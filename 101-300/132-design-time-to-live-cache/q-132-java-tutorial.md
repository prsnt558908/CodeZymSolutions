# Design a TTL (Time to Live) Cache in Java

#### Problem Statement
[https://codezym.com/question/132-design-time-to-live-cache](https://codezym.com/question/132-design-time-to-live-cache)

---

We need to build a cache where every key lives only for a limited time. Once that time is over, the key is gone.


The core idea is simple. For every key, we store the exact time at which it expires. In this problem, time never goes backwards. So an expired entry can never become valid again. This means we can safely delete it. If the same key is added again later, it simply gets a fresh entry.


The final solution uses two simple data structures:

- A **HashMap** to find any key quickly.
- A **min heap** (`PriorityQueue`) sorted by expiry time. The key that expires first is always on top.


Before every operation, we pop expired keys from the top of the heap and delete them from the map. After this cleanup, the map holds only active keys. So `get` is a plain map lookup, and the active key count is just the map size.


No design pattern is needed here. The hard part is picking the right data structures, not organizing many classes. A HashMap plus a min heap is the simplest and fastest solution.

---

## Do We Need a Design Pattern?

Two patterns can look useful at first.


**Strategy pattern:** We could hide the expiry rule behind an interface, so it can be swapped later. But this problem has only one rule: a fixed TTL from insert time. An interface with just one implementation adds code but no value. It also does nothing for the real challenge, which is counting active keys fast.


**Decorator pattern:** We could build a plain key-value store and wrap it with a TTL layer. But there is no existing store to wrap. TTL is the whole purpose of this class. The extra layer would only make the code longer.


So we keep it simple: one `TTLCache` class and one small `CacheEntry` class.

---

## Solution 1: Simple HashMap (Brute Force)

### Idea

For every key, we save its value and its expiry time:

`expiryTime = currentTime + ttlSeconds`


A key is valid while `currentTime < expiryTime`. It is expired once `currentTime >= expiryTime`.


- `put`: save a new entry. If the key already exists, the new entry replaces the old one.
- `get`: look up the key. If it is missing or expired, return `""`.
- `getActiveKeyCount`: loop over all entries and count the ones that are not expired.


### Why `CacheEntry`?

Each key has two things attached to it: a value and an expiry time. `CacheEntry` keeps them together, so the map can store one object per key.


### Code

```java
import java.util.HashMap;
import java.util.Map;

public class TTLCache {

    // key -> its value and expiry time
    Map<String, CacheEntry> entries = new HashMap<>();

    public TTLCache() {
    }

    public void put(String key, String value, int ttlSeconds, int currentTime) {
        // Replaces the old entry if the key already exists
        entries.put(key, new CacheEntry(value, currentTime + ttlSeconds));
    }

    public String get(String key, int currentTime) {
        CacheEntry entry = entries.get(key);
        if (entry == null || entry.expiryTime <= currentTime) {
            return "";
        }
        return entry.value;
    }

    public int getActiveKeyCount(int currentTime) {
        // Checks every key, one by one. This is the slow part.
        int count = 0;
        for (CacheEntry entry : entries.values()) {
            if (entry.expiryTime > currentTime) {
                count++;
            }
        }
        return count;
    }
}

/**
 * Value of a key along with the time at which it expires.
 */
class CacheEntry {
    String value;
    int expiryTime; // the key is valid while currentTime < expiryTime

    CacheEntry(String value, int expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }
}
```


### Problems With This Solution

1. **Counting is slow.** `getActiveKeyCount` checks every key on every call. With `n` keys, one call costs O(n). With up to 10^5 calls, this can reach billions of steps.
2. **Memory keeps growing.** Expired keys are never deleted. They stay in the map and keep using memory.


Both problems have the same cause: we never remove expired keys. Let's fix that.

---

## Solution 2: HashMap + Min Heap (Optimal)

### Key Observation

`currentTime` never goes backwards. If an entry has expired at time 20, it is also expired at time 21, 22 and forever after. A later `put` on the same key does not bring it back. It creates a brand new entry.


So it is safe to **delete expired entries from the map**. If the map holds only active keys, then:

- `get` is a simple map lookup.
- `getActiveKeyCount` is just `entries.size()`.


The only question left: how do we find expired keys fast, without scanning the whole map?


### Finding Expired Keys Fast: Min Heap

We also add every entry to a min heap, sorted by expiry time. The entry that expires first is always on top.


Before every operation, we run a cleanup:

1. Look at the entry on top of the heap.
2. If it has expired (`expiryTime <= currentTime`), pop it and delete its key from the map.
3. Repeat until the top entry is still valid, or the heap is empty.


We stop at the first valid entry. Valid keys further down the heap are never touched.


**Why not a simple queue?** Keys do not expire in the order they are added. A key added at time 1 with TTL 100 expires long after a key added at time 2 with TTL 3. The heap always gives us the soonest expiry, no matter the insert order.


**Why does `CacheEntry` now store the key?** When we pop an entry from the heap, we need to know which key to delete from the map.


### Handling Updates

There is one catch. When a key is updated, its old entry is still sitting in the heap. Removing an item from the middle of a heap is slow.


So we leave it there. Every time we pop an entry, we double check with the map:

- If the key's **latest** entry in the map has also expired, delete the key.
- Otherwise, the popped entry is old. The key was either updated with a new expiry time, or it is already deleted. We just drop the popped entry.


This way, an old heap entry can never delete a key that is still valid.


### Dry Run (Example 3)

| Call | What happens | Map | Heap |
|---|---|---|---|
| `put(A, apple, 5, 10)` | A expires at 15. | A: apple, 15 | (15, A) |
| `put(A, apricot, 10, 12)` | Nothing has expired yet. A now expires at 22. | A: apricot, 22 | (15, A), (22, A) |
| `get(A, 16)` | Pop (15, A). Map says A expires at 22, which is still ahead. So this heap entry is old. Drop it. Return `"apricot"`. | A: apricot, 22 | (22, A) |
| `get(A, 22)` | Pop (22, A). Map says A expires at 22, which is now. So A has expired. Delete it. Return `""`. | empty | empty |


### Code

```java
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class TTLCache {

    // key -> latest entry of that key. After cleanup, only active keys remain here.
    Map<String, CacheEntry> entries = new HashMap<>();

    // Min heap ordered by expiry time. The entry that expires first is always on top.
    PriorityQueue<CacheEntry> expiryHeap =
            new PriorityQueue<>((a, b) -> Integer.compare(a.expiryTime, b.expiryTime));

    public TTLCache() {
    }

    public void put(String key, String value, int ttlSeconds, int currentTime) {
        removeExpired(currentTime);
        CacheEntry entry = new CacheEntry(key, value, currentTime + ttlSeconds);
        // Replaces the old entry of this key, if any.
        // The old entry stays in the heap and gets dropped when it reaches the top.
        entries.put(key, entry);
        expiryHeap.add(entry);
    }

    public String get(String key, int currentTime) {
        removeExpired(currentTime);
        CacheEntry entry = entries.get(key);
        return entry == null ? "" : entry.value;
    }

    public int getActiveKeyCount(int currentTime) {
        removeExpired(currentTime);
        return entries.size();
    }

    /**
     * Deletes every key that has expired at currentTime.
     * Time never goes backwards, so an expired entry can never become valid again.
     */
    void removeExpired(int currentTime) {
        while (!expiryHeap.isEmpty() && expiryHeap.peek().expiryTime <= currentTime) {
            CacheEntry top = expiryHeap.poll();
            CacheEntry latest = entries.get(top.key);
            // Delete only if the key's latest entry has expired.
            // Otherwise 'top' is just an old entry of an updated or already deleted key.
            if (latest != null && latest.expiryTime <= currentTime) {
                entries.remove(top.key);
            }
        }
    }
}

/**
 * One version of a key: its value and the time at which it expires.
 */
class CacheEntry {
    String key;     // needed to find this key in the map after popping it from the heap
    String value;
    int expiryTime; // the key is valid while currentTime < expiryTime

    CacheEntry(String key, String value, int expiryTime) {
        this.key = key;
        this.value = value;
        this.expiryTime = expiryTime;
    }
}
```


### Complexity

| Operation | Solution 1 | Solution 2 |
|---|---|---|
| `put` | O(1) | O(log n) |
| `get` | O(1) | O(1) + cleanup |
| `getActiveKeyCount` | O(n) | O(1) + cleanup |


Here `n` is the number of stored entries.


One cleanup may pop many entries. But every entry enters the heap once and leaves it at most once. So over all calls, cleanup adds only O(log n) per `put`.


**Space:** the heap may keep old entries of updated keys until their expiry time comes. So it can hold up to one entry per `put` call.