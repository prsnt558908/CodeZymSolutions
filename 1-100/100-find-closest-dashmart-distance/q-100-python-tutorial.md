# Find Closest DashMart Distance in Python

#### Problem Statement
[https://codezym.com/question/100-find-closest-dashmart-distance](https://codezym.com/question/100-find-closest-dashmart-distance)

This is a shortest path problem on a grid where every move costs exactly one step. BFS (Breadth First Search) is the natural tool for this, because it visits cells in order of their distance.

The main trick is to flip the question. Instead of running a separate BFS from every location to look for a DashMart, we run one BFS that starts from all DashMarts at the same time. This single pass gives every cell its distance to the closest DashMart, so each query becomes a simple lookup.

For the follow-up, the same BFS also remembers which DashMart reached each cell. Then we just count customers per DashMart and pick the winner.

## Reading the Input

- Each city row looks like `"X|.|.|D"`. We split it on `|` to get the cells. Python's `str.split` treats `|` as a plain character, so `row.split('|')` is all we need.
- Each location is a string such as `"1,4"`. We pull out the two numbers with `re.findall(r'-?\d+', location)`, so formats like `"1,4"`, `"[1, 4]"` or `"-1,5"` all work. If a location already comes as a `[row, col]` list, we use it directly.
- Every cell except `X` can be walked on. That includes `C` and `D` cells.

## Approach 1: BFS From Every Location (Brute Force)

The most direct idea is to run a BFS from each location and stop at the first DashMart we meet. BFS spreads out one step at a time, so the first DashMart it meets is the closest one.

```python
def bfs_from_location(self, start_row, start_col):
    """Brute force: BFS from one location until we meet the first DashMart."""
    if not self.is_inside_city(start_row, start_col) or self.grid[start_row][start_col] == 'X':
        return -1
    visited = [[False] * self.cols for _ in range(self.rows)]
    queue = deque([(start_row, start_col, 0)])   # (row, col, steps)
    visited[start_row][start_col] = True

    while queue:
        r, c, steps = queue.popleft()
        if self.grid[r][c] == 'D':
            return steps   # BFS meets the closest DashMart first
        for dr, dc in self.directions:
            nr, nc = r + dr, c + dc
            if self.is_inside_city(nr, nc) and self.grid[nr][nc] != 'X' and not visited[nr][nc]:
                visited[nr][nc] = True
                queue.append((nr, nc, steps + 1))
    return -1   # no DashMart can be reached
```

The answer for each location is simply `self.bfs_from_location(row, col)`.

### Why it is slow

A city can have 200 × 200 = 40,000 cells and there can be 100,000 locations. A single BFS may visit all 40,000 cells, so the total work can reach 100,000 × 40,000 = 4 billion steps.

The follow-up has the same problem, because we would need one BFS for every customer.

Notice the waste: every BFS walks over the same roads again and again, looking for the same DashMarts.

## Approach 2: One BFS From All DashMarts (Multi-Source BFS)

### Flip the question

Instead of asking "which DashMart is closest to this location?" again and again, let the DashMarts do the searching.

Picture every DashMart sending out delivery riders at the same moment. In each round, the riders spread one more cell up, down, left and right, but never into a blocked road.

The first rider to reach a cell must have come from the closest DashMart. The round in which it arrives is the distance.

This is a normal BFS where the queue starts with all DashMarts instead of a single cell. It is called a multi-source BFS.

### Steps

1. Add every DashMart to the queue with distance 0.
2. Take a cell out of the queue. For each open neighbor that was never reached, set its distance to current distance + 1 and add it to the queue.
3. Repeat until the queue is empty.

The queue is first in, first out. So BFS finishes all cells at distance 1 before any cell at distance 2, and so on. That is why the first time we reach a cell, we already have its shortest distance, and it never needs an update.

Cells that are never reached keep `-1`. These are blocked roads and cells that are cut off from every DashMart.

Here is the distance map that BFS builds for Example 1. The `0` cells are the DashMarts.

```
      col 0  1  2  3  4  5  6  7  8
row 0     X  2  1  0  1  2  X  6  X
row 1     X  3  X  X  2  3  4  5  X
row 2     3  2  1  0  X  X  5  X -1
row 3     3  2  1  0  X  X  6  X -1
row 4     4  3  2  1  2  X  7  8  X
row 5     5  4  3  2  X  9  8  X  X
```

The answers can be read straight from it: `[1, 4]` is 2, `[0, 7]` is 6 and `[5, 5]` is 9. Cells `[2, 8]` and `[3, 8]` are boxed in by blocked roads and the city edge, so they stay `-1`.

### Answering each location

- If the location is outside the city, the answer is `-1`.
- Otherwise the answer is `distance[row][col]`. It is already `0` for a DashMart and `-1` for blocked or unreachable cells, so no extra checks are needed.

The outside check matters even more in Python. A negative index like `distance[-1]` does not fail, it quietly reads the last row. So `[-1, 5]` must be caught by `is_inside_city` first.

## Follow-up: DashMart Serving Maximum Customers

### Remember which DashMart reached each cell

Along with the distance, every cell also stores an **owner**: the DashMart whose rider reached it. When a rider steps into a new cell, it passes its owner along.

So when BFS ends, the owner of a customer cell is the DashMart that serves this customer.

### Breaking ties

Riders from two DashMarts can reach the same cell in the same round. The rule says the DashMart with the smaller `(row, col)` wins.

To turn this into an easy number comparison, we collect DashMarts into a list while scanning the grid row by row, left to right. This list is automatically sorted by `(row, col)`. We store the owner as an index into this list, so a smaller index means a smaller location.

During BFS, if we step into a cell that another rider already reached in the same round, we keep the smaller owner. In code, this is a neighbor whose distance is already equal to our distance + 1.

Is it safe to change the owner of a cell that is already in the queue? Yes. BFS finishes every cell of one round before it starts the next round. So the owner of a cell is final before that cell passes it on to its neighbors.

### Counting and picking the winner

1. For every customer cell that has an owner, add 1 to `served_count[owner]`.
2. Walk over the DashMarts in list order and remember the one with the biggest count. Replace the best only when a count is strictly bigger.

This small loop covers every special rule:

- **Same count:** the earlier DashMart in the list (the smaller location) stays the best.
- **No customer served:** all counts are 0, so the first DashMart, which is the smallest location, is returned.
- **Customer cannot reach any DashMart:** its owner stays `-1`, so it is skipped.
- **No DashMart in the city:** we return `[-1, -1]` before counting.

### Example 2 walkthrough

| Customer | Steps to [0, 0] | Steps to [2, 2] | Served by |
|---|---|---|---|
| [0, 2] | 2 | 2 | [0, 0], tie goes to the smaller location |
| [0, 4] | 6 | 4 | [2, 2] |
| [2, 0] | 2 | 2 | [0, 0], tie goes to the smaller location |
| [2, 4] | 6 | 2 | [2, 2] |
| [3, 4] | 7 | 3 | [2, 2] |

`[0, 0]` serves 2 customers and `[2, 2]` serves 3, so the answer is `[2, 2]`.

## Key Data Structures

| Data structure | Why we need it |
|---|---|
| `deque` as the queue | First in, first out order makes BFS move round by round, so the first visit to a cell always has the shortest distance. `popleft()` is O(1), while `list.pop(0)` would shift the whole list on every call. |
| `distance` (2-D list) | Distance to the closest DashMart for every cell. `-1` means "not reached", so it also works as the visited marker. |
| `dash_marts` (list of `(row, col)`) | All DashMarts sorted by `(row, col)`. The index works as a short id, and comparing ids is the same as comparing locations. |
| `owner` (2-D list) | The DashMart that serves each cell, stored as an index into `dash_marts`. |
| `served_count` (list) | How many customers each DashMart serves. |

## Complexity

Let `R` be the number of rows, `C` the number of columns and `Q` the number of locations.

| | Brute force | Multi-source BFS |
|---|---|---|
| Part 1 time | O(Q × R × C) | O(R × C + Q) |
| Follow-up time | O((R × C)²) | O(R × C) |
| Extra space | O(R × C) | O(R × C) |

Each cell enters the queue at most once and checks 4 neighbors, so one BFS costs O(R × C).

## Python Code

```python
import re
from collections import deque


class DashMartPlanner:
    def __init__(self):
        # The four moves we are allowed to make: up, down, left, right
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]
        self.grid = []      # the city as a 2-D grid of cells
        self.rows = 0
        self.cols = 0
        # All DashMarts, found row by row and left to right.
        # So a smaller index always means a smaller (row, col) location.
        self.dash_marts = []
        # distance[r][c] = steps from cell (r, c) to its closest DashMart, -1 if not reachable
        self.distance = []
        # owner[r][c] = index in dash_marts of the DashMart that serves cell (r, c), -1 if none
        self.owner = []

    def getClosestDashMartDistances(self, city, locations):
        self.parse_city(city)
        self.run_bfs_from_all_dash_marts()

        result = []
        for location in locations:
            r, c = self.parse_location(location)
            if self.is_inside_city(r, c):
                # blocked and unreachable cells already hold -1
                result.append(self.distance[r][c])
            else:
                result.append(-1)
        return result

    def findDashMartServingMaximumCustomers(self, city):
        self.parse_city(city)
        self.run_bfs_from_all_dash_marts()

        if not self.dash_marts:
            return [-1, -1]

        # served_count[i] = number of customers served by dash_marts[i]
        served_count = [0] * len(self.dash_marts)
        for r in range(self.rows):
            for c in range(self.cols):
                if self.grid[r][c] == 'C' and self.owner[r][c] != -1:
                    served_count[self.owner[r][c]] += 1

        # Only a strictly bigger count replaces the current best,
        # so on a tie the smaller location (smaller index) stays.
        # If no customer is served, every count is 0 and index 0 wins.
        best = 0
        for i in range(1, len(self.dash_marts)):
            if served_count[i] > served_count[best]:
                best = i
        row, col = self.dash_marts[best]
        return [row, col]

    def parse_city(self, city):
        """Converts rows like "X|.|D" into a 2-D grid of cells."""
        self.grid = [[cell.strip() for cell in row.split('|')] for row in city]
        self.rows = len(self.grid)
        self.cols = len(self.grid[0]) if self.rows > 0 else 0

    def run_bfs_from_all_dash_marts(self):
        """
        One BFS that starts from all DashMarts at the same time.
        Fills distance and owner for every cell of the city.
        """
        self.distance = [[-1] * self.cols for _ in range(self.rows)]
        self.owner = [[-1] * self.cols for _ in range(self.rows)]
        self.dash_marts = []
        queue = deque()

        for r in range(self.rows):
            for c in range(self.cols):
                if self.grid[r][c] == 'D':
                    # every DashMart is a starting point at distance 0 and serves itself
                    self.distance[r][c] = 0
                    self.owner[r][c] = len(self.dash_marts)
                    self.dash_marts.append((r, c))
                    queue.append((r, c))

        while queue:
            r, c = queue.popleft()
            for dr, dc in self.directions:
                nr, nc = r + dr, c + dc
                if not self.is_inside_city(nr, nc) or self.grid[nr][nc] == 'X':
                    continue
                if self.distance[nr][nc] == -1:
                    # first time we reach this cell, so this is its shortest distance
                    self.distance[nr][nc] = self.distance[r][c] + 1
                    self.owner[nr][nc] = self.owner[r][c]
                    queue.append((nr, nc))
                elif (self.distance[nr][nc] == self.distance[r][c] + 1
                      and self.owner[r][c] < self.owner[nr][nc]):
                    # reached again in the same round: the smaller DashMart wins the tie
                    self.owner[nr][nc] = self.owner[r][c]

    def is_inside_city(self, r, c):
        return 0 <= r < self.rows and 0 <= c < self.cols

    def parse_location(self, location):
        """Reads a location like "1,4", "[1, 4]" or [1, 4] and returns (row, col)."""
        if isinstance(location, str):
            numbers = re.findall(r'-?\d+', location)
            return int(numbers[0]), int(numbers[1])
        return int(location[0]), int(location[1])
```