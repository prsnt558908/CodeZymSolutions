# Time To Live Key Data Structure in Python

#### Problem Statement
[https://codezym.com/question/242-time-to-live-key-data-str](https://codezym.com/question/242-time-to-live-key-data-str)

---

Every key instance in this problem lives for the same fixed time, `ttl`, and time only moves forward. So the instance that was added first will always be the first one to expire.

That is exactly how a **queue** works: first in, first out. We keep all instances in a queue and, before every method call, remove the expired ones from the front.

To answer count questions instantly, we also keep a **dict** from each key to its number of active instances. The total count is simply the size of the queue. With this, every method runs in O(1) time on average.

---

## Understanding the Problem

A key instance added at time `createdTimeStamp` is:

- **Active** while `createdTimeStamp + ttl > currentTimeStamp`
- **Expired** once `createdTimeStamp + ttl <= currentTimeStamp`

For example, with `ttl = 4`, an instance added at time `10` is active from time `10` to `13`. From time `14` onwards it is expired.

Two details are easy to miss:

1. Adding the same key again creates a **new, separate instance**. Each instance expires on its own.
2. `countAllActiveKeys` counts **instances**, not distinct key names. Two active `"cat"` instances count as `2`.

---

## Approach 1: Brute Force with a List

Each instance needs two things: its key and the time it was added. A small class holds both.

```python
class KeyInstance:
    def __init__(self, key: str, createdTimeStamp: int):
        self.key = key
        self.createdTimeStamp = createdTimeStamp
```

The simplest idea is to keep every instance in a list. Before each method, we check the whole list and keep only the active instances. To count one key, we loop over the list again.

```python
def __init__(self, ttl: int):
    self.ttl = ttl
    self.instances = []  # every stored key instance

# checks every instance in the list and keeps only the active ones
def removeExpired(self, currentTimeStamp: int) -> None:
    self.instances = [item for item in self.instances
                      if item.createdTimeStamp + self.ttl > currentTimeStamp]

def countActive(self, key: str, currentTimeStamp: int) -> int:
    self.removeExpired(currentTimeStamp)
    count = 0
    for item in self.instances:
        if item.key == key:
            count += 1
    return count
```

`addKey` calls `removeExpired` and then appends to the list. `countAllActiveKeys` calls `removeExpired` and returns the list length.

### Problems with this approach

- `removeExpired` checks **every** instance on **every** call, even when nothing has expired.
- `countActive` loops over the whole list again just to count one key.
- With `n` stored instances, each call costs O(n). With many calls and many keys, this gets slow very quickly.

---

## Approach 2: Queue + Count Dict (Optimal)

We fix the problems of the brute force one at a time.

### Step 1: Use a queue, because expired instances are always at the front

Timestamps never go backwards, so instances are added oldest first. All of them share the same `ttl`, so the oldest one always expires first.

This means expired instances always sit together at the **front**. Cleanup only needs to look at the front instance: remove it if it has expired, and stop as soon as it is still active.

A **`deque`** does exactly this: `append()` adds at the back and `popleft()` removes from the front, both in O(1). A plain list is not a good fit, because `list.pop(0)` shifts every remaining element and costs O(n).

A priority queue (heap) is not needed either, since instances already arrive in the order they will expire.

Removing an expired instance for good is safe. Time only moves forward, so it can never become active again.

### Step 2: Use a dict to count each key

Looping over the queue to count one key is still slow. So we keep a dict `activeCount` from each key to its number of active instances.

- When a key is added, its count goes up by 1.
- When an instance is removed from the queue, its key's count goes down by 1.

Now `countActive` is a single dict lookup.

### Step 3: The queue size is the total count

After cleanup, the queue holds only active instances. So `countAllActiveKeys` just returns `len(self.queue)`. No extra counter is needed.

### Why these data structures?

| Data structure | Why we need it |
|---|---|
| `deque` of `KeyInstance` | Keeps instances in the order they were added. Expired ones are always at the front, so cleanup only touches the front. |
| `dict` named `activeCount` | Gives the active count of any key in O(1), without looping over the queue. |

---

## Dry Run

Let's trace Example 2 with `ttl = 4`. Here `cat@10` means an instance of `"cat"` added at time `10`.

| Call | Removed during cleanup | Queue after the call | `activeCount` | Returns |
|---|---|---|---|---|
| `addKey("cat", 10)` | none | `[cat@10]` | `{cat: 1}` | - |
| `addKey("cat", 11)` | none | `[cat@10, cat@11]` | `{cat: 2}` | - |
| `countActive("cat", 11)` | none | `[cat@10, cat@11]` | `{cat: 2}` | `2` |
| `countActive("cat", 14)` | `cat@10`, since `10 + 4 <= 14` | `[cat@11]` | `{cat: 1}` | `1` |
| `addKey("dog", 14)` | none | `[cat@11, dog@14]` | `{cat: 1, dog: 1}` | - |
| `countAllActiveKeys(14)` | none | `[cat@11, dog@14]` | `{cat: 1, dog: 1}` | `2` |

At time `14`, cleanup removes `cat@10` and stops at `cat@11`, because `11 + 4 = 15` is greater than `14`.

---

## Complexity

- **Time:** O(1) on average for every method. One call may remove many instances at once. But each instance is added once and removed at most once. So across all calls, the total cleanup work is never more than the number of keys added. This is called **amortized O(1)**.
- **Space:** O(n), where `n` is the number of active key instances.

---

## Python Code

```python
from collections import deque


class KeyInstance:
    """One added instance of a key, along with the time it was added."""

    def __init__(self, key: str, createdTimeStamp: int):
        self.key = key
        self.createdTimeStamp = createdTimeStamp


class TimeToLiveKeyStore:

    def __init__(self, ttl: int):
        self.ttl = ttl
        # all instances in the order they were added, oldest at the front
        self.queue = deque()
        # key -> number of active instances of that key
        self.activeCount = {}

    def addKey(self, key: str, currentTimeStamp: int) -> None:
        self.removeExpired(currentTimeStamp)
        self.queue.append(KeyInstance(key, currentTimeStamp))
        self.activeCount[key] = self.activeCount.get(key, 0) + 1

    def countActive(self, key: str, currentTimeStamp: int) -> int:
        self.removeExpired(currentTimeStamp)
        return self.activeCount.get(key, 0)

    def countAllActiveKeys(self, currentTimeStamp: int) -> int:
        self.removeExpired(currentTimeStamp)
        # after cleanup, every instance left in the queue is active
        return len(self.queue)

    def removeExpired(self, currentTimeStamp: int) -> None:
        """
        Removes expired instances from the front of the queue.
        The oldest instance is always at the front, so we can stop
        at the first instance that is still active.
        """
        while self.queue and self.queue[0].createdTimeStamp + self.ttl <= currentTimeStamp:
            expired = self.queue.popleft()
            self.activeCount[expired.key] -= 1
            if self.activeCount[expired.key] == 0:
                # drop keys with no active instances so the dict stays small
                del self.activeCount[expired.key]
```