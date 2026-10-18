# Longest Path and Increasing Paths in a Grid in Java

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
- `remainingHigher` counts larger neighbors that have not been processed yet.
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


## Complete Java Code

```java
import java.util.*;

public class GridIncreasingPaths {
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public GridIncreasingPaths() {
    }

    public int getLongestPathLength(List<String> gridLines) {
        int[][] lengths = buildLengths(parseGrid(gridLines));
        int best = 0;

        for (int[] row : lengths) {
            for (int length : row) {
                best = Math.max(best, length);
            }
        }
        return best;
    }

    public String getOneLongestPath(List<String> gridLines) {
        int[][] grid = parseGrid(gridLines);
        int[][] lengths = buildLengths(grid);
        int row = 0;
        int col = 0;

        // Compare the printed labels, not the numeric coordinates.
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[0].length; c++) {
                if (lengths[r][c] > lengths[row][col]
                        || (lengths[r][c] == lengths[row][col]
                        && cellText(grid, r, c).compareTo(cellText(grid, row, col)) < 0)) {
                    row = r;
                    col = c;
                }
            }
        }

        StringBuilder path = new StringBuilder();
        while (true) {
            if (path.length() > 0) {
                path.append(" -> ");
            }
            path.append(cellText(grid, row, col));

            int nextRow = -1;
            int nextCol = -1;
            for (int[] direction : directions) {
                int r = row + direction[0];
                int c = col + direction[1];
                if (isInside(grid, r, c) && grid[r][c] > grid[row][col]
                        && lengths[r][c] == lengths[row][col] - 1) {
                    if (nextRow == -1
                            || cellText(grid, r, c).compareTo(cellText(grid, nextRow, nextCol)) < 0) {
                        nextRow = r;
                        nextCol = c;
                    }
                }
            }

            if (nextRow == -1) {
                break;
            }
            row = nextRow;
            col = nextCol;
        }
        return path.toString();
    }

    public List<String> getAllStrictlyIncreasingPaths(List<String> gridLines) {
        int[][] grid = parseGrid(gridLines);
        List<String> paths = new ArrayList<>();

        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[0].length; col++) {
                collectPaths(grid, row, col, paths);
            }
        }
        Collections.sort(paths);
        return paths;
    }

    void collectPaths(int[][] grid, int startRow, int startCol, List<String> paths) {
        // Each frame stores row, column, and the next direction to try.
        Deque<int[]> stack = new ArrayDeque<>();
        List<String> path = new ArrayList<>();
        stack.push(new int[]{startRow, startCol, 0});
        path.add(cellText(grid, startRow, startCol));
        paths.add(String.join(" -> ", path));

        while (!stack.isEmpty()) {
            int[] frame = stack.peek();
            if (frame[2] == directions.length) {
                stack.pop();
                path.remove(path.size() - 1);
                continue;
            }

            int[] direction = directions[frame[2]];
            frame[2]++;
            int row = frame[0] + direction[0];
            int col = frame[1] + direction[1];

            if (isInside(grid, row, col) && grid[row][col] > grid[frame[0]][frame[1]]) {
                path.add(cellText(grid, row, col));
                // Record every prefix, not only paths ending at a local maximum.
                paths.add(String.join(" -> ", path));
                stack.push(new int[]{row, col, 0});
            }
        }
    }

    int[][] buildLengths(int[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] lengths = new int[rows][cols];
        int[][] remainingHigher = new int[rows][cols];
        Queue<int[]> ready = new ArrayDeque<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                lengths[row][col] = 1;
                for (int[] direction : directions) {
                    int r = row + direction[0];
                    int c = col + direction[1];
                    if (isInside(grid, r, c) && grid[r][c] > grid[row][col]) {
                        remainingHigher[row][col]++;
                    }
                }
                if (remainingHigher[row][col] == 0) {
                    ready.offer(new int[]{row, col});
                }
            }
        }

        while (!ready.isEmpty()) {
            int[] cell = ready.poll();
            int row = cell[0];
            int col = cell[1];

            for (int[] direction : directions) {
                int r = row + direction[0];
                int c = col + direction[1];
                if (isInside(grid, r, c) && grid[r][c] < grid[row][col]) {
                    lengths[r][c] = Math.max(lengths[r][c], lengths[row][col] + 1);
                    remainingHigher[r][c]--;
                    // All larger neighbors must be processed before this cell is ready.
                    if (remainingHigher[r][c] == 0) {
                        ready.offer(new int[]{r, c});
                    }
                }
            }
        }
        return lengths;
    }

    int[][] parseGrid(List<String> gridLines) {
        int rows = gridLines.size();
        int cols = gridLines.get(0).split(",").length;
        int[][] grid = new int[rows][cols];

        for (int row = 0; row < rows; row++) {
            String[] values = gridLines.get(row).split(",");
            for (int col = 0; col < cols; col++) {
                grid[row][col] = Integer.parseInt(values[col]);
            }
        }
        return grid;
    }

    boolean isInside(int[][] grid, int row, int col) {
        return row >= 0 && row < grid.length && col >= 0 && col < grid[0].length;
    }

    String cellText(int[][] grid, int row, int col) {
        return "(" + row + "," + col + ")=" + grid[row][col];
    }
}
```
