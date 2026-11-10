# Maximum Power Assigned To Machines in Java

#### Problem Statement
[https://codezym.com/question/173-maximum-power-assigned-to-machines](https://codezym.com/question/173-maximum-power-assigned-to-machines)

---

## Core Idea

Instead of searching for the best power directly, we ask a simpler yes/no question: **"Can every machine get at least `x` power?"**

This question is easy to answer for any single `x`. And the answers follow a clear pattern: if `x` works, every smaller value works too.

That pattern lets us use **binary search on the answer**. Rather than trying every possible power, we keep cutting the range of possible answers in half until we find the largest `x` that works.

---

## Checking One Power Value

Each machine gets power from exactly one source. So for a power value `x`, a source with capacity `p` can serve `p / x` machines (integer division).

For example, a source with `8` units and `x = 3` can serve `8 / 3 = 2` machines. The last `2` units are wasted, because no machine can mix power from two sources.

To check if `x` works, we add up the machines that every source can serve and compare the total with `machineCount`.

```java
public boolean canPowerAllMachines(List<Integer> sources, int machineCount, int power) {
    long machines = 0;
    for (int source : sources) {
        machines += source / power;
    }
    return machines >= machineCount;
}
```

This is one pass over the list, so it takes `O(n)` time.

**Why `long`?** The total can get very large. With `power = 1` and `100,000` sources of `100,000,000` each, it is `10^13`. That is far beyond the `int` limit (about `2.1 billion`), so `long` keeps the check correct for any power.

---

## Brute Force: Try Every Power

The answer can never be bigger than the largest source, because a machine takes power from only one source.

So we can try every power from the largest source down to `1`. The first power that works is the answer.

```java
for (int power = Collections.max(sources); power >= 1; power--) {
    if (canPowerAllMachines(sources, machineCount, power)) {
        return power; // first power that works, from the top, is the largest
    }
}
return 0; // even power 1 does not work
```

### Why this is slow

The largest source can be `100,000,000`, and each check loops over up to `100,000` sources.

In the worst case that is about `10^13` steps, which is far too slow.

---

## Optimal Solution: Binary Search on the Answer

### The key observation

Giving each machine **less** power can never hurt. If every machine can get `x` power, then every machine can surely get `x - 1`.

Here is Example 1, with `sources = [5, 8, 6]` and `machineCount = 4`:

| Power `x` | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Machines powered | 19 | 9 | 5 | 4 | 3 | 2 | 1 | 1 |
| Works? | Yes | Yes | Yes | Yes | No | No | No | No |

The answers are all **Yes** first and then all **No**. We want the **last Yes**.

When the answers are lined up like this, binary search can find the switch point without checking every value.

### How the search works

We search between `low = 1` and `high` = the largest source.

At each step we check the middle value `mid`:

- If `mid` works, we save it as the best answer so far and search the right half for something bigger.
- If `mid` does not work, it is too big, so we search the left half.

We start with `answer = 0`. If even power `1` does not work (like Example 4), the answer simply stays `0`.

### Dry run on Example 1

The search range is `1` to `8`.

| low | high | mid | Machines powered | Works? | Next step |
|---|---|---|---|---|---|
| 1 | 8 | 4 | 1 + 2 + 1 = 4 | Yes | answer = 4, low = 5 |
| 5 | 8 | 6 | 0 + 1 + 1 = 2 | No | high = 5 |
| 5 | 5 | 5 | 1 + 1 + 1 = 3 | No | high = 4 |

Now `low > high`, so the search stops. The answer is `4`.

### Complexity

Let `n` be the number of sources and `M` be the largest source.

- **Time:** `O(n × log M)`. Binary search needs about `log2(100,000,000) ≈ 27` checks, and each check is `O(n)`.
- **Space:** `O(1)`. We only use a few variables.

That is about `2.7 million` steps in the worst case, instead of `10^13`.

---

## Final Code

```java
import java.util.*;

public class PowerSourceAllocator {

    public PowerSourceAllocator() {
    }

    /**
     * Returns the largest power x such that every machine can get at least x power.
     * Returns 0 if even power 1 is not possible.
     */
    public int maximumMinimumPower(List<Integer> sources, int machineCount) {
        int low = 1;
        // A machine takes power from only one source,
        // so it can never get more than the biggest source has.
        int high = Collections.max(sources);
        int answer = 0; // stays 0 if no positive power works

        while (low <= high) {
            int mid = low + (high - low) / 2; // same as (low + high) / 2, but safe from overflow

            if (canPowerAllMachines(sources, machineCount, mid)) {
                answer = mid;   // mid works, remember it
                low = mid + 1;  // and try a bigger power
            } else {
                high = mid - 1; // mid is too big, try a smaller power
            }
        }
        return answer;
    }

    /**
     * Checks if every machine can get at least 'power' units.
     * A source with capacity p can serve p / power machines.
     */
    public boolean canPowerAllMachines(List<Integer> sources, int machineCount, int power) {
        long machines = 0; // long, because this total can be bigger than the int limit
        for (int source : sources) {
            machines += source / power;
        }
        return machines >= machineCount;
    }
}
```