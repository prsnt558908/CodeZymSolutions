# Design Uber Shuttle Seats in Java

#### Problem Statement
[https://codezym.com/question/231-uber-shuttle-seats](https://codezym.com/question/231-uber-shuttle-seats)


Every new rider wants the seat that is farthest from the nearest rider. The trick is to stop looking at single seats and look at **gaps** instead. A gap is a group of empty seats between two riders. Inside one gap, the best seat is easy to find: the middle seat, or the first or last seat of the shuttle when the gap touches an end.


So the problem becomes: always pick the best gap. We first do this with a sorted list that checks every gap on each call. Then we make it fast with a **heap** that keeps the best gap on top, and two **maps** that remember each rider's left and right neighbour.


---

## Approach 1: Sorted List (Simple)

`n` can be as big as 1,000,000,000, so we cannot keep an array of all seats. We only store the seats that are taken, in sorted order.


When a rider boards, only three kinds of seats can be the answer:

1. **Seat `0`**. Its nearest rider is the first taken seat, so its distance is `seats[0]`.
2. **The middle seat between two neighbouring riders `a` and `b`**. It is seat `a + (b - a) / 2` and its distance is `(b - a) / 2`.
3. **Seat `n - 1`**. Its distance is `n - 1 - lastTakenSeat`.


We check the candidates from left to right and replace the answer only when we find a **strictly bigger** distance. This way, a tie always keeps the smaller seat.

```java
// taken seats, always kept in sorted order
List<Integer> seats = new ArrayList<>();

public int board() {
    if (seats.isEmpty()) {
        seats.add(0);
        return 0;
    }
    // candidate 1: seat 0 (its distance is 0 if seat 0 is already taken)
    int bestSeat = 0, bestDist = seats.get(0), bestIndex = 0;

    // candidate 2: middle seat between every two neighbouring riders
    for (int i = 0; i + 1 < seats.size(); i++) {
        int a = seats.get(i), b = seats.get(i + 1);
        int mid = a + (b - a) / 2;
        if (mid - a > bestDist) {
            bestSeat = mid;
            bestDist = mid - a;
            bestIndex = i + 1;
        }
    }

    // candidate 3: last seat
    int last = seats.get(seats.size() - 1);
    if (n - 1 - last > bestDist) {
        bestSeat = n - 1;
        bestIndex = seats.size();
    }

    seats.add(bestIndex, bestSeat); // list stays sorted
    return bestSeat;
}

public void dropOff(int p) {
    seats.remove(Integer.valueOf(p)); // remove by value, not by index
}
```

### Problem with this approach

Every `board()` walks over all `k` riders on the shuttle. Adding or removing a seat in the middle of a list also shifts up to `k` items.


So each call takes `O(k)` time. With many riders and many calls, this gets slow.


---

## Approach 2: Heap of Gaps (Optimal)

Each `board()` only needs the **single best gap**. A heap gives us exactly that, without checking every gap.


### Step 1: Add two walls

Pretend there are two extra riders who never leave: one at seat `-1` (front wall) and one at seat `n` (back wall).


Now every group of empty seats sits between two riders, `left` and `right`. We call it the gap `(left, right)`. An empty shuttle is just one gap: `(-1, n)`.


### Step 2: Best seat inside one gap

The walls only mark the ends of the shuttle. They do not count when we measure distance. So a gap that touches a wall puts the rider at the very end seat.

| Gap | Best seat | Distance |
|---|---|---|
| touches the front (`left == -1`) | `0` | `right` |
| touches the back (`right == n`) | `n - 1` | `n - 1 - left` |
| between two riders | `left + (right - left) / 2` | `seat - left` |


The empty shuttle `(-1, n)` falls in the first row, so the first rider gets seat `0`, just as the problem asks.


In the middle case, if two seats are equally good, integer division picks the smaller one.


A gap with no empty seat, like `(3, 4)`, gets distance `0`. `board()` is only called when some seat is empty, so a better gap always exists and such a gap is never picked.


### Step 3: Keep all gaps in a heap

Each gap is stored as a small `Gap` object. It keeps the two ends, the best seat and the distance. We compute the seat once, when the gap is created, so the heap can compare gaps directly.


`Gap` implements `Comparable`, so the `PriorityQueue` orders gaps like this:

1. bigger distance first
2. if distances are equal, smaller seat first


So the top of the heap is always the seat the next rider should take. The tie rule is correct because gaps never overlap and each gap already offers its smallest best seat.


### Step 4: Remember neighbours with two maps

- `leftOf` maps a seat to the nearest taken seat on its left.
- `rightOf` maps a seat to the nearest taken seat on its right.


Together they work like a linked list of riders. We use maps instead of arrays because seat numbers can go up to 1,000,000,000.


We need them for two things:

1. In `dropOff(p)`, they give the riders on both sides of `p` in `O(1)`, so we can join the two gaps around `p`.
2. They tell us if a gap inside the heap still exists (Step 6).


### Step 5: board() and dropOff()

**`board()`**

1. Pop the best gap `(left, right)` from the heap.
2. The rider takes its best seat `s`.
3. Link `left <-> s <-> right` in the maps.
4. Push the two new gaps `(left, s)` and `(s, right)`.


**`dropOff(p)`**

1. Read `left = leftOf[p]` and `right = rightOf[p]`, then remove `p` from both maps.
2. Link `left <-> right`.
3. Push the joined gap `(left, right)`.


### Step 6: Skip old gaps

After `dropOff(p)`, the gaps `(left, p)` and `(p, right)` no longer exist, but they are still inside the heap. A heap cannot delete an item from the middle quickly.


So we simply leave them there. When `board()` pops a gap, it first checks if the gap is still real:

> Gap `(left, right)` is real only if `rightOf[left] == right`.


If seat `left` is empty now, it has no entry in `rightOf`. If someone sat inside the gap, `rightOf[left]` points to that new rider. In both cases the check fails, so we throw the gap away and pop the next one.


If an old gap becomes real again later (same two ends), the check passes. That is fine, because a gap's best seat depends only on its two ends.


Each gap is pushed once and popped at most once. So all the skipping together costs no more than the pushes we already did.


### Dry run (Example 2, n = 10)

| Call | Best gap on top | Seat taken | New gaps pushed |
|---|---|---|---|
| `board()` | `(-1, 10)`, empty shuttle | `0` | `(-1, 0)`, `(0, 10)` |
| `board()` | `(0, 10)`, distance 9 | `9` | `(0, 9)`, `(9, 10)` |
| `board()` | `(0, 9)`, distance 4 | `4` | `(0, 4)`, `(4, 9)` |
| `dropOff(0)` | | | `(-1, 4)` |
| `board()` | `(-1, 4)`, distance 4 | `0` | `(-1, 0)`, `(0, 4)` |


In the last call, gap `(-1, 4)` has distance 4. It beats gap `(4, 9)`, whose best seat 6 has distance 2. So seat `0` is taken again.


### Complexity

- `board()`: `O(log m)` on average, where `m` is the number of calls so far.
- `dropOff()`: `O(log m)`.
- Space: `O(m)`, because old gaps can stay in the heap.


---

## Java Code

```java
import java.util.*;

public class UberShuttle {
    int n;
    // seat -> nearest taken seat on its left (-1 is the front wall)
    Map<Integer, Integer> leftOf = new HashMap<>();
    // seat -> nearest taken seat on its right (n is the back wall)
    Map<Integer, Integer> rightOf = new HashMap<>();
    // best gap is always on top. It can also hold old gaps that no longer exist.
    PriorityQueue<Gap> gaps = new PriorityQueue<>();

    public UberShuttle(int n) {
        this.n = n;
        // walls -1 and n are always taken, so the empty shuttle is one big gap
        rightOf.put(-1, n);
        leftOf.put(n, -1);
        gaps.add(new Gap(-1, n, n));
    }

    public int board() {
        Gap gap = gaps.poll();
        while (!isValid(gap)) { // skip old gaps
            gap = gaps.poll();
        }
        int left = gap.left, seat = gap.seat, right = gap.right;

        // link left <-> seat <-> right
        rightOf.put(left, seat);
        leftOf.put(seat, left);
        rightOf.put(seat, right);
        leftOf.put(right, seat);

        // the new rider splits the gap into two smaller gaps
        gaps.add(new Gap(left, seat, n));
        gaps.add(new Gap(seat, right, n));
        return seat;
    }

    public void dropOff(int p) {
        int left = leftOf.remove(p);
        int right = rightOf.remove(p);

        // link left <-> right, so the two gaps around p become one gap
        rightOf.put(left, right);
        leftOf.put(right, left);
        gaps.add(new Gap(left, right, n));
    }

    // a gap is real only if its right end is still the next rider after its left end
    boolean isValid(Gap gap) {
        Integer next = rightOf.get(gap.left);
        return next != null && next == gap.right;
    }
}

/**
 * Empty seats between two taken seats: left and right.
 * Seat -1 and seat n are walls that are always taken.
 */
class Gap implements Comparable<Gap> {
    int left, right;
    int seat; // best seat inside this gap
    int dist; // distance from that seat to the nearest taken seat

    Gap(int left, int right, int n) {
        this.left = left;
        this.right = right;
        if (left == -1) {           // touches the front, so sit at seat 0
            seat = 0;
            dist = right;
        } else if (right == n) {    // touches the back, so sit at the last seat
            seat = n - 1;
            dist = n - 1 - left;
        } else {                    // between two riders, so sit in the middle
            seat = left + (right - left) / 2;
            dist = seat - left;
        }
    }

    // bigger distance comes first, then smaller seat
    public int compareTo(Gap other) {
        if (dist != other.dist) {
            return Integer.compare(other.dist, dist);
        }
        return Integer.compare(seat, other.seat);
    }
}
```