# Longest Path in Grid in Python

#### Problem Statement
[https://codezym.com/question/152-longest-path-grid](https://codezym.com/question/152-longest-path-grid)


Finding the longest path in a grid has no shortcut. BFS gives us the shortest path, but there is no similar trick for the longest one. That is why the grid is kept tiny: at most 30 cells.

So we simply try every path using **backtracking**. We start a walk from each empty cell in the first row, mark cells as we use them, and step back to try other directions. Every time we stand on the last row, the path so far is a valid answer.

Plain backtracking is correct but slow on open grids. So we add **pruning**: before going deeper, a quick flood fill tells us if the last row can still be reached and if this path can still beat our best answer. If not, we turn back right away.


## Understanding the Example

`grid = ["0,0,0", "1,0,1", "0,0,0"]`, answer is `5`

```
grid          one longest path
0 0 0         1 2 .
1 0 1         # 3 #
0 0 0         5 4 .
```

The numbers show the order of cells in the path. `#` is a wall and `.` is an unused cell.

Three things to notice:

1. The path can move sideways, even inside the first row and the last row.
2. The path touches the last row at cell `4` and then keeps going to cell `5`. So reaching the last row does not end the walk. We record the length and keep exploring.
3. If the grid has only one row, the first row is also the last row. So a single empty cell is already a valid path of length `1`.


## Brute Force: Try Every Path

We use DFS (depth first search). From the current cell we try all 4 directions. We move to the next cell only if it is inside the grid, is not a wall and is not already on our path.

```python
# length = number of cells in the path so far
def dfs(self, row: int, col: int, length: int) -> None:
    self.visited[row][col] = True  # step in

    # standing on the last row: the path so far is valid
    if row == self.rows - 1:
        self.best = max(self.best, length)

    for dr, dc in self.moves:
        if self.isFree(row + dr, col + dc):
            self.dfs(row + dr, col + dc, length + 1)

    self.visited[row][col] = False  # step out (backtrack)
```

We call `self.dfs(0, col, 1)` for every empty cell in the first row.

**Why a `visited` grid?** A path can't use a cell twice. A 2D list of booleans tells us instantly if a cell is already on the current path.

**Why unmark at the end?** A cell is blocked only for the current path. When we step back, other paths must be able to use it. This "mark on the way in, unmark on the way out" is what backtracking means. It also lets one grid serve every path, so we never copy the path.


### Why Is It Slow?

It explores every possible path. On an open 5 x 6 grid (no walls) it makes about **8.4 million** DFS calls. Most of these paths are hopeless:

1. **Boxed in:** the walk's own cells block every way to the last row, so it can never end there. But it keeps wandering anyway.
2. **Can't win:** the best path so far has 20 cells. Our path has 8 cells and only 10 free cells are left to reach. Even if we use all of them, we get only 18.


## Better Solution: Backtracking With Pruning

Before going deeper from a cell, we run a quick **flood fill** (BFS with a queue) from it over the free cells. It gives us two facts:

- `can_reach_last_row`: can any free cell in the last row still be reached? If not, we are **boxed in**.
- `free_cells`: how many free cells we can still reach. Our path can never grow longer than `length + free_cells`. If that is not more than `best`, we **can't win**.

We go deeper only when both checks pass:

```python
return can_reach_last_row and length + free_cells > self.best
```

Each flood fill looks at no more than 30 cells, so it is cheap. But it cuts away huge parts of the search. On the same open 5 x 6 grid, DFS calls drop from about 8.4 million to about **100**.

**Why a separate `seen` grid?** The flood fill must not change `visited`, because `visited` holds our current path. `seen` only remembers which cells this flood fill has already counted.


### Complexity

- **Time:** exponential in the worst case, since the number of paths grows very fast with grid size. Each DFS call also runs an `O(R x C)` flood fill. This is fine because `R x C <= 30`, and pruning removes most paths.
- **Space:** `O(R x C)` for the grids, the queue and the recursion stack.


### Python Code

```python
from collections import deque
from typing import List


class LongestPathInGrid:

    def __init__(self):
        self.rows = 0
        self.cols = 0
        # wall[r][c] is True if cell (r, c) is a wall
        self.wall = []
        # visited[r][c] is True if cell (r, c) is on the current path
        self.visited = []
        # longest valid path found so far
        self.best = 0
        # the 4 moves as (row change, col change): up, down, left, right
        self.moves = [(-1, 0), (1, 0), (0, -1), (0, 1)]

    def longestPath(self, grid: List[str]) -> int:
        self.readGrid(grid)
        self.best = 0

        # a path can start from any empty cell in the first row
        for col in range(self.cols):
            if not self.wall[0][col]:
                self.dfs(0, col, 1)
        return self.best

    def readGrid(self, grid: List[str]) -> None:
        """Converts rows like "0,1,0" into a 2D wall grid."""
        self.rows = len(grid)
        self.cols = len(grid[0].split(","))
        self.wall = [[value.strip() == "1" for value in row.split(",")] for row in grid]
        self.visited = [[False] * self.cols for _ in range(self.rows)]

    def dfs(self, row: int, col: int, length: int) -> None:
        """
        We are standing on (row, col) and the path so far has 'length' cells.
        Tries every way to extend the path, then steps back.
        """
        self.visited[row][col] = True

        # standing on the last row means the path so far is valid
        if row == self.rows - 1:
            self.best = max(self.best, length)

        # go deeper only if this path can still give a better answer
        if self.worthExploring(row, col, length):
            for dr, dc in self.moves:
                if self.isFree(row + dr, col + dc):
                    self.dfs(row + dr, col + dc, length + 1)

        # backtrack: free this cell so other paths can use it
        self.visited[row][col] = False

    def worthExploring(self, row: int, col: int, length: int) -> bool:
        """
        Flood fill (BFS) from (row, col) over free cells.
        Returns False if the last row can no longer be reached,
        or if even using every reachable cell we can't beat 'best'.
        """
        # kept separate from 'visited', which holds the current path
        seen = [[False] * self.cols for _ in range(self.rows)]
        seen[row][col] = True

        queue = deque([(row, col)])
        free_cells = 0
        can_reach_last_row = False

        while queue:
            cell_row, cell_col = queue.popleft()
            for dr, dc in self.moves:
                r, c = cell_row + dr, cell_col + dc
                if self.isFree(r, c) and not seen[r][c]:
                    seen[r][c] = True
                    queue.append((r, c))
                    free_cells += 1
                    if r == self.rows - 1:
                        can_reach_last_row = True

        return can_reach_last_row and length + free_cells > self.best

    def isFree(self, row: int, col: int) -> bool:
        """True if (row, col) is inside the grid, not a wall and not on the current path."""
        return (0 <= row < self.rows and 0 <= col < self.cols
                and not self.wall[row][col] and not self.visited[row][col])
```