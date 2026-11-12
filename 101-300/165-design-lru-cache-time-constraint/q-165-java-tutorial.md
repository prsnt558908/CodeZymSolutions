# Design LRU Cache With Time Constraint in Java

#### Problem Statement

[https://codezym.com/question/165-design-lru-cache-time-constraint](https://codezym.com/question/165-design-lru-cache-time-constraint)

An LRU cache needs to know which key was used least recently. This cache also needs to know which key expires first. Keep two ordered maps, one for each job. That way, you can clear expired keys before deciding whether to evict a key that is still valid.

## Start with a full scan

Keep the values in recency order and store each key's expiry time in a second map. Before each `get` or `put`, scan the expiry map and delete expired keys from both maps.

```java
expiresAt.entrySet().removeIf(entry -> {
    if (entry.getValue() > currentTime) {
        return false;
    }

    values.remove(entry.getKey());
    return true;
});
```

This works, but a full cache makes you check `capacity` entries even when none have expired.

## Why the two orders differ

Suppose the cache holds two keys and the time to live (TTL) is 5 seconds. Write `A` at time `1`, write `B` at `2`, and read `A` at `3`.

`B` is now the least recently used key. `A` still expires first at time `6`, because reading it does not reset its expiry.

If you write `C` at time `6`, remove `A` first. `B` and `C` both fit, so there is no need for LRU eviction.

## Keep both maps in order

- **`values`** is a `LinkedHashMap` in access order. A hit or a write moves the key to the end, leaving the least recently used key at the front. Recency follows call order even when calls share a timestamp.
- **`expiresAt`** is a `LinkedHashMap` in insertion order. Every `put` removes the old expiry record and adds `currentTime + ttlSeconds` at the end. Time never goes backward and the TTL is the same for every key, so the earliest expiry stays at the front.

Start each call by removing keys whose expiry is at or before `currentTime` from both maps. Stop at the first key that is still valid.

On a `get` miss, return `"NOT_FOUND"`. If a `put` pushes the cache over capacity, evict the first key in `values` and delete its expiry record.

## Time and space

A call that removes `k` expired keys takes **O(1 + k)** time with average constant-time map operations. Each `put` adds only one expiry record, so the cost is **O(1) amortized per call**. The two maps use **O(capacity)** space.

## Java code

```java
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCacheWithTimeConstraint {
    int capacity;
    int ttlSeconds;
    LinkedHashMap<String, String> values;
    LinkedHashMap<String, Integer> expiresAt;

    public LRUCacheWithTimeConstraint(int capacity, int ttlSeconds) {
        this.capacity = capacity;
        this.ttlSeconds = ttlSeconds;
        values = new LinkedHashMap<>(16, 0.75f, true);
        expiresAt = new LinkedHashMap<>();
    }

    public void put(String key, String value, int currentTime) {
        removeExpired(currentTime);

        values.put(key, value);
        // Reinsert the expiry so the refreshed key moves to the end.
        expiresAt.remove(key);
        expiresAt.put(key, currentTime + ttlSeconds);

        if (values.size() > capacity) {
            String leastRecentKey = values.keySet().iterator().next();
            values.remove(leastRecentKey);
            expiresAt.remove(leastRecentKey);
        }
    }

    public String get(String key, int currentTime) {
        removeExpired(currentTime);
        return values.getOrDefault(key, "NOT_FOUND");
    }

    void removeExpired(int currentTime) {
        Iterator<Map.Entry<String, Integer>> entries = expiresAt.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Integer> entry = entries.next();
            if (entry.getValue() > currentTime) {
                break;
            }

            values.remove(entry.getKey());
            entries.remove();
        }
    }
}
```
