# Design Uber Shuttle Seats in Python

#### Problem Statement
[https://codezym.com/question/231-uber-shuttle-seats](https://codezym.com/question/231-uber-shuttle-seats)


Every new rider wants the seat that is farthest from the nearest rider. The trick is to stop looking at single seats and look at **gaps** instead. A gap is a group of empty seats between two riders. Inside one gap, the best seat is easy to find: the middle seat, or the first or last seat of the shuttle when the gap touches an end.


So the problem becomes: always pick the best gap. We first do this with a sorted list that checks every gap on each call. Then we make it fast with a **heap** that keeps the best gap on top, and two **dictionaries** that remember each rider's left and right neighbour.


---

## Approach 1: Sorted List (Simple)

`n` can be as big as 1,000,000,000, so we cannot keep a list of all seats. We only store the seats that are taken, in sorted order.


When a rider boards, only three kinds of seats can be the answer:

1. **Seat `0`**. Its nearest rider is the first taken seat, so its distance is `seats[0]`.
2. **The middle seat between two neighbouring riders `a` and `b`**. It is seat `a + (b - a) // 2` and its distance is `(b - a) // 2`.
3. **Seat `n - 1`**. Its distance is `n - 1 - seats[-1]`.


We check the candidates from left to right and replace the answer only when we find a **strictly bigger** distance. This way, a tie always keeps the smaller seat.

```python
def __init__(self, n: int):
    self.n = n
    # taken seats, always kept in sorted order
    self.seats = []

def board(self) -> int:
    seats = self.seats
    if not seats:
        seats.append(0)
        return 0
    # candidate 1: seat 0 (its distance is 0 if seat 0 is already taken)
    bestSeat, bestDist, bestIndex = 0, seats[0], 0

    # candidate 2: middle seat between every two neighbouring riders
    for i in range(len(seats) - 1):
        a, b = seats[i], seats[i + 1]
        mid = a + (b - a) // 2
        if mid - a > bestDist:
            bestSeat, bestDist, bestIndex = mid, mid - a, i + 1

    # candidate 3: last seat
    if self.n - 1 - seats[-1] > bestDist:
        bestSeat, bestIndex = self.n - 1, len(seats)

    seats.insert(bestIndex, bestSeat)  # list stays sorted
    return bestSeat

def dropOff(self, p: int) -> None:
    self.seats.remove(p)
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
| between two riders | `left + (right - left) // 2` | `seat - left` |


The empty shuttle `(-1, n)` falls in the first row, so the first rider gets seat `0`, just as the problem asks.


In the middle case, if two seats are equally good, integer division picks the smaller one.


A gap with no empty seat, like `(3, 4)`, gets distance `0`. `board()` is only called when some seat is empty, so a better gap always exists and such a gap is never picked.


### Step 3: Keep all gaps in a heap

Each gap is stored as a small `Gap` object. It keeps the two ends, the best seat and the distance. We compute the seat once, when the gap is created, so the heap can compare gaps directly.


Python's `heapq` is a min-heap: it keeps the "smallest" item on top. So `Gap.__lt__` calls a gap smaller when it is **better**:

1. bigger distance first
2. if distances are equal, smaller seat first


So the top of the heap is always the seat the next rider should take. The tie rule is correct because gaps never overlap and each gap already offers its smallest best seat.


### Step 4: Remember neighbours with two dictionaries

- `leftOf` maps a seat to the nearest taken seat on its left.
- `rightOf` maps a seat to the nearest taken seat on its right.


Together they work like a linked list of riders. We use dictionaries instead of lists because seat numbers can go up to 1,000,000,000.


We need them for two things:

1. In `dropOff(p)`, they give the riders on both sides of `p` in `O(1)`, so we can join the two gaps around `p`.
2. They tell us if a gap inside the heap still exists (Step 6).


### Step 5: board() and dropOff()

**`board()`**

1. Pop the best gap `(left, right)` from the heap.
2. The rider takes its best seat `s`.
3. Link `left <-> s <-> right` in the dictionaries.
4. Push the two new gaps `(left, s)` and `(s, right)`.


**`dropOff(p)`**

1. Read `left = leftOf[p]` and `right = rightOf[p]`, then remove `p` from both dictionaries.
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

## Python Code

```python
import heapq


class Gap:
    """
    Empty seats between two taken seats: left and right.
    Seat -1 and seat n are walls that are always taken.
    """

    def __init__(self, left: int, right: int, n: int):
        self.left = left
        self.right = right
        if left == -1:          # touches the front, so sit at seat 0
            self.seat = 0
            self.dist = right
        elif right == n:        # touches the back, so sit at the last seat
            self.seat = n - 1
            self.dist = n - 1 - left
        else:                   # between two riders, so sit in the middle
            self.seat = left + (right - left) // 2
            self.dist = self.seat - left

    def __lt__(self, other: "Gap") -> bool:
        # bigger distance comes first, then smaller seat
        if self.dist != other.dist:
            return self.dist > other.dist
        return self.seat < other.seat


class UberShuttle:

    def __init__(self, n: int):
        self.n = n
        # seat -> nearest taken seat on its left (-1 is the front wall)
        self.leftOf = {n: -1}
        # seat -> nearest taken seat on its right (n is the back wall)
        self.rightOf = {-1: n}
        # best gap is always on top. It can also hold old gaps that no longer exist.
        # walls -1 and n are always taken, so the empty shuttle is one big gap
        self.gaps = [Gap(-1, n, n)]

    def board(self) -> int:
        gap = heapq.heappop(self.gaps)
        while not self.isValid(gap):  # skip old gaps
            gap = heapq.heappop(self.gaps)
        left, seat, right = gap.left, gap.seat, gap.right

        # link left <-> seat <-> right
        self.rightOf[left] = seat
        self.leftOf[seat] = left
        self.rightOf[seat] = right
        self.leftOf[right] = seat

        # the new rider splits the gap into two smaller gaps
        heapq.heappush(self.gaps, Gap(left, seat, self.n))
        heapq.heappush(self.gaps, Gap(seat, right, self.n))
        return seat

    def dropOff(self, p: int) -> None:
        left = self.leftOf.pop(p)
        right = self.rightOf.pop(p)

        # link left <-> right, so the two gaps around p become one gap
        self.rightOf[left] = right
        self.leftOf[right] = left
        heapq.heappush(self.gaps, Gap(left, right, self.n))

    def isValid(self, gap: Gap) -> bool:
        # a gap is real only if its right end is still the next rider after its left end
        return self.rightOf.get(gap.left) == gap.right
```