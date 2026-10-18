# Unique Grid Paths in Java

#### Problem Statement

[https://codezym.com/question/316-unique-grid-paths](https://codezym.com/question/316-unique-grid-paths)

The main idea is to count paths using answers we already know. A cell can only be entered from above or from the left, so we can add those two counts. We will first use dynamic programming with one array, then reduce the work further by counting the positions of the moves.

## Why trying every path is slow

A simple recursive solution tries moving right and down from each cell. It counts one path whenever it reaches the destination.

However, different routes can reach the same cell. The recursion then counts the paths from that cell again and again. Saving smaller answers avoids this repeated work.

## Solution 1: Dynamic programming with one array

Let `ways[row][column]` be the number of paths to a cell. For an interior cell:

```text
ways[row][column] = ways[row - 1][column] + ways[row][column - 1]
```

Every cell in the first row and first column has exactly one path. The robot can only move straight along that boundary.

We do not need to store the whole grid. One array, `paths`, is enough because each update only needs the count from above and the count from the left.

Before an update, `paths[column]` holds the count from above. Since we update from left to right, `paths[column - 1]` already holds the count for the cell on the left.

Use the smaller dimension as the array length. Swapping the rows and columns preserves the answer because it simply exchanges right and down moves.

For a `4 x 4` grid, the array changes like this:

- First row: `[1, 1, 1, 1]`.
- Second row: `[1, 2, 3, 4]`.
- Third row: `[1, 3, 6, 10]`.
- Fourth row: `[1, 4, 10, 20]`.

The last value, `20`, is the answer.

### Why it works

Every path to an interior cell ends with either a down move from above or a right move from the left. These two groups do not overlap. Adding their counts therefore counts every path exactly once.

Starting with the correct boundary counts, each update produces the correct count for the next cell. The final array position gives the destination's count.

A single row or column has one path. A `1 x 1` grid also has one path, with no moves needed.

**Time:** `O(m * n)`. **Extra space:** `O(min(m, n))`.

### Java code

```java
import java.util.Arrays;

public class UniqueGridPaths {

    public UniqueGridPaths() {
    }

    public int uniquePaths(int m, int n) {
        int rows = Math.max(m, n);
        int columns = Math.min(m, n);

        // Every cell in the first row has one path.
        int[] paths = new int[columns];
        Arrays.fill(paths, 1);

        for (int row = 1; row < rows; row++) {
            for (int column = 1; column < columns; column++) {
                // Add the count from above to the count from the left.
                paths[column] += paths[column - 1];
            }
        }

        return paths[columns - 1];
    }
}
```

## Solution 2: Count the positions of the moves

Every path uses exactly `m - 1` down moves and `n - 1` right moves. That makes `m + n - 2` moves in total.

Choose which positions contain the down moves. All remaining positions must contain right moves. Each choice produces exactly one valid path, and every valid path produces one such choice.

The answer is the combination `C(m + n - 2, m - 1)`, where `C(N, k)` means choosing `k` positions from `N` positions.

We can choose positions for either direction, so use `chosenMoves = min(m - 1, n - 1)` to do fewer calculations.

For a `4 x 4` grid, we choose three positions from six. The answer is `C(6, 3) = 20`.

### Calculate without factorials

Let `N = m + n - 2` and `k = chosenMoves`. Start with `paths = 1` and repeat this update for `step` from `1` through `k`:

```text
paths = paths * (N - k + step) / step
```

After each step, `paths` equals `C(N - k + step, step)`. Each division is therefore exact, and the last step gives `C(N, k)`.

Multiply before dividing. Use `long` because the temporary multiplication can exceed the range of `int`. With the stated limits, it is at most `198 * 1,000,000,000`, which fits in `long`. The final answer fits in `int` as promised by the problem.

If either dimension is one, the loop is skipped and the answer stays one.

**Time:** `O(min(m, n))`. **Extra space:** `O(1)`.

### Java code

```java
public class UniqueGridPaths {

    public UniqueGridPaths() {
    }

    public int uniquePaths(int m, int n) {
        int totalMoves = m + n - 2;
        int chosenMoves = Math.min(m - 1, n - 1);
        long paths = 1;

        for (int step = 1; step <= chosenMoves; step++) {
            // Multiply before dividing to keep the result exact.
            paths = paths * (totalMoves - chosenMoves + step) / step;
        }

        return (int) paths;
    }
}
```
