# Maximum Rocks on a Road Trip in Python

#### Problem Statement

[https://codezym.com/question/480-maximum-rocks-road-trip](https://codezym.com/question/480-maximum-rocks-road-trip)

The key idea is to keep the best total we can collect when reaching each city. We can enter a city only from below or from the left, so we choose the better of those two totals and add its rocks. This is dynamic programming. We can store just one row of totals to save space.

## Start with all possible paths

One simple approach is to try every valid path from the bottom-left city to the top-right city and return the largest total.

This works for small grids, but the number of paths grows very quickly. Many paths also share the same earlier cities. Instead of checking those shared parts repeatedly, we can keep their best totals.

## Keep the best total for each city

Process rows from bottom to top. Within each row, process cities from left to right.

For a city that has both possible incoming directions:

```text
best total = rocks in this city + max(best total from below, best total from left)
```

The bottom row can only be reached from the left. The first column can only be reached from below. The starting city contributes its own rocks once.

We only need the best total for each earlier city. Any smaller total at the same city would have exactly the same choices for the rest of the trip, so it cannot give a better answer.

## Store only one row

Use an integer list called `best`, with one entry per column.

Before updating column `col`, `best[col]` holds the total from below. Since we move left to right, `best[col - 1]` already holds the updated total from the left.

At column `0`, add the current rocks to the total from below. At every other column, choose the larger total and add the current rocks.

The list starts with zeros. On the bottom row, the running total from the left is at least zero because all rock counts are nonnegative. This makes the same update work there too.

Each input string is split at commas to read one row. We never need to build a full integer grid.

## Walk through an example

For `cityRocks = ["2,1,7", "4,8,0", "3,2,5"]`:

- After the bottom row, `best = [3, 5, 10]`.
- After the middle row, `best = [7, 15, 15]`.
- After the top row, `best = [9, 16, 23]`.

For the middle city with `8` rocks, the total from below is `5` and the total from the left is `7`. Its best total is `8 + max(5, 7) = 15`.

The last entry is `23`, which is the maximum total at the top-right city.

## Complexity

For `R` rows and `C` columns, the time is `O(R × C)` because each city is processed once. Each rock count has at most seven digits, so parsing each value takes bounded time.

Extra space is `O(C)` for the `best` list and the split values of one row.

A path visits exactly `R + C - 1` cities. The largest possible total is `1999 × 1,000,000 = 1,999,000,000`.

## Python Code

```python
from typing import List


class MaximumRocks:
    def __init__(self):
        pass

    def maximumRocks(self, cityRocks: List[str]) -> int:
        rows = len(cityRocks)
        cols = len(cityRocks[0].split(","))
        best = [0] * cols

        # Move upward from the starting row.
        for row in range(rows - 1, -1, -1):
            values = cityRocks[row].split(",")

            for col in range(cols):
                rocks = int(values[col])

                if col == 0:
                    # The first column can only be reached from below.
                    best[col] += rocks
                else:
                    # best[col] is from below. best[col - 1] is from the left.
                    best[col] = rocks + max(best[col], best[col - 1])

        return best[cols - 1]
```
