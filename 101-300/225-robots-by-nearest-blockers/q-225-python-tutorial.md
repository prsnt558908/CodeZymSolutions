# Find Robots in Location Map by Nearest Blockers in Python

#### Problem Statement
[https://codezym.com/question/225-robots-by-nearest-blockers](https://codezym.com/question/225-robots-by-nearest-blockers)


For every robot we need four numbers: how far the nearest blocker is on its left, top, bottom and right. A robot is in the answer only when all four numbers match the query.

The core trick is a **step counter**. If we walk along a row from left to right and count the steps since the last blocker we passed, every robot we meet already knows its left distance.

So instead of walking out from every robot again and again, we walk each row and each column once in each direction. A robot that matches the query in all four walks goes into the answer.

Every cell is visited only a few times, which is fast even for a 1000 × 1000 map.


## Approach 1: Brute Force (Walk Out From Every Robot)

The distance in a direction is the number of steps a robot takes, one cell at a time, until it steps onto an `X` or steps outside the map. Stepping outside means it hit the boundary blocker.

Other robots do not block, so we simply walk over them.

Let's check Example 1. Row numbers are on the left and column numbers are on top.

```
     0 1 2 3
  0  O E E X
  1  E E E X
  2  X E O E
  3  E E E E
```

For the robot at `"2,2"`:

| Direction | What it hits | Distance |
|---|---|---|
| Left | `X` at column 0 | 2 |
| Top | top boundary | 3 |
| Bottom | bottom boundary | 2 |
| Right | right boundary | 2 |

That is `[2, 3, 2, 2]`, exactly the query. So the answer is `["2,2"]`.

In code, a direction is two numbers `(dr, dc)`: how much the row and the column change in one step. Left is `(0, -1)`, top is `(-1, 0)`, bottom is `(1, 0)` and right is `(0, 1)`.

```python
# Steps from the robot at (r, c) to the nearest blocker in direction (dr, dc)
def distance(self, r: int, c: int, dr: int, dc: int) -> int:
    steps = 0
    while True:
        r += dr
        c += dc
        steps += 1
        # stop when we step outside the grid (boundary) or onto an X
        if r < 0 or r >= self.rows or c < 0 or c >= self.cols or self.grid[r][c] == 'X':
            return steps
```

Then we check every robot against the query:

```python
for r in range(self.rows):
    for c in range(self.cols):
        if self.grid[r][c] != 'O':
            continue
        left = self.distance(r, c, 0, -1)
        top = self.distance(r, c, -1, 0)
        bottom = self.distance(r, c, 1, 0)
        right = self.distance(r, c, 0, 1)
        if [left, top, bottom, right] == query:
            result.append(f"{r},{c}")
```

### Why is this slow?

In the worst case, each robot walks across its whole row and its whole column. That is about `rows + cols` steps for a single robot.

A 1000 × 1000 map full of robots has 1,000,000 robots. That adds up to around 2,000,000,000 steps. Far too slow.

Look at the waste: robots in the same row walk over the same cells again and again. Let's remove that repeated work.


## Approach 2: Walk Each Row and Column Once (Optimal)

### The step counter

Walk along a row from left to right and carry a counter called `steps`.

- Before the first cell we are standing on the boundary blocker, so `steps` starts at 0.
- On every new cell, add 1 to `steps`.
- If the cell is `X`, reset `steps` to 0. This `X` is now the nearest blocker behind us.
- If the cell is a robot, `steps` is exactly its left distance.

Robots do not reset the counter.

Here is row 2 of Example 1, which is `X E O E`:

| Column | Cell | `steps` | Meaning |
|---|---|---|---|
| 0 | `X` | 0 | blocker, reset |
| 1 | `E` | 1 | |
| 2 | `O` | 2 | robot, left distance is 2 |
| 3 | `E` | 3 | |

One walk over a row gives the left distance of every robot in that row. No robot needs its own walk anymore.

### Same trick for all four directions

While walking, the blockers behind us are always on the side we came from. So:

| Distance we want | How we walk |
|---|---|
| left | each row, left to right |
| right | each row, right to left |
| top | each column, top to bottom |
| bottom | each column, bottom to top |

One method `walkLine(r, c, dr, dc, need)` handles all four. It starts at one end of a row or column and keeps moving by `(dr, dc)` until it leaves the grid. `need` is the query value for that direction.

### Why a `matchCount` grid?

We don't really need to store the four distances. We only need to know whether all four match the query.

So we keep one 2D list of counts called `matchCount`. During a walk, when a robot's distance equals `need`, we add 1 to its cell.

After all four walks, a robot with `matchCount == 4` matches the whole query. Only robots ever get counted, so empty cells and blockers stay at 0.

This is lighter and simpler than keeping four separate distance grids.

### Sorted output for free

The answer must be sorted by row, then by column. If we scan `matchCount` row by row, and each row from left to right, we meet the answers in exactly that order. So no sorting is needed.

### Python Code

A string can be indexed directly, so the map itself is used as `grid` and every walk reads a cell as `grid[r][c]`.

```python
from typing import List


class NearestBlockers:

    def __init__(self):
        # Shared by findRobots() and walkLine() during one call
        self.rows = 0
        self.cols = 0
        self.grid = []
        # matchCount[r][c] = how many of the 4 query distances the robot at (r, c) matches
        self.matchCount = []

    def findRobots(self, locationMap: List[str], query: List[int]) -> List[str]:
        self.rows = len(locationMap)
        self.cols = len(locationMap[0])
        self.grid = locationMap  # a string can be indexed directly, so grid[r][c] works
        self.matchCount = [[0] * self.cols for _ in range(self.rows)]

        needLeft, needTop, needBottom, needRight = query

        # Rows: left to right gives left distances, right to left gives right distances
        for r in range(self.rows):
            self.walkLine(r, 0, 0, 1, needLeft)
            self.walkLine(r, self.cols - 1, 0, -1, needRight)
        # Columns: top to bottom gives top distances, bottom to top gives bottom distances
        for c in range(self.cols):
            self.walkLine(0, c, 1, 0, needTop)
            self.walkLine(self.rows - 1, c, -1, 0, needBottom)

        # Scanning row by row, then column by column, gives the required sorted order
        result = []
        for r in range(self.rows):
            for c in range(self.cols):
                if self.matchCount[r][c] == 4:
                    result.append(f"{r},{c}")
        return result

    def walkLine(self, r: int, c: int, dr: int, dc: int, need: int) -> None:
        """
        Walks one full row or column, starting at (r, c) and moving by (dr, dc) each step.
        need = the query distance for this direction.
        steps = distance from the nearest blocker behind us.
        It starts at 0 because the boundary blocker sits just behind the first cell.
        """
        steps = 0
        while 0 <= r < self.rows and 0 <= c < self.cols:
            steps += 1
            cell = self.grid[r][c]
            if cell == 'X':
                steps = 0  # this X is now the nearest blocker behind us
            elif cell == 'O' and steps == need:
                self.matchCount[r][c] += 1  # one more direction matches the query
            r += dr
            c += dc
```

### Complexity

- **Time:** `O(rows × cols)`. Each cell is visited 4 times by the walks and once more in the final scan.
- **Space:** `O(rows × cols)` for `matchCount`.