# Max Path Sum in Matrix in Java

#### Problem Statement
[https://codezym.com/question/128-max-path-sum-matrix](https://codezym.com/question/128-max-path-sum-matrix)


The core idea is simple: to reach any cell, you must come either from the cell **above** it or from the cell on its **left**.


So if we already know the best sum for those two neighbours, the best sum for the current cell is just the bigger of the two, plus the current cell's value.


We fill these "best sums" one cell at a time, starting from the top-left corner. The last cell we fill, the bottom-right one, is our answer.


This technique is called **Dynamic Programming**. Solve each small part once, save it, and reuse it.


We will start with a brute force idea, see why it is slow, then build a 2-D table solution, and finally shrink it down to a single array.


---


## Understanding the Problem


- The input is a list of strings. Each string is one row, like `"5,3,2"`.
- We start at the top-left cell and must reach the bottom-right cell.
- From any cell we can move only **right** or **down**.
- We want the path whose cell values add up to the **maximum** sum.


Example 1 as a grid:

```
5  3  2
1  9  1
0  2  7
```

The best path is `5 -> 3 -> 9 -> 2 -> 7`, which gives `26`.


---


## Step 1: Parsing the Input


Each row arrives as text, so we first convert it into numbers.


We split each row on `,` and convert every piece with `Integer.parseInt`. The `trim()` call makes it safe even if a value has extra spaces around it.


We store the result in an `int[][]` because it is the simplest way to read any cell as `grid[r][c]`.


```java
int[][] parseGrid(List<String> matrix) {
    int rows = matrix.size();
    int[][] grid = new int[rows][];
    for (int r = 0; r < rows; r++) {
        String[] parts = matrix.get(r).split(",");
        grid[r] = new int[parts.length];
        for (int c = 0; c < parts.length; c++) {
            grid[r][c] = Integer.parseInt(parts[c].trim());
        }
    }
    return grid;
}
```


---


## Brute Force: Try Every Path


From every cell we have at most two choices: go right or go down.


So the most direct idea is: try both choices, find the best sum for each, and keep the bigger one.


```java
// Best sum from cell (r, c) to the bottom-right cell
int bestFrom(int[][] grid, int r, int c) {
    int rows = grid.length;
    int cols = grid[0].length;

    // reached the destination
    if (r == rows - 1 && c == cols - 1) {
        return grid[r][c];
    }

    int bestNext = 0;
    if (r + 1 < rows) {
        bestNext = Math.max(bestNext, bestFrom(grid, r + 1, c)); // go down
    }
    if (c + 1 < cols) {
        bestNext = Math.max(bestNext, bestFrom(grid, r, c + 1)); // go right
    }
    return grid[r][c] + bestNext;
}
```


This gives the correct answer, but it is far too slow for big inputs.


### Why is it slow?


Every call splits into two more calls. In a `200 x 200` grid, the number of different paths is a number with more than 100 digits. This will never finish.


The real waste is that we solve the **same cell again and again**.


For example, cell `(1, 1)` can be reached by "right then down" and also by "down then right". Each time, the recursion recalculates the full best sum from `(1, 1)` to the end, from scratch.


If we could solve every cell only once, the problem would become very fast. That is exactly what we do next.


---


## Key Observation


Let us flip the question.


Instead of asking "where do I go next?", ask "**how did I get here?**"


Any path that reaches cell `(r, c)` must arrive from one of only two cells:

- the cell above, `(r - 1, c)`
- the cell on the left, `(r, c - 1)`


So if `best[r][c]` means "the maximum sum of any path from the start to cell `(r, c)`", then:

```
best[r][c] = grid[r][c] + max(best[r - 1][c], best[r][c - 1])
```


The edge cells need a small adjustment:

- **Start cell:** `best[0][0] = grid[0][0]`
- **First row:** there is nothing above, so we can only come from the left.
- **First column:** there is nothing on the left, so we can only come from above.


If we fill the table **row by row, left to right**, then the cell above and the cell on the left are always ready before we need them.


### Dry Run on Example 1


Filling `best` for the grid `["5,3,2", "1,9,1", "0,2,7"]`:


|           | col 0       | col 1                | col 2                 |
|-----------|-------------|----------------------|-----------------------|
| **row 0** | 5           | 5 + 3 = **8**        | 8 + 2 = **10**        |
| **row 1** | 5 + 1 = **6** | max(8, 6) + 9 = **17** | max(10, 17) + 1 = **18** |
| **row 2** | 6 + 0 = **6** | max(17, 6) + 2 = **19** | max(18, 19) + 7 = **26** |


The bottom-right value is `26`, which matches the expected answer.


---


## Solution 1: Dynamic Programming with a 2-D Table


We create a table `best` with the same size as the grid.


**Why do we need the `best` table?** It remembers the answer for each cell, so every cell is solved exactly once. This removes all the repeated work from the brute force.


```java
import java.util.*;

public class MaxPathSumInMatrix {

    public MaxPathSumInMatrix() {
    }

    /**
     * Returns the maximum sum of a path from the top-left cell
     * to the bottom-right cell, moving only right or down.
     */
    public int maxPathSum(List<String> matrix) {
        int[][] grid = parseGrid(matrix);
        int rows = grid.length;
        int cols = grid[0].length;

        // best[r][c] = maximum parks collected on any path from (0,0) to (r,c)
        int[][] best = new int[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == 0 && c == 0) {
                    // starting cell
                    best[r][c] = grid[r][c];
                } else if (r == 0) {
                    // first row: we can only come from the left
                    best[r][c] = best[r][c - 1] + grid[r][c];
                } else if (c == 0) {
                    // first column: we can only come from above
                    best[r][c] = best[r - 1][c] + grid[r][c];
                } else {
                    // pick the better of "from above" and "from left"
                    best[r][c] = Math.max(best[r - 1][c], best[r][c - 1]) + grid[r][c];
                }
            }
        }
        return best[rows - 1][cols - 1];
    }

    /**
     * Converts rows like "5,3,2" into a 2-D int array.
     */
    int[][] parseGrid(List<String> matrix) {
        int rows = matrix.size();
        int[][] grid = new int[rows][];
        for (int r = 0; r < rows; r++) {
            String[] parts = matrix.get(r).split(",");
            grid[r] = new int[parts.length];
            for (int c = 0; c < parts.length; c++) {
                grid[r][c] = Integer.parseInt(parts[c].trim());
            }
        }
        return grid;
    }
}
```


### Complexity


- **Time:** `O(R x C)`. Each cell is visited once.
- **Space:** `O(R x C)` for the grid and the `best` table.


An `int` is enough for the answer. The longest path has `200 + 200 - 1 = 399` cells, and `399 x 10,000 = 3,990,000`, which fits easily.


---


## Solution 2: Space Optimized with a 1-D Array


Look closely at the formula again. To fill row `r`, we only need row `r` itself and row `r - 1`.


All the rows above that are never used again. So why keep them?


We can keep just **one array** `best` with one slot per column.


**Why does one array work?** When we are at column `c` of row `r`:

- `best[c]` has not been updated yet, so it still holds the answer for the cell **above**.
- `best[c - 1]` was already updated in this row, so it holds the answer for the cell on the **left**.


So the formula becomes:

```
best[c] = max(best[c], best[c - 1]) + row[c]
```


Since we only need one row of input at a time, we can also parse each row just when we need it. This way we never store the full grid.


### Dry Run on Example 1


Here is how the single array changes after each row:

| After row | best          |
|-----------|---------------|
| row 0     | [5, 8, 10]    |
| row 1     | [6, 17, 18]   |
| row 2     | [6, 19, 26]   |


The last value is `26`, the same answer as before, using much less memory.


```java
import java.util.*;

public class MaxPathSumInMatrix {

    public MaxPathSumInMatrix() {
    }

    /**
     * Keeps only one row of answers instead of a full table.
     * Before updating, best[c] holds the answer for the cell above.
     * best[c - 1] already holds the answer for the cell on the left.
     */
    public int maxPathSum(List<String> matrix) {
        int cols = parseRow(matrix.get(0)).length;
        int[] best = new int[cols];

        for (int r = 0; r < matrix.size(); r++) {
            // parse one row at a time, no need to store the whole grid
            int[] row = parseRow(matrix.get(r));

            for (int c = 0; c < cols; c++) {
                if (r == 0 && c == 0) {
                    best[c] = row[c];
                } else if (r == 0) {
                    // first row: only from the left
                    best[c] = best[c - 1] + row[c];
                } else if (c == 0) {
                    // first column: only from above (old value of best[0])
                    best[c] = best[c] + row[c];
                } else {
                    // best[c] = from above, best[c - 1] = from left
                    best[c] = Math.max(best[c], best[c - 1]) + row[c];
                }
            }
        }
        return best[cols - 1];
    }

    /**
     * Converts a row like "5,3,2" into an int array.
     */
    int[] parseRow(String rowText) {
        String[] parts = rowText.split(",");
        int[] row = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            row[i] = Integer.parseInt(parts[i].trim());
        }
        return row;
    }
}
```


### Complexity


- **Time:** `O(R x C)`. Each cell is still visited once.
- **Space:** `O(C)`. We keep only one row of answers and one parsed row.


---


## Summary


| Approach        | Time              | Space        |
|-----------------|-------------------|--------------|
| Brute force     | Exponential       | `O(R x C)`   |
| 2-D DP table    | `O(R x C)`        | `O(R x C)`   |
| 1-D DP array    | `O(R x C)`        | `O(C)`       |


The main lesson: when the same small problem keeps coming up again and again, solve it once, store the answer, and build the final answer from those stored pieces.