# Maximum Usable Cable Length in Python

#### Problem Statement
[https://codezym.com/question/366-max-usable-cable-length](https://codezym.com/question/366-max-usable-cable-length)


The key idea is to guess one number `t`: the length of the longest segment that passes inspection. We try every possible `t` and keep the best result.


Let `passCount = segmentCount // 2`, the number of passing segments. If every passing segment were exactly `t` long, they would add up to `passCount * t`. A passing segment shorter than `t` loses the difference.


So for a fixed `t`, we only need to make exactly `segmentCount` segments while losing as little length as possible. A simple greedy does this: free segments first, then the cheapest extra segments.


This avoids trying every possible way to cut the rolls, which is far too slow. A single roll of length 1,000 can already be split in a huge number of ways.


## Simple Version: Only Full Pieces

Guess `t`. Cut every roll into pieces of length `t`. If some length is left over, keep it attached to the last piece. Now every piece is at least `t` long, and a roll of length `L` gives `L // t` pieces.


If the rolls give at least `segmentCount` pieces in total, we can make exactly `segmentCount` segments. Just join a few pieces of the same roll back together, or leave some rolls unused.


Every segment is still at least `t` long, so the passing total is at least `passCount * t`.


A segment can never be longer than the longest roll, so we only try `t` from 1 to the longest roll.

```python
best = 0
for t in range(1, longestRoll + 1):
    # a roll of length L gives L // t pieces, each at least t long
    pieces = 0
    for roll in cableRolls:
        pieces += roll // t
    if pieces >= segmentCount:
        best = max(best, passCount * t)
```


### What is wrong with it?

It never makes a segment shorter than `t`, and sometimes the best answer needs one.


Take Example 1: `cableRolls = [6, 10, 11]` and `segmentCount = 4`. The simple version finds `t = 5` and returns `2 * 5 = 10`. At `t = 6` the rolls give only 3 full pieces, so it gives up.


The real answer is 11. Keep 6 and 10 whole, and cut 11 into 6 and 5. The segment 5 is shorter than `t = 6`, but it is still worth using.


So we need a way to allow short segments and measure what they cost.


## Final Solution: Pay for Short Segments

### Shortfall

Fix `t`. The **shortfall** of a segment of length `x` is how much it misses `t` by: `t - x` when `x < t`, and 0 otherwise.


For any cutting, we define its score as:

```
score = passCount * t - (total shortfall of all segments)
```


### Why this score works

- **It never claims too much.** Every segment is at least `t - shortfall` long. So the passing segments add up to at least `passCount * t` minus their own shortfall, and that is at least the score.

- **It is exact for the right `t`.** Let `t` be the longest passing segment of the best cutting. Every passing segment is at most `t`, so it is exactly `t - shortfall` long. Every failing segment is at least `t`, so it has no shortfall.


So the answer is the best score over all `t`, and our new goal is: **for each `t`, make exactly `segmentCount` segments with the smallest total shortfall.**


### What each segment costs

Fix `t` and look at a roll of length `L`. Let `leftover = L % t`. The roll can give segments in three ways.


1. **Free segments.** As in the simple version, the roll gives `L // t` segments with no shortfall.

2. **The leftover.** If `leftover > 0`, cut it off as its own segment for a cost of `t - leftover`. The last full piece becomes exactly `t` long, so it stays free. A roll shorter than `t` is all leftover, so using it costs `t - L`.

3. **An extra cut.** Split any segment of length `a` (at most `t`) into `b` and `a - b`. The shortfall goes from `t - a` to `(t - b) + (t - a + b) = 2t - a`. So every extra cut costs exactly `t`.


For every roll the costs only go up: 0 for its free segments, then `t - leftover`, then `t` for each extra cut. So taking the cheapest segments first is always best.


A roll of length 0 gives nothing, so it needs no special handling.


### Putting it together

For every `t` from 1 to the longest roll:

1. Count the free segments: the sum of `L // t` over all rolls.
2. If that is at least `segmentCount`, the shortfall is 0.
3. Otherwise, sort the leftover costs and take the cheapest ones until we have enough segments. Each segment still missing after that costs `t`.
4. `score = passCount * t - shortfall`.


The answer is the best score.


We keep the leftover costs in a plain list and sort it, because we always want the cheapest leftovers first.


The constraints promise enough total length, so there is always room for the extra cuts we need.


### Dry Run

Example 1: `cableRolls = [6, 10, 11]`, `segmentCount = 4`, so `passCount = 2`.

| t | Free segments | Leftover costs | Still needed | Shortfall | Score |
|---|---|---|---|---|---|
| 5 | 1 + 2 + 2 = 5 | not needed | 0 | 0 | 2 * 5 - 0 = 10 |
| 6 | 1 + 1 + 1 = 3 | 2 (from 10), 1 (from 11) | 1 | 1 | 2 * 6 - 1 = **11** |
| 7 | 0 + 1 + 1 = 2 | 1 (from 6), 4 (from 10), 3 (from 11) | 2 | 1 + 3 = 4 | 2 * 7 - 4 = 10 |


Every other `t` scores less, so the answer is **11**.


At `t = 6`: 6 stays whole, 10 stays whole (a 6 with its leftover 4 attached), and 11 becomes 6 and 5. The segments are `[5, 6, 6, 10]` and the passing total is `5 + 6 = 11`.


Example 2 shows an extra cut. With `cableRolls = [9]`, `segmentCount = 6` and `t = 2`, the roll gives 4 free segments `[2, 2, 2, 3]` (the 3 is a 2 with the leftover 1 attached).


Cutting off the leftover costs 1 and gives `[1, 2, 2, 2, 2]`. One segment is still missing, so an extra cut splits a 2 into 1 and 1 for a cost of 2.


The segments are `[1, 1, 1, 2, 2, 2]`, the shortfall is 3, and the score is `3 * 2 - 3 = 3`.


### Code

```python
from typing import List


class MaximumUsableCableLength:

    def __init__(self):
        pass

    def maximumUsableCableLength(self, cableRolls: List[int],
                                 segmentCount: int) -> int:
        passCount = segmentCount // 2

        # A segment can never be longer than the longest roll,
        # so this is the largest t worth trying.
        longestRoll = max(cableRolls)

        best = 0
        # t = our guess for the length of the longest passing segment
        for t in range(1, longestRoll + 1):
            shortfall = self.minShortfall(cableRolls, segmentCount, t)
            best = max(best, passCount * t - shortfall)
        return best

    def minShortfall(self, cableRolls: List[int], segmentCount: int,
                     t: int) -> int:
        """
        Smallest total shortfall for making exactly segmentCount segments.
        A segment of length x falls short by (t - x) when x < t, else 0.
        """
        freeSegments = 0

        # Cost of cutting off each roll's leftover as its own segment
        leftoverCosts = []

        for roll in cableRolls:
            # Pieces of length t cost nothing. The leftover stays
            # attached to the last piece, which only makes it longer.
            freeSegments += roll // t

            leftover = roll % t
            if leftover > 0:
                leftoverCosts.append(t - leftover)

        # Enough free segments: join extra pieces back or skip some rolls
        if freeSegments >= segmentCount:
            return 0

        need = segmentCount - freeSegments
        shortfall = 0

        # Use the cheapest leftovers first
        leftoverCosts.sort()
        for cost in leftoverCosts:
            if need == 0:
                break
            shortfall += cost
            need -= 1

        # Any segment still missing needs an extra cut, which costs t
        shortfall += need * t
        return shortfall
```


### Complexity

Let `M` be the length of the longest roll and `R` the number of rolls.

| | Complexity | Why |
|---|---|---|
| Time | `O(M * R log R)` | For each `t`, we scan the rolls and sort the leftover costs |
| Space | `O(R)` | The list of leftover costs |


With `M` and `R` up to 1,000, this is about 10 million steps.