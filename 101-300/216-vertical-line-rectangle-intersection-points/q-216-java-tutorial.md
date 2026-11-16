# Find Vertical Line Rectangle Intersection Points in Java

#### Problem Statement
[https://codezym.com/question/216-vertical-line-rectangle-intersection-points](https://codezym.com/question/216-vertical-line-rectangle-intersection-points)


## Core Idea

A vertical line is infinite in the up and down direction, so the y-coordinates of a rectangle never matter. Only `leftX` and `rightX` decide if a line passes through it.

A line at position `x` passes through a rectangle when `leftX < x < rightX`. Every such crossing gives exactly 2 points: one on the top side and one on the bottom side. So for each rectangle, we count the lines strictly between `leftX` and `rightX` and add `2 × count` to the answer.

We first solve it the direct way, by checking every line against every rectangle. Then we make it fast by sorting the lines once and using binary search to count the lines inside each rectangle.


## Things to Watch Out For

- **Edges do not count.** A line exactly at `leftX` or `rightX` lies on a side of the rectangle, not inside it. That is why both checks are strict (`<`, not `<=`).

- **Duplicate lines count separately.** Two lines at the same `x` are two different lines.

- **The answer can be very large.** If all 200,000 lines cross all 200,000 rectangles, we get `200,000 × 200,000 × 2 = 80,000,000,000` points. This does not fit in an `int`, so the answer is stored in a `long`.


## Solution 1: Brute Force

For every rectangle, check every line.

```java
long total = 0;
for (String rectangle : rectangles) {
    String[] parts = rectangle.split(",");
    int leftX = Integer.parseInt(parts[0].trim());
    int rightX = Integer.parseInt(parts[2].trim());

    for (int x : verticalLines) {
        if (leftX < x && x < rightX) {
            total += 2;
        }
    }
}
return total;
```

This is correct, but it does `n × m` checks. With 200,000 lines and 200,000 rectangles, that is 40 billion checks. Far too slow.

The waste is easy to see: for every rectangle, we look at every line, even the lines that are far away on the left or the right.


## Solution 2: Sorting + Binary Search (Optimal)

### The idea

Sort the line positions once. For example, `[7, 1, 5, 3]` becomes `[1, 3, 5, 7]`.

Now, for any rectangle, the lines strictly inside `(leftX, rightX)` sit together in one continuous block of the sorted array. We don't need to visit them one by one. We only need to know where the block starts and where it ends, and binary search finds both very quickly.


### One helper does all the counting

`countLinesBefore(sortedLines, x)` returns how many lines have a position strictly less than `x`.

In a sorted array, this count is simply the index of the first value that is `>= x`. That is a classic binary search.

With this one helper:

- lines with position `< rightX` = `countLinesBefore(sortedLines, rightX)`
- lines with position `<= leftX` = `countLinesBefore(sortedLines, leftX + 1)`
- lines strictly inside = first count minus second count

Why `leftX + 1`? All positions are integers, so `x <= leftX` means the same thing as `x < leftX + 1`. This small trick lets one helper do both counts.


### Dry run (Example 1)

Lines `[1, 3, 5, 7]` (already sorted), rectangles `"0,0,4,2"` and `"3,1,8,5"`.

| Rectangle | leftX | rightX | lines < rightX | lines <= leftX | inside | points |
|---|---|---|---|---|---|---|
| `"0,0,4,2"` | 0 | 4 | 2 (`1, 3`) | 0 | 2 | 4 |
| `"3,1,8,5"` | 3 | 8 | 4 (`1, 3, 5, 7`) | 2 (`1, 3`) | 2 | 4 |

Total = `4 + 4 = 8`.

Look at line `3` in the second rectangle. It sits exactly on the left edge, so it falls in the "lines <= leftX" count and gets removed.


### Why a sorted array?

The lines never change after we get the input, and every question we ask is the same: "how many lines are before this x?".

A plain sorted `int[]` answers that with one binary search. There are no inserts or deletes, so we don't need anything fancier.


### Java Code

```java
import java.util.*;

public class VerticalLineRectangleIntersections {

    public VerticalLineRectangleIntersections() {
    }

    public long countIntersectionPoints(List<Integer> verticalLines, List<String> rectangles) {
        // Copy the line positions into an array and sort them once.
        // After sorting, all lines inside any x-range sit next to each other.
        int[] sortedLines = new int[verticalLines.size()];
        for (int i = 0; i < sortedLines.length; i++) {
            sortedLines[i] = verticalLines.get(i);
        }
        Arrays.sort(sortedLines);

        // long, because the answer can go up to 80 billion
        long total = 0;

        for (String rectangle : rectangles) {
            // format: "leftX,bottomY,rightX,topY"
            // bottomY and topY are not needed, the lines are infinite
            String[] parts = rectangle.split(",");
            int leftX = Integer.parseInt(parts[0].trim());
            int rightX = Integer.parseInt(parts[2].trim());

            // (lines with x < rightX) minus (lines with x <= leftX)
            // x <= leftX is the same as x < leftX + 1, because all values are integers
            int linesInside = countLinesBefore(sortedLines, rightX)
                    - countLinesBefore(sortedLines, leftX + 1);

            // every crossing gives one point on the top side and one on the bottom side
            total += 2L * linesInside;
        }
        return total;
    }

    /**
     * Returns how many lines have a position strictly less than x.
     * In a sorted array, this is the index of the first value >= x.
     */
    int countLinesBefore(int[] sortedLines, int x) {
        int low = 0;
        int high = sortedLines.length;
        while (low < high) {
            int mid = (low + high) / 2;
            if (sortedLines[mid] < x) {
                // mid and everything on its left is smaller than x
                low = mid + 1;
            } else {
                // mid could be the first value >= x, keep it in range
                high = mid;
            }
        }
        return low;
    }
}
```


### Complexity

- **Time:** `O(n log n)` to sort the lines, plus two binary searches per rectangle, which is `O(m log n)`. Total: `O((n + m) log n)`.

- **Space:** `O(n)` for the sorted copy of the lines.