# Dasher Coverage of Connected User Areas in Java

#### Problem Statement
[https://codezym.com/question/275-dasher-coverage-connected-user-area](https://codezym.com/question/275-dasher-coverage-connected-user-area)


## Core Idea

This is the classic "count the islands" problem with one extra check.

User cells (`'1'` in `userClusters`) that touch each other up, down, left or right form one user cluster. Think of each cluster as an island on a map.

We scan the grid one cell at a time. When we reach a user cell we have not seen before, we have found a new cluster. We visit every cell of that cluster and check if each one also has a Dasher (`'1'` in `dasherAvailability`) at the same position. If all of them do, we count the cluster.

We will first write a simple recursive DFS (depth first search). Then we will see why it can crash on big grids, and fix it with a BFS (breadth first search) that uses a queue.


## A Quick Example

Here is Example 2:

```
userClusters        dasherAvailability
1 1 1 0             1 0 1 0
1 0 0 0             1 0 0 0
0 1 1 1             0 1 1 1
```

- **Upper cluster:** cells `(0,0)`, `(0,1)`, `(0,2)` and `(1,0)`. Cell `(0,1)` has no Dasher, so this cluster is **not** covered.
- **Lower cluster:** cells `(2,1)`, `(2,2)` and `(2,3)`. All of them have a Dasher, so this cluster **is** covered.

Cells `(1,0)` and `(2,1)` touch only diagonally, so they belong to different clusters.

Answer: `1`


## Approach 1: Recursive DFS (Simple Version)

We keep a 2D `visited` array. It remembers every user cell that already belongs to an explored cluster. Because of it, the search never goes back and forth between two cells forever, and no cluster is counted twice.

We also use a small helper, `isUserCell(row, col)`. It returns `true` if the cell is inside the grid and has users on it.

For every user cell that is not visited yet, we call `dfs(row, col)`. It marks the cell as visited, checks the Dasher at that cell, and then calls itself on the 4 neighbours. It returns `true` only if this cell and every cell reached from it have a Dasher.

```java
// main loop: every unvisited user cell starts a new cluster
for (int row = 0; row < rows; row++) {
    for (int col = 0; col < cols; col++) {
        if (isUserCell(row, col) && !visited[row][col] && dfs(row, col)) {
            coveredCount++;
        }
    }
}

// returns true if every cell reachable from (row, col) has a Dasher
boolean dfs(int row, int col) {
    if (!isUserCell(row, col) || visited[row][col]) return true;
    visited[row][col] = true;

    boolean covered = dasherAvailability.get(row).charAt(col) == '1';

    // visit all 4 neighbours, even if this cell has no Dasher
    boolean down = dfs(row + 1, col);
    boolean up = dfs(row - 1, col);
    boolean right = dfs(row, col + 1);
    boolean left = dfs(row, col - 1);

    return covered && down && up && right && left;
}
```


### A Common Mistake

It is tempting to write the last part in one line:

```java
return covered && dfs(row + 1, col) && dfs(row - 1, col) && dfs(row, col + 1) && dfs(row, col - 1);
```

This gives wrong answers. `&&` stops at the first `false`, so the remaining `dfs` calls never run and part of the cluster stays unvisited.

For example, take users `"11"` and Dashers `"01"`. The first cell has no Dasher, so `dfs` returns at once and never visits the second cell. The main loop then treats the second cell as a new cluster, finds a Dasher there, and counts it. The answer becomes `1` instead of `0`.

So even after we find a missing Dasher, we must finish visiting the whole cluster.


### Problem With This Approach

Each recursive call stays on the call stack until all its neighbours are done.

A single cluster can fill the whole `500 x 500` grid. For such a cluster, the recursion can go `250,000` calls deep, one call for every cell. That is far more than Java's default call stack allows, so the program crashes with a `StackOverflowError`.

The logic is correct. We just need to stop depending on the call stack.


## Approach 2: BFS With a Queue (Final Solution)

Instead of recursion, we keep our own queue of cells that are waiting to be explored.

**Why a queue?** It does the job of the call stack, but it is a normal Java object, so it can easily hold all `250,000` cells without crashing. Each entry is an `int[]` pair `{row, col}`.

Using our own stack instead of a queue would also work, because the order of visiting does not matter. We only need to reach every cell of the cluster once.

Here is how `exploreCluster(startRow, startCol)` works:

1. Add the starting cell to the queue and mark it as visited.
2. Take a cell out of the queue. If it has no Dasher, set `covered = false`, but keep going.
3. Check its 4 neighbours. Each neighbour that is a user cell and not visited yet is marked as visited and added to the queue.
4. Repeat until the queue is empty. Now every cell of the cluster has been visited.
5. Return `covered`.

We mark a cell as visited when we **add** it to the queue, not when we take it out. This way, the same cell can never be added to the queue twice.

The main loop stays the same. It calls `exploreCluster` for each unvisited user cell and counts the cluster if the result is `true`.


### Java Code

```java
import java.util.*;

public class DasherCoverage {

    List<String> dasherAvailability;
    List<String> userClusters;
    int rows, cols;

    // visited[r][c] becomes true once cell (r, c) is part of an explored cluster
    boolean[][] visited;

    // the 4 allowed moves: down, up, right, left (no diagonal moves)
    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public DasherCoverage() {
    }

    public int countFullyCoveredUserClusters(List<String> dasherAvailability, List<String> userClusters) {
        this.dasherAvailability = dasherAvailability;
        this.userClusters = userClusters;
        rows = userClusters.size();
        cols = userClusters.get(0).length();
        visited = new boolean[rows][cols];

        int coveredCount = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (isUserCell(row, col) && !visited[row][col]) {
                    // a new cluster starts here: explore all of it and check its coverage
                    if (exploreCluster(row, col)) {
                        coveredCount++;
                    }
                }
            }
        }
        return coveredCount;
    }

    /**
     * Visits every cell of the cluster that contains (startRow, startCol) using BFS.
     * Returns true if every cell of this cluster has an available Dasher.
     */
    boolean exploreCluster(int startRow, int startCol) {
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;
        boolean covered = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int row = cell[0], col = cell[1];

            // one missing Dasher fails the cluster, but we keep going
            // so that every cell of this cluster gets marked as visited
            if (dasherAvailability.get(row).charAt(col) != '1') {
                covered = false;
            }

            for (int[] move : directions) {
                int nextRow = row + move[0];
                int nextCol = col + move[1];
                if (isUserCell(nextRow, nextCol) && !visited[nextRow][nextCol]) {
                    // mark it now, so the same cell is never added to the queue twice
                    visited[nextRow][nextCol] = true;
                    queue.add(new int[]{nextRow, nextCol});
                }
            }
        }
        return covered;
    }

    // true if (row, col) is inside the grid and has users on it
    boolean isUserCell(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols
                && userClusters.get(row).charAt(col) == '1';
    }
}
```


### Complexity

- **Time:** `O(rows * cols)`. Each cell enters the queue at most once, and for each cell we check only 4 neighbours.
- **Space:** `O(rows * cols)` for the `visited` array and the queue.