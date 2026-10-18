# Container With Most Water, Upright and Tilted in Python

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

- `maxArea`: `O(n)` time and `O(1)` extra space.
- `maxAreaTilted`: `O(n log n)` time and `O(n)` extra space. Each recursion level scans `O(n)` candidate entries in total, across `O(log n)` levels.

The input list is unchanged. The candidate lists contain only indices, and recursion uses index ranges instead of copying list slices.


## Python Solution

```python
class ContainerWithMostWater:
    def __init__(self):
        pass

    def maxArea(self, height):
        left = 0
        right = len(height) - 1
        best = 0

        while left < right:
            area = (right - left) * min(height[left], height[right])
            best = max(best, area)

            # A narrower pair using this shorter line cannot improve the area.
            if height[left] <= height[right]:
                left += 1
            else:
                right -= 1

        return best

    def maxAreaTilted(self, height):
        left_candidates = []
        right_candidates = []

        # A line is useful on the left only if every earlier line is shorter.
        tallest = -1
        for index, value in enumerate(height):
            if value > tallest:
                left_candidates.append(index)
                tallest = value

        # Apply the same rule from the other end.
        tallest = -1
        for index in range(len(height) - 1, -1, -1):
            if height[index] > tallest:
                right_candidates.append(index)
                tallest = height[index]
        right_candidates.reverse()

        best_doubled = self.find_best_tilted(
            height, left_candidates, right_candidates,
            0, len(left_candidates) - 1, 0, len(right_candidates) - 1,
        )

        # Divide only once so half-unit answers remain exact.
        return best_doubled / 2.0

    def find_best_tilted(
        self, height, left_candidates, right_candidates,
        row_start, row_end, column_start, column_end,
    ):
        if row_start > row_end:
            return 0

        middle = (row_start + row_end) // 2
        left_index = left_candidates[middle]
        best_column = column_start
        best = -1

        for column in range(column_start, column_end + 1):
            right_index = right_candidates[column]
            doubled_area = (
                (right_index - left_index)
                * (height[left_index] + height[right_index])
            )

            # Keeping the first maximum makes tie handling consistent.
            if doubled_area > best:
                best = doubled_area
                best_column = column

        # Earlier rows need only columns up to this row's best column.
        before = self.find_best_tilted(
            height, left_candidates, right_candidates,
            row_start, middle - 1, column_start, best_column,
        )

        # Later rows need only columns starting at this row's best column.
        after = self.find_best_tilted(
            height, left_candidates, right_candidates,
            middle + 1, row_end, best_column, column_end,
        )

        return max(best, before, after)
```
