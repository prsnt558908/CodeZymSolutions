# Longest Constrained Path in Matrix in Python

#### Problem Statement
[https://codezym.com/question/141-longest-constrained-path-matrix](https://codezym.com/question/141-longest-constrained-path-matrix)


## Core Idea

The grid has fewer than 20 cells. That is very small, so we can simply try every valid path and keep the longest one.


We grow each path one cell at a time using DFS with backtracking. We step on a cell, try to go further, and when we come back we free that cell so other paths can use it.


The rule looks tricky, but it becomes very simple: the next value must be `<=` the bigger of the last two values in the path. So while growing a path, we only need to remember one number. We call it the `limit`.


## Understanding the Rule

The rule says the next cell `C` must satisfy `grid[C] <= grid[B]` or `grid[C] <= grid[A]`, where `A` and `B` are the last two cells.


If `C` is `<=` at least one of them, then it is also `<=` the bigger one. So the rule is just:

```
grid[C] <= max(grid[A], grid[B])
```


When the path has only one cell `A`, the next cell must be `<= grid[A]`. This is the same formula if we treat `A` as both of the last two cells.


Let us check the path `5 -> 4 -> 2 -> 3` from Example 2.

| Path so far | Last two values | Limit | Next value | Allowed? |
|---|---|---|---|---|
| `5` | 5 | 5 | 4 | Yes |
| `5 -> 4` | 5, 4 | 5 | 2 | Yes |
| `5 -> 4 -> 2` | 4, 2 | 4 | 3 | Yes |


Notice the last move goes up from 2 to 3. It is allowed because the cell before 2 had value 4.


## Why Not a Simple DP?

In the classic "Longest Increasing Path in a Matrix" problem, values strictly increase. A path can never reach a cell it already used, so storing one answer per cell is enough.


Here, equal values are allowed, and a path can even step up (like 2 to 3 above). So a path can run into cells it already used, and the best answer from a cell depends on which cells are already taken.


That is why we keep track of the used cells and try every path. With fewer than 20 cells, this is fast.


## Solution 1: Backtracking With the Full Path

The most direct idea is to follow the problem statement word by word. We keep the whole path in a list of cells.


From the last cell, we try all 4 neighbors. A neighbor is allowed if it is inside the grid, it is not already in the path, and it passes the rule. We add it, go deeper, and then remove it (backtrack).


We start this DFS once from every cell.

```python
# path holds the cells in the order we visited them
def dfs(self, path: List[tuple]) -> None:
    self.best = max(self.best, len(path))
    last_row, last_col = path[-1]

    for d in range(4):
        row = last_row + self.row_step[d]
        col = last_col + self.col_step[d]
        if not (0 <= row < self.rows and 0 <= col < self.cols):
            continue
        if self.contains(path, row, col):
            continue
        if not self.isValidMove(path, self.values[row][col]):
            continue

        path.append((row, col))
        self.dfs(path)
        path.pop()  # backtrack

# scans the whole path to see if a cell is already used
def contains(self, path: List[tuple], row: int, col: int) -> bool:
    for cell in path:
        if cell == (row, col):
            return True
    return False

# the two rules exactly as written in the problem
def isValidMove(self, path: List[tuple], next_value: int) -> bool:
    b_row, b_col = path[-1]
    if len(path) == 1:
        return next_value <= self.values[b_row][b_col]

    a_row, a_col = path[-2]
    return next_value <= self.values[b_row][b_col] or next_value <= self.values[a_row][a_col]
```


This gives the right answer, but it does extra work:

- `contains()` scans the whole path every time we try a neighbor.
- We carry the full path around, but the rule only needs the last two values.
- The rule check has two separate cases.


## Solution 2: Backtracking With a Visited Grid and a Limit

We fix each of these problems with a small change.


**1. A `visited` grid instead of scanning the path.**

`visited[row][col]` tells us in one step if a cell is already in the path. We set it to `True` when we step on a cell and back to `False` when we step back. Freeing the cell matters, because a different path may want to use it later.


**2. One `limit` value instead of the whole path.**

The next cell must be `<= limit`. After moving from cell `X` to cell `Y`, the new limit is `max(value of X, value of Y)`.


**3. No special case for the first move.**

When a path starts at a cell, we set `limit` to that cell's own value. This is exactly the one cell rule.


We also convert the input strings into a 2D list `values` once, at the start. So we never parse text inside the search.


### Python Code

```python
from typing import List


class LongestConstrainedPath:

    def __init__(self):
        self.rows = 0
        self.cols = 0
        self.values = []    # grid values after parsing the strings
        self.visited = []   # cells that are already part of the current path
        self.best = 0       # longest path length found so far

        # the 4 moves: up, down, left, right
        self.row_step = [-1, 1, 0, 0]
        self.col_step = [0, 0, -1, 1]

    def longestConstrainedPath(self, grid: List[str]) -> int:
        self.parseGrid(grid)
        self.visited = [[False] * self.cols for _ in range(self.rows)]
        self.best = 0

        # a path can start from any cell
        for row in range(self.rows):
            for col in range(self.cols):
                self.visited[row][col] = True
                # a one cell path: the next cell must be <= this cell's own value
                self.dfs(row, col, self.values[row][col], 1)
                self.visited[row][col] = False
        return self.best

    def dfs(self, row: int, col: int, limit: int, length: int) -> None:
        """
        Tries every valid way to extend the path that ends at (row, col).

        limit:  the bigger value among the last two cells of the path.
                The next cell must have a value <= limit.
        length: number of cells in the current path.
        """
        self.best = max(self.best, length)

        for d in range(4):
            next_row = row + self.row_step[d]
            next_col = col + self.col_step[d]

            if not (0 <= next_row < self.rows and 0 <= next_col < self.cols):
                continue
            if self.visited[next_row][next_col]:
                continue
            if self.values[next_row][next_col] > limit:
                continue

            self.visited[next_row][next_col] = True
            # after this move, the last two cells are (row, col) and (next_row, next_col)
            next_limit = max(self.values[row][col], self.values[next_row][next_col])
            self.dfs(next_row, next_col, next_limit, length + 1)
            self.visited[next_row][next_col] = False  # backtrack: free the cell for other paths

    def parseGrid(self, grid: List[str]) -> None:
        """Converts rows like "5,4" into a 2D list of ints."""
        self.values = [[int(part) for part in line.split(",")] for line in grid]
        self.rows = len(self.values)
        self.cols = len(self.values[0])
```


## Complexity

Let `N = m * n` be the number of cells.


- **Time:** we explore every valid path once, and for each one we check 4 neighbors. The number of paths grows very fast as the grid gets bigger, but here the grid has fewer than 20 cells. The hardest case is when all values are equal, because then every move is allowed. Even then, a `3 x 6` grid has fewer than 60,000 paths in total.
- **Space:** `O(N)` for the `values` and `visited` grids, and for the recursion depth (a path has at most `N` cells).