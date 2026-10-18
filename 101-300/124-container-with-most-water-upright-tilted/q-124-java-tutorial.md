# Container With Most Water, Upright and Tilted in Java

#### Problem Statement
[https://codezym.com/question/124-container-with-most-water-upright-tilted](https://codezym.com/question/124-container-with-most-water-upright-tilted)

The upright container is limited by its shorter wall, so we can use two pointers. The tilted version depends on the sum of both heights. For that method, we remove lines that cannot help, then use divide and conquer to check fewer pairs.


## Start with every pair

For each pair of indices `i < j`, calculate its area and keep the largest answer.

- Upright area: `(j - i) * min(height[i], height[j])`.
- Tilted area: `(j - i) * (height[i] + height[j]) / 2.0`.

This takes `O(n²)` time, which is too slow for `100,000` lines. We can improve both methods.


## Upright: move the shorter wall

Start with the first and last lines. Calculate their area, then move the pointer at the shorter line inward.

Why is this safe? Any narrower pair that keeps that shorter line has an effective height no greater than that line. Its area cannot beat the pair we just checked. We can therefore discard that endpoint.

If both heights are equal, either pointer can move. Continue until the pointers meet.


## Tilted: keep only useful lines

Use the trapezoid formula exactly as defined in the statement. The upright pointer rule does not work for this formula.

For example, in `[1, 2, 100, 1]`, moving the left pointer when the endpoint heights tie skips the best pair, indices `0` and `2`. Its tilted area is `2 * (1 + 100) / 2.0 = 101.0`.

Suppose a left endpoint has an earlier line that is at least as tall. Choosing that earlier line gives more width and at least the same height sum. We can discard the later line as a left endpoint.

Keep a left candidate only when it sets a new highest height while scanning from the left. Apply the same rule from the right for right candidates, then reverse that list.

For `[1, 2, 100, 1]`, the left candidate indices are `[0, 1, 2]` and the right candidate indices are `[2, 3]`. These lists store indices so we can still calculate widths.

The left list ends at the first maximum-height line. The right list starts at the last maximum-height line. Thus, every left index is at most every right index. A shared index contributes only zero and cannot change the maximum over valid pairs.


## Tilted: search smaller ranges

Think of the left candidates as rows and the right candidates as columns. Both lists are in increasing index order. Left heights increase, while right heights decrease.

These orders give us a useful rule: **the first best right column cannot move left as we move to later rows**.

To see why, let `S(i, j) = (j - i) * (height[i] + height[j])`, which is twice the tilted area. For left candidates `a < b` and right candidates `c < d`:

```text
(S(b, d) - S(b, c)) - (S(a, d) - S(a, c))
= (d - c) * (height[b] - height[a])
  + (b - a) * (height[c] - height[d])
>= 0
```

Both terms are nonnegative. So the later right column becomes at least as attractive relative to the earlier column when we move to a later row. Choosing the first maximum handles ties consistently.

Use this rule to divide the search:

1. Take the middle row and scan its allowed columns to find its best column.
2. Search earlier rows using only columns up to that best column.
3. Search later rows using only columns starting at that best column.

The recursive helper returns the best doubled area from these parts. Keeping doubled areas as integers avoids early rounding. Divide by `2.0` once at the end to preserve `.0` and `.5` answers.

For `[1, 2, 100, 1]`, the two methods return `3` and `101.0` respectively. Zero heights and repeated heights follow the same rules.


## Complexity

Let `n` be the number of lines.

- `maxArea`: `O(n)` time and `O(n)` extra space in this implementation. The array copy keeps indexing fast for any input `List`. The two-pointer scan itself uses `O(1)` extra space.
- `maxAreaTilted`: `O(n log n)` time and `O(n)` extra space. Each recursion level scans `O(n)` candidate entries in total, across `O(log n)` levels.


## Java Solution

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContainerWithMostWater {

    public ContainerWithMostWater() {
    }

    public int maxArea(List<Integer> height) {
        int[] values = toArray(height);
        int left = 0;
        int right = values.length - 1;
        int best = 0;

        while (left < right) {
            int area = (right - left) * Math.min(values[left], values[right]);
            best = Math.max(best, area);

            // A narrower pair using this shorter line cannot improve the area.
            if (values[left] <= values[right]) {
                left++;
            } else {
                right--;
            }
        }

        return best;
    }

    public double maxAreaTilted(List<Integer> height) {
        int[] values = toArray(height);
        List<Integer> leftCandidates = new ArrayList<>();
        List<Integer> rightCandidates = new ArrayList<>();

        // A line is useful on the left only if every earlier line is shorter.
        int tallest = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > tallest) {
                leftCandidates.add(i);
                tallest = values[i];
            }
        }

        // Apply the same rule from the other end.
        tallest = -1;
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] > tallest) {
                rightCandidates.add(i);
                tallest = values[i];
            }
        }
        Collections.reverse(rightCandidates);

        long bestDoubled = findBestTilted(
            values, leftCandidates, rightCandidates,
            0, leftCandidates.size() - 1, 0, rightCandidates.size() - 1
        );

        // Divide only once so half-unit answers remain exact.
        return bestDoubled / 2.0;
    }

    long findBestTilted(
        int[] values,
        List<Integer> leftCandidates,
        List<Integer> rightCandidates,
        int rowStart, int rowEnd,
        int columnStart, int columnEnd
    ) {
        if (rowStart > rowEnd) {
            return 0;
        }

        int middle = (rowStart + rowEnd) / 2;
        int leftIndex = leftCandidates.get(middle);
        int bestColumn = columnStart;
        long best = -1;

        for (int column = columnStart; column <= columnEnd; column++) {
            int rightIndex = rightCandidates.get(column);
            long doubledArea = (long) (rightIndex - leftIndex)
                * (values[leftIndex] + values[rightIndex]);

            // Keeping the first maximum makes tie handling consistent.
            if (doubledArea > best) {
                best = doubledArea;
                bestColumn = column;
            }
        }

        // Earlier rows need only columns up to this row's best column.
        long before = findBestTilted(
            values, leftCandidates, rightCandidates,
            rowStart, middle - 1, columnStart, bestColumn
        );

        // Later rows need only columns starting at this row's best column.
        long after = findBestTilted(
            values, leftCandidates, rightCandidates,
            middle + 1, rowEnd, bestColumn, columnEnd
        );

        return Math.max(best, Math.max(before, after));
    }

    int[] toArray(List<Integer> height) {
        // Array access stays constant-time for any input List implementation.
        int[] values = new int[height.size()];
        int index = 0;
        for (int value : height) {
            values[index] = value;
            index++;
        }
        return values;
    }
}
```
