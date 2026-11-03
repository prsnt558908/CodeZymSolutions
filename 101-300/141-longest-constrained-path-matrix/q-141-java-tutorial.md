# Longest Constrained Path in Matrix in Java

#### Problem Statement
[https://codezym.com/question/141-longest-constrained-path-matrix](https://codezym.com/question/141-longest-constrained-path-matrix)


## Core Idea

The grid has fewer than 20 cells. That is very small, so we can simply try every valid path and keep the longest one.


We grow each path one cell at a time using DFS with backtracking. We step on a cell, try to go further, and when we come back we free that cell so other paths can use it.


The rule looks tricky, but it becomes very simple: the next value must be `<=` the bigger of the last two values in the path. So while growing a path, we only need to remember one number. We call it the `limit`.


## Understanding the Rule

The rule says the next cell `C` must satisfy `grid[C] <= grid[B]` or `grid[C] <= grid[A]`, where `A` and `B` are the last two cells.


If `C` is `<=` at least one of them, then it is also `<=` the bigger one. So the rule is just:

```
grid[C] <= max(grid[A], grid[B])
```


When the path has only one cell `A`, the next cell must be `<= grid[A]`. This is the same formula if we treat `A` as both of the last two cells.


Let us check the path `5 -> 4 -> 2 -> 3` from Example 2.

| Path so far | Last two values | Limit | Next value | Allowed? |
|---|---|---|---|---|
| `5` | 5 | 5 | 4 | Yes |
| `5 -> 4` | 5, 4 | 5 | 2 | Yes |
| `5 -> 4 -> 2` | 4, 2 | 4 | 3 | Yes |


Notice the last move goes up from 2 to 3. It is allowed because the cell before 2 had value 4.


## Why Not a Simple DP?

In the classic "Longest Increasing Path in a Matrix" problem, values strictly increase. A path can never reach a cell it already used, so storing one answer per cell is enough.


Here, equal values are allowed, and a path can even step up (like 2 to 3 above). So a path can run into cells it already used, and the best answer from a cell depends on which cells are already taken.


That is why we keep track of the used cells and try every path. With fewer than 20 cells, this is fast.


## Solution 1: Backtracking With the Full Path

The most direct idea is to follow the problem statement word by word. We keep the whole path in a `List` of cells.


From the last cell, we try all 4 neighbors. A neighbor is allowed if it is inside the grid, it is not already in the path, and it passes the rule. We add it, go deeper, and then remove it (backtrack).


We start this DFS once from every cell.

```java
// path holds the cells in the order we visited them
void dfs(List<int[]> path) {
    best = Math.max(best, path.size());
    int[] last = path.get(path.size() - 1);

    for (int d = 0; d < 4; d++) {
        int row = last[0] + rowStep[d];
        int col = last[1] + colStep[d];
        if (row < 0 || row >= rows || col < 0 || col >= cols) continue;
        if (contains(path, row, col)) continue;
        if (!isValidMove(path, values[row][col])) continue;

        path.add(new int[]{row, col});
        dfs(path);
        path.remove(path.size() - 1); // backtrack
    }
}

// scans the whole path to see if a cell is already used
boolean contains(List<int[]> path, int row, int col) {
    for (int[] cell : path) {
        if (cell[0] == row && cell[1] == col) return true;
    }
    return false;
}

// the two rules exactly as written in the problem
boolean isValidMove(List<int[]> path, int nextValue) {
    int[] b = path.get(path.size() - 1);
    if (path.size() == 1) return nextValue <= values[b[0]][b[1]];

    int[] a = path.get(path.size() - 2);
    return nextValue <= values[b[0]][b[1]] || nextValue <= values[a[0]][a[1]];
}
```


This gives the right answer, but it does extra work:

- `contains()` scans the whole path every time we try a neighbor.
- We carry the full path around, but the rule only needs the last two values.
- The rule check has two separate cases.


## Solution 2: Backtracking With a Visited Grid and a Limit

We fix each of these problems with a small change.


**1. A `visited` grid instead of scanning the path.**

`visited[row][col]` tells us in one step if a cell is already in the path. We set it to `true` when we step on a cell and back to `false` when we step back. Freeing the cell matters, because a different path may want to use it later.


**2. One `limit` value instead of the whole path.**

The next cell must be `<= limit`. After moving from cell `X` to cell `Y`, the new limit is `max(value of X, value of Y)`.


**3. No special case for the first move.**

When a path starts at a cell, we set `limit` to that cell's own value. This is exactly the one cell rule.


We also convert the input strings into an `int[][] values` grid once, at the start. So we never parse text inside the search.


### Java Code

```java
import java.util.*;

public class LongestConstrainedPath {

    int rows;
    int cols;
    int[][] values;        // grid values after parsing the strings
    boolean[][] visited;   // cells that are already part of the current path
    int best;              // longest path length found so far

    // the 4 moves: up, down, left, right
    int[] rowStep = {-1, 1, 0, 0};
    int[] colStep = {0, 0, -1, 1};

    public LongestConstrainedPath() {

    }

    public int longestConstrainedPath(List<String> grid) {
        parseGrid(grid);
        visited = new boolean[rows][cols];
        best = 0;

        // a path can start from any cell
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                visited[row][col] = true;
                // a one cell path: the next cell must be <= this cell's own value
                dfs(row, col, values[row][col], 1);
                visited[row][col] = false;
            }
        }
        return best;
    }

    /**
     * Tries every valid way to extend the path that ends at (row, col).
     *
     * @param limit  the bigger value among the last two cells of the path.
     *               The next cell must have a value <= limit.
     * @param length number of cells in the current path
     */
    void dfs(int row, int col, int limit, int length) {
        best = Math.max(best, length);

        for (int d = 0; d < 4; d++) {
            int nextRow = row + rowStep[d];
            int nextCol = col + colStep[d];

            if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= cols) continue;
            if (visited[nextRow][nextCol]) continue;
            if (values[nextRow][nextCol] > limit) continue;

            visited[nextRow][nextCol] = true;
            // after this move, the last two cells are (row, col) and (nextRow, nextCol)
            int nextLimit = Math.max(values[row][col], values[nextRow][nextCol]);
            dfs(nextRow, nextCol, nextLimit, length + 1);
            visited[nextRow][nextCol] = false; // backtrack: free the cell for other paths
        }
    }

    // converts rows like "5,4" into a 2D int array
    void parseGrid(List<String> grid) {
        rows = grid.size();
        cols = grid.get(0).split(",").length;
        values = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            String[] parts = grid.get(row).split(",");
            for (int col = 0; col < cols; col++) {
                values[row][col] = Integer.parseInt(parts[col].trim());
            }
        }
    }
}
```


## Complexity

Let `N = m * n` be the number of cells.


- **Time:** we explore every valid path once, and for each one we check 4 neighbors. The number of paths grows very fast as the grid gets bigger, but here the grid has fewer than 20 cells. The hardest case is when all values are equal, because then every move is allowed. Even then, a `3 x 6` grid has fewer than 60,000 paths in total.
- **Space:** `O(N)` for the `values` and `visited` grids, and for the recursion depth (a path has at most `N` cells).