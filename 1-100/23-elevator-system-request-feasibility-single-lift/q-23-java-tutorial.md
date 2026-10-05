# Design an Elevator System - Request Feasibility (Single Lift) in Java

#### Problem Statement
[https://codezym.com/question/23-elevator-system-request-feasibility-single-lift](https://codezym.com/question/23-elevator-system-request-feasibility-single-lift)

---

The lift starts moving only after all requests have arrived. So for every new request we only need to answer one question: "If we accept this rider too, can the lift still serve everyone without breaking a rule?"


We never have to search for a clever plan. The lift must halt wherever someone gets in or gets out, and any extra halt only adds stops for the people inside. So the best plan is always the same: halt only at those floors. We check the two rules (capacity and stop limit) on this one plan and then accept or reject.


UP riders and DOWN riders never clash, so the problem splits into two separate passes of the lift, one going up and one going down. We first write a simple version that rechecks every rider on each request. Then we make it fast with a few small arrays indexed by floor, since there are at most 100 floors.

---

## Understanding the Rules

The lift makes one **UP pass** (bottom to top) and one **DOWN pass** (top to bottom). Every request belongs to exactly one of them.


We call the space between floor `f` and floor `f + 1` the **floor gap `f`**. A rider travelling between floors `low` and `high` is inside the lift for gaps `low` to `high - 1`.


At a halt, people get out first and then new people get in. So a rider leaving at floor 5 and a rider boarding at floor 5 never share a gap.


Inside one pass, two rules must hold for the new rider and for every rider accepted earlier:

1. **Capacity:** no floor gap has more than `liftCapacity` riders inside.
2. **Stop limit:** the number of halts strictly between a rider's two floors is at most `maxStops`.

If both rules hold, we accept. Otherwise we reject and change nothing.

---

## Key Observations

### 1. Halt only where someone gets in or out

Halts at boarding floors and destination floors are a must. Any other halt is useless: nobody gets in or out, and it adds one more stop for everyone riding past it.


So the best possible plan halts only at these must-have floors. If this plan breaks a rule, every other plan breaks it too, and we can reject right away.


Example 1 (`floorsCount = 30, liftCapacity = 3, maxStops = 2`) shows the idea:

```
floor       0                   10  12    15    18  20
A (0, 20)   |---------------------------------------|
B (10, 15)                      |---------|
C (12, 18)                          |-----------|
```

After A and B, the halts are {0, 10, 15, 20}. A sees 10 and 15 strictly inside its range: 2 stops, which is fine.


If we add C, the halts become {0, 10, 12, 15, 18, 20}. Now A sees 10, 12, 15 and 18: 4 stops, more than 2. So C is rejected.

### 2. Inside a pass, only the floor range matters

A DOWN rider from 14 to 3 is inside the lift for gaps 3 to 13, and the halts that count for them are at floors 4 to 13. An UP rider from 3 to 14 has exactly the same gaps and the same counted floors.


So inside a pass we save every trip as a range `(low, high)`, where `low = min(source, destination)` and `high = max(source, destination)`. The same code then works for both passes. The direction is used only once: to pick the right pass.

---

## Solution 1: Simple Approach (Recheck Everyone)

Each pass keeps a list of accepted trips. For a new trip `(low, high)`:

1. **Capacity:** for each floor gap of the new trip, count how many trips cover it. Only these gaps need a check, because no other gap changed.
2. **Halt plan:** put both floors of every trip in a set.
3. **Stop limit:** for every trip, count the halts strictly between its two floors.
4. If nothing went over its limit, save the trip and return `true`.

**Why a separate `LiftPass` class?** UP and DOWN riders never meet, so their data must stay apart. Two `LiftPass` objects, one per direction, keep it apart while sharing the same code. `LiftSystem` only picks the right pass.


**Why a `Set` for halts?** Many riders share floors, and the rule counts distinct floors. A set keeps each floor only once.


We also return `false` for a floor outside `0` to `floorsCount - 1`, so bad input can never break anything.

```java
import java.util.*;

public class LiftSystem {
    int floorsCount;
    LiftPass upPass, downPass;   // UP and DOWN riders never clash, so each direction gets its own pass

    public LiftSystem(int floorsCount, int liftCapacity, int maxStops) {
        this.floorsCount = floorsCount;
        upPass = new LiftPass(liftCapacity, maxStops);
        downPass = new LiftPass(liftCapacity, maxStops);
    }

    public boolean requestPickup(int source, int destination) {
        if (!isValidFloor(source) || !isValidFloor(destination)) return false;
        LiftPass pass = source < destination ? upPass : downPass;
        // inside a pass only the floor range matters, so a trip 14 -> 3 is saved as (3, 14)
        return pass.tryAddRider(Math.min(source, destination), Math.max(source, destination));
    }

    boolean isValidFloor(int floor) {
        return floor >= 0 && floor < floorsCount;
    }
}

/**
 * One pass of the lift: all UP riders or all DOWN riders.
 * Simple version: keeps every accepted trip and rechecks all of them on each request.
 */
class LiftPass {
    int liftCapacity, maxStops;
    List<int[]> trips = new ArrayList<>();   // accepted trips, each saved as {low, high}

    LiftPass(int liftCapacity, int maxStops) {
        this.liftCapacity = liftCapacity;
        this.maxStops = maxStops;
    }

    /** Saves the trip (low, high) if both rules still hold. Returns true if it was saved. */
    boolean tryAddRider(int low, int high) {
        List<int[]> allTrips = new ArrayList<>(trips);
        allTrips.add(new int[]{low, high});

        // Rule 1, capacity: count riders inside for every floor gap of the new trip
        for (int f = low; f < high; f++) {
            int insideLift = 0;
            for (int[] trip : allTrips) {
                if (trip[0] <= f && f < trip[1]) insideLift++;
            }
            if (insideLift > liftCapacity) return false;
        }

        // The lift halts only where someone gets in or out
        Set<Integer> halts = new HashSet<>();
        for (int[] trip : allTrips) {
            halts.add(trip[0]);
            halts.add(trip[1]);
        }

        // Rule 2, stop limit: count halts strictly inside every trip
        for (int[] trip : allTrips) {
            int stops = 0;
            for (int halt : halts) {
                if (trip[0] < halt && halt < trip[1]) stops++;
            }
            if (stops > maxStops) return false;
        }

        trips.add(new int[]{low, high});
        return true;
    }
}
```

### Problems with this approach

- Every request walks over all accepted trips again, even though most of them did not change.
- With `R` accepted riders and `F` floors, one request costs about `R × F` steps. `R` can reach about 2,000 per pass (20 riders on each of the 99 floor gaps), so a long stream of requests repeats a lot of the same work.

---

## Solution 2: Better Approach (Arrays Indexed by Floor)

There are at most 100 floors. So instead of a list of trips, each pass keeps a few small arrays indexed by floor. Now the work per request depends only on the number of floors, not on how many riders were accepted. We improve the simple solution one part at a time.

### Improvement 1: Keep the load of every floor gap

`load[f]` = number of riders inside while the lift moves between floor `f` and floor `f + 1`.


A new trip fits if every gap from `low` to `high - 1` still has a free spot. When we accept it, we add 1 to those gaps. We never count old trips again.

### Improvement 2: Remember halts in a boolean array

`isHalt[f]` is `true` if someone gets in or out at floor `f`.


To test a new rider, we treat its two floors as halts without changing the array. If the request is rejected, there is nothing to undo.

### Improvement 3: Only the longest trip from each floor matters

Take two trips with the same low floor, like `(2, 9)` and `(2, 6)`. Every floor strictly between 2 and 6 is also strictly between 2 and 9. So `(2, 9)` always sees at least as many halts as `(2, 6)`. If the longer trip is within the limit, the shorter one is too.


So for each floor we keep just one number: `farthest[f]` = the highest floor of any trip whose low floor is `f`, or `-1` if there is none. Now we check at most `F` trips, no matter how many riders were accepted.

### Improvement 4: Count halts with a running count

`haltsBelow[f]` = number of halt floors below floor `f`. We build it in one walk over the floors, with the new rider's two floors included.


Then the halts strictly between `bottom` and `top` are just `haltsBelow[top] - haltsBelow[bottom + 1]`. One subtraction instead of a loop.

### Steps for a new trip `(low, high)`

1. **Capacity:** if any gap from `low` to `high - 1` already has `load[f] == liftCapacity`, reject.
2. Build `haltsBelow`, treating `low` and `high` as halts.
3. **Stop limit:** for every floor `bottom` that has a trip, take `top = farthest[bottom]` and reject if `haltsBelow[top] - haltsBelow[bottom + 1] > maxStops`. For `bottom == low`, the new trip counts too, so `top` is the larger of `farthest[low]` and `high`.
4. **Save:** add 1 to `load` on the new gaps, mark `low` and `high` as halts and update `farthest[low]`.

All checks come before any change, so a rejected request leaves no trace.


**Dry run** (Example 1, third request `(12, 18)`): gaps 12 to 17 have a load of 1 or 2, below the capacity of 3, so capacity is fine. The halts would be {0, 10, 12, 15, 18, 20}. The longest trip from floor 0 is `(0, 20)`, and `haltsBelow[20] - haltsBelow[1]` = 5 - 1 = 4 halts (10, 12, 15, 18). That is more than 2, so we reject.

```java
import java.util.*;

public class LiftSystem {
    int floorsCount;
    LiftPass upPass, downPass;   // UP and DOWN riders never clash, so each direction gets its own pass

    public LiftSystem(int floorsCount, int liftCapacity, int maxStops) {
        this.floorsCount = floorsCount;
        upPass = new LiftPass(floorsCount, liftCapacity, maxStops);
        downPass = new LiftPass(floorsCount, liftCapacity, maxStops);
    }

    public boolean requestPickup(int source, int destination) {
        if (!isValidFloor(source) || !isValidFloor(destination)) return false;
        LiftPass pass = source < destination ? upPass : downPass;
        // inside a pass only the floor range matters, so a trip 14 -> 3 is saved as (3, 14)
        return pass.tryAddRider(Math.min(source, destination), Math.max(source, destination));
    }

    boolean isValidFloor(int floor) {
        return floor >= 0 && floor < floorsCount;
    }
}

/**
 * One pass of the lift: all UP riders or all DOWN riders.
 * Better version: small arrays indexed by floor, so the work does not grow with the number of riders.
 */
class LiftPass {
    int floorsCount, liftCapacity, maxStops;
    int[] load;          // load[f] = riders inside while the lift moves between floor f and f + 1
    boolean[] isHalt;    // isHalt[f] = true if someone gets in or out at floor f
    int[] farthest;      // farthest[f] = highest floor of any trip whose low floor is f, -1 if none

    LiftPass(int floorsCount, int liftCapacity, int maxStops) {
        this.floorsCount = floorsCount;
        this.liftCapacity = liftCapacity;
        this.maxStops = maxStops;
        load = new int[floorsCount];
        isHalt = new boolean[floorsCount];
        farthest = new int[floorsCount];
        Arrays.fill(farthest, -1);
    }

    /** Saves the trip (low, high) if both rules still hold. Returns true if it was saved. */
    boolean tryAddRider(int low, int high) {
        // Rule 1, capacity: every floor gap of the new trip needs one free spot
        for (int f = low; f < high; f++) {
            if (load[f] == liftCapacity) return false;
        }

        // Running count of halts, as if the new rider is already accepted.
        // haltsBelow[f] = number of halt floors below floor f
        int[] haltsBelow = new int[floorsCount + 1];
        for (int f = 0; f < floorsCount; f++) {
            boolean halt = isHalt[f] || f == low || f == high;
            haltsBelow[f + 1] = haltsBelow[f] + (halt ? 1 : 0);
        }

        // Rule 2, stop limit: only the longest trip from each low floor needs a check
        for (int bottom = 0; bottom < floorsCount; bottom++) {
            int top = farthest[bottom];
            if (bottom == low) top = Math.max(top, high);   // include the new rider
            if (top == -1) continue;                         // no trip has this low floor
            int stops = haltsBelow[top] - haltsBelow[bottom + 1];   // halts strictly between bottom and top
            if (stops > maxStops) return false;
        }

        // Both rules hold, so save the rider
        for (int f = low; f < high; f++) load[f]++;
        isHalt[low] = true;
        isHalt[high] = true;
        farthest[low] = Math.max(farthest[low], high);
        return true;
    }
}
```

---

## Complexity

`F` = `floorsCount`, `R` = accepted riders in the same direction.

| Solution | Time per request | Extra memory |
|---|---|---|
| Simple (recheck everyone) | O(R × F) | O(R) |
| Better (arrays indexed by floor) | O(F) | O(F) |

Since `F` is at most 100, the better solution does only a few hundred simple steps per request, however many riders are already in the plan.