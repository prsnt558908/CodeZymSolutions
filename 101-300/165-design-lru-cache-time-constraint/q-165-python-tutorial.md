# Design LRU Cache With Time Constraint in Python

#### Problem Statement

[https://codezym.com/question/165-design-lru-cache-time-constraint](https://codezym.com/question/165-design-lru-cache-time-constraint)

An LRU cache needs to know which key was used least recently. This cache also needs to know which key expires first. Keep two ordered maps, one for each job. That way, you can clear expired keys before deciding whether to evict a key that is still valid.

## Start with a full scan

Keep the values in recency order and store each key's expiry time in a second map. Before each `get` or `put`, scan the expiry map and delete expired keys from both maps.

```python
for key, expiry in list(self.expiresAt.items()):
    if expiry <= currentTime:
        del self.values[key]
        del self.expiresAt[key]
```

This works, but a full cache makes you check `capacity` entries even when none have expired.

## Why the two orders differ

Suppose the cache holds two keys and the time to live (TTL) is 5 seconds. Write `A` at time `1`, write `B` at `2`, and read `A` at `3`.

`B` is now the least recently used key. `A` still expires first at time `6`, because reading it does not reset its expiry.

If you write `C` at time `6`, remove `A` first. `B` and `C` both fit, so there is no need for LRU eviction.

## Keep both maps in order

- **`values`** is an `OrderedDict` in recency order. A hit or a write uses `move_to_end`, leaving the least recently used key at the front. Recency follows call order even when calls share a timestamp.
- **`expiresAt`** is an `OrderedDict` in expiry order. Every `put` sets the expiry to `currentTime + ttlSeconds` and moves the key to the end. Time never goes backward and the TTL is the same for every key, so the earliest expiry stays at the front.

Start each call by removing keys whose expiry is at or before `currentTime` from both maps. Stop at the first key that is still valid.

On a `get` miss, return `"NOT_FOUND"`. If a `put` pushes the cache over capacity, evict the first key in `values` and delete its expiry record.

## Time and space

A call that removes `k` expired keys takes **O(1 + k)** time with average constant-time map operations. Each `put` adds only one expiry record, so the cost is **O(1) amortized per call**. The two maps use **O(capacity)** space.

## Python code

```python
from collections import OrderedDict


class LRUCacheWithTimeConstraint:
    def __init__(self, capacity: int, ttlSeconds: int) -> None:
        self.capacity = capacity
        self.ttlSeconds = ttlSeconds
        self.values = OrderedDict()
        self.expiresAt = OrderedDict()

    def put(self, key: str, value: str, currentTime: int) -> None:
        self.removeExpired(currentTime)

        self.values[key] = value
        self.values.move_to_end(key)
        self.expiresAt[key] = currentTime + self.ttlSeconds
        self.expiresAt.move_to_end(key)

        if len(self.values) > self.capacity:
            leastRecentKey, _ = self.values.popitem(last=False)
            del self.expiresAt[leastRecentKey]

    def get(self, key: str, currentTime: int) -> str:
        self.removeExpired(currentTime)
        value = self.values.get(key)
        if value is None:
            return "NOT_FOUND"

        self.values.move_to_end(key)
        return value

    def removeExpired(self, currentTime: int) -> None:
        while self.expiresAt:
            key, expiry = next(iter(self.expiresAt.items()))
            # The remaining keys expire at this time or later.
            if expiry > currentTime:
                break

            del self.values[key]
            del self.expiresAt[key]
```
