# Assign Aggressive Cows to Stalls in Python

#### Problem Statement

[https://codezym.com/question/166-assign-aggressive-cows-stalls](https://codezym.com/question/166-assign-aggressive-cows-stalls)

Sort the stall positions, then ask a simpler question: can we place all cows at least `distance` apart? A greedy scan answers this question. Binary search finds the largest distance that works, without trying every possible placement.

## Start with a simple approach

Try every group of `cowCount` stalls. For each group, sort its positions and find the smallest gap between neighboring cows. Return the largest of these minimum gaps.

Checking neighbors is enough because the distance between non-neighboring cows is a sum of neighboring gaps.

This approach is correct, but the number of groups grows too quickly for up to `100,000` stalls.

## Better approach: greedy placement and binary search

### 1. Sort the stalls

Sorting lets us move from left to right. We use `sorted()` to create a sorted list, leaving the original list unchanged. Reading a position by index is fast.

No extra dictionary or set is needed because we only scan the sorted positions.

### 2. Check one candidate distance

For a candidate distance `d`:

1. Place the first cow in the leftmost stall.
2. Place each next cow in the first stall at least `d` away from the last placed cow.
3. Return `True` as soon as `cowCount` cows are placed.
4. Return `False` if the stalls run out first.

Choosing the earliest valid stall leaves as much room as possible for the remaining cows.

For example, consider `[1, 3, 7, 12, 20]` with `3` cows.

- For `d = 8`, place cows at `1`, `12`, and `20`. This works.
- For `d = 9`, place cows at `1` and `12`. The remaining stall at `20` is too close, so this fails.

### 3. Search for the largest working distance

If distance `d` works, every smaller distance also works. If `d` fails, every larger distance also fails.

This gives us a boundary that binary search can find.

Start with `low = 0` and `high = largestPosition - smallestPosition`. No pair of cows can be farther apart than this full span.

- If `mid` works, save it and search larger distances with `low = mid + 1`.
- Otherwise, search smaller distances with `high = mid - 1`.

For the example, the candidate distances are `9`, `4`, `6`, `7`, and `8`. Only `9` fails, and the final answer is `8`.

## Why this works

The greedy check never wastes space. In any valid placement, moving the first cow to the leftmost stall cannot reduce the space available to later cows. Each later greedy choice is also no farther right than the corresponding cow in any valid placement. So if a valid placement exists, the greedy scan can place all cows.

Binary search keeps looking above every working distance and below every failing distance. When the search ends, the saved answer is the largest feasible minimum distance.

## Complexity

Let `N` be the number of stalls and `R` be the difference between the largest and smallest positions.

Sorting takes `O(N log N)`. Each placement check takes `O(N)`, and binary search uses `O(log(R + 1))` checks.

Total time is `O(N log N + N log(R + 1))`. Extra space is `O(N)` for the copied list and sorting.

## Python solution

```python
class AssignAggressiveCowsToStalls:
    def __init__(self):
        pass

    def largestMinimumDistance(self, stallPositions, cowCount):
        # Sort a copy so the caller's list stays unchanged.
        positions = sorted(stallPositions)

        low = 0
        high = positions[-1] - positions[0]
        answer = 0

        while low <= high:
            mid = low + (high - low) // 2

            if self.canPlaceCows(positions, cowCount, mid):
                answer = mid
                low = mid + 1
            else:
                high = mid - 1

        return answer

    def canPlaceCows(self, positions, cowCount, distance):
        placed = 1
        lastPosition = positions[0]

        for i in range(1, len(positions)):
            position = positions[i]

            # Use the earliest stall that leaves the required gap.
            if position - lastPosition >= distance:
                placed += 1
                lastPosition = position

                if placed == cowCount:
                    return True

        return False
```
