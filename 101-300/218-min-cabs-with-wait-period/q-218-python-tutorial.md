# Minimum Cabs With Wait Period for Scheduled Bookings in Python

#### Problem Statement
[https://codezym.com/question/218-min-cabs-with-wait-period](https://codezym.com/question/218-min-cabs-with-wait-period)


The trick is to stop thinking of a cab as free when a ride ends. A cab is free only at `end + waitPeriod`. So every booking keeps its cab busy from `start` until `end + waitPeriod`, and the wait period disappears from the problem.


For `minCabsRequired`, we go through bookings in order of start time and reuse a cab whenever one is already free. A min-heap tells us in one step which cab gets free first.


For `minimizeMaxRidesPerCab`, we first run a fast greedy: give each booking to the free cab with the fewest rides. It is usually already the best answer, and we can prove it when it reaches the lower bound `ceil(n / cabCount)`. When it does not, backtracking looks for a better answer. It tries the useful choices for each booking, skips situations it has already explored, and stops any branch that cannot win.


---

## The Key Idea: A Cab Stays Busy Until `end + waitPeriod`

Take booking `"0,30"` with `waitPeriod = 5`. The ride ends at 30, but the cab can start its next ride only at 35.


So we turn every booking into a pair `[start, busyUntil]`, where `busyUntil = end + waitPeriod`.


Now the rule is simple: two bookings can share a cab only if one of them is done before the other starts, that is `busyUntilA <= startB`.

---

## Part 1: Minimum Cabs Required

### Simple Approach: Check Every Cab

Sort bookings by start time. For each booking, look through the cabs we already have. If one is free, give it the booking. If none is free, add a new cab.

```python
cabFreeAt = []   # cabFreeAt[i] = time at which cab i becomes free
for start, busyUntil in sortedBookings:
    reused = False
    for i in range(len(cabFreeAt)):
        if cabFreeAt[i] <= start:
            cabFreeAt[i] = busyUntil
            reused = True
            break
    if not reused:
        cabFreeAt.append(busyUntil)
return len(cabFreeAt)
```


**Why is any free cab fine here?** We visit bookings by start time, so every later booking starts at the same time or later. A free cab stays usable for all of them.


**Why is the count the minimum?** We add a new cab only when every cab is busy. At that moment, all those bookings and the new one are running together, so we really need that many cabs.


**The problem:** for each booking we may scan every cab. With many cabs this is `O(n²)`, too slow for 100,000 bookings.


### Better Approach: Min-Heap of Free Times

We don't need to check every cab. We only need the cab that becomes free **first**. If even that cab is still busy, every other cab is busy too.


A min-heap gives us the smallest value quickly. Python's `heapq` keeps the smallest value at `freeTimes[0]`. We keep one value per cab: the time it becomes free.


For each booking, in order of start time:

1. If `freeTimes[0] <= start`, that cab is free. Remove it, because this cab takes the booking.
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

- `busyCabs` holds cabs that are on a ride, as `[freeAt, rides]`. The cab that gets free first is on top, so before each booking we move every finished cab out of it.
- `freeCabRides` holds the ride counts of free cabs. The fewest rides are on top, so picking the right cab is one `heappop`.


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

```python
def tryAll(self, index, currentMax):
    if index == self.n:
        self.best = min(self.best, currentMax)
        return
    for cab in range(self.cabCount):
        if self.cabFreeAt[cab] <= self.starts[index]:
            oldFreeAt = self.cabFreeAt[cab]
            self.cabFreeAt[cab] = self.busyUntil[index]   # give the booking to this cab
            self.cabRides[cab] += 1
            self.tryAll(index + 1, max(currentMax, self.cabRides[cab]))
            self.cabRides[cab] -= 1                       # undo
            self.cabFreeAt[cab] = oldFreeAt
```


This is always correct, but it tries up to `cabCount^n` combinations. Most of them are copies of each other.


### Better Approach: Greedy First, Then Backtracking With Cuts

**Step 1:** run the greedy. Its answer becomes `best`, the score to beat. If `best` equals the lower bound, return it.


**Step 2:** backtracking looks only for answers smaller than `best`. Because bookings go in start order, each cab needs only two numbers: when it becomes free and how many rides it has. That is why we keep two plain lists, `cabFreeAt` and `cabRides`. More cabs than bookings never helps, so the lists hold at most `n` cabs. The search goes one level deeper for every booking, so we raise Python's recursion limit to at least `n + 1000`.


**Cut 1: skip copies.**

- All cabs with no rides are the same. We try only one of them.
- Two free cabs with the same ride count are also the same. Both are free now, and every later booking starts at this time or later, so they stay equally useful. We try each ride count once, using a `set` of counts already tried.


**Cut 2: stop branches that cannot win.** If some cab already has `best` rides, this branch can only tie or lose, so we stop.


**Cut 3: stop at the lower bound.** Once `best` equals `ceil(n / cabCount)`, nothing can be better, so the whole search ends.


**Cut 4: never explore the same situation twice.** Different choices often lead to the same situation: the same next booking, and the same cabs with the same free times and ride counts. If a situation was fully explored and did not beat `best`, it never will, because `best` only goes down. So we save each explored situation as a tuple in a `set` called `deadStates`. Free cabs all look the same, so in the key a free cab's time is replaced by the current start time. The cabs are sorted, so their order does not matter.


**Walkthrough** on the example above (lower bound = `ceil(4 / 2) = 2`):

1. The greedy gives 3, which is above the lower bound, so we search with `best = 3`.
2. The first path gives `"2,10"` to the empty cab B. Cab A then reaches 3 rides, which cannot beat 3, so Cut 2 stops it.
3. Backtracking gives `"2,10"` to cab A instead. Cab B takes `"3,4"` and `"5,6"`, so `best = 2`.
4. `best` now equals the lower bound, so the search stops. The answer is 2.

---

## Final Python Code

```python
import heapq
import sys
from typing import List


class CabScheduler:

    def __init__(self):
        # Search data for minimizeMaxRidesPerCab
        self.starts = []         # start time of each booking, sorted
        self.busyUntil = []      # end + waitPeriod of each booking, same order as starts
        self.cabFreeAt = []      # time at which each cab becomes free again
        self.cabRides = []       # rides given to each cab so far
        self.cabsUsed = 0        # cabs 0 .. cabsUsed - 1 have at least one ride
        self.maxCabs = 0         # cabs we are allowed to use
        self.best = 0            # smallest "max rides per cab" found so far
        self.lowerBound = 0      # the answer can never be smaller than this
        self.deadStates = set()  # situations already explored that cannot beat best

    def minCabsRequired(self, bookings: List[str], waitPeriod: int) -> int:
        """Returns the minimum number of cabs needed to finish all bookings."""
        # Min-heap. Each value is the time at which one cab becomes free.
        freeTimes = []

        for start, busyUntil in self.parseAndSort(bookings, waitPeriod):
            # Reuse the cab that gets free first, if it is free by this start time.
            if freeTimes and freeTimes[0] <= start:
                heapq.heappop(freeTimes)
            heapq.heappush(freeTimes, busyUntil)
        return len(freeTimes)

    def minimizeMaxRidesPerCab(self, bookings: List[str], cabCount: int, waitPeriod: int) -> int:
        """Returns the smallest possible value of the maximum rides given to one cab,
        or -1 if cabCount cabs are not enough."""
        if self.minCabsRequired(bookings, waitPeriod) > cabCount:
            return -1

        sortedBookings = self.parseAndSort(bookings, waitPeriod)
        n = len(sortedBookings)
        self.lowerBound = (n + cabCount - 1) // cabCount  # ceil(n / cabCount)

        # Step 1: fast greedy. Most of the time it already reaches the lower bound.
        self.best = self.greedyMaxRides(sortedBookings, cabCount)
        if self.best == self.lowerBound:
            return self.best

        # Step 2: backtracking looks only for answers smaller than the greedy one.
        self.starts = [start for start, _ in sortedBookings]
        self.busyUntil = [busyUntil for _, busyUntil in sortedBookings]
        self.maxCabs = min(cabCount, n)   # more cabs than bookings never helps
        self.cabFreeAt = [0] * self.maxCabs
        self.cabRides = [0] * self.maxCabs
        self.cabsUsed = 0
        self.deadStates = set()
        # The search goes one level deeper for every booking.
        sys.setrecursionlimit(max(sys.getrecursionlimit(), n + 1000))

        self.assignBooking(0, 0)
        return self.best

    def greedyMaxRides(self, sortedBookings: List[List[int]], cabCount: int) -> int:
        """Gives each booking to the free cab with the fewest rides (an unused cab has 0)
        and returns the highest ride count any cab ends up with."""
        busyCabs = []      # min-heap of [freeAt, rides]: the cab that gets free first on top
        freeCabRides = []  # min-heap of ride counts of free cabs: the fewest on top
        unusedCabs = cabCount
        maxRides = 0

        for start, busyUntil in sortedBookings:
            # Cabs whose ride plus wait is over become free.
            while busyCabs and busyCabs[0][0] <= start:
                heapq.heappush(freeCabRides, heapq.heappop(busyCabs)[1])
            if unusedCabs > 0:   # an unused cab has 0 rides, the fewest possible
                unusedCabs -= 1
                rides = 0
            else:
                rides = heapq.heappop(freeCabRides)
            heapq.heappush(busyCabs, [busyUntil, rides + 1])
            maxRides = max(maxRides, rides + 1)
        return maxRides

    def assignBooking(self, index: int, currentMax: int) -> None:
        """Tries every useful cab for booking "index", then moves to the next booking.
        currentMax is the highest ride count of any cab so far."""
        # Stop if this branch cannot beat the best answer, or nothing can.
        if currentMax >= self.best or self.best == self.lowerBound:
            return
        if index == len(self.starts):
            self.best = currentMax   # every booking has a cab
            return

        # Same situation explored before without beating best: skip it.
        state = self.stateKey(index)
        if state in self.deadStates:
            return

        # Choice 1: a cab with no rides yet. All of them are the same, so try one.
        if self.cabsUsed < self.maxCabs:
            self.cabFreeAt[self.cabsUsed] = self.busyUntil[index]
            self.cabRides[self.cabsUsed] = 1
            self.cabsUsed += 1
            self.assignBooking(index + 1, max(currentMax, 1))
            self.cabsUsed -= 1

        # Choice 2: a free cab that already has rides.
        # Free cabs with the same ride count are the same, so try each count once.
        triedCounts = set()
        for cab in range(self.cabsUsed):
            if self.best == self.lowerBound:
                return                   # nothing can be better, stop right away
            if self.cabFreeAt[cab] <= self.starts[index] and self.cabRides[cab] not in triedCounts:
                triedCounts.add(self.cabRides[cab])
                oldFreeAt = self.cabFreeAt[cab]
                self.cabFreeAt[cab] = self.busyUntil[index]
                self.cabRides[cab] += 1

                self.assignBooking(index + 1, max(currentMax, self.cabRides[cab]))

                self.cabRides[cab] -= 1          # undo the choice
                self.cabFreeAt[cab] = oldFreeAt

        self.deadStates.add(state)

    def stateKey(self, index: int) -> tuple:
        """Describes the situation before booking "index": every used cab as (freeAt, rides)."""
        cabs = []
        for cab in range(self.cabsUsed):
            # All free cabs look the same, so a free cab's time becomes the current start.
            freeAt = max(self.cabFreeAt[cab], self.starts[index])
            cabs.append((freeAt, self.cabRides[cab]))
        cabs.sort()
        return (index, tuple(cabs))

    def parseAndSort(self, bookings: List[str], waitPeriod: int) -> List[List[int]]:
        """Converts "start,end" strings into [start, end + waitPeriod] pairs sorted by start."""
        result = []
        for booking in bookings:
            start, end = booking.split(",")
            result.append([int(start), int(end) + waitPeriod])
        result.sort(key=lambda pair: pair[0])
        return result
```

---

## Complexity

**minCabsRequired:** sorting takes `O(n log n)`, and each booking does at most one heap pop and one push, each `O(log n)`. Total time `O(n log n)`, space `O(n)`.


**minimizeMaxRidesPerCab:** the greedy takes `O(n log n)`, and very often it is the whole cost. The backtracking runs only when the greedy misses the lower bound. It is exponential in the worst case, but the four cuts keep it fast for small and mid-size inputs. Extra space is `O(n)` for the lists and the recursion, plus the saved situations in `deadStates`.