# Longest Path in Grid in Java

#### Problem Statement
[https://codezym.com/question/152-longest-path-grid](https://codezym.com/question/152-longest-path-grid)


Finding the longest path in a grid has no shortcut. BFS gives us the shortest path, but there is no similar trick for the longest one. That is why the grid is kept tiny: at most 30 cells.

So we simply try every path using **backtracking**. We start a walk from each empty cell in the first row, mark cells as we use them, and step back to try other directions. Every time we stand on the last row, the path so far is a valid answer.

Plain backtracking is correct but slow on open grids. So we add **pruning**: before going deeper, a quick flood fill tells us if the last row can still be reached and if this path can still beat our best answer. If not, we turn back right away.


## Understanding the Example

`grid = ["0,0,0", "1,0,1", "0,0,0"]`, answer is `5`

```
grid          one longest path
0 0 0         1 2 .
1 0 1         # 3 #
0 0 0         5 4 .
```

The numbers show the order of cells in the path. `#` is a wall and `.` is an unused cell.

Three things to notice:

1. The path can move sideways, even inside the first row and the last row.
2. The path touches the last row at cell `4` and then keeps going to cell `5`. So reaching the last row does not end the walk. We record the length and keep exploring.
3. If the grid has only one row, the first row is also the last row. So a single empty cell is already a valid path of length `1`.


## Brute Force: Try Every Path

We use DFS (depth first search). From the current cell we try all 4 directions. We move to the next cell only if it is inside the grid, is not a wall and is not already on our path.

```java
// length = number of cells in the path so far
void dfs(int row, int col, int length) {
    visited[row][col] = true; // step in

    // standing on the last row: the path so far is valid
    if (row == rows - 1) {
        best = Math.max(best, length);
    }

    for (int[] move : moves) {
        int nextRow = row + move[0];
        int nextCol = col + move[1];
        if (isFree(nextRow, nextCol)) {
            dfs(nextRow, nextCol, length + 1);
        }
    }

    visited[row][col] = false; // step out (backtrack)
}
```

We call `dfs(0, col, 1)` for every empty cell in the first row.

**Why a `visited` grid?** A path can't use a cell twice. A 2D boolean array tells us instantly if a cell is already on the current path.

**Why unmark at the end?** A cell is blocked only for the current path. When we step back, other paths must be able to use it. This "mark on the way in, unmark on the way out" is what backtracking means. It also lets one array serve every path, so we never copy the path.


### Why Is It Slow?

It explores every possible path. On an open 5 x 6 grid (no walls) it makes about **8.4 million** DFS calls. Most of these paths are hopeless:

1. **Boxed in:** the walk's own cells block every way to the last row, so it can never end there. But it keeps wandering anyway.
2. **Can't win:** the best path so far has 20 cells. Our path has 8 cells and only 10 free cells are left to reach. Even if we use all of them, we get only 18.


## Better Solution: Backtracking With Pruning

Before going deeper from a cell, we run a quick **flood fill** (BFS with a queue) from it over the free cells. It gives us two facts:

- `canReachLastRow`: can any free cell in the last row still be reached? If not, we are **boxed in**.
- `freeCells`: how many free cells we can still reach. Our path can never grow longer than `length + freeCells`. If that is not more than `best`, we **can't win**.

We go deeper only when both checks pass:

```java
return canReachLastRow && length + freeCells > best;
```

Each flood fill looks at no more than 30 cells, so it is cheap. But it cuts away huge parts of the search. On the same open 5 x 6 grid, DFS calls drop from about 8.4 million to about **100**.

**Why a separate `seen` array?** The flood fill must not change `visited`, because `visited` holds our current path. `seen` only remembers which cells this flood fill has already counted.


### Complexity

- **Time:** exponential in the worst case, since the number of paths grows very fast with grid size. Each DFS call also runs an `O(R x C)` flood fill. This is fine because `R x C <= 30`, and pruning removes most paths.
- **Space:** `O(R x C)` for the grids, the queue and the recursion stack.


### Java Code

```java
import java.util.*;

public class LongestPathInGrid {

    int rows;
    int cols;

    // wall[r][c] is true if cell (r, c) is a wall
    boolean[][] wall;

    // visited[r][c] is true if cell (r, c) is on the current path
    boolean[][] visited;

    // longest valid path found so far
    int best;

    // the 4 moves: up, down, left, right
    int[][] moves = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public LongestPathInGrid() {
    }

    public int longestPath(List<String> grid) {
        readGrid(grid);
        best = 0;

        // a path can start from any empty cell in the first row
        for (int col = 0; col < cols; col++) {
            if (!wall[0][col]) {
                dfs(0, col, 1);
            }
        }
        return best;
    }

    /** Converts rows like "0,1,0" into a 2D wall grid. */
    void readGrid(List<String> grid) {
        rows = grid.size();
        cols = grid.get(0).split(",").length;
        wall = new boolean[rows][cols];
        visited = new boolean[rows][cols];

        for (int row = 0; row < rows; row++) {
            String[] values = grid.get(row).split(",");
            for (int col = 0; col < cols; col++) {
                wall[row][col] = values[col].trim().equals("1");
            }
        }
    }

    /**
     * We are standing on (row, col) and the path so far has 'length' cells.
     * Tries every way to extend the path, then steps back.
     */
    void dfs(int row, int col, int length) {
        visited[row][col] = true;

        // standing on the last row means the path so far is valid
        if (row == rows - 1) {
            best = Math.max(best, length);
        }

        // go deeper only if this path can still give a better answer
        if (worthExploring(row, col, length)) {
            for (int[] move : moves) {
                int nextRow = row + move[0];
                int nextCol = col + move[1];
                if (isFree(nextRow, nextCol)) {
                    dfs(nextRow, nextCol, length + 1);
                }
            }
        }

        // backtrack: free this cell so other paths can use it
        visited[row][col] = false;
    }

    /**
     * Flood fill (BFS) from (row, col) over free cells.
     * Returns false if the last row can no longer be reached,
     * or if even using every reachable cell we can't beat 'best'.
     */
    boolean worthExploring(int row, int col, int length) {
        // kept separate from 'visited', which holds the current path
        boolean[][] seen = new boolean[rows][cols];
        seen[row][col] = true;

        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{row, col});

        int freeCells = 0;
        boolean canReachLastRow = false;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] move : moves) {
                int r = cell[0] + move[0];
                int c = cell[1] + move[1];
                if (isFree(r, c) && !seen[r][c]) {
                    seen[r][c] = true;
                    queue.add(new int[]{r, c});
                    freeCells++;
                    if (r == rows - 1) {
                        canReachLastRow = true;
                    }
                }
            }
        }
        return canReachLastRow && length + freeCells > best;
    }

    /** True if (row, col) is inside the grid, not a wall and not on the current path. */
    boolean isFree(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols
                && !wall[row][col] && !visited[row][col];
    }
}
```