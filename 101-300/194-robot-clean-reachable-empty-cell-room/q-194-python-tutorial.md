# Use Robot To Clean Reachable Empty Cells in Room in Python

#### Problem Statement
[https://codezym.com/question/194-robot-clean-reachable-empty-cell-room](https://codezym.com/question/194-robot-clean-reachable-empty-cell-room)


## Core Idea

The robot can clean a cell only if it can walk there through empty cells. So we do not need to plan the robot's route at all. We just need to count **how many empty cells are connected to the starting cell**, including the starting cell itself.

This is the classic **flood fill** problem. Imagine pouring water on the starting cell. The water flows into every empty neighbour, then into their neighbours, and keeps going until walls stop it. Every wet cell is a cell the robot cleans.

We will first write the most natural version using recursion (DFS). Then we will see why deep recursion is risky here, and fix it with a queue (BFS).


## Building Blocks

Both approaches use the same three things:

- **`grid`**: a list of lists. We split every row string like `"0,1,1,0"` by commas and turn each value into an `int`. Now `grid[r][c]` tells us instantly whether a cell is a wall (`0`) or empty (`1`).
- **`cleaned`**: a list of lists of booleans that remembers which cells are already cleaned. Without it, the robot would walk back and forth between two cells forever and count them again and again.
- **`moves`**: the four moves up, right, down and left. With this list we can loop over the directions instead of writing four separate `if` blocks.

```python
# up, right, down, left
self.moves = [(-1, 0), (0, 1), (1, 0), (0, -1)]
```

We build `cleaned` with `[[False] * len(row) for row in grid]`. Writing `[[False] * n] * m` would be a bug, because every row would be the same list.

We also use a tiny helper `canClean()`. It returns `True` only if the cell is inside the room, is empty and is not cleaned yet.


## Simple Approach: Recursive DFS

DFS (Depth First Search) cleans the room the way a real robot would:

1. Clean the current cell.
2. Walk into a neighbour and clean everything reachable from there.
3. Walk back and try the next neighbour.

```python
# returns how many cells get cleaned starting from (row, col)
def cleanFrom(self, grid, cleaned, row, col):
    if not self.canClean(grid, cleaned, row, col):
        return 0  # outside the room, a wall, or already cleaned
    cleaned[row][col] = True
    count = 1  # the current cell

    for row_move, col_move in self.moves:
        count += self.cleanFrom(grid, cleaned, row + row_move, col + col_move)
    return count
```

`cleanRoom()` would simply parse the grid and return `self.cleanFrom(grid, cleaned, startRow, startCol)`.


### Problem With This Approach

The logic is correct and the time is fine. The problem is **how deep the recursion goes**.

Every call waits until its neighbour's call finishes. If the robot keeps walking forward without coming back, these waiting calls pile up.

In the worst case, the pile is as big as the number of reachable cells, which can be close to 20,000 here.

You do not even need a tricky room for this. Take a fully open `100 x 200` room and start in a corner. DFS walks through all 19,404 empty cells in one long zig-zag line, so the recursion goes 19,404 calls deep.

Python's default recursion limit is only 1,000 nested calls. Even a small open `34 x 34` room is enough to crash the program with a `RecursionError`.

We could raise the limit with `sys.setrecursionlimit()`, but that is just a workaround. A queue removes the problem completely.


## Better Approach: BFS With a Queue

The fix is simple. Instead of letting recursion remember which cells still need to be explored, we keep them ourselves in a queue.

1. Clean the start cell and add it to the queue.
2. Take a cell out of the queue.
3. Check its four neighbours. Every neighbour that can be cleaned is cleaned, counted and added to the queue.
4. Repeat until the queue is empty.

This is BFS (Breadth First Search). The cleaning spreads in layers: first the start cell, then cells 1 step away, then cells 2 steps away, and so on. Just like the water.

**Why a queue:** it holds the cells that are cleaned but whose neighbours are not checked yet. Each entry is a `(row, col)` tuple.

We use `deque` from the `collections` module. It removes items from the front in `O(1)` time. A normal list would be slow here, because `list.pop(0)` shifts every remaining item one step left.

The queue is a normal Python object, so it can hold 20,000 cells without hitting any limit.

A stack would also work in place of the queue. The cleaning order changes, but the final count stays the same.


### Mark a Cell When You Add It

We mark a cell as cleaned **at the moment we add it to the queue**, not when we take it out.

If we waited, two different neighbours could both add the same cell before it is taken out. That cell would enter the queue twice, which wastes work and can lead to counting it twice.

Marking early makes sure every cell enters the queue exactly once.


### Complexity

- **Time:** `O(m * n)`. Parsing reads every cell once. BFS takes each reachable cell out of the queue once and checks its 4 neighbours.
- **Space:** `O(m * n)` for `grid`, `cleaned` and the queue.


## Python Code

```python
from collections import deque
from typing import List


class RoomCleaner:

    def __init__(self):
        # the four moves the robot can make: up, right, down, left
        self.moves = [(-1, 0), (0, 1), (1, 0), (0, -1)]

    def cleanRoom(self, room: List[str], startRow: int, startCol: int) -> int:
        """Counts the empty cells the robot can reach from (startRow, startCol).
        Uses BFS with a queue, so there is no deep recursion."""
        grid = self.parseRoom(room)

        # cleaned[r][c] becomes True once the robot cleans cell (r, c)
        cleaned = [[False] * len(row) for row in grid]

        # safety check, the robot must start on an empty cell
        if not self.canClean(grid, cleaned, startRow, startCol):
            return 0

        # cells that are cleaned, but whose neighbours are not checked yet
        queue = deque()
        queue.append((startRow, startCol))
        cleaned[startRow][startCol] = True
        count = 1

        while queue:
            row, col = queue.popleft()

            for row_move, col_move in self.moves:
                next_row = row + row_move
                next_col = col + col_move

                if self.canClean(grid, cleaned, next_row, next_col):
                    # mark it now, so it is never added to the queue twice
                    cleaned[next_row][next_col] = True
                    count += 1
                    queue.append((next_row, next_col))

        return count

    def canClean(self, grid: List[List[int]], cleaned: List[List[bool]], row: int, col: int) -> bool:
        """A cell can be cleaned only if it is inside the room, empty and not cleaned yet."""
        if row < 0 or row >= len(grid) or col < 0 or col >= len(grid[row]):
            return False
        return grid[row][col] == 1 and not cleaned[row][col]

    def parseRoom(self, room: List[str]) -> List[List[int]]:
        """Turns rows like "0,1,1,0" into a 2D list of ints."""
        grid = []
        for line in room:
            grid.append([int(value) for value in line.split(",")])
        return grid
```