# Fill Grid Based on Crop Frequency in Java

#### Problem Statement
[https://codezym.com/question/177-fill-grid-based-on-crop-frequency](https://codezym.com/question/177-fill-grid-based-on-crop-frequency)

## Core Idea

We need to fill the grid so that all cells of the same crop form one connected group.

The snake order from the problem does most of the work for us. In a snake walk, every cell touches the cell we visited just before it.

So if a crop takes one continuous run of cells along the snake, its cells form an unbroken chain. An unbroken chain is always connected.

This means we never have to check connectivity. We walk the grid once in snake order, write the current crop id until its frequency runs out, and then move to the next crop id.

## A First Try: Fill Row by Row

The most natural idea is to fill every row from left to right, like reading a book.

We keep a small cursor over `cropArray`. `cropIndex` is the crop we are placing and `remaining` is how many cells it still needs.

When `remaining` hits `0`, we move to the next crop. This way we never have to build a long list of crop ids.

```java
int cropIndex = 0;                 // crop we are placing
int remaining = cropArray.get(0);  // cells it still needs

for (int r = 0; r < rowCount; r++) {
    for (int c = 0; c < columnCount; c++) {   // always left to right
        while (remaining == 0) {               // current crop is used up
            cropIndex++;
            remaining = cropArray.get(cropIndex);
        }
        grid[r][c] = cropIndex + 1;            // crop ids start from 1
        remaining--;
    }
}
```

This can break when a crop spills into the next row. Its cells on the upper row end at the right edge, but its cells on the next row start from the left edge. The two parts may not touch.

Take Example 2: `cropArray = [2, 4, 2]` on a `2 x 4` grid.

```
1 1 2 2
2 2 3 3
```

Crop `2` is split into two pieces that do not touch. This is not allowed.

## The Fix: Snake Order

Now flip the direction on every other row. This is exactly the snake order.

```
 1 →  2 →  3 →  4
                ↓
 8 ←  7 ←  6 ←  5
 ↓
 9 → 10 → 11 → 12
```

Look at the turns. Cell `4` is right above cell `5`, and cell `8` is right above cell `9`.

So every step moves to a touching cell: sideways inside a row, or straight down when the row changes. The walk never jumps.

Example 2 again, now in snake order:

```
1 1 2 2
3 3 2 2
```

Crop `2` is now one solid `2 x 2` block.

## Final Solution: One Snake Walk

The loop and the cursor stay the same. Only the column changes:

- **Even row:** column is `step`, so we go left to right.
- **Odd row:** column is `columnCount - 1 - step`, so we go right to left.

We also need an `int[] row` to hold the current row. Odd rows are filled from right to left, so we cannot build the row string as we go.

So we fill the array by column first, and join it with commas once the row is full, like `"3,2,2"`.

### Java Code

```java
import java.util.*;

public class FillCropGrid {

    public FillCropGrid() {
    }

    /**
     * Fills the grid in snake order. Each crop id takes one continuous
     * run of snake cells. Back to back snake cells always share an edge,
     * so every crop forms one connected group.
     */
    public List<String> fillCropGrid(List<Integer> cropArray, int rowCount, int columnCount) {
        List<String> grid = new ArrayList<>();

        int cropIndex = 0;                 // crop we are placing right now
        int remaining = cropArray.get(0);  // cells this crop still needs

        for (int r = 0; r < rowCount; r++) {
            int[] row = new int[columnCount];

            for (int step = 0; step < columnCount; step++) {
                // even rows go left to right, odd rows go right to left
                int c = (r % 2 == 0) ? step : columnCount - 1 - step;

                // current crop is used up, move to the next one
                while (remaining == 0) {
                    cropIndex++;
                    remaining = cropArray.get(cropIndex);
                }

                row[c] = cropIndex + 1;  // crop ids start from 1
                remaining--;
            }

            grid.add(toRowString(row));
        }

        return grid;
    }

    /** Joins one row with commas, for example [3, 2, 2] becomes "3,2,2". */
    String toRowString(int[] row) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < row.length; c++) {
            if (c > 0) {
                sb.append(',');
            }
            sb.append(row[c]);
        }
        return sb.toString();
    }
}
```

## Complexity

- **Time:** `O(rowCount * columnCount)`. Every cell is filled once, and `cropIndex` only moves forward.
- **Space:** `O(columnCount)` extra for the current row, not counting the output.