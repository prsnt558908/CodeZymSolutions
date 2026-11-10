# Grid With K Jumps Minimum Steps in Java

#### Problem Statement
[https://codezym.com/question/259-grid-with-k-jumps](https://codezym.com/question/259-grid-with-k-jumps)


## Core Idea

Every jump costs exactly 1 step, whether it moves 1 cell or `k` cells. When every move costs the same, **Breadth First Search (BFS)** finds the shortest path.


BFS works in rounds. First it finds every cell we can reach in 1 step, then every cell we can reach in 2 steps, and so on. So the first time BFS reaches a cell, it has found the fewest steps for that cell.


The only problem is speed. From one cell we can land on up to `4 * k` other cells.


We will first write a simple BFS, see why it is slow, and then add one rule that makes it fast: **while jumping, stop as soon as you meet a cell that needs the same or fewer steps than the cell you jumped from.**


## Solution 1: Simple BFS

We need two things:

- `steps[r][c]`: the fewest steps needed to reach cell `(r, c)`. `-1` means not reached yet, which also makes sure each cell joins the queue only once.
- `queue`: cells waiting to be processed. A queue returns cells in the order they were added, which gives us the round by round order of BFS.


Put the source in the queue with `0` steps. Take one cell out at a time. For each of the 4 directions, try jumps of length `1, 2, ..., k`. Every new cell we land on gets `currentSteps + 1` and joins the queue.


If a jump leaves the grid or hits a `#`, we `break`. We cannot jump over an obstacle, so every cell after it in that direction is also out of reach.

```java
for (int[] direction : directions) {
    for (int jump = 1; jump <= k; jump++) {
        int nextRow = row + direction[0] * jump;
        int nextColumn = column + direction[1] * jump;

        // we cannot leave the grid or jump over an obstacle
        if (nextRow < 0 || nextRow >= rows || nextColumn < 0 || nextColumn >= columns) {
            break;
        }
        if (grid.get(nextRow).charAt(nextColumn) == '#') {
            break;
        }

        // land here only if this cell is not visited yet
        if (steps[nextRow][nextColumn] == -1) {
            steps[nextRow][nextColumn] = currentSteps + 1;
            queue.add(new int[]{nextRow, nextColumn});
        }
    }
}
```


### Why is it slow?

Each cell joins the queue only once. But when it comes out, it checks up to `4 * k` cells, even if all of them were visited long ago.


Think of an open `1000 x 1000` grid with `k = 1000`. Every cell scans its whole row and its whole column, about 2,000 cells. For 1,000,000 cells, that is about 2,000,000,000 checks. Far too slow.


**Time:** `O(rows * columns * k)`, **Space:** `O(rows * columns)`


## Solution 2: BFS With Early Stop

Say we are processing cell `A`, which needs `d` steps, and we are jumping to the right. On the way, we meet a cell `B` that is already visited.


BFS handles cells in order of steps. So while `A` is being processed, every visited cell has `d + 1` steps or fewer. That leaves only two cases for `B`.


**Case 1: `B` needs `d` steps or fewer. Stop here.**


Every cell after `B` that `A` can reach, `B` can also reach in one jump, because `B` is closer to it. `B` needs only `d` steps or fewer, so those cells will get `d + 1` steps or fewer through `B`. That is already as good as what `A` can offer, so `A` stops.


**Case 2: `B` needs exactly `d + 1` steps. Jump over it and keep going.**


`B` is no better than what `A` would give it. If `A` stops here, the cells after `B` could only get `d + 2` steps through `B`. But `A` can reach them in `d + 1`. So `A` must skip `B` and continue.


Here is every case a jump can meet:

| Cell we land on | What we do |
| --- | --- |
| Outside the grid or `#` | Stop going in this direction |
| Not visited yet | Give it `d + 1` steps, add it to the queue, keep going |
| Visited with `d + 1` steps | Skip it, keep going |
| Visited with `d` steps or fewer | Stop going in this direction |


### Dry Run (Example 1)

```
k = 2, source = (0,0), destination = (2,3)

     0 1 2 3
 0   . . . .
 1   . # # .
 2   . . . .
```

| Steps | Cells reached for the first time |
| --- | --- |
| 0 | (0,0) |
| 1 | (1,0), (2,0), (0,1), (0,2) |
| 2 | (2,1), (2,2), (0,3) |
| 3 | (2,3), the destination, so we return 3 |


An early stop happens at step 2. When `(2,1)` jumps right, it meets `(2,2)`, which also needs 2 steps. So `(2,1)` stops there and leaves the rest of the row to `(2,2)`.


### Why is it fast?

Pick any cell `X` and look only at jumps coming from its left.


A cell with `d` steps can land on `X`, or jump over it, only if `X` ends up with `d + 1` steps. So every cell that passes over `X` from the left has the same number of steps.


Now say two such cells `P` and `Q` are on the left of `X`, and `Q` is nearer to `X`. When `P` jumps right, it meets `Q` first. `Q` has the same steps as `P`, so `P` stops at `Q` (Case 1). `P` never reaches `X`.


So only one cell can pass over `X` from the left. The same is true for the other 3 sides. This means every cell is passed over at most 4 times in the whole BFS, and `k` no longer matters.


### Complexity

- **Time:** `O(rows * columns)`. Each cell joins the queue once, is passed over at most 4 times, and each of its 4 directions ends with one stop.
- **Space:** `O(rows * columns)` for the `steps` grid and the queue.


### Java Code

```java
import java.util.*;

public class GridWithKJumpsMinimumSteps {

    // up, down, left, right
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public GridWithKJumpsMinimumSteps() {
    }

    /**
     * Returns the fewest steps (jumps) needed to go from source to destination,
     * or -1 if the destination cannot be reached.
     */
    public int minimumSteps(List<String> grid, int k, int sourceRow, int sourceColumn,
                            int destinationRow, int destinationColumn) {
        // already at the destination
        if (sourceRow == destinationRow && sourceColumn == destinationColumn) {
            return 0;
        }

        int rows = grid.size();
        int columns = grid.get(0).length();

        // steps[r][c] = fewest steps needed to reach cell (r, c), -1 means not reached yet
        int[][] steps = new int[rows][columns];
        for (int[] stepsRow : steps) {
            Arrays.fill(stepsRow, -1);
        }
        steps[sourceRow][sourceColumn] = 0;

        // BFS queue of cells stored as {row, column}
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sourceRow, sourceColumn});

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int row = cell[0];
            int column = cell[1];
            int currentSteps = steps[row][column];

            for (int[] direction : directions) {
                // try jumps of length 1, 2, ..., k in this direction
                for (int jump = 1; jump <= k; jump++) {
                    int nextRow = row + direction[0] * jump;
                    int nextColumn = column + direction[1] * jump;

                    // we cannot leave the grid or jump over an obstacle
                    if (nextRow < 0 || nextRow >= rows || nextColumn < 0 || nextColumn >= columns) {
                        break;
                    }
                    if (grid.get(nextRow).charAt(nextColumn) == '#') {
                        break;
                    }

                    int seenSteps = steps[nextRow][nextColumn];

                    if (seenSteps == -1) {
                        // first visit. BFS order makes this the fewest steps for this cell
                        if (nextRow == destinationRow && nextColumn == destinationColumn) {
                            return currentSteps + 1;
                        }
                        steps[nextRow][nextColumn] = currentSteps + 1;
                        queue.add(new int[]{nextRow, nextColumn});
                    } else if (seenSteps <= currentSteps) {
                        // reached in the same or fewer steps than the current cell.
                        // it covers every cell after it, so stop going this way.
                        break;
                    }
                    // otherwise seenSteps == currentSteps + 1: jump over it and keep going
                }
            }
        }
        return -1;
    }
}
```