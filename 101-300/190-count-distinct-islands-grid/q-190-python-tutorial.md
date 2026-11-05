# Count Distinct Islands in a Grid in Python

#### Problem Statement

[https://codezym.com/question/190-count-distinct-islands-grid](https://codezym.com/question/190-count-distinct-islands-grid)

The key idea is to describe an island by its shape instead of its location. Use breadth-first search, or BFS, to find its land cells. Record each cell's position relative to the island's first cell, then store that description in a set. Islands with the same shape produce the same description, so the set size gives the answer.

## Start with a simple solution

Find each island and build a description of its shape. Keep the descriptions in a list, adding a new one only when it is not already present.

```python
distinctShapes = []

# Run this for each island found during the grid scan.
shape = self.collectShape(grid, visited, row, col)
if shape not in distinctShapes:
    distinctShapes.append(shape)
```

This works, but checking the list may compare the new shape with every stored shape. With `I` islands and descriptions of length at most `K`, these comparisons can take `O(I² × K)` time in the worst case.

We can improve this by using a hash set. First, we need a reliable way to describe a shape.

## Describe cells relative to a starting cell

Scan the grid row by row. When an unvisited land cell appears, use it as the island's starting cell, `(startRow, startCol)`.

For every cell `(row, col)` reached by BFS, record:

```text
(row - startRow, col - startCol)
```

For example, these two islands have identical relative positions:

- Cells `(0, 0), (0, 1), (1, 0)` become `(0, 0), (0, 1), (1, 0)`.
- Cells `(2, 3), (2, 4), (3, 3)` also become `(0, 0), (0, 1), (1, 0)`.

Their positions differ, but their shape descriptions match. We never rotate or reflect the positions. Shapes that match only after such a change therefore keep different descriptions.

Column offsets can be negative if an island extends left below its starting cell. Keep those negative values in the description.

## Count unique descriptions with a set

Always explore neighbors in the same order: up, right, down, left. Because translated copies start at corresponding cells, BFS records their relative positions in the same order. No sorting is needed.

Join the pairs into a string such as `0,0|0,1|1,0|`. The comma and vertical bar separate the values and cells clearly.

The solution uses three simple data structures:

- `visited` prevents a land cell from being added more than once.
- A list holds the BFS queue. A `head` index reads its next cell without removing from the front.
- A `set` stores each distinct shape description once.

Mark a cell visited when adding it to the queue. All working data is created inside the method call, so the same object can safely process another grid. The input is left unchanged.

Build the description as a list of small strings and join them once at the end. This avoids repeatedly copying a growing string.

## Why this works

BFS follows only shared sides, so it visits exactly one island at a time. The outer scan eventually finds every island.

Shifting an island also shifts its starting cell by the same amount. Subtracting that starting cell removes the shift. A fixed BFS order therefore gives identical descriptions for identical shapes.

If two descriptions match, their relative land cells match exactly. One island can be shifted to cover the other. The set therefore keeps exactly one entry per distinct shape.

## Complexity

For `R` rows and `C` columns, expected time is `O(R × C)`, including building and hashing the descriptions. Each land cell is queued once and has four neighbors checked.

Extra space is `O(R × C)` for visited cells, the queue, and all stored descriptions. Iterative BFS also avoids a deep recursive call stack.

## Python solution

```python
from typing import List


class CountDistinctIslands:
    def __init__(self):
        pass

    def countDistinctIslands(self, grid: List[str]) -> int:
        rows = len(grid)
        cols = len(grid[0])
        visited = [[False] * cols for _ in range(rows)]
        distinctShapes = set()

        for row in range(rows):
            for col in range(cols):
                if grid[row][col] == "1" and not visited[row][col]:
                    distinctShapes.add(self.collectShape(grid, visited, row, col))

        return len(distinctShapes)

    def collectShape(
        self,
        grid: List[str],
        visited: List[List[bool]],
        startRow: int,
        startCol: int,
    ) -> str:
        """Return the island's description relative to its starting cell."""
        rows = len(grid)
        cols = len(grid[0])
        directions = [(-1, 0), (0, 1), (1, 0), (0, -1)]
        queue = [(startRow, startCol)]
        shape = []
        visited[startRow][startCol] = True
        head = 0

        while head < len(queue):
            row, col = queue[head]
            head += 1

            # Ignore the absolute position, but preserve the orientation.
            shape.append(f"{row - startRow},{col - startCol}|")

            for rowChange, colChange in directions:
                nextRow = row + rowChange
                nextCol = col + colChange

                if not (0 <= nextRow < rows and 0 <= nextCol < cols):
                    continue

                if grid[nextRow][nextCol] == "1" and not visited[nextRow][nextCol]:
                    # Mark on insertion so no cell enters the queue twice.
                    visited[nextRow][nextCol] = True
                    queue.append((nextRow, nextCol))

        return "".join(shape)
```
