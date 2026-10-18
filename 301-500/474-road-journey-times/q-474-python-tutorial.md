# Road Journey Times in Python

#### Problem Statement
[https://codezym.com/question/474-road-journey-times](https://codezym.com/question/474-road-journey-times)

---

## Core Idea

We get a grid of journey times. Each row goes up (or stays the same) from left to right. Each column goes up (or stays the same) from top to bottom.


We have to tell if one exact number, `requiredTime`, is present anywhere in this grid.


The simplest way is to check every cell. It works, but it ignores the sorted order completely.


The better way is to start from the **top-right corner**. From there, moving left always gives a smaller (or equal) value and moving down always gives a bigger (or equal) value. So one comparison tells us which way to go, and it also throws away a whole row or a whole column.


This is called a **staircase search**, because the path we walk looks like a staircase going down and to the left.

---

## Understanding the Input

Each row comes as a string like `"14,27,43,68"`. We split it by comma and turn each piece into an int.


Example 1 as a grid (`requiredTime = 51`):

|             | Dest 1 | Dest 2 | Dest 3 | Dest 4 |
|-------------|--------|--------|--------|--------|
| **Route 1** | 14     | 27     | 43     | 68     |
| **Route 2** | 20     | 34     | **51** | 79     |
| **Route 3** | 29     | 42     | 63     | 96     |


Every row grows from left to right. Every column grows from top to bottom.

---

## Solution 1: Brute Force

### Idea

Go through every route. Split it into numbers. Compare each number with `requiredTime`.


If any number matches, return `True`. If we finish all rows without a match, return `False`.

### Code

```python
class RoadJourneyTimes:
    def __init__(self):
        pass

    def hasJourneyTime(self, travelTimes, requiredTime):
        """Brute force: read every journey time and compare it with requiredTime."""
        for route in travelTimes:
            for part in route.split(","):
                if int(part) == requiredTime:
                    return True
        return False
```

### Complexity

- **Time:** `O(R × C)`, where `R` is the number of routes and `C` is the number of destinations.
- **Space:** `O(C)` for the split pieces of one row.

### What is the problem?

It is fast enough for these limits (at most 300 × 300 = 90,000 cells). But it does not use the sorted order at all.


It checks cells that clearly cannot be the answer. For example, if the first cell of a row is already bigger than `requiredTime`, the rest of that row is useless, but brute force still checks it.


A small improvement would be a binary search on each row. That takes `O(R × log C)`. But it still uses only the row order. We can do better by using the row order and the column order together.

---

## Solution 2: Staircase Search from the Top-Right Corner

### Why the top-right corner?

The top-right cell is the biggest (or tied) value in its row and the smallest (or tied) value in its column.


From this cell:

- Moving **left** gives a smaller or equal value.
- Moving **down** gives a bigger or equal value.


So we always know which way to go.


If we started at the top-left corner, both right and down would give bigger values. We would not know which way to move. The bottom-right corner has the same problem. The bottom-left corner also works, it is just the mirror image of top-right.

### The Rule

At each cell, compare `current` with `requiredTime`:

1. **`current == requiredTime`**: found it. Return `True`.
2. **`current > requiredTime`**: every cell below in this column is even bigger (or equal). None of them can match. Drop this column and **move left**.
3. **`current < requiredTime`**: every cell to the left in this row is even smaller (or equal). None of them can match. Drop this row and **move down**.


Stop when we go below the last row or to the left of the first column. Then return `False`.


Equal values do not break this rule. In case 2, the cells below are `>= current`, and `current > requiredTime`, so they are also `> requiredTime`. Case 3 works the same way.

### Walkthrough: Example 1 (`requiredTime = 51`)

| Step | Cell (row, col) | Value | Compare  | Move   |
|------|-----------------|-------|----------|--------|
| 1    | (0, 3)          | 68    | 68 > 51  | left   |
| 2    | (0, 2)          | 43    | 43 < 51  | down   |
| 3    | (1, 2)          | 51    | 51 == 51 | found  |


We checked only 3 cells instead of 12.

### Walkthrough: Example 2 (`requiredTime = 29`)

|             | Dest 1 | Dest 2 | Dest 3 |
|-------------|--------|--------|--------|
| **Route 1** | 9      | 23     | 38     |
| **Route 2** | 17     | 31     | 46     |


| Step | Cell (row, col) | Value | Compare          | Move               |
|------|-----------------|-------|------------------|--------------------|
| 1    | (0, 2)          | 38    | 38 > 29          | left               |
| 2    | (0, 1)          | 23    | 23 < 29          | down               |
| 3    | (1, 1)          | 31    | 31 > 29          | left               |
| 4    | (1, 0)          | 17    | 17 < 29          | down               |
| 5    | (2, 0)          | none  | outside the grid | stop, return False |

### Why do we build a 2D list?

The input is a list of strings, but the search jumps between rows and columns.


So we convert all strings into a 2D list of ints once, in a small helper method `buildGrid`. After that, any cell is just `grid[row][col]`, and the search loop stays short and clean.

### Code

```python
class RoadJourneyTimes:
    def __init__(self):
        pass

    def hasJourneyTime(self, travelTimes, requiredTime):
        """
        Staircase search starting from the top-right corner.
        Every step either finds requiredTime or throws away one full row or one full column.
        """
        grid = self.buildGrid(travelTimes)
        rows = len(grid)
        cols = len(grid[0])

        # Start at the top-right corner
        row = 0
        col = cols - 1

        while row < rows and col >= 0:
            current = grid[row][col]

            if current == requiredTime:
                return True
            elif current > requiredTime:
                # Every value below in this column is even bigger, so drop this column
                col -= 1
            else:
                # Every value to the left in this row is even smaller, so drop this row
                row += 1

        return False

    def buildGrid(self, travelTimes):
        """Converts rows like "14,27,43" into a 2D list of ints."""
        grid = []
        for route in travelTimes:
            grid.append([int(part) for part in route.split(",")])
        return grid
```

### Complexity

- **Search:** `O(R + C)`. Each step removes one row or one column, so there can be at most `R + C` steps.
- **Reading the input:** `O(R × C)`. The times come as text, so every number has to be parsed once. This part cannot be skipped.
- **Space:** `O(R × C)` for the grid.


The big gain is in the search itself. It goes from checking every cell to walking at most `R + C` cells.

---

## Key Takeaway

When a grid is sorted along rows and along columns, start from a corner where one direction goes down and the other goes up (top-right or bottom-left).


Each comparison then removes a full row or a full column, and the search finishes in `O(R + C)` steps.