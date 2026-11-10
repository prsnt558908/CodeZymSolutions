# Circular Delivery Route in Java

#### Problem Statement
[https://codezym.com/question/445-circular-delivery-route](https://codezym.com/question/445-circular-delivery-route)

## Core Idea

Every trip has only two real choices: go clockwise or go counterclockwise. Turning back midway would only add extra time. So we find the cost of both directions and keep the smaller one.

The cost of each direction is just the sum of `zoneTimes` over a range of zones that sit next to each other on the circle. So the whole problem comes down to one question: how fast can we add up a range of numbers?

Walking zone by zone takes `O(m)` per trip. **Prefix sums** answer the same question in `O(1)`, after a single `O(m)` setup. That gives the required `O(m + q)` solution.

## Solution 1: Brute Force (Walk the Road)

Time is charged for the zone the rider **leaves**, not the zone the rider enters. The destination is never charged, because the rider does not leave it during that trip.

For example, with `zoneTimes = [4, 1, 7, 2, 5]`, a trip from zone `1` to zone `4` costs:

- **Clockwise:** leave zones 1, 2, 3, so `4 + 1 + 7 = 12`
- **Counterclockwise:** leave zones 1, 5, so `4 + 5 = 9`

The best choice is `9`.

The simplest way to get these numbers is to really walk the circle, one zone at a time, and add the time of every zone we leave.

```java
// Walk zone by zone and add the time of every zone we leave
long walk(List<Integer> zoneTimes, int start, int end, boolean clockwise) {
    int m = zoneTimes.size();
    long time = 0;
    int zone = start;
    while (zone != end) {
        time += zoneTimes.get(zone - 1);
        if (clockwise) {
            zone = (zone == m) ? 1 : zone + 1; // after zone m comes zone 1
        } else {
            zone = (zone == 1) ? m : zone - 1; // before zone 1 comes zone m
        }
    }
    return time;
}
```

For each stop, we walk both ways and keep the cheaper one:

```java
long totalTime = 0;
int current = 1;
for (int stop : deliveryStops) {
    long clockwise = walk(zoneTimes, current, stop, true);
    long counterClockwise = walk(zoneTimes, current, stop, false);
    totalTime += Math.min(clockwise, counterClockwise);
    current = stop;
}
```

### Why is this slow?

One trip can walk almost the full circle, which is up to `m` steps. With `q` trips, the total work is `O(m × q)`.

With `m` and `q` both up to 100,000, that can be 10 billion steps. Far too slow.

## Solution 2: Prefix Sums (Optimal)

Look at what `walk` really does. It adds up `zoneTimes` for a range of zones that are next to each other.

We repeat this for every trip, adding the same numbers again and again. Prefix sums fix this. We do the adding once, and after that any range sum is just a subtraction.

### Step 1: Build the prefix array

`prefix[i]` is the total time of zones `1` to `i`. We keep `prefix[0] = 0`, so zone numbers line up with array positions and no special case is needed.

For `zoneTimes = [4, 1, 7, 2, 5]`:

| i | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| prefix[i] | 0 | 4 | 5 | 12 | 14 | 19 |

**Why this array?** There can be 100,000 trips, and each one asks "what is the total time of this range of zones?". The prefix array turns each of these questions into one or two subtractions.

We store it as `long[]`. A single trip can cost around 5 billion minutes, which is more than an `int` can hold.

### Step 2: Total time of any range on the circle

One helper does all the work: `rangeTime(first, last)`. It returns the total time of zones `first, first + 1, ..., last`, moving clockwise.

**Normal range** (`first <= last`): zones 2 to 4 cost `prefix[4] - prefix[1] = 14 - 4 = 10`. That is `1 + 7 + 2`.

**Wrapping range** (`first > last`): the range goes past zone `m` and continues from zone `1`. We split it into two normal parts: `first` to `m`, and `1` to `last`.

Zones 4 to 1 (that is 4, 5, 1) cost `(prefix[5] - prefix[3]) + prefix[1] = (19 - 12) + 4 = 11`. That is `2 + 5 + 4`.

### Step 3: Pick the range for each direction

For a trip from zone `current` to zone `stop`:

- **Clockwise:** the rider leaves `current`, `current + 1`, ... up to the zone just before `stop`. So the range is `current` to `zoneBeforeStop`.
- **Counterclockwise:** the rider leaves `current`, `current - 1`, ... down to the zone just after `stop`. Read in clockwise order, that is the range `zoneAfterStop` to `current`.

Because the road is a circle, the zone before `1` is `m`, and the zone after `m` is `1`.

Now both directions use the same helper, and each trip costs `O(1)`.

### Step 4: Skip trips to the same zone

If `stop == current`, the trip is free, so we skip it.

This check is required, not just a shortcut. Without it, the range "from `current` to the zone just before `current`" would wrap all the way around and charge the full circle.

### Walkthrough

Example 1: `zoneTimes = [4, 1, 7, 2, 5]`, `deliveryStops = [4, 2, 2, 5]`

| Trip | Clockwise | Counterclockwise | Best |
|---|---|---|---|
| 1 → 4 | `rangeTime(1, 3)` = 12 | `rangeTime(5, 1)` = 9 | 9 |
| 4 → 2 | `rangeTime(4, 1)` = 11 | `rangeTime(3, 4)` = 9 | 9 |
| 2 → 2 | skipped | skipped | 0 |
| 2 → 5 | `rangeTime(2, 4)` = 10 | `rangeTime(1, 2)` = 5 | 5 |

Total: `9 + 9 + 0 + 5 = 23`

### Complexity

- **Time:** `O(m + q)`. Building the prefix array is `O(m)`, and each trip is `O(1)`.
- **Space:** `O(m)` for the prefix array.

### Java Code

```java
import java.util.*;

public class DeliveryRoutePlanner {

    // prefix[i] = total time of zones 1 to i. prefix[0] = 0.
    long[] prefix;

    // number of zones
    int m;

    public DeliveryRoutePlanner() {
    }

    public long minimumTravelTime(List<Integer> zoneTimes, List<Integer> deliveryStops) {
        m = zoneTimes.size();

        // Build the prefix sums once: O(m)
        prefix = new long[m + 1];
        for (int i = 1; i <= m; i++) {
            prefix[i] = prefix[i - 1] + zoneTimes.get(i - 1);
        }

        long totalTime = 0;
        int current = 1; // the rider starts in zone 1

        for (int stop : deliveryStops) {
            // Already in this zone, so the trip is free
            if (stop == current) {
                continue;
            }

            // Neighbors of the stop on the circle
            int zoneBeforeStop = (stop == 1) ? m : stop - 1;
            int zoneAfterStop = (stop == m) ? 1 : stop + 1;

            // Clockwise: we leave current, current + 1, ... up to zoneBeforeStop
            long clockwise = rangeTime(current, zoneBeforeStop);

            // Counterclockwise: we leave current, current - 1, ... down to zoneAfterStop.
            // In clockwise order, that is the range zoneAfterStop to current.
            long counterClockwise = rangeTime(zoneAfterStop, current);

            totalTime += Math.min(clockwise, counterClockwise);
            current = stop;
        }

        return totalTime;
    }

    /**
     * Total time of zones first, first + 1, ..., last, moving clockwise.
     * If the range wraps past zone m, it is split into
     * (first to m) and (1 to last).
     */
    long rangeTime(int first, int last) {
        if (first <= last) {
            return prefix[last] - prefix[first - 1];
        }
        return (prefix[m] - prefix[first - 1]) + prefix[last];
    }
}
```