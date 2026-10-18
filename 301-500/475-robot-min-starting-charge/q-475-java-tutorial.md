# Minimum Starting Charge for a Warehouse Robot in Java

#### Problem Statement
[https://codezym.com/question/475-robot-min-starting-charge](https://codezym.com/question/475-robot-min-starting-charge)

---

## Core Idea

We need the smallest starting battery so that **at least one** right/down route keeps the battery at 1 or more after every workstation.


The trick is to **work backwards**, from the destination to the start. For every workstation, we answer one question: *"How much charge must the robot have when it arrives here, so it can finish safely?"*


Think of it like planning fuel for a road trip. You start from the last stop and ask how much fuel you need there, then the stop before it, and so on, until you reach home.


Each workstation's answer depends only on the workstation below it and the one to its right. So we fill a table from the bottom-right corner to the top-left corner. This is **dynamic programming**, and it runs in `O(rows x cols)` time.

---

## Understanding the Problem

- The robot starts at the top-left and ends at the bottom-right.
- It can only move **right** or **down**.
- Each workstation adds or removes battery. The change is applied at every visited workstation, including the first and the last.
- After every workstation, the battery must be **at least 1**.
- Return the minimum starting charge, measured before the first change is applied.

---

## Why Going Forward Is Hard

**Trying every route is too slow.** The number of routes explodes as the grid grows. A `20 x 20` grid already has about 35 billion routes, and a `200 x 200` grid has a number of routes with 119 digits.


**Saving results while going forward also does not work.** When moving forward, a route has to remember **two** things: how much charge it has now, and how much starting charge it needed so far.


Look at two routes that reach the same workstation:

| Route | Current total | Start needed so far |
|-------|---------------|---------------------|
| A     | 10            | 6                   |
| B     | 0             | 2                   |


Which one is better? It depends on what comes next.


- If the next workstation costs `20` units, route A needs a start of `11`, but route B needs `21`. **A wins.**
- If nothing costs anything after this, route A needs `6`, but route B needs `2`. **B wins.**


Since neither route is always better, we cannot keep just one result per workstation. This is the reason we flip the direction.

---

## Thinking Backwards

### What we store

```
need[r][c] = minimum charge the robot must have when it ARRIVES at (r, c),
             before applying that workstation's change,
             so that it can reach the end safely
```


### Why backwards works

1. What happens after a workstation does not depend on how the robot got there.
2. The future is described by **one number**: the charge needed on arrival. A smaller number is always better.


So each workstation stores just one value, and there is nothing to compare between routes.


### Building the rule step by step

**Step 1: the last workstation.**

After applying its change, the robot must still have at least 1 unit.

- If the change is `-3`, the robot must arrive with `4`, because `4 - 3 = 1`.
- If the change is `+7`, the robot could arrive with very little. But the battery is never below 1, so the answer is `1`.

```
need = max(1, 1 - change)
```


**Step 2: a workstation with one next step.**

Say the next workstation needs `8` units on arrival.

- If this workstation **adds** `1`, the robot can arrive with `7`, because `7 + 1 = 8`.
- If this workstation **costs** `4`, the robot must arrive with `12`, because `12 - 4 = 8`.

```
need = max(1, nextNeed - change)
```


**Step 3: a workstation with two next steps.**

The robot can go right or down. We only need **one** valid route, so we pick the cheaper option.

```
bestNext = min(need of cell below, need of cell to the right)
need     = max(1, bestNext - change)
```


### Why the `max(1, ...)` is important

It is easy to forget, so let us see what goes wrong without it.


Take a single column `["-5", "100"]`. The robot loses 5 units and then gains 100.

- Bottom cell without the floor: `1 - 100 = -99`.
- Top cell without the floor: `-99 + 5 = -94`. This is a wrong answer.


The real answer is `6`. The robot must survive losing 5 units **first**. A big bonus later cannot save it from running out now.


With the floor, the bottom cell becomes `max(1, -99) = 1`, and the top cell becomes `max(1, 1 + 5) = 6`. Correct.


### Fill order

Each cell needs the answers of the cell below and the cell to its right. So we fill **from the bottom row up**, and **from right to left** in each row.


- Cells in the last row can only move right.
- Cells in the last column can only move down.

---

## Walkthrough of Example 1

Input: `["-2,-5,9", "1,-4,-3"]`

| | col 0 | col 1 | col 2 |
|---|---|---|---|
| **row 0** | -2 | -5 | 9 |
| **row 1** | 1 | -4 | -3 |


Fill from the bottom-right corner:

| Cell | Change | bestNext | need |
|------|--------|----------|------|
| (1, 2) | -3 | end | max(1, 1 + 3) = **4** |
| (1, 1) | -4 | right = 4 | max(1, 4 + 4) = **8** |
| (1, 0) | 1 | right = 8 | max(1, 8 - 1) = **7** |
| (0, 2) | 9 | down = 4 | max(1, 4 - 9) = **1** |
| (0, 1) | -5 | min(8, 1) = 1 | max(1, 1 + 5) = **6** |
| (0, 0) | -2 | min(7, 6) = 6 | max(1, 6 + 2) = **8** |


Final `need` table:

| | col 0 | col 1 | col 2 |
|---|---|---|---|
| **row 0** | **8** | 6 | 1 |
| **row 1** | 7 | 8 | 4 |


The answer is `need[0][0] = 8`.


Following the smaller choices from the top-left gives the route right, right, down. Starting with 8, the battery goes `6 → 1 → 10 → 7`, which matches the example.

---

## Data Structure Used

A simple **2D int array** `need`, the same size as the grid.


Every cell's answer is reused by the cell above it and the cell to its left. Storing it means each cell is computed only once, instead of once per route.

---

## Code

```java
import java.util.*;

public class WarehouseRobot {

    public WarehouseRobot() {
    }

    public int minimumStartingCharge(List<String> chargeChanges) {
        int[][] grid = parseGrid(chargeChanges);
        int rows = grid.length;
        int cols = grid[0].length;

        // need[r][c] = minimum charge the robot must have when it ARRIVES at (r, c),
        // before applying that workstation's change, so it can finish safely
        int[][] need = new int[rows][cols];

        // Fill from the bottom-right corner, going up and to the left
        for (int r = rows - 1; r >= 0; r--) {
            for (int c = cols - 1; c >= 0; c--) {
                int change = grid[r][c];

                if (r == rows - 1 && c == cols - 1) {
                    // Last workstation: after the change we must still have at least 1
                    need[r][c] = Math.max(1, 1 - change);
                    continue;
                }

                // Pick the cheaper of the two next workstations
                int bestNext = Integer.MAX_VALUE;
                if (r + 1 < rows) {
                    bestNext = Math.min(bestNext, need[r + 1][c]); // move down
                }
                if (c + 1 < cols) {
                    bestNext = Math.min(bestNext, need[r][c + 1]); // move right
                }

                // After applying 'change' we need 'bestNext' units.
                // The charge can never drop below 1, so 1 is the floor.
                need[r][c] = Math.max(1, bestNext - change);
            }
        }

        return need[0][0];
    }

    // Converts ["-2,-5,9", "1,-4,-3"] into a 2D int array
    int[][] parseGrid(List<String> chargeChanges) {
        int[][] result = new int[chargeChanges.size()][];
        for (int r = 0; r < chargeChanges.size(); r++) {
            String[] parts = chargeChanges.get(r).split(",");
            result[r] = new int[parts.length];
            for (int c = 0; c < parts.length; c++) {
                result[r][c] = Integer.parseInt(parts[c].trim());
            }
        }
        return result;
    }
}
```

---

## Complexity

- **Time:** `O(rows x cols)`. Every workstation is processed once.
- **Space:** `O(rows x cols)` for the `need` table.


Each row only looks at the row below it. So you could keep just one row of `need` and bring space down to `O(cols)`. The full table is kept here because it is easier to read.

---

## Key Takeaway

When moving forward needs two numbers to describe where you are, try moving **backwards**. Often the backward view needs only one number, and that makes dynamic programming simple.