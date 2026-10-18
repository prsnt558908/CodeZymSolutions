# Matrix Special Elements in Java

#### Problem Statement
[https://codezym.com/question/108-matrix-special-elements](https://codezym.com/question/108-matrix-special-elements)

---

## Core Idea

Think of every row and every column as a simple **line of numbers**.


For each line we only need to know two things: its smallest value and its largest value, and whether each of them appears **exactly once**.


If any line has a repeated min or a repeated max, we stop and return `-1`.


Otherwise, we drop each line's min and max into a **Set**. A set keeps only distinct values, so duplicates across rows and columns are removed for free. The answer is simply the size of the set.


We will first build a slow but easy brute force solution. Then we will improve it to a clean linear time solution.

---

## Understanding the Problem with an Example

Take Example 2:

```
5  1  7
3  9  2
8  4  6
```

| Line     | Values  | Min | Max |
|----------|---------|-----|-----|
| Row 1    | 5, 1, 7 | 1   | 7   |
| Row 2    | 3, 9, 2 | 2   | 9   |
| Row 3    | 8, 4, 6 | 4   | 8   |
| Column 1 | 5, 3, 8 | 3   | 8   |
| Column 2 | 1, 9, 4 | 1   | 9   |
| Column 3 | 7, 2, 6 | 2   | 7   |


Every min and max appears only once in its line, so the matrix is valid.


Collecting all of them gives `{1, 2, 3, 4, 7, 8, 9}`, which has **7** distinct values.


Note that a line can never have "zero" minimums. So the only way a line fails is when its min or max shows up **two or more times**, like the row `1, 1` in Example 3.

---

## Solution 1: Brute Force

### Idea

For every cell, compare it with every other cell in its own row.


- If the cell is **strictly smaller** than all others, it is the unique minimum.
- If the cell is **strictly larger** than all others, it is the unique maximum.


We use strict comparison on purpose. If two cells share the smallest value, neither is strictly smaller than the other, so the row ends up with **zero** unique minimums.


So a row is valid only when we find exactly **one** unique min cell and exactly **one** unique max cell.


Then we repeat the same thing for every column.


A row or column of length `1` works naturally: there is no other cell to compare with, so the single cell is both the unique min and the unique max.

### Code

```java
import java.util.*;

public class MatrixSpecialElements {

    public MatrixSpecialElements() {
    }

    public int countMatrixSpecialElements(List<String> matrixRows) {
        int[][] grid = parseMatrix(matrixRows);
        int rows = grid.length;
        int cols = grid[0].length;

        // Stores every distinct special value
        Set<Integer> special = new HashSet<>();

        // Check every row
        for (int r = 0; r < rows; r++) {
            int uniqueMins = 0, uniqueMaxs = 0;
            for (int c = 0; c < cols; c++) {
                boolean isMin = true, isMax = true;
                // Compare this cell with every other cell of the same row
                for (int k = 0; k < cols; k++) {
                    if (k == c) continue;
                    if (grid[r][k] <= grid[r][c]) isMin = false;
                    if (grid[r][k] >= grid[r][c]) isMax = false;
                }
                if (isMin) { uniqueMins++; special.add(grid[r][c]); }
                if (isMax) { uniqueMaxs++; special.add(grid[r][c]); }
            }
            // Exactly one cell must be the unique min and one the unique max
            if (uniqueMins != 1 || uniqueMaxs != 1) return -1;
        }

        // Check every column
        for (int c = 0; c < cols; c++) {
            int uniqueMins = 0, uniqueMaxs = 0;
            for (int r = 0; r < rows; r++) {
                boolean isMin = true, isMax = true;
                // Compare this cell with every other cell of the same column
                for (int k = 0; k < rows; k++) {
                    if (k == r) continue;
                    if (grid[k][c] <= grid[r][c]) isMin = false;
                    if (grid[k][c] >= grid[r][c]) isMax = false;
                }
                if (isMin) { uniqueMins++; special.add(grid[r][c]); }
                if (isMax) { uniqueMaxs++; special.add(grid[r][c]); }
            }
            if (uniqueMins != 1 || uniqueMaxs != 1) return -1;
        }

        return special.size();
    }

    // Converts "1,2,3" style strings into a 2-D int array
    int[][] parseMatrix(List<String> matrixRows) {
        int[][] grid = new int[matrixRows.size()][];
        for (int r = 0; r < matrixRows.size(); r++) {
            String[] parts = matrixRows.get(r).split(",");
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

Let `R` be the number of rows and `C` be the number of columns.


- **Time:** `O(R * C * (R + C))`. Each cell is compared with its whole row and its whole column.
- **Space:** `O(R * C)` for the parsed grid.

### What is wrong with this?

With `R = C = 1000`, this does about **2 billion** comparisons. That is far too slow.


The waste is easy to spot. To find the min of a row, we do not need to compare every cell with every other cell. One scan of the row is enough to find the min, and a second scan is enough to count it.

---

## Solution 2: Optimized (Two Scans per Line)

### Idea

For each line (row or column), do just two quick scans:


1. **Scan 1:** find `min` and `max`.
2. **Scan 2:** count how many times `min` and `max` appear.


If either count is not `1`, the matrix is invalid and we return `-1`.


Otherwise, add `min` and `max` to the set.


Rows and columns follow the exact same rule. So we write the logic **once** in a helper method `addUniqueMinMax(int[] line, Set<Integer> special)` and call it for both.


A row is already an `int[]` in our grid. For a column, we simply copy its values into a small `int[]` and pass it to the same helper. This keeps the code short and avoids writing the same loops twice.

### Why these data structures?

**`int[][] grid`**: The input comes as strings, but we need to read the matrix both row by row and column by column. Parsing it once into a 2-D array makes both directions easy and fast.


**`HashSet<Integer> special`**: The same value can be the min or max of many lines. For example, in Example 2 the value `9` is the max of row 2 and also of column 2. A set removes such duplicates automatically, so `special.size()` is directly our answer.

### Code

```java
import java.util.*;

public class MatrixSpecialElements {

    public MatrixSpecialElements() {
    }

    public int countMatrixSpecialElements(List<String> matrixRows) {
        int[][] grid = parseMatrix(matrixRows);
        int rows = grid.length;
        int cols = grid[0].length;

        // Stores every distinct special value
        Set<Integer> special = new HashSet<>();

        // Check every row
        for (int r = 0; r < rows; r++) {
            if (!addUniqueMinMax(grid[r], special)) return -1;
        }

        // Check every column
        for (int c = 0; c < cols; c++) {
            // Copy the column into a 1-D array so we can reuse the same helper
            int[] column = new int[rows];
            for (int r = 0; r < rows; r++) {
                column[r] = grid[r][c];
            }
            if (!addUniqueMinMax(column, special)) return -1;
        }

        return special.size();
    }

    /**
     * Works on one line (a row or a column).
     * Returns false if its min or max appears more than once.
     * Otherwise adds the min and max to the special set and returns true.
     */
    boolean addUniqueMinMax(int[] line, Set<Integer> special) {
        // Pass 1: find min and max
        int min = line[0], max = line[0];
        for (int value : line) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        // Pass 2: count how many times min and max appear
        int minCount = 0, maxCount = 0;
        for (int value : line) {
            if (value == min) minCount++;
            if (value == max) maxCount++;
        }

        if (minCount != 1 || maxCount != 1) return false;

        special.add(min);
        special.add(max);
        return true;
    }

    // Converts "1,2,3" style strings into a 2-D int array
    int[][] parseMatrix(List<String> matrixRows) {
        int[][] grid = new int[matrixRows.size()][];
        for (int r = 0; r < matrixRows.size(); r++) {
            String[] parts = matrixRows.get(r).split(",");
            grid[r] = new int[parts.length];
            for (int c = 0; c < parts.length; c++) {
                grid[r][c] = Integer.parseInt(parts[c].trim());
            }
        }
        return grid;
    }
}
```

### Walkthrough of the edge cases

- **Single value matrix `["7"]`:** min and max are both `7`, and each count is `1`. The set becomes `{7}`, so the answer is `1`.
- **Row `1, 1`:** min is `1` with count `2`. The helper returns `false`, so we return `-1`.
- **Column `2, 2` (Example 4):** all rows pass, but the first column has min `2` with count `2`. We return `-1`.

### Complexity

- **Time:** `O(R * C)`. Every cell is touched a small, fixed number of times: during parsing, during the row check and during the column check.
- **Space:** `O(R * C)` for the grid. The set holds at most `2R + 2C` values and the column copy holds `R` values.

---

## Summary

| Solution    | Time                  | Space      |
|-------------|-----------------------|------------|
| Brute Force | `O(R * C * (R + C))`  | `O(R * C)` |
| Optimized   | `O(R * C)`            | `O(R * C)` |


The key takeaway: a line's unique min and max can be found with **two simple scans**, and a **Set** takes care of counting distinct values for us.