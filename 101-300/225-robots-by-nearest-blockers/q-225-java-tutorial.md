# Find Robots in Location Map by Nearest Blockers in Java

#### Problem Statement
[https://codezym.com/question/225-robots-by-nearest-blockers](https://codezym.com/question/225-robots-by-nearest-blockers)


For every robot we need four numbers: how far the nearest blocker is on its left, top, bottom and right. A robot is in the answer only when all four numbers match the query.

The core trick is a **step counter**. If we walk along a row from left to right and count the steps since the last blocker we passed, every robot we meet already knows its left distance.

So instead of walking out from every robot again and again, we walk each row and each column once in each direction. A robot that matches the query in all four walks goes into the answer.

Every cell is visited only a few times, which is fast even for a 1000 × 1000 map.


## Approach 1: Brute Force (Walk Out From Every Robot)

The distance in a direction is the number of steps a robot takes, one cell at a time, until it steps onto an `X` or steps outside the map. Stepping outside means it hit the boundary blocker.

Other robots do not block, so we simply walk over them.

Let's check Example 1. Row numbers are on the left and column numbers are on top.

```
     0 1 2 3
  0  O E E X
  1  E E E X
  2  X E O E
  3  E E E E
```

For the robot at `"2,2"`:

| Direction | What it hits | Distance |
|---|---|---|
| Left | `X` at column 0 | 2 |
| Top | top boundary | 3 |
| Bottom | bottom boundary | 2 |
| Right | right boundary | 2 |

That is `[2, 3, 2, 2]`, exactly the query. So the answer is `["2,2"]`.

In code, a direction is two numbers `(dr, dc)`: how much the row and the column change in one step. Left is `(0, -1)`, top is `(-1, 0)`, bottom is `(1, 0)` and right is `(0, 1)`.

```java
// Steps from the robot at (r, c) to the nearest blocker in direction (dr, dc)
int distance(int r, int c, int dr, int dc) {
    int steps = 0;
    while (true) {
        r += dr;
        c += dc;
        steps++;
        // stop when we step outside the grid (boundary) or onto an X
        if (r < 0 || r >= rows || c < 0 || c >= cols || grid[r][c] == 'X') {
            return steps;
        }
    }
}
```

Then we check every robot against the query:

```java
for (int r = 0; r < rows; r++) {
    for (int c = 0; c < cols; c++) {
        if (grid[r][c] != 'O') continue;
        int left = distance(r, c, 0, -1);
        int top = distance(r, c, -1, 0);
        int bottom = distance(r, c, 1, 0);
        int right = distance(r, c, 0, 1);
        if (List.of(left, top, bottom, right).equals(query)) {
            result.add(r + "," + c);
        }
    }
}
```

### Why is this slow?

In the worst case, each robot walks across its whole row and its whole column. That is about `rows + cols` steps for a single robot.

A 1000 × 1000 map full of robots has 1,000,000 robots. That adds up to around 2,000,000,000 steps. Far too slow.

Look at the waste: robots in the same row walk over the same cells again and again. Let's remove that repeated work.


## Approach 2: Walk Each Row and Column Once (Optimal)

### The step counter

Walk along a row from left to right and carry a counter called `steps`.

- Before the first cell we are standing on the boundary blocker, so `steps` starts at 0.
- On every new cell, add 1 to `steps`.
- If the cell is `X`, reset `steps` to 0. This `X` is now the nearest blocker behind us.
- If the cell is a robot, `steps` is exactly its left distance.

Robots do not reset the counter.

Here is row 2 of Example 1, which is `X E O E`:

| Column | Cell | `steps` | Meaning |
|---|---|---|---|
| 0 | `X` | 0 | blocker, reset |
| 1 | `E` | 1 | |
| 2 | `O` | 2 | robot, left distance is 2 |
| 3 | `E` | 3 | |

One walk over a row gives the left distance of every robot in that row. No robot needs its own walk anymore.

### Same trick for all four directions

While walking, the blockers behind us are always on the side we came from. So:

| Distance we want | How we walk |
|---|---|
| left | each row, left to right |
| right | each row, right to left |
| top | each column, top to bottom |
| bottom | each column, bottom to top |

One method `walkLine(r, c, dr, dc, need)` handles all four. It starts at one end of a row or column and keeps moving by `(dr, dc)` until it leaves the grid. `need` is the query value for that direction.

### Why a `matchCount` grid?

We don't really need to store the four distances. We only need to know whether all four match the query.

So we keep one 2D int array `matchCount`. During a walk, when a robot's distance equals `need`, we add 1 to its cell.

After all four walks, a robot with `matchCount == 4` matches the whole query. Only robots ever get counted, so empty cells and blockers stay at 0.

This is lighter and simpler than keeping four separate distance grids.

### Sorted output for free

The answer must be sorted by row, then by column. If we scan `matchCount` row by row, and each row from left to right, we meet the answers in exactly that order. So no sorting is needed.

### Java Code

The map is copied into a `char[][] grid`, so every walk can read a cell as `grid[r][c]` instead of `locationMap.get(r).charAt(c)`.

```java
import java.util.*;

public class NearestBlockers {

    // Shared by findRobots() and walkLine() during one call
    int rows, cols;
    char[][] grid;
    // matchCount[r][c] = how many of the 4 query distances the robot at (r, c) matches
    int[][] matchCount;

    public NearestBlockers() {
    }

    public List<String> findRobots(List<String> locationMap, List<Integer> query) {
        rows = locationMap.size();
        cols = locationMap.get(0).length();
        grid = new char[rows][];
        for (int r = 0; r < rows; r++) {
            grid[r] = locationMap.get(r).toCharArray();
        }
        matchCount = new int[rows][cols];

        int needLeft = query.get(0);
        int needTop = query.get(1);
        int needBottom = query.get(2);
        int needRight = query.get(3);

        // Rows: left to right gives left distances, right to left gives right distances
        for (int r = 0; r < rows; r++) {
            walkLine(r, 0, 0, 1, needLeft);
            walkLine(r, cols - 1, 0, -1, needRight);
        }
        // Columns: top to bottom gives top distances, bottom to top gives bottom distances
        for (int c = 0; c < cols; c++) {
            walkLine(0, c, 1, 0, needTop);
            walkLine(rows - 1, c, -1, 0, needBottom);
        }

        // Scanning row by row, then column by column, gives the required sorted order
        List<String> result = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (matchCount[r][c] == 4) {
                    result.add(r + "," + c);
                }
            }
        }
        return result;
    }

    /**
     * Walks one full row or column, starting at (r, c) and moving by (dr, dc) each step.
     * need = the query distance for this direction.
     * steps = distance from the nearest blocker behind us.
     * It starts at 0 because the boundary blocker sits just behind the first cell.
     */
    void walkLine(int r, int c, int dr, int dc, int need) {
        int steps = 0;
        while (r >= 0 && r < rows && c >= 0 && c < cols) {
            steps++;
            if (grid[r][c] == 'X') {
                steps = 0; // this X is now the nearest blocker behind us
            } else if (grid[r][c] == 'O' && steps == need) {
                matchCount[r][c]++; // one more direction matches the query
            }
            r += dr;
            c += dc;
        }
    }
}
```

### Complexity

- **Time:** `O(rows × cols)`. Each cell is visited 4 times by the walks and once more in the final scan.
- **Space:** `O(rows × cols)` for `grid` and `matchCount`.