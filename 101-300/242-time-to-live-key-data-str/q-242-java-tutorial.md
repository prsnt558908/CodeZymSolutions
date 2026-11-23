# Time To Live Key Data Structure in Java

#### Problem Statement
[https://codezym.com/question/242-time-to-live-key-data-str](https://codezym.com/question/242-time-to-live-key-data-str)

---

Every key instance in this problem lives for the same fixed time, `ttl`, and time only moves forward. So the instance that was added first will always be the first one to expire.

That is exactly how a **queue** works: first in, first out. We keep all instances in a queue and, before every method call, remove the expired ones from the front.

To answer count questions instantly, we also keep a **map** from each key to its number of active instances. The total count is simply the size of the queue. With this, every method runs in O(1) time on average.

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

```java
class KeyInstance {
    String key;
    int createdTimeStamp;

    KeyInstance(String key, int createdTimeStamp) {
        this.key = key;
        this.createdTimeStamp = createdTimeStamp;
    }
}
```

The simplest idea is to keep every instance in a list. Before each method, we check the whole list and delete the expired instances. To count one key, we loop over the list again.

```java
List<KeyInstance> instances = new ArrayList<>();

// checks every instance in the list and deletes the expired ones
void removeExpired(int currentTimeStamp) {
    instances.removeIf(item -> item.createdTimeStamp + ttl <= currentTimeStamp);
}

public int countActive(String key, int currentTimeStamp) {
    removeExpired(currentTimeStamp);
    int count = 0;
    for (KeyInstance item : instances) {
        if (item.key.equals(key)) count++;
    }
    return count;
}
```

`addKey` calls `removeExpired` and then appends to the list. `countAllActiveKeys` calls `removeExpired` and returns the list size.

### Problems with this approach

- `removeExpired` checks **every** instance on **every** call, even when nothing has expired.
- `countActive` loops over the whole list again just to count one key.
- With `n` stored instances, each call costs O(n). With many calls and many keys, this gets slow very quickly.

---

## Approach 2: Queue + Count Map (Optimal)

We fix the problems of the brute force one at a time.

### Step 1: Use a queue, because expired instances are always at the front

Timestamps never go backwards, so instances are added oldest first. All of them share the same `ttl`, so the oldest one always expires first.

This means expired instances always sit together at the **front**. Cleanup only needs to look at the front instance: remove it if it has expired, and stop as soon as it is still active.

A **queue** does exactly this: add at the back, remove from the front, both in O(1). A priority queue (heap) is not needed, since instances already arrive in the order they will expire.

Removing an expired instance for good is safe. Time only moves forward, so it can never become active again.

### Step 2: Use a map to count each key

Looping over the queue to count one key is still slow. So we keep a map `activeCount` from each key to its number of active instances.

- When a key is added, its count goes up by 1.
- When an instance is removed from the queue, its key's count goes down by 1.

Now `countActive` is a single map lookup.

### Step 3: The queue size is the total count

After cleanup, the queue holds only active instances. So `countAllActiveKeys` just returns the queue size. No extra counter is needed.

### Why these data structures?

| Data structure | Why we need it |
|---|---|
| `Queue<KeyInstance>` (an `ArrayDeque`) | Keeps instances in the order they were added. Expired ones are always at the front, so cleanup only touches the front. |
| `Map<String, Integer> activeCount` | Gives the active count of any key in O(1), without looping over the queue. |

---

## Dry Run

Let's trace Example 2 with `ttl = 4`. Here `cat@10` means an instance of `"cat"` added at time `10`.

| Call | Removed during cleanup | Queue after the call | `activeCount` | Returns |
|---|---|---|---|---|
| `addKey("cat", 10)` | none | `[cat@10]` | `{cat=1}` | - |
| `addKey("cat", 11)` | none | `[cat@10, cat@11]` | `{cat=2}` | - |
| `countActive("cat", 11)` | none | `[cat@10, cat@11]` | `{cat=2}` | `2` |
| `countActive("cat", 14)` | `cat@10`, since `10 + 4 <= 14` | `[cat@11]` | `{cat=1}` | `1` |
| `addKey("dog", 14)` | none | `[cat@11, dog@14]` | `{cat=1, dog=1}` | - |
| `countAllActiveKeys(14)` | none | `[cat@11, dog@14]` | `{cat=1, dog=1}` | `2` |

At time `14`, cleanup removes `cat@10` and stops at `cat@11`, because `11 + 4 = 15` is greater than `14`.

---

## Complexity

- **Time:** O(1) on average for every method. One call may remove many instances at once. But each instance is added once and removed at most once. So across all calls, the total cleanup work is never more than the number of keys added. This is called **amortized O(1)**.
- **Space:** O(n), where `n` is the number of active key instances.

---

## Java Code

```java
import java.util.*;

/**
 * One added instance of a key, along with the time it was added.
 */
class KeyInstance {
    String key;
    int createdTimeStamp;

    KeyInstance(String key, int createdTimeStamp) {
        this.key = key;
        this.createdTimeStamp = createdTimeStamp;
    }
}

public class TimeToLiveKeyStore {
    int ttl;

    // all instances in the order they were added, oldest at the front
    Queue<KeyInstance> queue = new ArrayDeque<>();

    // key -> number of active instances of that key
    Map<String, Integer> activeCount = new HashMap<>();

    public TimeToLiveKeyStore(int ttl) {
        this.ttl = ttl;
    }

    public void addKey(String key, int currentTimeStamp) {
        removeExpired(currentTimeStamp);
        queue.add(new KeyInstance(key, currentTimeStamp));
        activeCount.put(key, activeCount.getOrDefault(key, 0) + 1);
    }

    public int countActive(String key, int currentTimeStamp) {
        removeExpired(currentTimeStamp);
        return activeCount.getOrDefault(key, 0);
    }

    public int countAllActiveKeys(int currentTimeStamp) {
        removeExpired(currentTimeStamp);
        // after cleanup, every instance left in the queue is active
        return queue.size();
    }

    /**
     * Removes expired instances from the front of the queue.
     * The oldest instance is always at the front, so we can stop
     * at the first instance that is still active.
     */
    void removeExpired(int currentTimeStamp) {
        while (!queue.isEmpty() && queue.peek().createdTimeStamp + ttl <= currentTimeStamp) {
            KeyInstance expired = queue.poll();
            int count = activeCount.get(expired.key) - 1;
            if (count == 0) {
                // drop keys with no active instances so the map stays small
                activeCount.remove(expired.key);
            } else {
                activeCount.put(expired.key, count);
            }
        }
    }
}
```