# Bingo Card Generator in Java

#### Problem Statement
[https://codezym.com/question/240-bingo-card-generator](https://codezym.com/question/240-bingo-card-generator)

## Core Idea

This problem has no searching and no guessing. The positions of the numbers are fixed, and every number comes from a given formula. There is no randomness, so the same seed always gives the same card.

The key point is that the formula works **column by column**, but the answer is returned **row by row**. So we fill the card one column at a time. Once all columns are filled, we read the card row by row and join each row with commas.

## Understanding the Layout

The card has 3 rows and 9 columns. The cells that get a number are always the same, whatever the seed.

Here `X` means the cell gets a number and `.` means it stays `0`:

```
col      0  1  2  3  4  5  6  7  8
row 0    X  .  X  .  X  .  X  X  .
row 1    .  X  .  X  .  X  X  .  X
row 2    X  X  X  .  X  .  .  X  .
count    2  2  2  1  2  1  2  2  1
```

The last line, `count`, is how many numbers each column needs. Each row has 5 `X`s, and every column has at least one.

In code, we store this as a 3 x 9 grid of 1s and 0s:

```java
// 1 means the cell gets a number, 0 means it stays empty
int[][] layout = {
    {1, 0, 1, 0, 1, 0, 1, 1, 0},
    {0, 1, 0, 1, 0, 1, 1, 0, 1},
    {1, 1, 1, 0, 1, 0, 0, 1, 0}
};
```

**Why a grid?** The problem gives, for each row, the columns that hold a number, like `{0, 2, 4, 6, 7}` for row 0. But while filling a column, we keep asking: does row `r` have a number in column `c`? With lists, we would have to search for `c` every time.

The grid answers that with a direct lookup, `layout[r][c]`. It also looks just like the card, so it is easy to check by eye.

## The Formula for One Column

For a column `col` that needs `count` numbers:

- `low = col * 10 + 1` is the smallest number allowed in this column.
- `offset = (seed + 7 * col) % (11 - count)` decides where inside the range we start.
- The column gets `low + offset`, then `low + offset + 1`, then `low + offset + 2`, stopping after `count` numbers.
- These numbers go into the column's `X` cells from top to bottom.

For example, take `seed = 12` and column 0. Here `low = 1`, `count = 2` and `offset = 12 % 9 = 3`.

So column 0 gets `4` and `5`. Row 0 gets `4`, row 1 stays empty, and row 2 gets `5`.

## Do We Need to Check the Card Rules?

No. The formula already makes sure every rule holds:

- **Numbers stay inside the column range.** `offset` is at most `10 - count`, so the last number is at most `low + (10 - count) + (count - 1) = low + 9`, the top of the range. This is why the formula uses `11 - count`.
- **Numbers go up inside a column and never repeat.** They are consecutive and placed from top to bottom.
- **No number repeats across columns.** Each column has its own range of 10 numbers.
- **5 numbers per row, 15 in total, no empty column.** This comes from the fixed layout.

So there is no checking and no retrying. We just apply the formula.

## Solution 1: Fill Row by Row

The answer is needed row by row, so the first idea is to walk the card row by row and work out each number on the spot.

For a cell in column `col`, we need two things:

- `count`: how many numbers this column has.
- `above`: how many numbers sit above this cell in the same column. The top number gets `low + offset`, the next one gets `+ 1`, and so on.

```java
int[][] card = new int[3][9];

for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 9; col++) {
        if (layout[row][col] == 0) continue;

        // scan the whole column to count its numbers
        int count = 0;
        for (int r = 0; r < 3; r++) count += layout[r][col];

        // scan again to see how many numbers sit above this cell
        int above = 0;
        for (int r = 0; r < row; r++) above += layout[r][col];

        int low = col * 10 + 1;
        int offset = (seed + 7 * col) % (11 - count);
        card[row][col] = low + offset + above;
    }
}
```

This gives the right card, but it does a lot of extra work:

- For every cell, we scan its column twice, once for `count` and once for `above`.
- Cells in the same column compute the same `count` and `offset` again and again.
- `count` never depends on the seed, yet we count it again on every call.

The card is tiny, so speed is not the real problem. The real problem is that we walk row by row while the formula thinks column by column. The extra scans are the price of that mismatch.

## Solution 2: Fill Column by Column

Let the code follow the formula and fill the card **one column at a time**.

**Step 1: Count each column once.** The layout never changes, so the count of each column never changes either. We work it out once in the constructor and keep it in an array, `columnCount`. Every call to `generateCard` reuses it.

**Step 2: Fill each column.** For each column, compute `low` and `offset` once. Then walk down the column with a running `number`.

Each time we reach a cell marked `1`, we write `number` there and add 1 to it. This hands out `low + offset`, `low + offset + 1`, and so on from top to bottom, so we no longer need `above`.

`seed` can be as large as 1,000,000,000. `seed + 7 * col` is at most 1,000,000,056, which still fits in an `int`, so there is no overflow.

**Step 3: Turn each row into a string.** After all 9 columns are filled, read the card row by row and join the values with commas, with no spaces.

### Walk-through for seed = 12

| col | count | low | offset | numbers (top to bottom) |
|-----|-------|-----|--------|-------------------------|
| 0 | 2 | 1 | `(12 + 0) % 9 = 3` | 4, 5 |
| 1 | 2 | 11 | `(12 + 7) % 9 = 1` | 12, 13 |
| 2 | 2 | 21 | `(12 + 14) % 9 = 8` | 29, 30 |
| 3 | 1 | 31 | `(12 + 21) % 10 = 3` | 34 |
| 4 | 2 | 41 | `(12 + 28) % 9 = 4` | 45, 46 |
| 5 | 1 | 51 | `(12 + 35) % 10 = 7` | 58 |
| 6 | 2 | 61 | `(12 + 42) % 9 = 0` | 61, 62 |
| 7 | 2 | 71 | `(12 + 49) % 9 = 7` | 78, 79 |
| 8 | 1 | 81 | `(12 + 56) % 10 = 8` | 89 |

Putting these numbers into the layout gives:

```
4,0,29,0,45,0,61,78,0
0,12,0,34,0,58,62,0,89
5,13,30,0,46,0,0,79,0
```

This matches Example 1.

### Java Code

```java
import java.util.*;

/**
 * Builds a 3 x 9 Bingo card from a seed.
 * The layout is fixed and the numbers come from a fixed formula,
 * so the same seed always gives the same card.
 */
public class BingoCardGenerator {

    int ROWS = 3;
    int COLS = 9;

    // The fixed layout from the problem.
    // 1 means the cell gets a number, 0 means it stays empty.
    int[][] layout = {
        {1, 0, 1, 0, 1, 0, 1, 1, 0},
        {0, 1, 0, 1, 0, 1, 1, 0, 1},
        {1, 1, 1, 0, 1, 0, 0, 1, 0}
    };

    // columnCount[col] = how many numbers column col needs
    int[] columnCount;

    public BingoCardGenerator() {
        // The layout never changes, so we count each column only once.
        columnCount = new int[COLS];
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                columnCount[col] += layout[row][col];
            }
        }
    }

    public List<String> generateCard(int seed) {
        int[][] card = new int[ROWS][COLS];

        // The formula works per column, so fill the card column by column.
        for (int col = 0; col < COLS; col++) {
            fillColumn(card, col, seed);
        }

        // The answer is needed row by row.
        List<String> result = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            result.add(rowToString(card[row]));
        }
        return result;
    }

    /**
     * Writes low + offset, low + offset + 1, ... into the
     * positive cells of this column, from top to bottom.
     */
    void fillColumn(int[][] card, int col, int seed) {
        int low = col * 10 + 1;
        int offset = (seed + 7 * col) % (11 - columnCount[col]);
        int number = low + offset;

        for (int row = 0; row < ROWS; row++) {
            if (layout[row][col] == 1) {
                card[row][col] = number;
                number++;
            }
        }
    }

    // Turns {4, 0, 29, ...} into "4,0,29,..."
    String rowToString(int[] row) {
        List<String> parts = new ArrayList<>();
        for (int value : row) {
            parts.add(String.valueOf(value));
        }
        return String.join(",", parts);
    }
}
```

### Complexity

- **Time:** `O(ROWS * COLS)` per call. That is only 27 cells, so it is effectively constant.
- **Space:** `O(ROWS * COLS)` for the card grid.