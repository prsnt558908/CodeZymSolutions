# Improve Fire Station Coverage in Python

#### Problem Statement
[https://codezym.com/question/433-improve-fire-station-coverage](https://codezym.com/question/433-improve-fire-station-coverage)


## Core Idea

A fire truck can move in 8 directions, like a king in chess. So in `limit` minutes, a station reaches every cell of a square around it.


We solve the problem in two steps. First, one multi source BFS finds how far every neighborhood is from its nearest station. Then we turn the question around. Instead of asking "where should we build?", we ask "can the response time be at most `limit`?". All neighborhoods that are still farther than `limit` must fit inside the square of the one new station.


We start with a brute force idea and see why it is slow. Then we find the smallest working `limit` in two ways:

1. **BFS + Binary Search:** guess a `limit` and check it.
2. **BFS + One Sweep:** lower the `limit` step by step and stop at the first one that fails.


## Reading the Input

Each row comes as a string like `"100"`. Character `c` of row `r` is `"1"` when neighborhood `(r, c)` has a fire station, and `"0"` when it does not. We read it directly as `townMap[r][c]`, so no parsing step is needed.


## Travel Time Is a Square

Travel time is the bigger of the row difference and the column difference. Diagonal moves are allowed, so going 3 rows down and 2 columns right takes only 3 minutes.


So the cells a station reaches within `limit` minutes form a square of side `2 × limit + 1`, with the station in the middle. For `limit = 1`:

```
. . . . .
. x x x .
. x S x .
. x x x .
. . . . .
```


Keep this square in mind. The whole solution is built on it.


## Brute Force: Try Every Empty Cell

Try building nothing, and also try building the new station in every empty cell. For each choice, `responseTime(grid)` runs one BFS from all stations (Step 1 shows how) and returns the biggest distance. Keep the smallest result.


Here `grid` is a 2D list copy of the town, so we can change a cell and undo it.

```python
# Build nothing (allowed only if the town already has a station)
best = self.responseTime(grid) if has_station else float("inf")

# Or build the new station in each empty cell, one by one
for r in range(rows):
    for c in range(cols):
        if grid[r][c] == 0:
            grid[r][c] = 1                             # build here
            best = min(best, self.responseTime(grid))  # BFS over the whole town
            grid[r][c] = 0                             # undo
return best
```


**Problem:** a 500 × 500 town has 250,000 cells. That is up to 250,000 choices, and every BFS visits all 250,000 cells again. About 62 billion steps is far too slow.


## Step 1: Distance to the Nearest Station (Multi Source BFS)

Let `dist[r][c]` be the minutes from cell `(r, c)` to its nearest fire station.


We put **all** stations into the queue at the start, each with distance 0. Then we run a normal BFS with the 8 moves. A cell gets its distance from the first station that reaches it, and that is always its nearest one.


There are no walls, so the number of BFS steps between two cells is exactly their travel time. For Example 1:

```
townMap      dist
1 0 0        0 1 2
0 0 0        1 1 2
0 0 0        2 2 2
```


Every cell starts with `not_reached = rows + cols`, a value bigger than any real distance. If the town has no station, BFS does nothing and every cell keeps this value. So every cell counts as far (see Step 2), and the code is forced to build the one station the rules ask for. No special case is needed.


## Step 2: Turn the Question Around

Pick a number `limit` and ask: can the response time be at most `limit`?


Cells with `dist <= limit` are already fine. Let us call the others **far cells**. The new station alone must reach all of them.


Draw the smallest box around all far cells. Put the new station in the middle of the box. It reaches the whole box when the box is at most `2 × limit + 1` cells tall and wide. In code:

```python
bottom - top <= 2 * limit and right - left <= 2 * limit
```


If the box is taller than that, no single station can reach both the top-most and the bottom-most far cell. The same is true for the width. So this one check gives the exact answer.


**Is the middle cell empty?** Yes. It is within `limit` minutes of every far cell. If it had a station, those cells would not be far. So we never have to check it.


In Example 1 with `limit = 1`, the far cells are the 2s. Their box is the whole 3 × 3 town, and a 3 × 3 square covers it. Building at the middle `(1, 1)` works.


With `limit = 0`, every cell except `(0, 0)` is far. The box is still 3 × 3, but the square is only 1 × 1. So `limit = 0` fails, and the answer is 1.


## Key Observation

If a `limit` works, every bigger `limit` works too. A bigger limit means fewer far cells and a bigger square.


For Example 2 (a 5 × 5 town with no station):

| limit | 0 | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- | --- |
| Works? | No | No | Yes | Yes | Yes | Yes |


All the "No" answers come first, then all the "Yes" answers. We only need the first "Yes".


## Solution 1: BFS + Binary Search

### How it works

1. Run the BFS once to fill `dist`.
2. Binary search `limit` between 0 and `max(rows, cols)`. The answer is never bigger, because any single station reaches the whole town in less time than that.
3. `canAchieve(mid)` scans `dist` once, builds the box around the far cells and checks its size.


- If `mid` works, the answer is `mid` or smaller.
- If it fails, the answer is bigger than `mid`.


### Why a `dist` grid and a queue?

`dist` is filled once and reused by every check. Without it, each check would need its own BFS.


The queue (`deque`) gives back cells in the order BFS reached them, so closer cells always come out first. Each entry is a small tuple `(row, col)`, so we do not need an extra class.


### Complexity

- **Time:** O(rows × cols × log(max(rows, cols))). BFS runs once, and binary search makes about 10 checks of one grid scan each.
- **Space:** O(rows × cols) for `dist` and the queue.


### Code

```python
from collections import deque
from typing import List


class ImproveFireStationCoverage:

    def __init__(self):
        # The 8 moves: up, down, left, right and the 4 diagonals
        self.directions = [(-1, -1), (-1, 0), (-1, 1), (0, -1),
                           (0, 1), (1, -1), (1, 0), (1, 1)]
        self.rows = 0
        self.cols = 0
        self.dist = []  # dist[r][c] = minutes from (r, c) to its nearest fire station

    def minimumFireResponseTime(self, townMap: List[str]) -> int:
        self.rows, self.cols = len(townMap), len(townMap[0])
        self.findDistances(townMap)

        # The answer is never more than the longer side of the town
        low, high = 0, max(self.rows, self.cols)

        # Find the smallest limit that we can reach with one new station
        while low < high:
            mid = (low + high) // 2
            if self.canAchieve(mid):
                high = mid  # mid works, look for a smaller one
            else:
                low = mid + 1  # mid is too small
        return low

    def findDistances(self, townMap: List[str]) -> None:
        """
        Multi source BFS. All fire stations start in the queue together,
        so every cell is first reached by its nearest station.
        """
        rows, cols = self.rows, self.cols
        not_reached = rows + cols  # bigger than any real distance
        dist = [[not_reached] * cols for _ in range(rows)]
        queue = deque()

        for r in range(rows):
            for c in range(cols):
                if townMap[r][c] == "1":
                    dist[r][c] = 0  # a station is 0 minutes from itself
                    queue.append((r, c))

        while queue:
            row, col = queue.popleft()
            for d_row, d_col in self.directions:
                next_row, next_col = row + d_row, col + d_col

                if not (0 <= next_row < rows and 0 <= next_col < cols):
                    continue  # outside the town
                if dist[next_row][next_col] != not_reached:
                    continue  # already has its distance
                dist[next_row][next_col] = dist[row][col] + 1
                queue.append((next_row, next_col))

        self.dist = dist

    def canAchieve(self, limit: int) -> bool:
        """Can the response time be at most limit, if we build at most one new station?"""
        # Box around all far cells (cells with dist > limit)
        top, bottom, left, right = self.rows, -1, self.cols, -1

        for r in range(self.rows):
            for c in range(self.cols):
                if self.dist[r][c] > limit:
                    top = min(top, r)
                    bottom = max(bottom, r)
                    left = min(left, c)
                    right = max(right, c)

        if bottom == -1:
            return True  # no far cells, the old stations are enough

        # A new station in the middle of the box reaches all far cells
        # only if the box is at most (2 * limit + 1) cells tall and wide
        return bottom - top <= 2 * limit and right - left <= 2 * limit
```


## Solution 2: BFS + One Sweep

Binary search still scans the whole grid about 10 times. We can do the same work with a single scan.


### How it works

Start with `limit = max_dist`, the biggest value in `dist`. No cell is far, so it works. Then lower `limit` one step at a time.


When `limit` goes down by one, only the cells with `dist = limit + 1` become far. So the far box only grows, one group of cells at a time.


To grow it quickly, we scan the grid once and store a small box for every distance `d`. `top[d]`, `bottom[d]`, `left[d]` and `right[d]` give the box around all cells with `dist = d`. Adding a whole group to the far box is then just 4 `min` / `max` calls.


We stop at the first `limit` that fails. By the Key Observation, every smaller limit fails too, so the last working `limit` is the answer.


For Example 1 (`max_dist = 2`):

| limit | Cells that become far | Far box | Square | Works? |
| --- | --- | --- | --- | --- |
| 2 | none | empty | 5 × 5 | Yes |
| 1 | `dist = 2` | 3 × 3 | 3 × 3 | Yes |
| 0 | `dist = 1` | 3 × 3 | 1 × 1 | No, stop |


The last working limit is 1, so the answer is 1.


### Why four box lists?

`top`, `bottom`, `left` and `right` have one entry per distance value. With them, each step adds a whole group of cells in O(1) time, instead of scanning the grid again.


### Complexity

- **Time:** O(rows × cols). One BFS, one scan to fill the box lists, and at most `max_dist` cheap steps.
- **Space:** O(rows × cols) for `dist` and the queue. The box lists are small, with `max_dist + 1` entries each.


### Code

```python
from collections import deque
from typing import List


class ImproveFireStationCoverage:

    def __init__(self):
        # The 8 moves: up, down, left, right and the 4 diagonals
        self.directions = [(-1, -1), (-1, 0), (-1, 1), (0, -1),
                           (0, 1), (1, -1), (1, 0), (1, 1)]
        self.rows = 0
        self.cols = 0
        self.dist = []  # dist[r][c] = minutes from (r, c) to its nearest fire station

        # Box around the cells at distance exactly d:
        # rows from top[d] to bottom[d], columns from left[d] to right[d]
        self.top = []
        self.bottom = []
        self.left = []
        self.right = []

    def minimumFireResponseTime(self, townMap: List[str]) -> int:
        self.rows, self.cols = len(townMap), len(townMap[0])
        self.findDistances(townMap)

        max_dist = max(max(row) for row in self.dist)
        self.groupByDistance(max_dist)

        # Box around all far cells. With limit = max_dist no cell is far, so it starts empty.
        far_top, far_bottom, far_left, far_right = self.rows, -1, self.cols, -1
        answer = max_dist

        # Try smaller and smaller limits until one fails
        for limit in range(max_dist - 1, -1, -1):
            # Cells at distance limit + 1 are now far, so grow the box to hold them
            d = limit + 1
            far_top = min(far_top, self.top[d])
            far_bottom = max(far_bottom, self.bottom[d])
            far_left = min(far_left, self.left[d])
            far_right = max(far_right, self.right[d])

            # The far cells do not fit in one square anymore.
            # Smaller limits only add more far cells, so they fail too.
            if far_bottom - far_top > 2 * limit or far_right - far_left > 2 * limit:
                break
            answer = limit
        return answer

    def findDistances(self, townMap: List[str]) -> None:
        """
        Multi source BFS. All fire stations start in the queue together,
        so every cell is first reached by its nearest station.
        """
        rows, cols = self.rows, self.cols
        not_reached = rows + cols  # bigger than any real distance
        dist = [[not_reached] * cols for _ in range(rows)]
        queue = deque()

        for r in range(rows):
            for c in range(cols):
                if townMap[r][c] == "1":
                    dist[r][c] = 0  # a station is 0 minutes from itself
                    queue.append((r, c))

        while queue:
            row, col = queue.popleft()
            for d_row, d_col in self.directions:
                next_row, next_col = row + d_row, col + d_col

                if not (0 <= next_row < rows and 0 <= next_col < cols):
                    continue  # outside the town
                if dist[next_row][next_col] != not_reached:
                    continue  # already has its distance
                dist[next_row][next_col] = dist[row][col] + 1
                queue.append((next_row, next_col))

        self.dist = dist

    def groupByDistance(self, max_dist: int) -> None:
        """Fills top, bottom, left and right for every distance from 0 to max_dist."""
        # Every box starts empty
        self.top = [self.rows] * (max_dist + 1)
        self.bottom = [-1] * (max_dist + 1)
        self.left = [self.cols] * (max_dist + 1)
        self.right = [-1] * (max_dist + 1)

        for r in range(self.rows):
            for c in range(self.cols):
                d = self.dist[r][c]
                self.top[d] = min(self.top[d], r)
                self.bottom[d] = max(self.bottom[d], r)
                self.left[d] = min(self.left[d], c)
                self.right[d] = max(self.right[d], c)
```