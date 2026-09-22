# Excel Cell Values and Sum Formulas in Java

#### Problem Statement
[https://codezym.com/question/405-excel-cell-values-and-sum-formula](https://codezym.com/question/405-excel-cell-values-and-sum-formula)

Think of this problem as building a tiny version of Excel. Every cell holds either a plain number or a formula that adds up other cells and ranges. The real challenge is not storing the numbers, it is keeping every formula correct as the numbers underneath it keep changing.

We can solve this with nothing more than a grid, a few lists, and a set. We will start with the most direct approach, where every `get` call recalculates a formula from scratch. Then we will see why that gets wasteful, and fix it so a change only recalculates the formulas that are actually affected by it.

## Approach 1: Recalculate Everything on Get

Since the number of rows and columns is small and known upfront, we can lay out the whole sheet as a plain 2D grid, one slot per cell. Any cell can then be reached directly using its row and column, no extra lookup needed.

A formula cell does not need to store a number at all. It can just store a recipe, the list of ranges it needs to add up. Whenever someone asks for its value, we follow that recipe and add up whatever the referenced cells currently hold.

Since a cell can be a plain number one moment and a formula the next, we wrap both possibilities in one small `Cell` class. It holds a `value` for the plain case and a list of `ranges` for the formula case. When `ranges` is null, the cell is just a number and we use `value` directly.

To read a formula cell, we loop through every range in its recipe. For every cell inside that range, we ask for its value again using the same method. If that cell is itself a formula, the same process repeats one level deeper. The problem guarantees formulas never refer back to themselves, directly or indirectly, so this recursion always comes to an end.

This approach is easy to get right because nothing needs to be kept in sync. Every `get` looks at the live formulas and produces a fresh answer using today's numbers. The catch is repeated work. If the same formula cell is read many times, or if formulas are stacked on top of each other, we redo the same addition again and again instead of remembering the answer from last time.

```java
import java.util.*;

public class Excel {

    // A single spreadsheet cell.
    // If `ranges` is null the cell just holds a plain number in `value`.
    // Otherwise the cell holds a sum formula described by `ranges`.
    private static class Cell {
        int value;
        List<int[]> ranges; // each entry: {row1, col1, row2, col2}
    }

    private final Cell[][] grid; // grid[row][col], row is 1-indexed, col is 0-indexed ('A' -> 0)

    public Excel(int H, char W) {
        int totalCols = W - 'A' + 1;
        grid = new Cell[H + 1][totalCols];
        for (int r = 1; r <= H; r++) {
            for (int c = 0; c < totalCols; c++) {
                grid[r][c] = new Cell(); // starts at value 0, no formula
            }
        }
    }

    public void set(int row, char column, int val) {
        Cell cell = grid[row][column - 'A'];
        cell.value = val;
        cell.ranges = null; // a direct set always wipes out any old formula
    }

    public int get(int row, char column) {
        return resolve(grid[row][column - 'A']);
    }

    public int sum(int row, char column, List<String> numbers) {
        List<int[]> ranges = new ArrayList<>();
        for (String token : numbers) {
            ranges.add(parseRange(token));
        }
        Cell cell = grid[row][column - 'A'];
        cell.ranges = ranges;
        return resolve(cell);
    }

    // Computes the current value of a cell, recursively expanding formulas.
    private int resolve(Cell cell) {
        if (cell.ranges == null) {
            return cell.value;
        }
        int total = 0;
        for (int[] range : cell.ranges) {
            for (int r = range[0]; r <= range[2]; r++) {
                for (int c = range[1]; c <= range[3]; c++) {
                    total += resolve(grid[r][c]);
                }
            }
        }
        return total;
    }

    // Parses "B2" or "B2:D5" into {row1, col1, row2, col2}.
    private int[] parseRange(String token) {
        String[] parts = token.split(":");
        int[] topLeft = parseCell(parts[0]);
        int[] bottomRight = parts.length > 1 ? parseCell(parts[1]) : topLeft;
        return new int[]{topLeft[0], topLeft[1], bottomRight[0], bottomRight[1]};
    }

    // Parses "D4" into {row=4, col=3}.
    private int[] parseCell(String token) {
        char column = token.charAt(0);
        int row = Integer.parseInt(token.substring(1));
        return new int[]{row, column - 'A'};
    }
}
```

## Approach 2: Track Dependents and Update Eagerly

The repeated work in Approach 1 happens because a formula cell never remembers its own answer. We can fix this by having every cell always hold its current value, even formula cells, and only recompute a formula when something it depends on actually changes.

For that to work, each cell needs to know who depends on it. If cell C sums up cell B, then B needs a way to know that C depends on it. We keep this as a small set on every cell called `dependents`. It answers one question only, which formulas need to be refreshed when this cell changes. A set is exactly right here, we never look these cells up by name, we only ever need to walk through all of them.

Whenever a cell's value changes, whether from a direct `set` or from a `sum`, we walk its `dependents` set, recompute each one, and then walk that cell's own `dependents` too. This lets the change ripple outward until every affected formula is refreshed. Since formulas can never form a cycle, this walk always finishes.

One detail matters a lot here. Before a cell is given a new value or a new formula, we must first remove it from the `dependents` set of whatever it used to depend on. Skipping this step would leave behind a stale link, so a cell could keep getting refreshed because of a dependency it no longer has. If that cell had also been reset to a plain number, the refresh would find no formula to work with and break.

With this change, `get` simply reads the stored value, no recalculation needed. All the work moves into `set` and `sum`, and even there it only touches the formulas that are genuinely affected.

```java
import java.util.*;

public class Excel {

    // A single spreadsheet cell.
    // `value` always holds the up-to-date result, even for formula cells.
    // `ranges` is null for a plain number cell, or the formula's ranges otherwise.
    // `dependents` are the formula cells that read this cell, kept so we know
    // who needs to be refreshed whenever this cell's value changes.
    private static class Cell {
        int value;
        List<int[]> ranges;
        Set<Cell> dependents = new HashSet<>();
    }

    private final Cell[][] grid;

    public Excel(int H, char W) {
        int totalCols = W - 'A' + 1;
        grid = new Cell[H + 1][totalCols];
        for (int r = 1; r <= H; r++) {
            for (int c = 0; c < totalCols; c++) {
                grid[r][c] = new Cell();
            }
        }
    }

    public void set(int row, char column, int val) {
        Cell cell = grid[row][column - 'A'];
        detachFormula(cell);
        cell.value = val;
        notifyDependents(cell);
    }

    public int get(int row, char column) {
        return grid[row][column - 'A'].value; // already up to date, O(1)
    }

    public int sum(int row, char column, List<String> numbers) {
        Cell cell = grid[row][column - 'A'];
        detachFormula(cell);

        List<int[]> ranges = new ArrayList<>();
        for (String token : numbers) {
            ranges.add(parseRange(token));
        }
        cell.ranges = ranges;
        attachFormula(cell);

        recompute(cell);
        notifyDependents(cell);
        return cell.value;
    }

    // Registers `cell` as a dependent of every cell its formula reads from.
    private void attachFormula(Cell cell) {
        for (int[] range : cell.ranges) {
            for (int r = range[0]; r <= range[2]; r++) {
                for (int c = range[1]; c <= range[3]; c++) {
                    grid[r][c].dependents.add(cell);
                }
            }
        }
    }

    // Undoes attachFormula: removes `cell` from every cell it used to depend on,
    // then clears its formula. This must run before a formula is replaced or
    // removed, otherwise a stale dependency link would stay around forever.
    private void detachFormula(Cell cell) {
        if (cell.ranges == null) return;
        for (int[] range : cell.ranges) {
            for (int r = range[0]; r <= range[2]; r++) {
                for (int c = range[1]; c <= range[3]; c++) {
                    grid[r][c].dependents.remove(cell);
                }
            }
        }
        cell.ranges = null;
    }

    // Recalculates a formula cell's value from its current ranges.
    private void recompute(Cell cell) {
        int total = 0;
        for (int[] range : cell.ranges) {
            for (int r = range[0]; r <= range[2]; r++) {
                for (int c = range[1]; c <= range[3]; c++) {
                    total += grid[r][c].value;
                }
            }
        }
        cell.value = total;
    }

    // Whenever `cell`'s value changes, every formula that depends on it must
    // be refreshed, and that refresh can ripple further outward.
    private void notifyDependents(Cell cell) {
        for (Cell dependent : cell.dependents) {
            recompute(dependent);
            notifyDependents(dependent);
        }
    }

    // Parses "B2" or "B2:D5" into {row1, col1, row2, col2}.
    private int[] parseRange(String token) {
        String[] parts = token.split(":");
        int[] topLeft = parseCell(parts[0]);
        int[] bottomRight = parts.length > 1 ? parseCell(parts[1]) : topLeft;
        return new int[]{topLeft[0], topLeft[1], bottomRight[0], bottomRight[1]};
    }

    // Parses "D4" into {row=4, col=3}.
    private int[] parseCell(String token) {
        char column = token.charAt(0);
        int row = Integer.parseInt(token.substring(1));
        return new int[]{row, column - 'A'};
    }
}
```

Both approaches always agree on the final numbers. The first one is simpler to reason about since nothing is cached. The second one avoids repeating the same addition when a formula cell is read again and again, and only pays extra work at the exact moment something actually changes.