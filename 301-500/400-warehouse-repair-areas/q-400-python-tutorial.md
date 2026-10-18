# Warehouse Repair Areas in Python

#### Problem Statement
[https://codezym.com/question/400-warehouse-repair-areas](https://codezym.com/question/400-warehouse-repair-areas)

---

The floor is a grid, and every repair area is just a group of `X` tiles joined by their sides.


The whole solution is one simple habit: **count, then paint**. We scan the floor tile by tile. When we find a damaged tile we have never seen, it must be the start of a new repair area. We add 1 to our answer and then "paint" the full area as visited, so none of its tiles get counted again.


This painting step is called **flood fill**. We will first write it with recursion (DFS), see why it can crash on big floors, and then fix it by using a queue (BFS).

---

## Understanding the Problem

Two damaged tiles are in the same area only if they touch **up, down, left or right**.


Tiles that touch only at a corner are **not** connected. That is why Example 3 (`"X.X", "...", "X.X"`) has 4 areas, not 1.


Let us label the areas in Example 2 so the idea is easy to see:

```
Floor            Areas
X . . . X        A . . . B
X X . . X        A A . . B
. . . . .        . . . . .
. . X X .        . . C C .
. . . X .        . . . C .
```

Three groups: `A`, `B` and `C`. So the answer is `3`.


The space outside the floor counts as usable. In code, this simply means: if a neighbour is outside the grid, ignore it.

---

## Core Idea: Count and Paint

1. Go through every tile, row by row.
2. If the tile is `X` and not visited yet, we found a **new** repair area. Add 1 to the count.
3. Visit every damaged tile connected to it and mark them all as visited.
4. Keep scanning. Tiles of already counted areas are skipped because they are marked.


Every tile gets painted only once, so the work stays small.

### Data structures we need

- **`grid` (list of strings)**: the floor itself. Python strings can be indexed, so `grid[row][col]` already gives us any tile. No copy needed.
- **`visited` (list of lists of booleans)**: remembers which damaged tiles are already part of a counted area. Strings cannot be changed in Python, so we keep this separate 2D list.
- **`directions` (list of tuples)**: the 4 moves (up, down, left, right). Looping over it saves us from writing 4 almost same `if` blocks.

---

## Solution 1: Recursive DFS (Simple, but Risky)

DFS means "go as deep as you can, then come back".


From a damaged tile, we mark it and then call the same method on its 4 neighbours. Each of those calls does the same thing, so the paint spreads through the whole area.


The method stops right away when the tile is:

- outside the floor
- usable (`.`)
- already visited

### Code

```python
from typing import List


class WarehouseRepairAreas:
    def __init__(self):
        # The 4 side neighbours of a tile: up, down, left, right
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]
        self.grid = []
        self.visited = []
        self.rows = 0
        self.cols = 0

    def countRepairAreas(self, floorPlan: List[str]) -> int:
        self.grid = floorPlan
        self.rows = len(floorPlan)
        self.cols = len(floorPlan[0])
        self.visited = [[False] * self.cols for _ in range(self.rows)]

        areas = 0
        for i in range(self.rows):
            for j in range(self.cols):
                # A damaged tile not seen before starts a brand new repair area
                if self.grid[i][j] == 'X' and not self.visited[i][j]:
                    areas += 1
                    self.mark_area(i, j)
        return areas

    def mark_area(self, row: int, col: int) -> None:
        """Marks every damaged tile connected to (row, col) as visited."""
        # Stop at the floor edge, at usable tiles and at tiles already seen
        if row < 0 or row >= self.rows or col < 0 or col >= self.cols:
            return
        if self.grid[row][col] != 'X' or self.visited[row][col]:
            return

        self.visited[row][col] = True
        for dr, dc in self.directions:
            self.mark_area(row + dr, col + dc)
```

### Problem with this solution

The logic is correct. The problem is **how deep the recursion can go**.


Every recursive call waits for its neighbour calls to finish. These waiting calls pile up on the call stack.


Picture a 300 x 300 floor where every tile is `X`. That is one area with 90,000 tiles. The DFS can snake through the whole floor, so up to 90,000 calls may be waiting at the same time.


Python allows only about 1000 nested calls by default. So this code fails with a `RecursionError` even on medium sized floors.


Raising the limit with `sys.setrecursionlimit` is not a reliable fix. So we need a way to spread the paint **without** deep recursion.

**Complexity:** Time `O(rows * cols)`, Space `O(rows * cols)`, but the recursion breaks on big floors.

---

## Solution 2: BFS with a Queue (Final Solution)

The fix is simple: instead of letting Python keep track of "tiles still to process" on the call stack, we keep them in **our own queue**.


A queue is a normal Python object, so it can grow as big as needed. Even a 90,000 tile area is no problem.


BFS spreads the paint layer by layer, like water spreading out from the first tile.

### How `mark_area` works now

1. Mark the starting tile as visited and put it in the queue.
2. While the queue is not empty:
   - take a tile out of the queue
   - look at its 4 neighbours
   - if a neighbour is inside the floor, damaged and not visited, mark it visited and add it to the queue

### Why mark a tile when adding it, not when removing it?

A tile can be a neighbour of several tiles in the queue.


If we waited until removal to mark it, two different tiles could both add it, and it would be processed twice. Marking it right when it is added makes sure every tile enters the queue **only once**.

### Why `deque`?

For counting areas, the order of visiting does not really matter. A stack would also work.


We use `collections.deque` because it is the standard queue in Python. `append()` adds a tile at the back and `popleft()` takes one from the front, both very fast.


A plain list would be slow here, because `list.pop(0)` has to shift every other item.

### Dry run on Example 2

- Scan reaches `(0,0)`: new area, count = 1. BFS paints `(0,0)`, `(1,0)`, `(1,1)`.
- Scan reaches `(0,4)`: new area, count = 2. BFS paints `(0,4)`, `(1,4)`.
- Scan reaches `(3,2)`: new area, count = 3. BFS paints `(3,2)`, `(3,3)`, `(4,3)`.
- Every other `X` is already painted, so it is skipped.


Final answer: `3`.

### Code

```python
from collections import deque
from typing import List


class WarehouseRepairAreas:
    def __init__(self):
        # The 4 side neighbours of a tile: up, down, left, right
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]
        self.grid = []
        self.visited = []
        self.rows = 0
        self.cols = 0

    def countRepairAreas(self, floorPlan: List[str]) -> int:
        self.grid = floorPlan
        self.rows = len(floorPlan)
        self.cols = len(floorPlan[0])
        self.visited = [[False] * self.cols for _ in range(self.rows)]

        areas = 0
        for i in range(self.rows):
            for j in range(self.cols):
                # A damaged tile not seen before starts a brand new repair area
                if self.grid[i][j] == 'X' and not self.visited[i][j]:
                    areas += 1
                    self.mark_area(i, j)
        return areas

    def mark_area(self, start_row: int, start_col: int) -> None:
        """Uses a queue to mark every damaged tile connected to (start_row, start_col)."""
        queue = deque()
        self.visited[start_row][start_col] = True
        queue.append((start_row, start_col))

        while queue:
            row, col = queue.popleft()

            for dr, dc in self.directions:
                next_row = row + dr
                next_col = col + dc

                if self.is_new_damaged_tile(next_row, next_col):
                    # Mark now, so the same tile is never added to the queue twice
                    self.visited[next_row][next_col] = True
                    queue.append((next_row, next_col))

    def is_new_damaged_tile(self, row: int, col: int) -> bool:
        """True if the tile is inside the floor, damaged, and not visited yet."""
        if row < 0 or row >= self.rows or col < 0 or col >= self.cols:
            return False
        return self.grid[row][col] == 'X' and not self.visited[row][col]
```

`grid`, `visited`, `rows` and `cols` are stored on the object so the helper methods stay short. They are set again at the start of every call, so the same object can be reused safely.

---

## Complexity

Let `R` be the number of rows and `C` the number of columns.


- **Time:** `O(R * C)`. Each tile is scanned once, enters the queue at most once, and checks only 4 neighbours.
- **Space:** `O(R * C)` for the `visited` list and the queue.


For the biggest floor (300 x 300 = 90,000 tiles), this runs comfortably fast.

| | Recursive DFS | BFS with Queue |
|---|---|---|
| Time | `O(R * C)` | `O(R * C)` |
| Space | `O(R * C)` | `O(R * C)` |
| Safe on a 300 x 300 floor | No, hits the recursion limit | Yes |

---

## Key Takeaways

- Counting groups in a grid is a **count and paint** problem: count a new group, then paint all of it.
- A `visited` list makes sure every tile is counted only once.
- Recursive DFS is the quickest to write, but it hits Python's recursion limit on big grids.
- BFS with a `deque` does the same job safely, with no deep recursion.
- Mark a tile as visited **when you add it** to the queue, not when you remove it.