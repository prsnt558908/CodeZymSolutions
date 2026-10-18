# Assign Aggressive Cows to Stalls in Java

#### Problem Statement

[https://codezym.com/question/166-assign-aggressive-cows-stalls](https://codezym.com/question/166-assign-aggressive-cows-stalls)

Sort the stall positions, then ask a simpler question: can we place all cows at least `distance` apart? A greedy scan answers this question. Binary search finds the largest distance that works, without trying every possible placement.

## Start with a simple approach

Try every group of `cowCount` stalls. For each group, sort its positions and find the smallest gap between neighboring cows. Return the largest of these minimum gaps.

Checking neighbors is enough because the distance between non-neighboring cows is a sum of neighboring gaps.

This approach is correct, but the number of groups grows too quickly for up to `100,000` stalls.

## Better approach: greedy placement and binary search

### 1. Sort the stalls

Sorting lets us move from left to right. We use an `ArrayList` copy so the original list stays unchanged and reading a position by index is fast.

No extra map or set is needed because we only scan the sorted positions.

### 2. Check one candidate distance

For a candidate distance `d`:

1. Place the first cow in the leftmost stall.
2. Place each next cow in the first stall at least `d` away from the last placed cow.
3. Return `true` as soon as `cowCount` cows are placed.
4. Return `false` if the stalls run out first.

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

## Java solution

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssignAggressiveCowsToStalls {

    public AssignAggressiveCowsToStalls() {
    }

    public int largestMinimumDistance(List<Integer> stallPositions, int cowCount) {
        // Sort a copy so the caller's list stays unchanged.
        List<Integer> positions = new ArrayList<>(stallPositions);
        Collections.sort(positions);

        int low = 0;
        int high = positions.get(positions.size() - 1) - positions.get(0);
        int answer = 0;

        while (low <= high) {
            int mid = low + (high - low) / 2;

            if (canPlaceCows(positions, cowCount, mid)) {
                answer = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return answer;
    }

    public boolean canPlaceCows(List<Integer> positions, int cowCount, int distance) {
        int placed = 1;
        int lastPosition = positions.get(0);

        for (int i = 1; i < positions.size(); i++) {
            int position = positions.get(i);

            // Use the earliest stall that leaves the required gap.
            if (position - lastPosition >= distance) {
                placed++;
                lastPosition = position;

                if (placed == cowCount) {
                    return true;
                }
            }
        }

        return false;
    }
}
```
