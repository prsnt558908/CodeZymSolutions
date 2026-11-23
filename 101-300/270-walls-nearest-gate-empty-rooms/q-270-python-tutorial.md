# Walls and Nearest Gate for Empty Rooms in Python

#### Problem Statement
[https://codezym.com/question/270-walls-nearest-gate-empty-rooms](https://codezym.com/question/270-walls-nearest-gate-empty-rooms)

---

## Core Idea

Every empty room wants to know one thing: how many steps away is the nearest gate?

Instead of searching outwards from every room, we turn the question around. We start from **all the gates at the same time** and spread outwards, one step per round.

The first time this spreading wave reaches a room, it comes from the nearest gate. So we write that distance into the room and never touch it again.

This is called **multi-source BFS** (Breadth First Search that starts from many cells together). Each cell is handled only once, so the whole grid is filled in `O(rows × cols)` time.

---

## Input and Output

The grid comes as a `List[str]`. Each string is one row, with numbers separated by commas.

- `-1` is a wall
- `0` is a gate
- `2147483647` is an empty room

We first turn the rows into a 2D list of ints. Then we fill in the distances. Finally, we turn the grid back into comma separated strings in the same order.

---

## Solution 1: BFS From Every Gate (Brute Force)

The most direct idea is to handle one gate at a time.

From each gate, run a normal BFS. Every room it reaches keeps the smaller of two values: what the room already holds, and its distance from this gate.

```python
# run a separate BFS from every gate
for gr, gc in gates:
    visited = [[False] * cols for _ in range(rows)]
    queue = deque([(gr, gc, 0)])  # row, col, steps
    visited[gr][gc] = True

    while queue:
        r, c, steps = queue.popleft()
        for dr, dc in self.directions:
            nr, nc = r + dr, c + dc
            # skip cells outside the grid, visited cells, walls and gates
            if nr < 0 or nr >= rows or nc < 0 or nc >= cols:
                continue
            if visited[nr][nc] or grid[nr][nc] == -1 or grid[nr][nc] == 0:
                continue

            visited[nr][nc] = True
            # keep the smaller distance if another gate came closer
            grid[nr][nc] = min(grid[nr][nc], steps + 1)
            queue.append((nr, nc, steps + 1))
```

### What is wrong with it?

It gives the right answer, but it is slow.

Each gate walks over the grid on its own. With `G` gates, the time becomes `O(G × rows × cols)`.

A 250 x 250 grid has 62,500 cells. With thousands of gates spread around, this quickly turns into hundreds of millions of steps.

The real waste is simple: a room gets visited again for every gate, even though only the nearest gate matters.

---

## Solution 2: Multi-Source BFS (Optimal)

### The Idea

Instead of one BFS per gate, we run **one BFS that starts from all gates together**.

We put every gate into the queue before the loop starts. Then we run a normal BFS.

Think of dropping a stone at every gate in a pond, all at the same moment. The ripples spread at the same speed. Each room is touched first by the ripple from its closest gate.

### Why the First Visit Gives the Shortest Distance

A queue is first in, first out. That is exactly why we use it here: cells come out of the queue in order of their distance.

- First, all the gates (distance `0`)
- Then, every room at distance `1`
- Then, every room at distance `2`, and so on

When a room is reached for the first time, no gate can reach it in fewer steps. If some gate could, the room would have been reached in an earlier round.

So once a room has a distance, it is final.

We use `deque` from `collections` as the queue. Its `popleft()` takes `O(1)` time. A plain list would need `pop(0)`, which shifts every element and gets slow on big grids.

### No Visited Set Needed

The grid itself tells us what to skip.

A room that no gate has reached yet still holds `2147483647`. Walls (`-1`), gates (`0`) and filled rooms all hold something else.

So for each neighbor we ask only one question: **is it still `2147483647`?** If yes, we fill it and add it to the queue. If no, we skip it.

We also don't need to store the distance in the queue. A gate holds `0` and a filled room holds its own distance, so a neighbor's distance is simply `grid[r][c] + 1`.

### Dry Run

Example 1, where `INF` means `2147483647`:

```
Start              After round 1
INF  -1   0 INF    INF  -1   0   1
INF INF INF  -1      1 INF   1  -1
  0  -1 INF INF      0  -1 INF INF

After round 2      After round 3
  2  -1   0   1      2  -1   0   1
  1   2   1  -1      1   2   1  -1
  0  -1   2 INF      0  -1   2   3
```

Look at the room in the middle row, second column. It is 2 steps away from both gates, so it simply gets `2` in round 2.

The code has no separate loop for each round. The queue order alone makes the rooms fill up round by round like this.

Rooms that no gate can reach are never touched, so they keep `2147483647`. That is exactly what the problem asks for.

### Steps

1. Parse each row string into a list of ints.
2. Add every gate to the queue.
3. Take a cell out of the queue and check its 4 neighbors.
4. If a neighbor is still empty, set it to `grid[r][c] + 1` and add it to the queue.
5. Repeat until the queue is empty, then convert the grid back into strings.

### Complexity

- **Time:** `O(rows × cols)`. Each cell enters the queue at most once and checks only 4 neighbors.
- **Space:** `O(rows × cols)` for the grid and the queue.

### Python Code

```python
from collections import deque
from typing import List


class WallsAndGates:

    def __init__(self):
        # An empty room. It also means "no gate has reached this room yet".
        self.EMPTY = 2147483647
        # Moves to the cell above, below, left and right.
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]

    def wallsAndGates(self, rooms: List[str]) -> List[str]:
        grid = self.parseGrid(rooms)
        rows = len(grid)
        cols = len(grid[0])

        # Step 1: every gate is a starting point, so add all of them to the queue.
        queue = deque()
        for r in range(rows):
            for c in range(cols):
                if grid[r][c] == 0:
                    queue.append((r, c))

        # Step 2: spread out from all gates together, one step at a time.
        while queue:
            r, c = queue.popleft()

            for dr, dc in self.directions:
                nr = r + dr
                nc = c + dc

                # Skip cells outside the grid.
                if nr < 0 or nr >= rows or nc < 0 or nc >= cols:
                    continue
                # Only fill rooms that are still empty.
                # Walls, gates and already filled rooms are skipped.
                if grid[nr][nc] != self.EMPTY:
                    continue

                # The neighbor is one step farther from a gate than the current cell.
                grid[nr][nc] = grid[r][c] + 1
                queue.append((nr, nc))

        return self.toStringList(grid)

    # Converts rows like "0,-1,2147483647" into an int grid.
    def parseGrid(self, rooms: List[str]) -> List[List[int]]:
        grid = []
        for row in rooms:
            grid.append([int(part) for part in row.split(",")])
        return grid

    # Converts the int grid back into comma separated rows.
    def toStringList(self, grid: List[List[int]]) -> List[str]:
        result = []
        for row in grid:
            result.append(",".join(str(value) for value in row))
        return result
```