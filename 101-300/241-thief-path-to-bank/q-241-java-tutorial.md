# Thief Path to Bank Avoiding Police Patrol in Java

#### Problem Statement
[https://codezym.com/question/241-thief-path-to-bank](https://codezym.com/question/241-thief-path-to-bank)


The solution has two simple steps. First, mark every cell the police can see. Second, run a normal BFS from the thief that walks only on safe cells, and check if it reaches the bank.


To mark unsafe cells fast, we do not check each cell against every station. Instead, we start one BFS from all stations at the same time and let the patrol spread outward, one step per round. For the follow-up, where each station has its own range, we keep the same spreading but let stations with a bigger range start earlier.


Both methods run in `O(rows * cols)` time.


## What Does a Patrol Cover?

A station with range `k` covers every cell within Manhattan distance `k`, which is `|r1 - r2| + |c1 - c2|`. This makes a diamond shape. For `k = 2`:

```
. . X . .
. X X X .
X X P X X
. X X X .
. . X . .
```

The thief can never step on a `P` or an `X`.


## Approach 1: Brute Force

For every cell, loop over all stations and check the distance. The snippet keeps a range for each station, so it works for the follow-up too.

```java
// station = {row, col, range}
for (int r = 0; r < rows; r++) {
    for (int c = 0; c < cols; c++) {
        for (int[] station : stations) {
            int distance = Math.abs(r - station[0]) + Math.abs(c - station[1]);
            if (distance <= station[2]) {
                unsafe[r][c] = true;
                break;
            }
        }
    }
}
```

After this, one BFS from the thief gives the answer.


**Problem:** the grid can have 1 million cells, and almost all of them can be stations. Checking every cell against every station is far too slow.


The waste comes from every cell asking "is any station close to me?" on its own. It is better to flip it: let the stations push the danger outward, so each cell is touched only once.


## Approach 2: Multi-Source BFS (same range `k`)

Patrols are not blocked by anything. So on this grid, the number of BFS steps from a station to a cell is exactly their Manhattan distance.


That means the cells within distance `k` of a station are exactly the cells a BFS reaches from it in `k` rounds.


We do not need one BFS per station. We put **all** stations in round 0 and spread from all of them together. Think of a fire starting at every station at the same moment and moving one cell per round, for `k` rounds. Every cell the fire touches is unsafe.

```java
// round 0: every station, already marked unsafe
for (int round = 1; round <= k && !current.isEmpty(); round++) {
    current = spreadOneStep(current, unsafe);
}
```

`spreadOneStep` moves every cell in `current` one step up, down, left and right. Each new cell it reaches is marked unsafe and goes into the next round. Cells that are already unsafe are skipped, so a cell joins a round at most once.


`k` can be as large as `10^9`. That is fine: the fire covers the whole grid within `rows + cols` rounds. After that `current` is empty and the loop stops.


**Why these data structures**

- `boolean[][] unsafe`: marks patrolled cells. It also acts as the visited array for the spreading, so no cell is added twice.
- `List<int[]> current`: the cells reached in the current round. We keep one list per round, instead of one long queue, because we need to count rounds and stop after `k`.


## Walking the Thief to the Bank

Once `unsafe` is ready, `thiefCanReach` runs a plain BFS from the thief:

1. If the thief cell or the bank cell is unsafe, return `false`.
2. Add the thief to a queue and mark it visited.
3. Take a cell from the queue. If it is the bank, return `true`.
4. Otherwise add every neighbor that is inside the grid, safe and not visited.
5. If the queue becomes empty, return `false`.

We only need yes or no, so we never store the path. DFS would also work, but a recursive DFS over 1 million cells can overflow the call stack, so BFS is the safer choice.


## Approach 3: Follow-up, Every Station Has Its Own Range

Give every patrol a number called **steps left**. A station starts with steps left equal to its range, and each move uses one step. A cell is unsafe if some patrol reaches it with 0 or more steps left.


### Why starting everyone together breaks

Suppose we reuse Approach 2 as it is: every station starts in round 0 with its own steps left, and a cell that is already unsafe is skipped.


In Approach 2 that skip was safe, because all patrols were equally strong. Now a weak patrol can reach a cell first and block a strong patrol that arrives later.


Example: a single row with ranges `[3, 0]`.

```
P P . T B
```

The second station (range 0) marks column 1 in round 0. The first station (range 3) can only move right, through column 1, which is already marked. So its patrol is skipped there, columns 2 and 3 look safe, and we would return `true`.


But the thief at column 3 is only 3 steps from the first station. The real answer is `false`.


### Fix: bigger range starts earlier

Imagine all patrols must finish at the same moment. A station with a bigger range needs more rounds, so it must start earlier.


In code, we count `stepsLeft` down from the biggest possible range to `0`. In each round:

1. Stations whose range equals `stepsLeft` join `current`, unless their cell is already unsafe.
2. Now every cell in `current` has exactly `stepsLeft` steps left.
3. If `stepsLeft > 0`, spread one step. The new cells will have one step less.

```java
for (int stepsLeft = maxRange; stepsLeft >= 0; stepsLeft--) {
    for (int[] station : stationsByRange.get(stepsLeft)) {
        if (!unsafe[station[0]][station[1]]) {
            unsafe[station[0]][station[1]] = true;
            current.add(station);
        }
    }
    if (stepsLeft > 0) {
        current = spreadOneStep(current, unsafe);
    }
}
```

Because the count only goes down, the first patrol to reach a cell always has the most steps left. Any patrol that arrives later has the same or fewer steps left, so skipping it loses nothing. Each cell is still processed once.


Back to the example. When the count reaches 3, the first station joins, and its patrol marks columns 1, 2 and 3, one per round. When the count reaches 0, the second station joins, but its cell is already marked. The thief cell is unsafe, so we return `false`.


**Why these data structures**

- `List<List<int[]>> stationsByRange`: `stationsByRange.get(s)` holds the stations with range `s`. Each round needs "the stations that join now", and this list of lists gives them directly, with no sorting.
- Ranges can be up to `10^9`, but no patrol can walk more than `rows + cols` steps inside the grid. So we cap every range at `rows + cols` (`maxRange`). This keeps `stationsByRange` small, at most about 2,000 lists.


`canReachBank` is just the special case where every station joins in the same round.


## Complexity

| Method | Time | Space |
|---|---|---|
| `canReachBank` | `O(rows * cols)` | `O(rows * cols)` |
| `canReachBankWithRanges` | `O(rows * cols)` | `O(rows * cols)` |

Every cell is marked unsafe at most once and visited by the thief BFS at most once. The follow-up also runs `rows + cols + 1` rounds, which adds only `O(rows + cols)`.


## Java Code

```java
import java.util.*;

public class ThiefPathToBank {

    int rows, cols;

    // up, down, left, right
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public ThiefPathToBank() {

    }

    /**
     * Every police station patrols up to distance k.
     * All patrols start together and spread one cell per round, for k rounds.
     */
    public boolean canReachBank(List<String> grid, int k) {
        rows = grid.size();
        cols = grid.get(0).length();
        boolean[][] unsafe = new boolean[rows][cols];

        // round 0: every police station is unsafe
        List<int[]> current = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid.get(r).charAt(c) == 'P') {
                    unsafe[r][c] = true;
                    current.add(new int[]{r, c});
                }
            }
        }

        // k more rounds, stop early once no new cell is reached
        for (int round = 1; round <= k && !current.isEmpty(); round++) {
            current = spreadOneStep(current, unsafe);
        }

        return thiefCanReach(grid, unsafe);
    }

    /**
     * Each police station has its own patrol range.
     * Stations with a bigger range start spreading earlier, so the first
     * patrol that reaches a cell is always the one with the most steps left.
     */
    public boolean canReachBankWithRanges(List<String> grid, List<Integer> policeRanges) {
        rows = grid.size();
        cols = grid.get(0).length();
        boolean[][] unsafe = new boolean[rows][cols];

        // no patrol can walk more than rows + cols steps inside the grid
        int maxRange = rows + cols;

        // stationsByRange.get(s) = stations that start with s steps left
        List<List<int[]>> stationsByRange = new ArrayList<>();
        for (int s = 0; s <= maxRange; s++) {
            stationsByRange.add(new ArrayList<>());
        }

        int index = 0; // stations get their ranges in row-major order
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid.get(r).charAt(c) == 'P') {
                    int patrolRange = Math.min(policeRanges.get(index), maxRange);
                    stationsByRange.get(patrolRange).add(new int[]{r, c});
                    index++;
                }
            }
        }

        // every cell in 'current' has exactly 'stepsLeft' steps left
        List<int[]> current = new ArrayList<>();
        for (int stepsLeft = maxRange; stepsLeft >= 0; stepsLeft--) {
            // stations whose range equals stepsLeft join now
            for (int[] station : stationsByRange.get(stepsLeft)) {
                if (!unsafe[station[0]][station[1]]) {
                    unsafe[station[0]][station[1]] = true;
                    current.add(station);
                }
            }
            // with 0 steps left, a patrol cannot move any further
            if (stepsLeft > 0) {
                current = spreadOneStep(current, unsafe);
            }
        }

        return thiefCanReach(grid, unsafe);
    }

    /**
     * Moves every patrol in 'current' one cell in all 4 directions.
     * Newly reached cells are marked unsafe and returned as the next round.
     */
    List<int[]> spreadOneStep(List<int[]> current, boolean[][] unsafe) {
        List<int[]> nextCells = new ArrayList<>();
        for (int[] cell : current) {
            for (int[] d : directions) {
                int r = cell[0] + d[0], c = cell[1] + d[1];
                if (isInside(r, c) && !unsafe[r][c]) {
                    unsafe[r][c] = true;
                    nextCells.add(new int[]{r, c});
                }
            }
        }
        return nextCells;
    }

    /**
     * Plain BFS from the thief that walks only on safe cells.
     */
    boolean thiefCanReach(List<String> grid, boolean[][] unsafe) {
        int[] thief = findCell(grid, 'T');
        int[] bank = findCell(grid, 'B');
        if (unsafe[thief[0]][thief[1]] || unsafe[bank[0]][bank[1]]) {
            return false;
        }

        boolean[][] visited = new boolean[rows][cols];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(thief);
        visited[thief[0]][thief[1]] = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (cell[0] == bank[0] && cell[1] == bank[1]) {
                return true;
            }
            for (int[] d : directions) {
                int r = cell[0] + d[0], c = cell[1] + d[1];
                if (isInside(r, c) && !unsafe[r][c] && !visited[r][c]) {
                    visited[r][c] = true;
                    queue.add(new int[]{r, c});
                }
            }
        }
        return false;
    }

    /**
     * Returns {row, col} of the first cell holding 'target'.
     */
    int[] findCell(List<String> grid, char target) {
        for (int r = 0; r < rows; r++) {
            int c = grid.get(r).indexOf(target);
            if (c != -1) {
                return new int[]{r, c};
            }
        }
        return null;
    }

    boolean isInside(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }
}
```