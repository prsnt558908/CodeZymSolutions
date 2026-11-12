# Multiply Sparse Matrices in Java

#### Problem Statement

[https://codezym.com/question/195-multiply-sparse-matrices](https://codezym.com/question/195-multiply-sparse-matrices)

The key is to skip zeros before multiplying. Store only the nonzero entries from each row of `B`. For every nonzero value `A[i][k]`, visit those saved entries in row `k` of `B` and add their contributions to the answer. We build one result row at a time.


## 1. Start with ordinary multiplication

Let `A` have `m` rows and `n` columns. Let `B` have `n` rows and `p` columns. The answer has `m` rows and `p` columns.

For each answer cell, multiply matching values from a row of `A` and a column of `B`, then add them.

Assuming the input strings have already been converted into integer arrays:

```java
long[][] result = new long[m][p];

for (int i = 0; i < m; i++) {
    for (int j = 0; j < p; j++) {
        for (int k = 0; k < n; k++) {
            result[i][j] += (long) A[i][k] * B[k][j];
        }
    }
}
```

This takes `O(m * n * p)` time. It also multiplies values when either side is zero, which adds nothing to the answer.


## 2. Keep only useful entries of B

Skipping a zero in `A` avoids scanning a whole row of `B`. However, a nonzero value in `A` would still make us scan that row's zeros.

We can avoid both kinds of wasted work:

1. Read `B` once. For each row, save only its nonzero values and their column positions.
2. Read one row of `A`. Skip every zero.
3. For each remaining value at position `k`, visit only the saved entries in `B[k]`.
4. Add each product to its answer column, then convert the completed row into a comma-separated string.

`MatrixEntry` holds a column and its value, so the code can use clear names instead of numbered pair positions. `nonZeroRows` is a list of these entry lists, indexed by the row of `B`.

`resultRow` stores the current answer row. It keeps column order and includes zeros, which the required output must contain. There is no need to store a second full matrix for `A` or the numeric answer.


## 3. A small walkthrough

```text
matrixA = ["2,0,-1"]
matrixB = ["0,3", "7,0", "4,0"]
```

The saved entries of `B` are:

- Row `0`: column `1`, value `3`.
- Row `1`: column `0`, value `7`.
- Row `2`: column `0`, value `4`.

Start the answer row at `[0, 0]`.

The value `2` uses row `0` of `B`, adding `2 * 3 = 6` to column `1`. The next value is zero, so we skip it. The value `-1` uses row `2` of `B`, adding `-1 * 4 = -4` to column `0`.

The returned list is `["-4,6"]`.


## 4. Why it works

Every answer cell needs the sum of `A[i][k] * B[k][j]` over all `k`. If either value is zero, that term can be skipped without changing the sum.

For every remaining term, the algorithm visits the corresponding entry in `B[k]` exactly once and adds the product to column `j`. Therefore, each cell receives exactly the contributions it needs. Negative values and contributions that cancel each other work the same way.


## 5. Complexity and number limits

Let `zB` be the number of nonzero entries in `B`. Let `T` count the pairs `A[i][k]` and `B[k][j]` where both values are nonzero.

**Time: `O(m * n + n * p + T + m * p)`.** Reading the inputs takes `O(m * n + n * p)`. Multiplication takes `O(T)`, and producing all output cells takes `O(m * p)`. If both matrices are dense, the worst case remains `O(m * n * p)`.

**Extra space: `O(n + zB + p)`, excluding the input and returned strings.** This includes the saved rows of `B`, one split input row, and one answer row. The returned strings represent `m * p` values.

A result cell can reach `1000 * 1000000 * 1000000 = 10^15` in magnitude. Use `long` for the sum and cast before multiplication, so the product is not first computed as an overflowing `int`.


## 6. Complete solution

```java
import java.util.ArrayList;
import java.util.List;

class MatrixEntry {
    int column;
    int value;

    MatrixEntry(int column, int value) {
        this.column = column;
        this.value = value;
    }
}

public class MultiplySparseMatrices {
    public MultiplySparseMatrices() {
    }

    /** Returns the product as comma-separated rows. */
    public List<String> multiplySparseMatrices(
            List<String> matrixA, List<String> matrixB) {
        int columns = matrixB.get(0).split(",").length;
        List<List<MatrixEntry>> nonZeroRows = new ArrayList<>();

        // Save only the nonzero entries in each row of B.
        for (String row : matrixB) {
            String[] values = row.split(",");
            List<MatrixEntry> entries = new ArrayList<>();

            for (int j = 0; j < values.length; j++) {
                int value = Integer.parseInt(values[j].trim());
                if (value != 0) {
                    entries.add(new MatrixEntry(j, value));
                }
            }
            nonZeroRows.add(entries);
        }

        List<String> result = new ArrayList<>();

        // Build and format one answer row at a time.
        for (String row : matrixA) {
            String[] values = row.split(",");
            long[] resultRow = new long[columns];

            for (int k = 0; k < values.length; k++) {
                int value = Integer.parseInt(values[k].trim());
                if (value == 0) {
                    continue;
                }

                for (MatrixEntry entry : nonZeroRows.get(k)) {
                    // Convert before multiplying to avoid int overflow.
                    resultRow[entry.column] += (long) value * entry.value;
                }
            }

            // Include every column, even when its value is zero.
            StringBuilder rowText = new StringBuilder();
            for (int j = 0; j < columns; j++) {
                if (j > 0) {
                    rowText.append(',');
                }
                rowText.append(resultRow[j]);
            }
            result.add(rowText.toString());
        }

        return result;
    }
}
```
