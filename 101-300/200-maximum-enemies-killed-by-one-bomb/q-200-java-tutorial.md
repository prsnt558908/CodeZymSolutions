# Maximum Enemies Killed By One Bomb in Java

#### Problem Statement
[https://codezym.com/question/200-maximum-enemies-killed-by-one-bomb](https://codezym.com/question/200-maximum-enemies-killed-by-one-bomb)


## Core Idea

A bomb kills enemies in its row and its column, but a wall stops the blast in that direction.

So walls split every row and every column into smaller parts. Let's call each part a **segment**.

The key thinking is simple: every empty cell inside the same row segment kills exactly the same enemies in that row. The same is true for a column segment.

So we don't need to walk from every cell again and again. We can count the enemies in each segment once, and reuse that count for every cell inside it.

We will start with a simple brute force solution, see why it is slow, and then improve it so that every cell is visited only a few times.


## Reading the Input

Each row comes as a string like `"0,E,W,0"`. We split it by commas and store every cell as one character in a `char[][]` grid.

A 2D array lets us read any cell directly as `cells[r][c]`, instead of splitting strings again and again.


## Solution 1: Brute Force

For every empty cell, walk left, right, up and down. Count the enemies until you reach a wall or the edge of the grid. Keep the best total.

```java
// Try every empty cell
for (int r = 0; r < rows; r++) {
    for (int c = 0; c < cols; c++) {
        if (cells[r][c] == '0') {
            int kills = countInDirection(cells, r, c, 0, -1)  // left
                    + countInDirection(cells, r, c, 0, 1)     // right
                    + countInDirection(cells, r, c, -1, 0)    // up
                    + countInDirection(cells, r, c, 1, 0);    // down
            best = Math.max(best, kills);
        }
    }
}

// Counts enemies from (r, c) in one direction until a wall or the grid edge
int countInDirection(char[][] cells, int r, int c, int rowStep, int colStep) {
    int count = 0;
    r += rowStep;
    c += colStep;
    while (r >= 0 && r < cells.length && c >= 0 && c < cells[0].length
            && cells[r][c] != 'W') {
        if (cells[r][c] == 'E') {
            count++;
        }
        r += rowStep;
        c += colStep;
    }
    return count;
}
```

`rowStep` and `colStep` tell us which way to move. For example, `(0, -1)` means stay in the same row and move one column to the left.


### Why Is This Slow?

Let R be the number of rows and C be the number of columns. Each empty cell may walk a full row and a full column, so the time is **O(R × C × (R + C))**.

For a 500 × 500 grid, that is 250,000 cells × about 1,000 steps, which is around 250 million steps.

Most of this work is repeated. Look at this row:

```
0  0  0  0  E
```

All four empty cells walk the same row and find the same single enemy. We count that one enemy four times.


## Solution 2: Count Each Segment Only Once (Optimal)

### How Segments Help

```
Column:   0  1  2  3  4  5
Row:      E  0  W  0  E  0
Segment:  1  1  -  2  2  2
```

The wall at column 2 splits this row into two segments. Each segment has 1 enemy. So a bomb at column 1, 3 or 5 kills exactly 1 enemy in this row.

This gives us a simple formula for any empty cell:

```
kills = enemies in its row segment + enemies in its column segment
```

The bomb cell itself is always empty, so counting the whole segment never adds an extra enemy by mistake.


### Reusing the Counts

We visit the grid row by row, from left to right, and keep two things:

- `rowKills`: enemies in the row segment we are in right now.
- `colKills[c]`: enemies in the current segment of column `c`.

We count again only when a new segment starts:

- A **new row segment** starts at column 0, or right after a wall. We scan right until the next wall and save the count in `rowKills`.
- A **new column segment** starts at row 0, or right below a wall. We scan down until the next wall and save the count in `colKills[c]`.

We are standing on the first cell of the segment, so scanning in just one direction covers the whole segment.

In the row above, we count only at column 0 and column 3. Every other cell reuses the saved count.


### Why One Number for Rows but an Array for Columns?

We finish a full row before we move to the next row. So at any moment we are inside only one row segment, and one number `rowKills` is enough.

Columns are different. As we move along a row, we step into a new column every time. So we must remember the current segment count of every column at the same time. That is why `colKills` is an array with one slot per column.


### Java Code

```java
import java.util.*;

public class BombEnemy {

    public BombEnemy() {
    }

    /**
     * Returns the maximum number of enemies that one bomb can kill.
     * A bomb can only be placed on an empty "0" cell.
     */
    public int maxKilledEnemies(List<String> grid) {
        if (grid.isEmpty()) {
            return 0; // no cells, so no place for a bomb
        }

        char[][] cells = parseGrid(grid);
        int rows = cells.length;
        int cols = cells[0].length;

        int best = 0;

        // Enemies in the row segment we are in right now
        int rowKills = 0;

        // colKills[c] = enemies in the current segment of column c
        int[] colKills = new int[cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {

                // A new row segment starts at column 0 or right after a wall
                if (c == 0 || cells[r][c - 1] == 'W') {
                    rowKills = countRowEnemies(cells, r, c);
                }

                // A new column segment starts at row 0 or right below a wall
                if (r == 0 || cells[r - 1][c] == 'W') {
                    colKills[c] = countColumnEnemies(cells, r, c);
                }

                // A bomb can only be placed on an empty cell
                if (cells[r][c] == '0') {
                    best = Math.max(best, rowKills + colKills[c]);
                }
            }
        }
        return best;
    }

    /**
     * Turns each row like "0,E,W" into a char array like ['0', 'E', 'W'].
     */
    char[][] parseGrid(List<String> grid) {
        char[][] cells = new char[grid.size()][];
        for (int r = 0; r < grid.size(); r++) {
            String[] values = grid.get(r).split(",");
            cells[r] = new char[values.length];
            for (int c = 0; c < values.length; c++) {
                cells[r][c] = values[c].charAt(0);
            }
        }
        return cells;
    }

    /**
     * Counts enemies from (row, col) to the right until a wall or the grid edge.
     */
    int countRowEnemies(char[][] cells, int row, int col) {
        int count = 0;
        for (int c = col; c < cells[row].length && cells[row][c] != 'W'; c++) {
            if (cells[row][c] == 'E') {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts enemies from (row, col) downward until a wall or the grid edge.
     */
    int countColumnEnemies(char[][] cells, int row, int col) {
        int count = 0;
        for (int r = row; r < cells.length && cells[r][col] != 'W'; r++) {
            if (cells[r][col] == 'E') {
                count++;
            }
        }
        return count;
    }
}
```


### Complexity

Each segment is scanned only once, and segments never overlap. So all row scans together cover each cell about once, and so do all column scans. Add the main loop, and every cell is visited only a few times.

- **Time:** O(R × C)
- **Space:** O(R × C) for the parsed grid. The counting itself needs only O(C) extra space for `colKills`.