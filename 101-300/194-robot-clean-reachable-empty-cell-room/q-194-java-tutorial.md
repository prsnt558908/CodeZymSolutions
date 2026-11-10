# Use Robot To Clean Reachable Empty Cells in Room in Java

#### Problem Statement
[https://codezym.com/question/194-robot-clean-reachable-empty-cell-room](https://codezym.com/question/194-robot-clean-reachable-empty-cell-room)


## Core Idea

The robot can clean a cell only if it can walk there through empty cells. So we do not need to plan the robot's route at all. We just need to count **how many empty cells are connected to the starting cell**, including the starting cell itself.

This is the classic **flood fill** problem. Imagine pouring water on the starting cell. The water flows into every empty neighbour, then into their neighbours, and keeps going until walls stop it. Every wet cell is a cell the robot cleans.

We will first write the most natural version using recursion (DFS). Then we will see why deep recursion is risky here, and fix it with a queue (BFS).


## Building Blocks

Both approaches use the same three things:

- **`int[][] grid`**: we split every row string like `"0,1,1,0"` by commas and store the numbers here. Now `grid[r][c]` tells us instantly whether a cell is a wall (`0`) or empty (`1`).
- **`boolean[][] cleaned`**: remembers which cells are already cleaned. Without it, the robot would walk back and forth between two cells forever and count them again and again.
- **`moves`**: the four moves up, right, down and left. With this array we can loop over the directions instead of writing four separate `if` blocks.

```java
// up, right, down, left
int[][] moves = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
```

We also use a tiny helper `canClean()`. It returns `true` only if the cell is inside the room, is empty and is not cleaned yet.


## Simple Approach: Recursive DFS

DFS (Depth First Search) cleans the room the way a real robot would:

1. Clean the current cell.
2. Walk into a neighbour and clean everything reachable from there.
3. Walk back and try the next neighbour.

```java
// returns how many cells get cleaned starting from (row, col)
int cleanFrom(int[][] grid, boolean[][] cleaned, int row, int col) {
    if (!canClean(grid, cleaned, row, col)) {
        return 0; // outside the room, a wall, or already cleaned
    }
    cleaned[row][col] = true;
    int count = 1; // the current cell

    for (int[] move : moves) {
        count += cleanFrom(grid, cleaned, row + move[0], col + move[1]);
    }
    return count;
}
```

`cleanRoom()` would simply parse the grid and return `cleanFrom(grid, cleaned, startRow, startCol)`.


### Problem With This Approach

The logic is correct and the time is fine. The problem is **how deep the recursion goes**.

Every call waits until its neighbour's call finishes. If the robot keeps walking forward without coming back, these waiting calls pile up.

In the worst case, the pile is as big as the number of reachable cells, which can be close to 20,000 here.

You do not even need a tricky room for this. Take a fully open `100 x 200` room and start in a corner. DFS walks through all 19,404 empty cells in one long zig-zag line, so the recursion goes 19,404 calls deep.

Java's call stack is limited. With default JVM settings, the program crashes with a `StackOverflowError` long before that depth.


## Better Approach: BFS With a Queue

The fix is simple. Instead of letting recursion remember which cells still need to be explored, we keep them ourselves in a queue.

1. Clean the start cell and add it to the queue.
2. Take a cell out of the queue.
3. Check its four neighbours. Every neighbour that can be cleaned is cleaned, counted and added to the queue.
4. Repeat until the queue is empty.

This is BFS (Breadth First Search). The cleaning spreads in layers: first the start cell, then cells 1 step away, then cells 2 steps away, and so on. Just like the water.

**Why a queue:** it holds the cells that are cleaned but whose neighbours are not checked yet. Each entry is a `{row, col}` pair. We use `ArrayDeque`, which is a fast and simple queue.

The queue is stored in the heap, which is much bigger than the call stack. Holding 20,000 cells there is no problem.

A stack would also work in place of the queue. The cleaning order changes, but the final count stays the same.


### Mark a Cell When You Add It

We mark a cell as cleaned **at the moment we add it to the queue**, not when we take it out.

If we waited, two different neighbours could both add the same cell before it is taken out. That cell would enter the queue twice, which wastes work and can lead to counting it twice.

Marking early makes sure every cell enters the queue exactly once.


### Complexity

- **Time:** `O(m * n)`. Parsing reads every cell once. BFS takes each reachable cell out of the queue once and checks its 4 neighbours.
- **Space:** `O(m * n)` for `grid`, `cleaned` and the queue.


## Java Code

```java
import java.util.*;

public class RoomCleaner {

    // the four moves the robot can make: up, right, down, left
    int[][] moves = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};

    public RoomCleaner() {
    }

    /**
     * Counts the empty cells the robot can reach from (startRow, startCol).
     * Uses BFS with a queue, so there is no deep recursion.
     */
    public int cleanRoom(List<String> room, int startRow, int startCol) {
        int[][] grid = parseRoom(room);

        // cleaned[r][c] becomes true once the robot cleans cell (r, c)
        boolean[][] cleaned = new boolean[grid.length][grid[0].length];

        // safety check, the robot must start on an empty cell
        if (!canClean(grid, cleaned, startRow, startCol)) {
            return 0;
        }

        // cells that are cleaned, but whose neighbours are not checked yet
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startRow, startCol});
        cleaned[startRow][startCol] = true;
        int count = 1;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();

            for (int[] move : moves) {
                int nextRow = cell[0] + move[0];
                int nextCol = cell[1] + move[1];

                if (canClean(grid, cleaned, nextRow, nextCol)) {
                    // mark it now, so it is never added to the queue twice
                    cleaned[nextRow][nextCol] = true;
                    count++;
                    queue.add(new int[]{nextRow, nextCol});
                }
            }
        }
        return count;
    }

    // a cell can be cleaned only if it is inside the room, empty and not cleaned yet
    boolean canClean(int[][] grid, boolean[][] cleaned, int row, int col) {
        if (row < 0 || row >= grid.length || col < 0 || col >= grid[row].length) {
            return false;
        }
        return grid[row][col] == 1 && !cleaned[row][col];
    }

    // turns rows like "0,1,1,0" into a 2D int grid
    int[][] parseRoom(List<String> room) {
        int[][] grid = new int[room.size()][];

        for (int r = 0; r < room.size(); r++) {
            String[] values = room.get(r).split(",");
            grid[r] = new int[values.length];

            for (int c = 0; c < values.length; c++) {
                grid[r][c] = Integer.parseInt(values[c].trim());
            }
        }
        return grid;
    }
}
```