# Longest Path and Increasing Paths in a Grid in Python

#### Problem Statement

[https://codezym.com/question/106-longest-increasing-path-grid](https://codezym.com/question/106-longest-increasing-path-grid)


A cell's longest increasing path depends only on its larger neighbors. We solve cells with no larger neighbor first, then work backwards using a queue. Saved lengths help us find one longest path, while a separate stack lists every path when requested.


## Start with a simple search

Start from every cell and explore its larger neighbors. Record the current path after every move, including the starting cell alone.

This finds all paths, but it repeatedly explores the same routes. Listing every path can take exponential time, which we cannot avoid when the output itself is that large. We can avoid that work for the two longest-path methods.


## Save the longest length at each cell

Let `lengths[row][col]` mean the longest path starting at that cell, measured in cells.

A cell with no larger neighbor has length `1`. Otherwise, its length is `1` plus the largest saved length among its larger neighbors.

We use two integer grids and a queue.

- `lengths` stores the best length starting at each cell.
- `remaining_higher` counts larger neighbors that have not been processed yet.
- The queue contains cells whose count is zero, so their lengths are ready.

First, give every cell length `1` and count its larger neighbors. Add cells with count zero to the queue.

When removing a cell from the queue, update each smaller neighbor with `current length + 1`. Decrease that neighbor's count. Add it to the queue when the count reaches zero.

Values increase at every move, so cycles are impossible. Every cell becomes ready after all its larger neighbors. Its saved length is therefore correct. The largest saved length answers `getLongestPathLength`.


## Build the smallest longest path

For `getOneLongestPath`, choose the smallest cell label among cells with the maximum saved length.

From the current cell, consider only larger neighbors whose saved length is exactly one less. Each choice can finish a longest path. Pick the smallest next-cell label and repeat.

Compare labels as text, using the exact format `(row,col)=value`. For example, `(0,10)=4` comes before `(0,2)=4`. Numeric coordinate order would give the wrong tie break.

Different cells have different labels, and no complete cell label is a prefix of another. The first differing label decides the full path's order, so these choices give the smallest longest path string.


## List all increasing paths

For `getAllStrictlyIncreasingPaths`, start a depth-first search from every cell. An explicit stack stores each active cell and the next direction to try. A list stores the current path's labels.

Record the path whenever a cell is added, including the first cell. When a cell has no directions left to try, remove it from the stack and the current path.

We do not need a visited set. Strictly increasing values prevent a path from revisiting a cell.

Finally, sort all complete path strings. Paths that can still be extended must also appear in the result.

The queue and explicit stack avoid recursion depth problems on paths containing up to 40,000 cells. Each public method builds its data from its own input.


## Small example

For `gridLines = ["2,3", "4,5"]`, the cell containing `5` has saved length `1`. The cells containing `3` and `4` have length `2`. The cell containing `2` has length `3`.

Both longest paths start at `(0,0)=2`. We choose `(0,1)=3` next because its label is smaller. The result is `(0,0)=2 -> (0,1)=3 -> (1,1)=5`.

The all-path result also includes `(0,0)=2` and `(0,0)=2 -> (0,1)=3`, even though both can be extended.


## Complexity

Let `N` be the number of cells. Finding only the longest length takes `O(N)` time and space. Returning one longest path takes `O(N + H)` time and space, where `H` is the number of characters in its answer.

For all paths, let `P` be the number of returned paths, `S` their total characters, and `H` the longest returned string's length. Enumeration takes `O(N + S)`. Sorting makes `O(P log P)` string comparisons, each taking up to `O(H)`. Total time is `O(N + S + P log P × H)`, with `O(N + S)` space including the output.


## Complete Python Code

```python
from collections import deque


class GridIncreasingPaths:
    def __init__(self):
        self.directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]

    def getLongestPathLength(self, gridLines):
        lengths = self.build_lengths(self.parse_grid(gridLines))
        return max(max(row) for row in lengths)

    def getOneLongestPath(self, gridLines):
        grid = self.parse_grid(gridLines)
        lengths = self.build_lengths(grid)
        row, col = 0, 0

        # Compare the printed labels, not the numeric coordinates.
        for r in range(len(grid)):
            for c in range(len(grid[0])):
                if lengths[r][c] > lengths[row][col] or (
                    lengths[r][c] == lengths[row][col]
                    and self.cell_text(grid, r, c) < self.cell_text(grid, row, col)
                ):
                    row, col = r, c

        path = []
        while True:
            path.append(self.cell_text(grid, row, col))
            next_cell = None

            for dr, dc in self.directions:
                r, c = row + dr, col + dc
                if (
                    self.is_inside(grid, r, c)
                    and grid[r][c] > grid[row][col]
                    and lengths[r][c] == lengths[row][col] - 1
                ):
                    if next_cell is None or self.cell_text(grid, r, c) < self.cell_text(
                        grid, next_cell[0], next_cell[1]
                    ):
                        next_cell = (r, c)

            if next_cell is None:
                break
            row, col = next_cell

        return " -> ".join(path)

    def getAllStrictlyIncreasingPaths(self, gridLines):
        grid = self.parse_grid(gridLines)
        paths = []

        for row in range(len(grid)):
            for col in range(len(grid[0])):
                self.collect_paths(grid, row, col, paths)

        paths.sort()
        return paths

    def collect_paths(self, grid, start_row, start_col, paths):
        # Each frame stores row, column, and the next direction to try.
        stack = [[start_row, start_col, 0]]
        path = [self.cell_text(grid, start_row, start_col)]
        paths.append(" -> ".join(path))

        while stack:
            frame = stack[-1]
            if frame[2] == len(self.directions):
                stack.pop()
                path.pop()
                continue

            dr, dc = self.directions[frame[2]]
            frame[2] += 1
            row, col = frame[0] + dr, frame[1] + dc

            if self.is_inside(grid, row, col) and grid[row][col] > grid[frame[0]][frame[1]]:
                path.append(self.cell_text(grid, row, col))
                # Record every prefix, not only paths ending at a local maximum.
                paths.append(" -> ".join(path))
                stack.append([row, col, 0])

    def build_lengths(self, grid):
        rows, cols = len(grid), len(grid[0])
        lengths = [[1] * cols for _ in range(rows)]
        remaining_higher = [[0] * cols for _ in range(rows)]
        ready = deque()

        for row in range(rows):
            for col in range(cols):
                for dr, dc in self.directions:
                    r, c = row + dr, col + dc
                    if self.is_inside(grid, r, c) and grid[r][c] > grid[row][col]:
                        remaining_higher[row][col] += 1
                if remaining_higher[row][col] == 0:
                    ready.append((row, col))

        while ready:
            row, col = ready.popleft()

            for dr, dc in self.directions:
                r, c = row + dr, col + dc
                if self.is_inside(grid, r, c) and grid[r][c] < grid[row][col]:
                    lengths[r][c] = max(lengths[r][c], lengths[row][col] + 1)
                    remaining_higher[r][c] -= 1
                    # All larger neighbors must be processed before this cell is ready.
                    if remaining_higher[r][c] == 0:
                        ready.append((r, c))

        return lengths

    def parse_grid(self, grid_lines):
        return [list(map(int, line.split(","))) for line in grid_lines]

    def is_inside(self, grid, row, col):
        return 0 <= row < len(grid) and 0 <= col < len(grid[0])

    def cell_text(self, grid, row, col):
        return f"({row},{col})={grid[row][col]}"
```
