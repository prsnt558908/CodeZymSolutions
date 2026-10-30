# Minimum Suspension Strength in Python

#### Problem Statement
[https://codezym.com/question/421-minimum-suspension-strength](https://codezym.com/question/421-minimum-suspension-strength)


## Core Idea

Every move between two neighboring tiles has a strain. A route needs a suspension as strong as its single worst move. So we are looking for the route whose worst move is as small as possible.


Strains are not added up. Only the biggest strain on a route counts.


We first look at a brute force idea that tries every route. Then we improve it in two steps:

1. **Binary Search + BFS:** guess a strength and check if the robot can reach the end with it.
2. **Dijkstra style search with a min heap:** always grow the easiest route first and get the answer in one pass.


## Reading the Input

Each row comes as a string like `"6,2,3"`. We split it on commas and store the values in a 2D list called `grid`. Both solutions use the same `parseGrid` helper for this.


## Brute Force: Try Every Route

Start at the top-left tile and use DFS to walk in all 4 directions. Carry the worst strain seen so far on the current route. When we reach the bottom-right tile, keep the smallest worst strain found.


A `visited` grid stops a route from walking in circles. We un-mark a tile when we step back, so other routes can still use it.

```python
# Called as self.explore(0, 0, 0). self.best starts at float("inf").
def explore(self, row, col, worst_so_far):
    """Tries every route from (row, col) to the bottom-right tile."""
    if row == self.rows - 1 and col == self.cols - 1:
        self.best = min(self.best, worst_so_far)
        return
    self.visited[row][col] = True

    for d_row, d_col in self.directions:
        next_row, next_col = row + d_row, col + d_col
        if (0 <= next_row < self.rows and 0 <= next_col < self.cols
                and not self.visited[next_row][next_col]):
            strain = abs(self.grid[row][col] - self.grid[next_row][next_col])
            self.explore(next_row, next_col, max(worst_so_far, strain))

    self.visited[row][col] = False  # free this tile so other routes can use it
```


**Problem:** the number of routes grows exponentially with the size of the floor. A 100 × 100 floor has far too many routes to try one by one.


## Key Observation

If the robot can reach the end with strength `S`, it can also reach it with any strength bigger than `S`.


For Example 1, the results look like this:

| Strength | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Can reach the end? | No | No | No | No | Yes | Yes | Yes |


All the "No" answers come first, then all the "Yes" answers. We only need the first "Yes", and binary search finds it quickly.


## Solution 1: Binary Search + BFS

### How it works

Binary search picks a guessed strength `limit`. To check it, we run BFS from the top-left tile. The robot may step to a neighbor only if the strain is at most `limit`. If BFS reaches the bottom-right tile, `limit` is enough.

- If `limit` works, the answer is `limit` or smaller.
- If it fails, the answer is bigger than `limit`.


Every value is between 1 and 1,000,000, so the answer is always between 0 and 1,000,000. That is our search range.


### Why a queue and a `visited` grid?

The queue (`deque`) holds tiles the robot can reach but we have not explored yet. The `visited` grid makes sure every tile enters the queue only once.


So one check takes O(rows × cols) time, instead of trying every route.


### Complexity

- **Time:** O(rows × cols × log(1,000,000)), which is about 20 BFS runs.
- **Space:** O(rows × cols) for `visited` and the queue.


### Code

```python
from collections import deque
from typing import List


class WarehouseRobotRoutePlanner:

    def __init__(self):
        # The 4 moves: up, down, left, right
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]

    def minimumSuspensionStrength(self, surfaceResistance: List[str]) -> int:
        grid = self.parseGrid(surfaceResistance)

        # Every value is between 1 and 1,000,000,
        # so no strain can be bigger than 1,000,000
        low, high = 0, 1000000

        # Find the smallest strength that lets the robot reach the end
        while low < high:
            mid = (low + high) // 2
            if self.canReach(grid, mid):
                high = mid  # mid works, look for a smaller one
            else:
                low = mid + 1  # mid is too small
        return low

    def canReach(self, grid: List[List[int]], limit: int) -> bool:
        """BFS from the top-left tile. Only moves with strain <= limit are allowed."""
        rows, cols = len(grid), len(grid[0])
        visited = [[False] * cols for _ in range(rows)]
        queue = deque()

        queue.append((0, 0))
        visited[0][0] = True

        while queue:
            row, col = queue.popleft()

            if row == rows - 1 and col == cols - 1:
                return True

            for d_row, d_col in self.directions:
                next_row, next_col = row + d_row, col + d_col

                if not (0 <= next_row < rows and 0 <= next_col < cols):
                    continue  # outside the floor
                if visited[next_row][next_col]:
                    continue

                strain = abs(grid[row][col] - grid[next_row][next_col])
                if strain <= limit:
                    visited[next_row][next_col] = True
                    queue.append((next_row, next_col))
        return False

    def parseGrid(self, surfaceResistance: List[str]) -> List[List[int]]:
        """Converts ["6,2,3", "7,12,4"] into a 2D list of ints."""
        grid = []
        for line in surfaceResistance:
            grid.append([int(value) for value in line.split(",")])
        return grid
```


## Solution 2: Dijkstra Style Search with a Min Heap

Binary search runs BFS about 20 times. We can find the answer in a single pass instead.


### How it works

Let `strength[r][c]` be the smallest strength found so far to reach tile `(r, c)`. In the beginning we only know `strength[0][0] = 0`.


When the robot moves from tile `A` to a neighbor `B`, the new route to `B` needs:

```
max(strength of A, strain from A to B)
```

If this is smaller than the current `strength` of `B`, we update `B` and add it to the heap.


We always take out the tile with the smallest strength first. This is Dijkstra's shortest path algorithm with one change: instead of adding the move costs, we take the maximum.


In Example 2, tiles come out of the heap in this order: 20, 21, 18, 17, 16, 15. The last one is the destination, with strength 2. Tiles 30 and 40 wait in the heap with strengths 9 and 19, and are never needed.


### Why does smallest first work?

A route's strength can only stay the same or go up as the route gets longer. So when a tile comes out of the heap with the smallest strength, no route found later can beat it.


The first time the bottom-right tile comes out, its strength is the answer.


### Why the `strength` grid and a min heap?

`strength` remembers the best known value for every tile. It lets us ignore any route that is worse than one we already found.


The min heap (`heapq`) always gives us the tile with the smallest strength in O(log n) time. Each entry is a tuple `(strength, row, col)`. Python compares tuples item by item, starting with the first one, so the smallest strength comes out first.


The same tile can enter the heap more than once if a better route to it is found later. When an old entry comes out, its strength is bigger than `strength[row][col]`, so we just skip it.


### Complexity

- **Time:** O(rows × cols × log(rows × cols)). Each tile is processed once, and each heap operation takes log time.
- **Space:** O(rows × cols) for `strength` and the heap.


### Code

```python
import heapq
from typing import List


class WarehouseRobotRoutePlanner:

    def __init__(self):
        # The 4 moves: up, down, left, right
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]

    def minimumSuspensionStrength(self, surfaceResistance: List[str]) -> int:
        grid = self.parseGrid(surfaceResistance)
        rows, cols = len(grid), len(grid[0])

        # strength[r][c] = smallest strength found so far to reach tile (r, c)
        strength = [[float("inf")] * cols for _ in range(rows)]
        strength[0][0] = 0

        # Min heap of (strength, row, col). The smallest strength comes out first.
        heap = [(0, 0, 0)]

        while heap:
            current_strength, row, col = heapq.heappop(heap)

            # The first time the destination comes out, its strength is final
            if row == rows - 1 and col == cols - 1:
                return current_strength

            # Old entry: a better route to this tile was already found
            if current_strength > strength[row][col]:
                continue

            for d_row, d_col in self.directions:
                next_row, next_col = row + d_row, col + d_col

                if not (0 <= next_row < rows and 0 <= next_col < cols):
                    continue  # outside the floor

                # A route needs as much strength as its worst move
                strain = abs(grid[row][col] - grid[next_row][next_col])
                next_strength = max(current_strength, strain)

                if next_strength < strength[next_row][next_col]:
                    strength[next_row][next_col] = next_strength
                    heapq.heappush(heap, (next_strength, next_row, next_col))

        return strength[rows - 1][cols - 1]

    def parseGrid(self, surfaceResistance: List[str]) -> List[List[int]]:
        """Converts ["6,2,3", "7,12,4"] into a 2D list of ints."""
        grid = []
        for line in surfaceResistance:
            grid.append([int(value) for value in line.split(",")])
        return grid
```