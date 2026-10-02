# Find Closest DashMart Distance in Java

#### Problem Statement
[https://codezym.com/question/100-find-closest-dashmart-distance](https://codezym.com/question/100-find-closest-dashmart-distance)

This is a shortest path problem on a grid where every move costs exactly one step. BFS (Breadth First Search) is the natural tool for this, because it visits cells in order of their distance.

The main trick is to flip the question. Instead of running a separate BFS from every location to look for a DashMart, we run one BFS that starts from all DashMarts at the same time. This single pass gives every cell its distance to the closest DashMart, so each query becomes a simple lookup.

For the follow-up, the same BFS also remembers which DashMart reached each cell. Then we just count customers per DashMart and pick the winner.

## Reading the Input

- Each city row looks like `"X|.|.|D"`. We split it on `|` to get the cells. In Java, `split` takes a regex where `|` means "or", so we must write `split("\\|")`.
- Each location is a string such as `"1,4"`. We replace everything except digits and minus signs with spaces, so formats like `"1,4"`, `"[1, 4]"` or `"-1,5"` all give us the two numbers.
- Every cell except `X` can be walked on. That includes `C` and `D` cells.

## Approach 1: BFS From Every Location (Brute Force)

The most direct idea is to run a BFS from each location and stop at the first DashMart we meet. BFS spreads out one step at a time, so the first DashMart it meets is the closest one.

```java
/** Brute force: BFS from one location until we meet the first DashMart. */
int bfsFromLocation(int startRow, int startCol) {
    if (!isInsideCity(startRow, startCol) || grid[startRow][startCol] == 'X') {
        return -1;
    }
    boolean[][] visited = new boolean[rows][cols];
    Queue<int[]> queue = new ArrayDeque<>();
    queue.add(new int[]{startRow, startCol, 0});   // {row, col, steps}
    visited[startRow][startCol] = true;

    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        if (grid[cell[0]][cell[1]] == 'D') {
            return cell[2];   // BFS meets the closest DashMart first
        }
        for (int[] d : directions) {
            int nr = cell[0] + d[0], nc = cell[1] + d[1];
            if (isInsideCity(nr, nc) && grid[nr][nc] != 'X' && !visited[nr][nc]) {
                visited[nr][nc] = true;
                queue.add(new int[]{nr, nc, cell[2] + 1});
            }
        }
    }
    return -1;   // no DashMart can be reached
}
```

The answer for each location is simply `bfsFromLocation(row, col)`.

### Why it is slow

A city can have 200 × 200 = 40,000 cells and there can be 100,000 locations. A single BFS may visit all 40,000 cells, so the total work can reach 100,000 × 40,000 = 4 billion steps.

The follow-up has the same problem, because we would need one BFS for every customer.

Notice the waste: every BFS walks over the same roads again and again, looking for the same DashMarts.

## Approach 2: One BFS From All DashMarts (Multi-Source BFS)

### Flip the question

Instead of asking "which DashMart is closest to this location?" again and again, let the DashMarts do the searching.

Picture every DashMart sending out delivery riders at the same moment. In each round, the riders spread one more cell up, down, left and right, but never into a blocked road.

The first rider to reach a cell must have come from the closest DashMart. The round in which it arrives is the distance.

This is a normal BFS where the queue starts with all DashMarts instead of a single cell. It is called a multi-source BFS.

### Steps

1. Add every DashMart to the queue with distance 0.
2. Take a cell out of the queue. For each open neighbor that was never reached, set its distance to current distance + 1 and add it to the queue.
3. Repeat until the queue is empty.

The queue is first in, first out. So BFS finishes all cells at distance 1 before any cell at distance 2, and so on. That is why the first time we reach a cell, we already have its shortest distance, and it never needs an update.

Cells that are never reached keep `-1`. These are blocked roads and cells that are cut off from every DashMart.

Here is the distance map that BFS builds for Example 1. The `0` cells are the DashMarts.

```
      col 0  1  2  3  4  5  6  7  8
row 0     X  2  1  0  1  2  X  6  X
row 1     X  3  X  X  2  3  4  5  X
row 2     3  2  1  0  X  X  5  X -1
row 3     3  2  1  0  X  X  6  X -1
row 4     4  3  2  1  2  X  7  8  X
row 5     5  4  3  2  X  9  8  X  X
```

The answers can be read straight from it: `[1, 4]` is 2, `[0, 7]` is 6 and `[5, 5]` is 9. Cells `[2, 8]` and `[3, 8]` are boxed in by blocked roads and the city edge, so they stay `-1`.

### Answering each location

- If the location is outside the city, the answer is `-1`.
- Otherwise the answer is `distance[row][col]`. It is already `0` for a DashMart and `-1` for blocked or unreachable cells, so no extra checks are needed.

## Follow-up: DashMart Serving Maximum Customers

### Remember which DashMart reached each cell

Along with the distance, every cell also stores an **owner**: the DashMart whose rider reached it. When a rider steps into a new cell, it passes its owner along.

So when BFS ends, the owner of a customer cell is the DashMart that serves this customer.

### Breaking ties

Riders from two DashMarts can reach the same cell in the same round. The rule says the DashMart with the smaller `(row, col)` wins.

To turn this into an easy number comparison, we collect DashMarts into a list while scanning the grid row by row, left to right. This list is automatically sorted by `(row, col)`. We store the owner as an index into this list, so a smaller index means a smaller location.

During BFS, if we step into a cell that another rider already reached in the same round, we keep the smaller owner. In code, this is a neighbor whose distance is already equal to our distance + 1.

Is it safe to change the owner of a cell that is already in the queue? Yes. BFS finishes every cell of one round before it starts the next round. So the owner of a cell is final before that cell passes it on to its neighbors.

### Counting and picking the winner

1. For every customer cell that has an owner, add 1 to `servedCount[owner]`.
2. Walk over the DashMarts in list order and remember the one with the biggest count. Replace the best only when a count is strictly bigger.

This small loop covers every special rule:

- **Same count:** the earlier DashMart in the list (the smaller location) stays the best.
- **No customer served:** all counts are 0, so the first DashMart, which is the smallest location, is returned.
- **Customer cannot reach any DashMart:** its owner stays `-1`, so it is skipped.
- **No DashMart in the city:** we return `[-1, -1]` before counting.

### Example 2 walkthrough

| Customer | Steps to [0, 0] | Steps to [2, 2] | Served by |
|---|---|---|---|
| [0, 2] | 2 | 2 | [0, 0], tie goes to the smaller location |
| [0, 4] | 6 | 4 | [2, 2] |
| [2, 0] | 2 | 2 | [0, 0], tie goes to the smaller location |
| [2, 4] | 6 | 2 | [2, 2] |
| [3, 4] | 7 | 3 | [2, 2] |

`[0, 0]` serves 2 customers and `[2, 2]` serves 3, so the answer is `[2, 2]`.

## Key Data Structures

| Data structure | Why we need it |
|---|---|
| `Queue<int[]> queue` | First in, first out order makes BFS move round by round, so the first visit to a cell always has the shortest distance. |
| `int[][] distance` | Distance to the closest DashMart for every cell. `-1` means "not reached", so it also works as the visited marker. |
| `List<int[]> dashMarts` | All DashMarts sorted by `(row, col)`. The index works as a short id, and comparing ids is the same as comparing locations. |
| `int[][] owner` | The DashMart that serves each cell, stored as an index into `dashMarts`. |
| `int[] servedCount` | How many customers each DashMart serves. |

## Complexity

Let `R` be the number of rows, `C` the number of columns and `Q` the number of locations.

| | Brute force | Multi-source BFS |
|---|---|---|
| Part 1 time | O(Q × R × C) | O(R × C + Q) |
| Follow-up time | O((R × C)²) | O(R × C) |
| Extra space | O(R × C) | O(R × C) |

Each cell enters the queue at most once and checks 4 neighbors, so one BFS costs O(R × C).

## Java Code

```java
import java.util.*;

public class DashMartPlanner {

    // The four moves we are allowed to make: up, down, left, right
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    char[][] grid;   // the city as a 2-D grid of cells
    int rows, cols;

    // All DashMarts, found row by row and left to right.
    // So a smaller index always means a smaller (row, col) location.
    List<int[]> dashMarts;

    // distance[r][c] = steps from cell (r, c) to its closest DashMart, -1 if not reachable
    int[][] distance;

    // owner[r][c] = index in dashMarts of the DashMart that serves cell (r, c), -1 if none
    int[][] owner;

    public DashMartPlanner() {
    }

    public List<Integer> getClosestDashMartDistances(List<String> city, List<String> locations) {
        parseCity(city);
        runBfsFromAllDashMarts();

        List<Integer> result = new ArrayList<>();
        for (String location : locations) {
            int[] cell = parseLocation(location);
            int r = cell[0], c = cell[1];
            if (isInsideCity(r, c)) {
                // blocked and unreachable cells already hold -1
                result.add(distance[r][c]);
            } else {
                result.add(-1);
            }
        }
        return result;
    }

    public List<Integer> findDashMartServingMaximumCustomers(List<String> city) {
        parseCity(city);
        runBfsFromAllDashMarts();

        if (dashMarts.isEmpty()) {
            return List.of(-1, -1);
        }

        // servedCount[i] = number of customers served by dashMarts.get(i)
        int[] servedCount = new int[dashMarts.size()];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == 'C' && owner[r][c] != -1) {
                    servedCount[owner[r][c]]++;
                }
            }
        }

        // Only a strictly bigger count replaces the current best,
        // so on a tie the smaller location (smaller index) stays.
        // If no customer is served, every count is 0 and index 0 wins.
        int best = 0;
        for (int i = 1; i < dashMarts.size(); i++) {
            if (servedCount[i] > servedCount[best]) {
                best = i;
            }
        }
        int[] bestDashMart = dashMarts.get(best);
        return List.of(bestDashMart[0], bestDashMart[1]);
    }

    /** Converts rows like "X|.|D" into a 2-D grid of cells. */
    void parseCity(List<String> city) {
        rows = city.size();
        cols = (rows == 0) ? 0 : city.get(0).split("\\|").length;
        grid = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            // "|" is a special character in regex, so it must be escaped
            String[] cells = city.get(r).split("\\|");
            for (int c = 0; c < cols; c++) {
                grid[r][c] = cells[c].trim().charAt(0);
            }
        }
    }

    /**
     * One BFS that starts from all DashMarts at the same time.
     * Fills distance[][] and owner[][] for every cell of the city.
     */
    void runBfsFromAllDashMarts() {
        distance = new int[rows][cols];
        owner = new int[rows][cols];
        dashMarts = new ArrayList<>();
        Queue<int[]> queue = new ArrayDeque<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                distance[r][c] = -1;
                owner[r][c] = -1;
                if (grid[r][c] == 'D') {
                    // every DashMart is a starting point at distance 0 and serves itself
                    distance[r][c] = 0;
                    owner[r][c] = dashMarts.size();
                    dashMarts.add(new int[]{r, c});
                    queue.add(new int[]{r, c});
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int r = cell[0], c = cell[1];
            for (int[] d : directions) {
                int nr = r + d[0], nc = c + d[1];
                if (!isInsideCity(nr, nc) || grid[nr][nc] == 'X') {
                    continue;
                }
                if (distance[nr][nc] == -1) {
                    // first time we reach this cell, so this is its shortest distance
                    distance[nr][nc] = distance[r][c] + 1;
                    owner[nr][nc] = owner[r][c];
                    queue.add(new int[]{nr, nc});
                } else if (distance[nr][nc] == distance[r][c] + 1 && owner[r][c] < owner[nr][nc]) {
                    // reached again in the same round: the smaller DashMart wins the tie
                    owner[nr][nc] = owner[r][c];
                }
            }
        }
    }

    boolean isInsideCity(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    /** Reads a location like "1,4" or "[1, 4]" and returns {row, col}. */
    int[] parseLocation(String location) {
        String[] parts = location.replaceAll("[^0-9-]+", " ").trim().split(" ");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }
}
```