# Warehouse Repair Areas in Java

#### Problem Statement
[https://codezym.com/question/400-warehouse-repair-areas](https://codezym.com/question/400-warehouse-repair-areas)

---

The floor is a grid, and every repair area is just a group of `X` tiles joined by their sides.


The whole solution is one simple habit: **count, then paint**. We scan the floor tile by tile. When we find a damaged tile we have never seen, it must be the start of a new repair area. We add 1 to our answer and then "paint" the full area as visited, so none of its tiles get counted again.


This painting step is called **flood fill**. We will first write it with recursion (DFS), see why it can crash on big floors, and then fix it by using a queue (BFS).

---

## Understanding the Problem

Two damaged tiles are in the same area only if they touch **up, down, left or right**.


Tiles that touch only at a corner are **not** connected. That is why Example 3 (`"X.X", "...", "X.X"`) has 4 areas, not 1.


Let us label the areas in Example 2 so the idea is easy to see:

```
Floor            Areas
X . . . X        A . . . B
X X . . X        A A . . B
. . . . .        . . . . .
. . X X .        . . C C .
. . . X .        . . . C .
```

Three groups: `A`, `B` and `C`. So the answer is `3`.


The space outside the floor counts as usable. In code, this simply means: if a neighbour is outside the grid, ignore it.

---

## Core Idea: Count and Paint

1. Go through every tile, row by row.
2. If the tile is `X` and not visited yet, we found a **new** repair area. Add 1 to the count.
3. Visit every damaged tile connected to it and mark them all as visited.
4. Keep scanning. Tiles of already counted areas are skipped because they are marked.


Every tile gets painted only once, so the work stays small.

### Data structures we need

- **`char[][] grid`**: a copy of the floor. We turn each row string into a char array so we can read any tile simply as `grid[row][col]`.
- **`boolean[][] visited`**: remembers which damaged tiles are already part of a counted area. We use a separate array because the input list may be read only (like `List.of(...)`), and changing input is not a good habit anyway.
- **`int[][] directions`**: the 4 moves (up, down, left, right). Looping over it saves us from writing 4 almost same `if` blocks.

---

## Solution 1: Recursive DFS (Simple, but Risky)

DFS means "go as deep as you can, then come back".


From a damaged tile, we mark it and then call the same method on its 4 neighbours. Each of those calls does the same thing, so the paint spreads through the whole area.


The method stops right away when the tile is:

- outside the floor
- usable (`.`)
- already visited

### Code

```java
import java.util.*;

public class WarehouseRepairAreas {

    // The 4 side neighbours of a tile: up, down, left, right
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    char[][] grid;
    boolean[][] visited;
    int rows, cols;

    public WarehouseRepairAreas() {
    }

    public int countRepairAreas(List<String> floorPlan) {
        rows = floorPlan.size();
        cols = floorPlan.get(0).length();
        grid = new char[rows][];
        visited = new boolean[rows][cols];

        int r = 0;
        for (String row : floorPlan) {
            grid[r++] = row.toCharArray();
        }

        int areas = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // A damaged tile not seen before starts a brand new repair area
                if (grid[i][j] == 'X' && !visited[i][j]) {
                    areas++;
                    markArea(i, j);
                }
            }
        }
        return areas;
    }

    /** Marks every damaged tile connected to (row, col) as visited. */
    void markArea(int row, int col) {
        // Stop at the floor edge, at usable tiles and at tiles already seen
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        if (grid[row][col] != 'X' || visited[row][col]) return;

        visited[row][col] = true;
        for (int[] d : directions) {
            markArea(row + d[0], col + d[1]);
        }
    }
}
```

### Problem with this solution

The logic is correct. The problem is **how deep the recursion can go**.


Every recursive call waits for its neighbour calls to finish. These waiting calls pile up on the call stack.


Picture a 300 x 300 floor where every tile is `X`. That is one area with 90,000 tiles. The DFS can snake through the whole floor, so up to 90,000 calls may be waiting at the same time.


Java gives each thread only a small stack. On large floors like this, the program crashes with a `StackOverflowError`.


So we need a way to spread the paint **without** deep recursion.

**Complexity:** Time `O(rows * cols)`, Space `O(rows * cols)`, but the call stack can overflow on big floors.

---

## Solution 2: BFS with a Queue (Final Solution)

The fix is simple: instead of letting Java keep track of "tiles still to process" on the call stack, we keep them in **our own queue**.


A queue lives in normal heap memory, which is much bigger than the call stack. So even a 90,000 tile area is no problem.


BFS spreads the paint layer by layer, like water spreading out from the first tile.

### How `markArea` works now

1. Mark the starting tile as visited and put it in the queue.
2. While the queue is not empty:
   - take a tile out of the queue
   - look at its 4 neighbours
   - if a neighbour is inside the floor, damaged and not visited, mark it visited and add it to the queue

### Why mark a tile when adding it, not when removing it?

A tile can be a neighbour of several tiles in the queue.


If we waited until removal to mark it, two different tiles could both add it, and it would be processed twice. Marking it right when it is added makes sure every tile enters the queue **only once**.

### Why a queue?

For counting areas, the order of visiting does not really matter. A stack would also work.


We use a queue (`ArrayDeque`) because it is the classic choice for BFS and is simple to read. `add()` puts a tile at the back and `poll()` takes one from the front.

### Dry run on Example 2

- Scan reaches `(0,0)`: new area, count = 1. BFS paints `(0,0)`, `(1,0)`, `(1,1)`.
- Scan reaches `(0,4)`: new area, count = 2. BFS paints `(0,4)`, `(1,4)`.
- Scan reaches `(3,2)`: new area, count = 3. BFS paints `(3,2)`, `(3,3)`, `(4,3)`.
- Every other `X` is already painted, so it is skipped.


Final answer: `3`.

### Code

```java
import java.util.*;

public class WarehouseRepairAreas {

    // The 4 side neighbours of a tile: up, down, left, right
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    char[][] grid;
    boolean[][] visited;
    int rows, cols;

    public WarehouseRepairAreas() {
    }

    public int countRepairAreas(List<String> floorPlan) {
        rows = floorPlan.size();
        cols = floorPlan.get(0).length();
        grid = new char[rows][];
        visited = new boolean[rows][cols];

        int r = 0;
        for (String row : floorPlan) {
            grid[r++] = row.toCharArray();
        }

        int areas = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // A damaged tile not seen before starts a brand new repair area
                if (grid[i][j] == 'X' && !visited[i][j]) {
                    areas++;
                    markArea(i, j);
                }
            }
        }
        return areas;
    }

    /** Uses a queue to mark every damaged tile connected to (startRow, startCol). */
    void markArea(int startRow, int startCol) {
        Queue<int[]> queue = new ArrayDeque<>();
        visited[startRow][startCol] = true;
        queue.add(new int[]{startRow, startCol});

        while (!queue.isEmpty()) {
            int[] tile = queue.poll();

            for (int[] d : directions) {
                int nextRow = tile[0] + d[0];
                int nextCol = tile[1] + d[1];

                if (isNewDamagedTile(nextRow, nextCol)) {
                    // Mark now, so the same tile is never added to the queue twice
                    visited[nextRow][nextCol] = true;
                    queue.add(new int[]{nextRow, nextCol});
                }
            }
        }
    }

    /** True if the tile is inside the floor, damaged, and not visited yet. */
    boolean isNewDamagedTile(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return false;
        return grid[row][col] == 'X' && !visited[row][col];
    }
}
```

`grid`, `visited`, `rows` and `cols` are stored as fields so the helper methods stay short. They are set again at the start of every call, so the same object can be reused safely.

---

## Complexity

Let `R` be the number of rows and `C` the number of columns.


- **Time:** `O(R * C)`. Each tile is scanned once, enters the queue at most once, and checks only 4 neighbours.
- **Space:** `O(R * C)` for the `visited` array, the grid copy and the queue.


For the biggest floor (300 x 300 = 90,000 tiles), this runs very fast.

| | Recursive DFS | BFS with Queue |
|---|---|---|
| Time | `O(R * C)` | `O(R * C)` |
| Space | `O(R * C)` | `O(R * C)` |
| Safe on a 300 x 300 floor | No, can overflow the stack | Yes |

---

## Key Takeaways

- Counting groups in a grid is a **count and paint** problem: count a new group, then paint all of it.
- A `visited` array makes sure every tile is counted only once.
- Recursive DFS is the quickest to write, but it can run out of stack on big grids.
- BFS with a queue does the same job safely, because the queue lives in heap memory.
- Mark a tile as visited **when you add it** to the queue, not when you remove it.