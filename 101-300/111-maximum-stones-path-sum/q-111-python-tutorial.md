# Maximum Stones Path in Python

#### Problem Statement
[https://codezym.com/question/111-maximum-stones-path-sum](https://codezym.com/question/111-maximum-stones-path-sum)

The key idea is to remember the best total from each cell to the top-right. A cell can move only up or right, so we add its value to the larger of those two totals. Dynamic programming calculates each result once, and a single list stores the results for one row.


## Start with all possible paths

Start at the bottom-left cell. At each step, try moving up and moving right. Add the values along each complete path and return the largest sum.


This repeats work because many paths reach the same cell and explore the same remaining routes. The number of paths grows quickly when both grid dimensions increase, so this approach is too slow for a `1000 x 1000` grid.


## Save the best total for each cell

Let `best[r][c]` mean the maximum stones collected from cell `(r, c)` to the top-right, including both endpoints.


For a cell with both moves available:

```text
best[r][c] = value[r][c] + max(best[r - 1][c], best[r][c + 1])
```

The top-right cell contributes only its own value. On the top row, we can only move right. On the rightmost column, we can only move up.


Process rows from top to bottom, and process each row from right to left. This makes the results above and to the right available before we need them. We are calculating answers backwards from the destination, while the allowed path moves remain up and right.


## Keep only one row

A full grid of saved totals would work, but we only need one row at a time. Use a list called `dp` with one entry per column.


Before updating column `c`:

- `dp[c]` holds the best total from the cell above, if one exists.
- If there is a cell to the right, `dp[c + 1]` holds its best total, already calculated in the current row.

After the update, `dp[c]` holds the best total from the current cell. After the bottom row is complete, `dp[0]` is the answer for the bottom-left starting cell.


Parse one comma-separated row at a time. There is no need to create a full integer grid. `MaximumStonesPath` provides the required method and keeps no state between calls.


## Walk through an example

```text
locations = ["9,0,2,0,1", "0,1,1,1,0", "2,0,0,0,0"]

After the top row:     dp = [12, 3, 3, 1, 1]
After the middle row:  dp = [12, 5, 4, 2, 1]
After the bottom row:  dp = [14, 5, 4, 2, 1]
```

Each entry gives the best path total from that cell to the destination.


The answer is `14`. One best path collects `2 + 0 + 9 + 0 + 2 + 0 + 1`. The starting and ending cells are both included. For a single-cell grid, that cell is counted once.


## Why this works

Except at the destination, every path must first go up or right. Once we know the best total from each available next cell, taking the larger total and adding the current value gives the best total from the current cell.


The destination starts with its own value. Our processing order makes each needed result ready before it is used. Applying the same rule to every cell therefore gives the correct result at the starting cell.


## Complexity

For `R` rows and `C` columns, time is **O(R * C)**. Processing each grid cell takes constant work.


Extra space is **O(C)** for `dp` and the parsed values of one row. This excludes the supplied input.


## Python code

```python
from dataclasses import dataclass
from typing import List


@dataclass
class MaximumStonesPath:
    def maximumStones(self, locations: List[str]) -> int:
        """Return the maximum stones from the bottom-left to the top-right."""
        cols = len(locations[0].split(","))
        dp = [0] * cols
        first_row = True

        for location in locations:
            values = location.split(",")

            # The cells above and to the right must be ready before this cell.
            for col in range(cols - 1, -1, -1):
                stones = int(values[col].strip())

                if first_row and col == cols - 1:
                    # The destination contributes its own value once.
                    dp[col] = stones
                elif first_row:
                    # On the top row, the only possible move is right.
                    dp[col] = stones + dp[col + 1]
                elif col == cols - 1:
                    # On the rightmost column, the only possible move is up.
                    dp[col] = stones + dp[col]
                else:
                    # dp[col] is from above. dp[col + 1] is from the right.
                    dp[col] = stones + max(dp[col], dp[col + 1])

            first_row = False

        # After the last row, column zero is the starting cell.
        return dp[0]
```
