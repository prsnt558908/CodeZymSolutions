# Minimum Cabs With Wait Period for Scheduled Bookings in Java

#### Problem Statement
[https://codezym.com/question/218-min-cabs-with-wait-period](https://codezym.com/question/218-min-cabs-with-wait-period)


The trick is to stop thinking of a cab as free when a ride ends. A cab is free only at `end + waitPeriod`. So every booking keeps its cab busy from `start` until `end + waitPeriod`, and the wait period disappears from the problem.


For `minCabsRequired`, we go through bookings in order of start time and reuse a cab whenever one is already free. A min-heap tells us in one step which cab gets free first.


For `minimizeMaxRidesPerCab`, we first run a fast greedy: give each booking to the free cab with the fewest rides. It is usually already the best answer, and we can prove it when it reaches the lower bound `ceil(n / cabCount)`. When it does not, backtracking looks for a better answer. It tries the useful choices for each booking, skips situations it has already explored, and stops any branch that cannot win.


---

## The Key Idea: A Cab Stays Busy Until `end + waitPeriod`

Take booking `"0,30"` with `waitPeriod = 5`. The ride ends at 30, but the cab can start its next ride only at 35.


So we turn every booking into a pair `{start, busyUntil}`, where `busyUntil = end + waitPeriod`.


Now the rule is simple: two bookings can share a cab only if one of them is done before the other starts, that is `busyUntilA <= startB`.


`busyUntil` can reach 2,000,000,000, which is very close to the `int` limit. So we store times as `long` to be safe.

---

## Part 1: Minimum Cabs Required

### Simple Approach: Check Every Cab

Sort bookings by start time. For each booking, look through the cabs we already have. If one is free, give it the booking. If none is free, add a new cab.

```java
// cabFreeAt.get(i) = time at which cab i becomes free
List<Long> cabFreeAt = new ArrayList<>();
for (long[] booking : sortedBookings) {          // booking = {start, busyUntil}
    boolean reused = false;
    for (int i = 0; i < cabFreeAt.size(); i++) {
        if (cabFreeAt.get(i) <= booking[0]) {
            cabFreeAt.set(i, booking[1]);
            reused = true;
            break;
        }
    }
    if (!reused) {
        cabFreeAt.add(booking[1]);
    }
}
return cabFreeAt.size();
```


**Why is any free cab fine here?** We visit bookings by start time, so every later booking starts at the same time or later. A free cab stays usable for all of them.


**Why is the count the minimum?** We add a new cab only when every cab is busy. At that moment, all those bookings and the new one are running together, so we really need that many cabs.


**The problem:** for each booking we may scan every cab. With many cabs this is `O(n²)`, too slow for 100,000 bookings.


### Better Approach: Min-Heap of Free Times

We don't need to check every cab. We only need the cab that becomes free **first**. If even that cab is still busy, every other cab is busy too.


A min-heap (`PriorityQueue`) gives us the smallest value quickly. We keep one value per cab: the time it becomes free.


For each booking, in order of start time:

1. If the top of the heap is `<= start`, that cab is free. Remove it, because this cab takes the booking.
2. Add the booking's `busyUntil`. This is the new free time of the cab that took it.


At the end, the heap holds one value per cab, so its size is the answer.


**Example 2:** bookings `"5,15"`, `"15,25"`, `"25,35"`, `waitPeriod = 1`.

| Booking | busyUntil | Heap top | Action | Heap after |
|---|---|---|---|---|
| `"5,15"` | 16 | empty | new cab | [16] |
| `"15,25"` | 26 | 16 > 15, busy | new cab | [16, 26] |
| `"25,35"` | 36 | 16 <= 25, free | reuse | [26, 36] |

The heap ends with 2 values, so 2 cabs are needed.

---

## Part 2: Minimize Maximum Rides Per Cab

### When Is It Impossible?

If `minCabsRequired` is more than `cabCount`, some booking will find no free cab, so we return `-1`. Otherwise a valid assignment always exists, and we search for the best one.


The answer can never be smaller than `ceil(n / cabCount)`. If `n` rides go to `cabCount` cabs, some cab gets at least that many. We call this the **lower bound**.


### Simple Approach: Fewest Rides First

In Part 1, any free cab was fine. Now it matters, because every ride adds to that cab's count.


A natural rule is "give the booking to the free cab with the fewest rides". A cab that was never used counts as 0 rides. This spreads rides out, and with two min-heaps it runs in `O(n log n)`:

- `busyCabs` holds cabs that are on a ride, as `{freeAt, rides}`. The cab that gets free first is on top, so before each booking we move every finished cab out of it.
- `freeCabRides` holds the ride counts of free cabs. The fewest rides are on top, so picking the right cab is one `poll()`.


The result is a real assignment, so its max rides is a valid answer. If it equals the lower bound, nothing can beat it and we are done. This happens very often.


But the rule is not always right. Here it fails:

`bookings = "0,1", "2,10", "3,4", "5,6"`, `cabCount = 2`, `waitPeriod = 0`

| Booking | Free cabs (rides so far) | Rule picks |
|---|---|---|
| `"0,1"` | A (0), B (0) | A |
| `"2,10"` | A (1), B (0) | B |
| `"3,4"` | A (1). B is busy until 10 | A |
| `"5,6"` | A (2). B is busy until 10 | A |

Cab A ends with 3 rides. The best answer is 2: cab A takes `"0,1"` and `"2,10"`, cab B takes `"3,4"` and `"5,6"`.


The right cab for a booking depends on bookings that come later. To be sure, we must try the choices.


### Brute Force: Try Every Cab for Every Booking

Go through bookings in start order. For each one, try every free cab, go deeper, then undo the choice.

```java
void tryAll(int index, int currentMax) {
    if (index == n) {
        best = Math.min(best, currentMax);
        return;
    }
    for (int cab = 0; cab < cabCount; cab++) {
        if (cabFreeAt[cab] <= starts[index]) {
            long oldFreeAt = cabFreeAt[cab];
            cabFreeAt[cab] = busyUntil[index];      // give the booking to this cab
            cabRides[cab]++;
            tryAll(index + 1, Math.max(currentMax, cabRides[cab]));
            cabRides[cab]--;                        // undo
            cabFreeAt[cab] = oldFreeAt;
        }
    }
}
```


This is always correct, but it tries up to `cabCount^n` combinations. Most of them are copies of each other.


### Better Approach: Greedy First, Then Backtracking With Cuts

**Step 1:** run the greedy. Its answer becomes `best`, the score to beat. If `best` equals the lower bound, return it.


**Step 2:** backtracking looks only for answers smaller than `best`. Because bookings go in start order, each cab needs only two numbers: when it becomes free and how many rides it has. That is why we keep two plain arrays, `cabFreeAt` and `cabRides`. More cabs than bookings never helps, so the arrays hold at most `n` cabs.


**Cut 1: skip copies.**

- All cabs with no rides are the same. We try only one of them.
- Two free cabs with the same ride count are also the same. Both are free now, and every later booking starts at this time or later, so they stay equally useful. We try each ride count once, using a `HashSet` of counts already tried.


**Cut 2: stop branches that cannot win.** If some cab already has `best` rides, this branch can only tie or lose, so we stop.


**Cut 3: stop at the lower bound.** Once `best` equals `ceil(n / cabCount)`, nothing can be better, so the whole search ends.


**Cut 4: never explore the same situation twice.** Different choices often lead to the same situation: the same next booking, and the same cabs with the same free times and ride counts. If a situation was fully explored and did not beat `best`, it never will, because `best` only goes down. So we save each explored situation as a text key in a `HashSet` called `deadStates`. Free cabs all look the same, so in the key a free cab's time is replaced by the current start time. The cabs are sorted, so their order does not matter.


**Walkthrough** on the example above (lower bound = `ceil(4 / 2) = 2`):

1. The greedy gives 3, which is above the lower bound, so we search with `best = 3`.
2. The first path gives `"2,10"` to the empty cab B. Cab A then reaches 3 rides, which cannot beat 3, so Cut 2 stops it.
3. Backtracking gives `"2,10"` to cab A instead. Cab B takes `"3,4"` and `"5,6"`, so `best = 2`.
4. `best` now equals the lower bound, so the search stops. The answer is 2.

---

## Final Java Code

```java
import java.util.*;

public class CabScheduler {

    // Search data for minimizeMaxRidesPerCab
    long[] starts;          // start time of each booking, sorted
    long[] busyUntil;       // end + waitPeriod of each booking, same order as starts
    long[] cabFreeAt;       // time at which each cab becomes free again
    int[] cabRides;         // rides given to each cab so far
    int cabsUsed;           // cabs 0 .. cabsUsed - 1 have at least one ride
    int maxCabs;            // cabs we are allowed to use
    int best;               // smallest "max rides per cab" found so far
    int lowerBound;         // the answer can never be smaller than this
    Set<String> deadStates; // situations already explored that cannot beat best

    public CabScheduler() {
    }

    /**
     * Returns the minimum number of cabs needed to finish all bookings.
     */
    public int minCabsRequired(List<String> bookings, int waitPeriod) {
        // Min-heap. Each value is the time at which one cab becomes free.
        PriorityQueue<Long> freeTimes = new PriorityQueue<>();

        for (long[] booking : parseAndSort(bookings, waitPeriod)) {
            // Reuse the cab that gets free first, if it is free by this start time.
            if (!freeTimes.isEmpty() && freeTimes.peek() <= booking[0]) {
                freeTimes.poll();
            }
            freeTimes.add(booking[1]);
        }
        return freeTimes.size();
    }

    /**
     * Returns the smallest possible value of the maximum rides given to one cab,
     * or -1 if cabCount cabs are not enough.
     */
    public int minimizeMaxRidesPerCab(List<String> bookings, int cabCount, int waitPeriod) {
        if (minCabsRequired(bookings, waitPeriod) > cabCount) {
            return -1;
        }

        List<long[]> sorted = parseAndSort(bookings, waitPeriod);
        int n = sorted.size();
        lowerBound = (n + cabCount - 1) / cabCount;  // ceil(n / cabCount)

        // Step 1: fast greedy. Most of the time it already reaches the lower bound.
        best = greedyMaxRides(sorted, cabCount);
        if (best == lowerBound) {
            return best;
        }

        // Step 2: backtracking looks only for answers smaller than the greedy one.
        starts = new long[n];
        busyUntil = new long[n];
        for (int i = 0; i < n; i++) {
            starts[i] = sorted.get(i)[0];
            busyUntil[i] = sorted.get(i)[1];
        }
        maxCabs = Math.min(cabCount, n);   // more cabs than bookings never helps
        cabFreeAt = new long[maxCabs];
        cabRides = new int[maxCabs];
        cabsUsed = 0;
        deadStates = new HashSet<>();

        assignBooking(0, 0);
        return best;
    }

    /**
     * Gives each booking to the free cab with the fewest rides (an unused cab has 0)
     * and returns the highest ride count any cab ends up with.
     */
    int greedyMaxRides(List<long[]> sorted, int cabCount) {
        // Cabs on a ride as {freeAt, rides}: the cab that gets free first on top.
        PriorityQueue<long[]> busyCabs = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        // Ride counts of free cabs: the fewest on top.
        PriorityQueue<Integer> freeCabRides = new PriorityQueue<>();
        int unusedCabs = cabCount;
        int maxRides = 0;

        for (long[] booking : sorted) {
            // Cabs whose ride plus wait is over become free.
            while (!busyCabs.isEmpty() && busyCabs.peek()[0] <= booking[0]) {
                freeCabRides.add((int) busyCabs.poll()[1]);
            }
            int rides;
            if (unusedCabs > 0) {   // an unused cab has 0 rides, the fewest possible
                unusedCabs--;
                rides = 0;
            } else {
                rides = freeCabRides.poll();
            }
            busyCabs.add(new long[]{booking[1], rides + 1});
            maxRides = Math.max(maxRides, rides + 1);
        }
        return maxRides;
    }

    /**
     * Tries every useful cab for booking "index", then moves to the next booking.
     * currentMax is the highest ride count of any cab so far.
     */
    void assignBooking(int index, int currentMax) {
        // Stop if this branch cannot beat the best answer, or nothing can.
        if (currentMax >= best || best == lowerBound) {
            return;
        }
        if (index == starts.length) {
            best = currentMax;   // every booking has a cab
            return;
        }

        // Same situation explored before without beating best: skip it.
        String state = stateKey(index);
        if (deadStates.contains(state)) {
            return;
        }

        // Choice 1: a cab with no rides yet. All of them are the same, so try one.
        if (cabsUsed < maxCabs) {
            cabFreeAt[cabsUsed] = busyUntil[index];
            cabRides[cabsUsed] = 1;
            cabsUsed++;
            assignBooking(index + 1, Math.max(currentMax, 1));
            cabsUsed--;
        }

        // Choice 2: a free cab that already has rides.
        // Free cabs with the same ride count are the same, so try each count once.
        Set<Integer> triedCounts = new HashSet<>();
        for (int cab = 0; cab < cabsUsed && best > lowerBound; cab++) {
            if (cabFreeAt[cab] <= starts[index] && triedCounts.add(cabRides[cab])) {
                long oldFreeAt = cabFreeAt[cab];
                cabFreeAt[cab] = busyUntil[index];
                cabRides[cab]++;

                assignBooking(index + 1, Math.max(currentMax, cabRides[cab]));

                cabRides[cab]--;            // undo the choice
                cabFreeAt[cab] = oldFreeAt;
            }
        }

        deadStates.add(state);
    }

    /**
     * Describes the situation before booking "index": every used cab as "freeAt:rides".
     */
    String stateKey(int index) {
        List<String> cabs = new ArrayList<>();
        for (int cab = 0; cab < cabsUsed; cab++) {
            // All free cabs look the same, so a free cab's time becomes the current start.
            long freeAt = Math.max(cabFreeAt[cab], starts[index]);
            cabs.add(freeAt + ":" + cabRides[cab]);
        }
        Collections.sort(cabs);
        return index + "|" + cabs;
    }

    /**
     * Converts "start,end" strings into {start, end + waitPeriod} pairs sorted by start.
     */
    List<long[]> parseAndSort(List<String> bookings, int waitPeriod) {
        List<long[]> result = new ArrayList<>();
        for (String booking : bookings) {
            String[] parts = booking.split(",");
            long start = Long.parseLong(parts[0].trim());
            long end = Long.parseLong(parts[1].trim());
            result.add(new long[]{start, end + waitPeriod});
        }
        result.sort((a, b) -> Long.compare(a[0], b[0]));
        return result;
    }
}
```

---

## Complexity

**minCabsRequired:** sorting takes `O(n log n)`, and each booking does at most one heap removal and one insert, each `O(log n)`. Total time `O(n log n)`, space `O(n)`.


**minimizeMaxRidesPerCab:** the greedy takes `O(n log n)`, and very often it is the whole cost. The backtracking runs only when the greedy misses the lower bound. It is exponential in the worst case, but the four cuts keep it fast for small and mid-size inputs. Extra space is `O(n)` for the arrays and the recursion, plus the saved situations in `deadStates`.