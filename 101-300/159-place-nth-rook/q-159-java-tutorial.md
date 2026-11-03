# Place Nth Rook in Java

#### Problem Statement
[https://codezym.com/question/159-place-nth-rook](https://codezym.com/question/159-place-nth-rook)


## Core Idea

There are `n - 1` rooks and no two of them share a row or a column. So exactly one row is empty and exactly one column is empty. The new rook goes where this empty row and empty column meet.

Row queries only tell us about rows. Column queries only tell us about columns. So we find the missing row and the missing column separately, using the same logic for both.

Each query gives one of two hints: "every row in this range has a rook" or "the missing row is somewhere in this range". We read every query only once. The second kind of hint shrinks a search window. The first kind is marked using a **difference array**, a simple trick that marks a whole range in one step. A final walk over the rows gives the answer in `O(n + q)` time, where `q` is the number of queries.


## What Does One Query Tell Us?

Take a row query `"a,b,1,n,count"`. It counts the rooks in rows `a` to `b`.

Every row except the missing one has exactly one rook. So `count` can only have one of two values:

| count | Meaning | Name we will use |
|---|---|---|
| `b - a + 1` | every row in `a..b` has a rook, so the missing row is **not** here | **full range** |
| `b - a` | one rook is short, so the missing row **is** here | **short range** |

For example, on a `4 x 4` board, `"3,4,1,4,1"` covers 2 rows but has only 1 rook. It is a short range, so the missing row is `3` or `4`.

From now on we only talk about rows. Columns work exactly the same way with column queries `"1,n,c,d,count"`.


## Solution 1: Brute Force

Try every row as the missing row. For each try, check if every row query agrees with it.

```java
// rowQueries holds {a, b, count} for every row query
for (int row = 1; row <= n; row++) {
    boolean matchesAll = true;
    for (int[] q : rowQueries) {
        boolean inside = q[0] <= row && row <= q[1];
        int expected = inside ? q[1] - q[0] : q[1] - q[0] + 1;
        if (q[2] != expected) {
            matchesAll = false;
            break;
        }
    }
    if (matchesAll) {
        return row;
    }
}
```

**Problem:** for each row we may check every query. That is `O(n * q)`, which can reach `10^5 * 2 * 10^5 = 2 * 10^10` checks. This is far too slow.


## Solution 2: Optimized (Window + Difference Array)

Instead of testing each row against each query, we read every query once and remember just two things.

### Step 1: Short ranges shrink a window

The missing row is inside **every** short range. So it must be inside the part where all of them overlap.

We keep a window `[low, high]` that starts as `[1, n]`. For every short range `[a, b]`:

```
low  = max(low, a)
high = min(high, b)
```

This takes `O(1)` per query.

### Step 2: Full ranges mark rows as taken

Every row inside a full range has a rook, so it can never be the answer.

Marking these rows one by one is slow. A single full range can have `10^5` rows, and there can be `2 * 10^5` queries.

A difference array marks a whole range in `O(1)`:

- `diff[a] += 1` means "a full range starts here"
- `diff[b + 1] -= 1` means "that full range ended just before here"

Later we walk from row `1` to row `n` and keep a running total of `diff` (called `covered` in the code). At any row, this total is the number of full ranges covering that row. A total of `0` means the row is free.

### Step 3: Pick the first free row inside the window

While walking, the first row inside `[low, high]` with a running total of `0` is the missing row.

We walk from small to large, so if more than one row is possible we return the smallest one. Columns behave the same way, which is exactly the tie rule in the problem.

The walk starts from row `1`, not from `low`. A full range that starts before `low` can still cover rows inside the window, and we need its `+1` in the running total.

### Dry Run: Example 2

Row queries: `"1,2,1,4,2"`, `"3,4,1,4,1"`, `"3,3,1,4,1"`.

- Rows `1..2` have `2` rooks in `2` rows. Full range: `diff[1] += 1` and `diff[3] -= 1`.
- Rows `3..4` have `1` rook in `2` rows. Short range: the window becomes `[3, 4]`.
- Row `3` has `1` rook in `1` row. Full range: `diff[3] += 1` and `diff[4] -= 1`.

| Row | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| `diff` | 1 | 0 | 0 | -1 |
| Running total | 1 | 1 | 1 | 0 |
| Inside window `[3, 4]`? | no | no | yes | yes |

Row `4` is the first row inside the window with a total of `0`. So the missing row is `4`.

The column queries give column `4` in the same way. Answer: `"4,4"`.

### Classes

**`MissingLineFinder`**: rows and columns need the exact same logic. So we write it once in this class and create two objects of it, one for rows and one for columns. A **line** here just means a row or a column.

It keeps only:

- `low` and `high`: the window where the missing line must be.
- `diff`: the difference array that marks lines which surely have a rook.

We do not store the queries at all. Each query updates these fields and is then forgotten.

**`RookPlacement`**: reads each query and sends it to the right finder.

- If `c == 1` and `d == n`, it is a row query, so it goes to `rows`.
- If `a == 1` and `b == n`, it is a column query, so it goes to `columns`.

A query that covers the whole board passes both checks. Sending it to both finders is safe: its count is always `n - 1`, so it only says "the missing line is somewhere in `1..n`", which changes nothing.

### Java Code

```java
/**
 * Finds the missing line on the board.
 * A "line" means a row or a column. The same logic works for both.
 */
class MissingLineFinder {
    // The missing line must be inside [low, high]
    int low;
    int high;
    // Difference array to mark lines that surely have a rook.
    // Size n + 2 so that diff[end + 1] is safe when end = n.
    int[] diff;

    MissingLineFinder(int n) {
        low = 1;
        high = n;
        diff = new int[n + 2];
    }

    /**
     * Records one query result: lines start..end have count rooks.
     */
    void addQuery(int start, int end, int count) {
        int size = end - start + 1;

        if (count == size) {
            // Full range: every line here has a rook, so mark all of them in O(1)
            diff[start]++;
            diff[end + 1]--;
        } else {
            // Short range: the missing line is somewhere in start..end
            low = Math.max(low, start);
            high = Math.min(high, end);
        }
    }

    /**
     * Returns the smallest line inside [low, high] that no full range covers.
     */
    int findMissingLine() {
        int covered = 0; // number of full ranges covering the current line

        // Start from 1: a full range that starts before low can still cover lines inside the window
        for (int line = 1; line <= high; line++) {
            covered += diff[line];
            if (line >= low && covered == 0) {
                return line;
            }
        }
        return -1; // never happens for valid input
    }
}

public class RookPlacement {
    MissingLineFinder rows;
    MissingLineFinder columns;

    public RookPlacement(int n, String[] rectangleQueries) {
        rows = new MissingLineFinder(n);
        columns = new MissingLineFinder(n);

        for (String query : rectangleQueries) {
            String[] parts = query.split(",");
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            int c = Integer.parseInt(parts[2].trim());
            int d = Integer.parseInt(parts[3].trim());
            int count = Integer.parseInt(parts[4].trim());

            // Row query: covers all columns, so it only tells us about rows a..b
            if (c == 1 && d == n) {
                rows.addQuery(a, b, count);
            }
            // Column query: covers all rows, so it only tells us about columns c..d
            if (a == 1 && b == n) {
                columns.addQuery(c, d, count);
            }
        }
    }

    public String placeNthRook() {
        return rows.findMissingLine() + "," + columns.findMissingLine();
    }
}
```

### Complexity

- **Time:** `O(n + q)`. Each query is handled in `O(1)`, and we walk the rows and the columns once.
- **Space:** `O(n)` for the two difference arrays.