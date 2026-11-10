# Improve Fire Station Coverage in Java

#### Problem Statement
[https://codezym.com/question/433-improve-fire-station-coverage](https://codezym.com/question/433-improve-fire-station-coverage)


## Core Idea

A fire truck can move in 8 directions, like a king in chess. So in `limit` minutes, a station reaches every cell of a square around it.


We solve the problem in two steps. First, one multi source BFS finds how far every neighborhood is from its nearest station. Then we turn the question around. Instead of asking "where should we build?", we ask "can the response time be at most `limit`?". All neighborhoods that are still farther than `limit` must fit inside the square of the one new station.


We start with a brute force idea and see why it is slow. Then we find the smallest working `limit` in two ways:

1. **BFS + Binary Search:** guess a `limit` and check it.
2. **BFS + One Sweep:** lower the `limit` step by step and stop at the first one that fails.


## Reading the Input

Each row comes as a string like `"100"`. Character `c` of row `r` is `'1'` when neighborhood `(r, c)` has a fire station, and `'0'` when it does not. We read it directly with `charAt(c)`, so no parsing step is needed.


## Travel Time Is a Square

Travel time is the bigger of the row difference and the column difference. Diagonal moves are allowed, so going 3 rows down and 2 columns right takes only 3 minutes.


So the cells a station reaches within `limit` minutes form a square of side `2 × limit + 1`, with the station in the middle. For `limit = 1`:

```
. . . . .
. x x x .
. x S x .
. x x x .
. . . . .
```


Keep this square in mind. The whole solution is built on it.


## Brute Force: Try Every Empty Cell

Try building nothing, and also try building the new station in every empty cell. For each choice, `responseTime(grid)` runs one BFS from all stations (Step 1 shows how) and returns the biggest distance. Keep the smallest result.


Here `grid` is a 2D `int` copy of the town, so we can change a cell and undo it.

```java
// Build nothing (allowed only if the town already has a station)
int best = hasStation ? responseTime(grid) : Integer.MAX_VALUE;

// Or build the new station in each empty cell, one by one
for (int r = 0; r < rows; r++) {
    for (int c = 0; c < cols; c++) {
        if (grid[r][c] == 0) {
            grid[r][c] = 1;                              // build here
            best = Math.min(best, responseTime(grid));   // BFS over the whole town
            grid[r][c] = 0;                              // undo
        }
    }
}
return best;
```


**Problem:** a 500 × 500 town has 250,000 cells. That is up to 250,000 choices, and every BFS visits all 250,000 cells again. About 62 billion steps is far too slow.


## Step 1: Distance to the Nearest Station (Multi Source BFS)

Let `dist[r][c]` be the minutes from cell `(r, c)` to its nearest fire station.


We put **all** stations into the queue at the start, each with distance 0. Then we run a normal BFS with the 8 moves. A cell gets its distance from the first station that reaches it, and that is always its nearest one.


There are no walls, so the number of BFS steps between two cells is exactly their travel time. For Example 1:

```
townMap      dist
1 0 0        0 1 2
0 0 0        1 1 2
0 0 0        2 2 2
```


Every cell starts with `notReached = rows + cols`, a value bigger than any real distance. If the town has no station, BFS does nothing and every cell keeps this value. So every cell counts as far (see Step 2), and the code is forced to build the one station the rules ask for. No special case is needed.


## Step 2: Turn the Question Around

Pick a number `limit` and ask: can the response time be at most `limit`?


Cells with `dist <= limit` are already fine. Let us call the others **far cells**. The new station alone must reach all of them.


Draw the smallest box around all far cells. Put the new station in the middle of the box. It reaches the whole box when the box is at most `2 × limit + 1` cells tall and wide. In code:

```java
bottom - top <= 2 * limit && right - left <= 2 * limit
```


If the box is taller than that, no single station can reach both the top-most and the bottom-most far cell. The same is true for the width. So this one check gives the exact answer.


**Is the middle cell empty?** Yes. It is within `limit` minutes of every far cell. If it had a station, those cells would not be far. So we never have to check it.


In Example 1 with `limit = 1`, the far cells are the 2s. Their box is the whole 3 × 3 town, and a 3 × 3 square covers it. Building at the middle `(1, 1)` works.


With `limit = 0`, every cell except `(0, 0)` is far. The box is still 3 × 3, but the square is only 1 × 1. So `limit = 0` fails, and the answer is 1.


## Key Observation

If a `limit` works, every bigger `limit` works too. A bigger limit means fewer far cells and a bigger square.


For Example 2 (a 5 × 5 town with no station):

| limit | 0 | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- | --- |
| Works? | No | No | Yes | Yes | Yes | Yes |


All the "No" answers come first, then all the "Yes" answers. We only need the first "Yes".


## Solution 1: BFS + Binary Search

### How it works

1. Run the BFS once to fill `dist`.
2. Binary search `limit` between 0 and `max(rows, cols)`. The answer is never bigger, because any single station reaches the whole town in less time than that.
3. `canAchieve(mid)` scans `dist` once, builds the box around the far cells and checks its size.


- If `mid` works, the answer is `mid` or smaller.
- If it fails, the answer is bigger than `mid`.


### Why a `dist` array and a queue?

`dist` is filled once and reused by every check. Without it, each check would need its own BFS.


The queue (`ArrayDeque`) gives back cells in the order BFS reached them, so closer cells always come out first. Each entry is a small array `{row, col}`, so we do not need an extra class.


### Complexity

- **Time:** O(rows × cols × log(max(rows, cols))). BFS runs once, and binary search makes about 10 checks of one grid scan each.
- **Space:** O(rows × cols) for `dist` and the queue.


### Code

```java
import java.util.*;

public class ImproveFireStationCoverage {

    // The 8 moves: up, down, left, right and the 4 diagonals
    int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    int rows, cols;
    int[][] dist; // dist[r][c] = minutes from (r, c) to its nearest fire station

    public ImproveFireStationCoverage() {
    }

    public int minimumFireResponseTime(List<String> townMap) {
        rows = townMap.size();
        cols = townMap.get(0).length();
        findDistances(townMap);

        // The answer is never more than the longer side of the town
        int low = 0, high = Math.max(rows, cols);

        // Find the smallest limit that we can reach with one new station
        while (low < high) {
            int mid = (low + high) / 2;
            if (canAchieve(mid)) {
                high = mid;      // mid works, look for a smaller one
            } else {
                low = mid + 1;   // mid is too small
            }
        }
        return low;
    }

    /**
     * Multi source BFS. All fire stations start in the queue together,
     * so every cell is first reached by its nearest station.
     */
    void findDistances(List<String> townMap) {
        int notReached = rows + cols; // bigger than any real distance
        dist = new int[rows][cols];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (townMap.get(r).charAt(c) == '1') {
                    dist[r][c] = 0; // a station is 0 minutes from itself
                    queue.add(new int[]{r, c});
                } else {
                    dist[r][c] = notReached;
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int row = cell[0], col = cell[1];

            for (int[] dir : directions) {
                int nextRow = row + dir[0], nextCol = col + dir[1];

                if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= cols) {
                    continue; // outside the town
                }
                if (dist[nextRow][nextCol] != notReached) {
                    continue; // already has its distance
                }
                dist[nextRow][nextCol] = dist[row][col] + 1;
                queue.add(new int[]{nextRow, nextCol});
            }
        }
    }

    /**
     * Can the response time be at most limit, if we build at most one new station?
     */
    boolean canAchieve(int limit) {
        // Box around all far cells (cells with dist > limit)
        int top = rows, bottom = -1, left = cols, right = -1;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (dist[r][c] > limit) {
                    top = Math.min(top, r);
                    bottom = Math.max(bottom, r);
                    left = Math.min(left, c);
                    right = Math.max(right, c);
                }
            }
        }

        if (bottom == -1) {
            return true; // no far cells, the old stations are enough
        }

        // A new station in the middle of the box reaches all far cells
        // only if the box is at most (2 * limit + 1) cells tall and wide
        return bottom - top <= 2 * limit && right - left <= 2 * limit;
    }
}
```


## Solution 2: BFS + One Sweep

Binary search still scans the whole grid about 10 times. We can do the same work with a single scan.


### How it works

Start with `limit = maxDist`, the biggest value in `dist`. No cell is far, so it works. Then lower `limit` one step at a time.


When `limit` goes down by one, only the cells with `dist = limit + 1` become far. So the far box only grows, one group of cells at a time.


To grow it quickly, we scan the grid once and store a small box for every distance `d`. `top[d]`, `bottom[d]`, `left[d]` and `right[d]` give the box around all cells with `dist = d`. Adding a whole group to the far box is then just 4 `min` / `max` calls.


We stop at the first `limit` that fails. By the Key Observation, every smaller limit fails too, so the last working `limit` is the answer.


For Example 1 (`maxDist = 2`):

| limit | Cells that become far | Far box | Square | Works? |
| --- | --- | --- | --- | --- |
| 2 | none | empty | 5 × 5 | Yes |
| 1 | `dist = 2` | 3 × 3 | 3 × 3 | Yes |
| 0 | `dist = 1` | 3 × 3 | 1 × 1 | No, stop |


The last working limit is 1, so the answer is 1.


### Why four box arrays?

`top`, `bottom`, `left` and `right` have one entry per distance value. With them, each step adds a whole group of cells in O(1) time, instead of scanning the grid again.


### Complexity

- **Time:** O(rows × cols). One BFS, one scan to fill the box arrays, and at most `maxDist` cheap steps.
- **Space:** O(rows × cols) for `dist` and the queue. The box arrays are small, with `maxDist + 1` entries each.


### Code

```java
import java.util.*;

public class ImproveFireStationCoverage {

    // The 8 moves: up, down, left, right and the 4 diagonals
    int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    int rows, cols;
    int[][] dist; // dist[r][c] = minutes from (r, c) to its nearest fire station

    // Box around the cells at distance exactly d:
    // rows from top[d] to bottom[d], columns from left[d] to right[d]
    int[] top, bottom, left, right;

    public ImproveFireStationCoverage() {
    }

    public int minimumFireResponseTime(List<String> townMap) {
        rows = townMap.size();
        cols = townMap.get(0).length();
        findDistances(townMap);

        int maxDist = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                maxDist = Math.max(maxDist, dist[r][c]);
            }
        }
        groupByDistance(maxDist);

        // Box around all far cells. With limit = maxDist no cell is far, so it starts empty.
        int farTop = rows, farBottom = -1, farLeft = cols, farRight = -1;
        int answer = maxDist;

        // Try smaller and smaller limits until one fails
        for (int limit = maxDist - 1; limit >= 0; limit--) {
            // Cells at distance limit + 1 are now far, so grow the box to hold them
            int d = limit + 1;
            farTop = Math.min(farTop, top[d]);
            farBottom = Math.max(farBottom, bottom[d]);
            farLeft = Math.min(farLeft, left[d]);
            farRight = Math.max(farRight, right[d]);

            // The far cells do not fit in one square anymore.
            // Smaller limits only add more far cells, so they fail too.
            if (farBottom - farTop > 2 * limit || farRight - farLeft > 2 * limit) {
                break;
            }
            answer = limit;
        }
        return answer;
    }

    /**
     * Multi source BFS. All fire stations start in the queue together,
     * so every cell is first reached by its nearest station.
     */
    void findDistances(List<String> townMap) {
        int notReached = rows + cols; // bigger than any real distance
        dist = new int[rows][cols];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (townMap.get(r).charAt(c) == '1') {
                    dist[r][c] = 0; // a station is 0 minutes from itself
                    queue.add(new int[]{r, c});
                } else {
                    dist[r][c] = notReached;
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int row = cell[0], col = cell[1];

            for (int[] dir : directions) {
                int nextRow = row + dir[0], nextCol = col + dir[1];

                if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= cols) {
                    continue; // outside the town
                }
                if (dist[nextRow][nextCol] != notReached) {
                    continue; // already has its distance
                }
                dist[nextRow][nextCol] = dist[row][col] + 1;
                queue.add(new int[]{nextRow, nextCol});
            }
        }
    }

    // Fills top, bottom, left and right for every distance from 0 to maxDist
    void groupByDistance(int maxDist) {
        top = new int[maxDist + 1];
        bottom = new int[maxDist + 1];
        left = new int[maxDist + 1];
        right = new int[maxDist + 1];

        // Every box starts empty
        Arrays.fill(top, rows);
        Arrays.fill(bottom, -1);
        Arrays.fill(left, cols);
        Arrays.fill(right, -1);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int d = dist[r][c];
                top[d] = Math.min(top[d], r);
                bottom[d] = Math.max(bottom[d], r);
                left[d] = Math.min(left[d], c);
                right[d] = Math.max(right[d], c);
            }
        }
    }
}
```