# Return a Cleaning Robot to Its Charger in Java

#### Problem Statement
[https://codezym.com/question/479-cleaning-robot-charger](https://codezym.com/question/479-cleaning-robot-charger)

---

The robot can only "make a choice" at the cells where it stops. The cells it slides over in between do not matter at all.

So let us think of every stopping cell as a spot on a map. From each spot, the 4 commands (up, down, left, right) take the robot to at most 4 other spots.

Now the question becomes very simple: **starting from the robot's spot, can we reach the charger's spot?**

We answer it with a Breadth First Search (BFS): a queue of spots to explore and a `visited` grid so we never explore the same spot twice.

We will first build a simple BFS that slides the robot one cell at a time. Then we will make it faster by working out every stopping cell in advance.

---

## Understanding the Problem

The floor is a grid. `0` is a free cell and `1` is furniture.

Once the robot starts moving, it keeps going until the next cell is furniture or outside the floor. Only then does it stop.

The charger does not stop the robot. The robot must **stop** on it. Sliding over it does not count.

Look at Example 2. `R` is the robot and `C` is the charger.

```
0   0   C   0   R
```

Moving left, the robot slides over `C` and stops at the far left end. Moving right from there, it slides back to the far right end. It can only ever stop at the two ends, so the answer is `false`.

---

## A First Thought: Try Every Command Sequence

The most direct idea is to try every possible list of commands: one command, then two, then three, and so on.

This breaks quickly.

The robot can go left, right, left, right forever between the same two cells. So this search never ends on its own.

Also, the number of command lists grows 4 times with every extra command. That is way too many.

The fix is easy: **remember every cell where the robot has already stopped.** If a command leads to one of those cells, there is nothing new to learn there, so we skip it.

That small fix turns the idea into a proper BFS.

---

## Solution 1: BFS With Step by Step Sliding

### Idea

1. Put the robot's starting cell in a queue and mark it as visited.
2. Take a cell out of the queue. If it is the charger, return `true`.
3. Otherwise, try all 4 commands. For each one, slide the robot cell by cell until the next step is blocked or off the floor.
4. If the cell where it stops is new, mark it visited and add it to the queue.
5. If the queue becomes empty, the charger can never be a stopping cell. Return `false`.

### Why these data structures?

**`Queue<int[]>`**: holds the stopping cells we still need to explore. It gives them back in the same order we added them, which is exactly how BFS works. Each cell is just a pair `{row, column}`, so a small `int[]` is enough. No extra class is needed.

**`boolean[][] visited`**: one `true/false` per cell. It tells us in a single lookup whether the robot has already stopped there. This is what stops the endless left, right, left, right loop.

**`boolean[][] blocked`**: the floor plan arrives as strings like `"0,1,0"`. We convert it once into a grid, so checking a cell later is quick and easy.

### Why BFS and not DFS?

A recursive DFS would also give the right answer.

But on a 100 x 100 floor there can be up to 10,000 stopping cells, and deep recursion can run out of stack space. A queue with a loop has no such risk and is just as easy to read.

### Walkthrough of Example 1

```
row 0:  R  #  .  .  C
row 1:  .  #  .  #  .
row 2:  .  .  .  #  .
```

`#` is furniture and `.` is a free cell.

- Start at `[0, 0]`. Only **down** moves it. It stops at `[2, 0]`.
- From `[2, 0]`, **right** slides it until the furniture at `[2, 3]`. It stops at `[2, 2]`.
- From `[2, 2]`, **up** slides it to the top wall. It stops at `[0, 2]`.
- From `[0, 2]`, **right** slides it to the right wall. It stops at `[0, 4]`, which is the charger.

So the answer is `true`.

### Code

```java
import java.util.*;

public class CleaningRobotCharger {

    int rows, cols;

    // blocked[r][c] is true when furniture is in that cell
    boolean[][] blocked;

    // The 4 commands: up, down, left, right
    int[] dRow = {-1, 1, 0, 0};
    int[] dCol = {0, 0, -1, 1};

    public CleaningRobotCharger() {
    }

    public boolean canReachCharger(List<String> floorPlan, List<Integer> robotCell, List<Integer> chargerCell) {
        buildGrid(floorPlan);

        int startRow = robotCell.get(0), startCol = robotCell.get(1);
        int targetRow = chargerCell.get(0), targetCol = chargerCell.get(1);

        // visited[r][c] is true once the robot has stopped at (r, c)
        boolean[][] visited = new boolean[rows][cols];
        Queue<int[]> queue = new LinkedList<>();

        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();

            if (cell[0] == targetRow && cell[1] == targetCol) {
                return true;
            }

            // Try all 4 commands from this stopping cell
            for (int d = 0; d < 4; d++) {
                int[] stop = roll(cell[0], cell[1], d);
                if (!visited[stop[0]][stop[1]]) {
                    visited[stop[0]][stop[1]] = true;
                    queue.add(stop);
                }
            }
        }
        return false;
    }

    /**
     * Moves the robot one step at a time in direction d
     * until the next step is blocked or off the floor.
     * Returns the cell where it stops.
     */
    int[] roll(int r, int c, int d) {
        while (isFree(r + dRow[d], c + dCol[d])) {
            r += dRow[d];
            c += dCol[d];
        }
        return new int[]{r, c};
    }

    // A cell is free if it is inside the floor and has no furniture
    boolean isFree(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols && !blocked[r][c];
    }

    // Converts rows like "0,1,0" into a boolean grid
    void buildGrid(List<String> floorPlan) {
        rows = floorPlan.size();
        cols = floorPlan.get(0).split(",").length;
        blocked = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            String[] parts = floorPlan.get(r).split(",");
            for (int c = 0; c < cols; c++) {
                blocked[r][c] = parts[c].trim().equals("1");
            }
        }
    }
}
```

### Complexity

Let `R` be the number of rows and `C` the number of columns.

- **Time: O(R x C x (R + C))**. Each cell enters the queue at most once. For each one we slide in 4 directions, and a slide can walk across a whole row or column.
- **Space: O(R x C)** for the `blocked` and `visited` grids and the queue.

---

## Where Solution 1 Wastes Time

Solution 1 walks every slide cell by cell.

Think of a long empty column. Many different stopping cells may slide up through this same column, and each time we walk the same cells again to find the same top cell.

We keep asking the same question: "If I slide up from here, where do I stop?" Let us answer it once for every cell and store the answers.

---

## Solution 2: Work Out Every Stop in Advance

### The key observation

Suppose the robot is at a free cell and moves **up**.

- If the cell above is blocked or outside the floor, the robot stops right here.
- If the cell above is free, the robot moves into it and keeps going. So it stops at **the same place the cell above would stop at**.

That means the answer for a cell comes straight from the answer of its neighbour. We only need to fill the cells in the right order.

For **up**, we fill rows from top to bottom. That way the cell above already has its answer when we need it.

### A small example

Take one column, top to bottom: `free, free, blocked, free, free`.

| Row | Cell    | Cell above       | upStop             |
|-----|---------|------------------|--------------------|
| 0   | free    | outside          | 0                  |
| 1   | free    | free             | same as row 0 = 0  |
| 2   | blocked | not needed       | not needed         |
| 3   | free    | blocked          | 3                  |
| 4   | free    | free             | same as row 3 = 3  |

Each cell needs just one quick check. No walking.

### The 4 tables

We build one table per command. Each one needs its own fill order.

| Table       | Stores        | Fill order     | Copies from         |
|-------------|---------------|----------------|---------------------|
| `upStop`    | a row number  | top to bottom  | the cell above      |
| `downStop`  | a row number  | bottom to top  | the cell below      |
| `leftStop`  | a column      | left to right  | the cell on the left  |
| `rightStop` | a column      | right to left  | the cell on the right |

Moving up or down never changes the column, so `upStop` and `downStop` only need to store the row. Moving left or right never changes the row, so `leftStop` and `rightStop` only need to store the column.

### Why these data structures?

**`int[][]` stop tables**: a plain 2D array gives the answer for any cell in a single lookup. Since a cell's position is already the index, we do not need a map.

The BFS part stays exactly the same as Solution 1. The only change is that the slow `roll` loop is replaced by 4 table lookups.

### Code

```java
import java.util.*;

public class CleaningRobotCharger {

    int rows, cols;

    // blocked[r][c] is true when furniture is in that cell
    boolean[][] blocked;

    // For every free cell, where the robot stops after each command.
    // upStop and downStop hold a row number.
    // leftStop and rightStop hold a column number.
    int[][] upStop, downStop, leftStop, rightStop;

    public CleaningRobotCharger() {
    }

    public boolean canReachCharger(List<String> floorPlan, List<Integer> robotCell, List<Integer> chargerCell) {
        buildGrid(floorPlan);
        buildStopTables();

        int startRow = robotCell.get(0), startCol = robotCell.get(1);
        int targetRow = chargerCell.get(0), targetCol = chargerCell.get(1);

        // visited[r][c] is true once the robot has stopped at (r, c)
        boolean[][] visited = new boolean[rows][cols];
        Queue<int[]> queue = new LinkedList<>();

        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int r = cell[0], c = cell[1];

            if (r == targetRow && c == targetCol) {
                return true;
            }

            // Each stop is now a direct lookup, no walking needed
            int[][] nextCells = {
                {upStop[r][c], c},
                {downStop[r][c], c},
                {r, leftStop[r][c]},
                {r, rightStop[r][c]}
            };

            for (int[] next : nextCells) {
                if (!visited[next[0]][next[1]]) {
                    visited[next[0]][next[1]] = true;
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /**
     * Fills the 4 stop tables in 4 sweeps over the grid.
     * Key idea: if the neighbour in the moving direction is free,
     * the robot stops at the same place that neighbour would stop at.
     */
    void buildStopTables() {
        upStop = new int[rows][cols];
        downStop = new int[rows][cols];
        leftStop = new int[rows][cols];
        rightStop = new int[rows][cols];

        // Up: go top to bottom, so the cell above is ready first
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!blocked[r][c]) {
                    upStop[r][c] = isFree(r - 1, c) ? upStop[r - 1][c] : r;
                }
            }
        }

        // Down: go bottom to top, so the cell below is ready first
        for (int r = rows - 1; r >= 0; r--) {
            for (int c = 0; c < cols; c++) {
                if (!blocked[r][c]) {
                    downStop[r][c] = isFree(r + 1, c) ? downStop[r + 1][c] : r;
                }
            }
        }

        // Left: go left to right, so the cell on the left is ready first
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!blocked[r][c]) {
                    leftStop[r][c] = isFree(r, c - 1) ? leftStop[r][c - 1] : c;
                }
            }
        }

        // Right: go right to left, so the cell on the right is ready first
        for (int r = 0; r < rows; r++) {
            for (int c = cols - 1; c >= 0; c--) {
                if (!blocked[r][c]) {
                    rightStop[r][c] = isFree(r, c + 1) ? rightStop[r][c + 1] : c;
                }
            }
        }
    }

    // A cell is free if it is inside the floor and has no furniture
    boolean isFree(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols && !blocked[r][c];
    }

    // Converts rows like "0,1,0" into a boolean grid
    void buildGrid(List<String> floorPlan) {
        rows = floorPlan.size();
        cols = floorPlan.get(0).split(",").length;
        blocked = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            String[] parts = floorPlan.get(r).split(",");
            for (int c = 0; c < cols; c++) {
                blocked[r][c] = parts[c].trim().equals("1");
            }
        }
    }
}
```

### Complexity

- **Time: O(R x C)**. Building each table visits every cell once. The BFS visits each cell at most once and does 4 lookups per cell.
- **Space: O(R x C)** for the grid, the 4 tables, `visited` and the queue.

---

## Comparing the Two Solutions

| Solution | Time | Space | Notes |
|---|---|---|---|
| BFS with step by step sliding | O(R x C x (R + C)) | O(R x C) | Shortest code, easy to follow |
| BFS with stops worked out in advance | O(R x C) | O(R x C) | Each slide becomes a single lookup |

On a 100 x 100 floor both are fast enough.

Solution 1 is the one to write first in an interview. Solution 2 is a nice follow up when you are asked to make it faster, because it shows how saving answers you already worked out removes repeated work.