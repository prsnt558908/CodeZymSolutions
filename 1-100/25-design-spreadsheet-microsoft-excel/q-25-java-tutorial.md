# Design Spreadsheet like Microsoft Excel in Java

#### Problem Statement
[https://codezym.com/question/25-design-spreadsheet-microsoft-excel](https://codezym.com/question/25-design-spreadsheet-microsoft-excel)

## Core Idea

A spreadsheet is a grid of cells. Every filled cell has two parts: its own **text** and a **style** (font name, font size, bold, italic).

Text is different in almost every cell, but styles repeat a lot. A real sheet can easily have thousands of cells that are all `calibri-11`.

So the best fit here is the **Flyweight** design pattern: when many objects carry the same data, keep one shared copy of that data and let all of them point to it. A small **Factory** (`StyleFactory`) creates each unique style once and hands out the same object after that. Flyweight together with this factory is the best combination for this problem.

The second idea is about inserting rows and columns. Instead of moving cells around, every row and column gets a permanent id, and we keep an ordered list of these ids. Inserting a row or column just adds one id to a list. No cell ever moves.

We will start with a simple 2D grid, look at where it wastes time and memory, and then fix those problems one by one.

## Solution 1: Simple 2D Grid

The most direct way is to store the sheet exactly the way it looks: a list of rows, where each row is a list of cells.

- `grid.get(r).get(c)` is the cell at `(r, c)`. `null` means the cell is empty.
- **Constructor**: start with 5 columns, then add 5 empty rows.
- **addRow(index)**: create a row full of `null` and insert it at `index`. `List.add(index, item)` shifts every row after it down by one for us.
- **addColumn(index)**: insert one `null` at `index` in **every** row.
- **addEntry**: create a new `Cell` and put it in the grid. An old entry in that cell is simply replaced.
- **getEntry**: return the formatted cell, or `""` if it is empty.

The `Cell` class holds the text and all the style fields, and builds the output `text-fontName-fontSize[-b][-i]`.

### Code

```java
import java.util.ArrayList;
import java.util.List;

/**
 * One filled cell. Every cell keeps its own full copy of the style.
 */
class Cell {
    String text;
    String fontName;
    int fontSize;
    boolean isBold;
    boolean isItalic;

    Cell(String text, String fontName, int fontSize, boolean isBold, boolean isItalic) {
        this.text = text;
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.isBold = isBold;
        this.isItalic = isItalic;
    }

    /** Returns "text-fontName-fontSize[-b][-i]" */
    String format() {
        String result = text + "-" + fontName + "-" + fontSize;
        if (isBold) {
            result += "-b";
        }
        if (isItalic) {
            result += "-i";
        }
        return result;
    }
}

public class Spreadsheet {
    // grid.get(r).get(c) is the cell at (r, c), null means empty
    List<List<Cell>> grid = new ArrayList<>();
    int columns = 5;

    public Spreadsheet() {
        for (int r = 0; r < 5; r++) {
            addRow(r);
        }
    }

    public void addRow(int index) {
        List<Cell> newRow = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            newRow.add(null);
        }
        // rows at index, index+1, ... shift down by 1
        grid.add(index, newRow);
    }

    public void addColumn(int index) {
        // in every row, cells at index, index+1, ... shift right by 1
        for (List<Cell> row : grid) {
            row.add(index, null);
        }
        columns++;
    }

    public void addEntry(int row, int column, String text, String fontName, int fontSize, boolean isBold, boolean isItalic) {
        grid.get(row).set(column, new Cell(text, fontName, fontSize, isBold, isItalic));
    }

    public String getEntry(int row, int column) {
        Cell cell = grid.get(row).get(column);
        return cell == null ? "" : cell.format();
    }
}
```

### Problems with this approach

1. **addColumn is slow**: it inserts into every row, and each insert shifts all the cells to its right. One new column costs O(rows × columns) work.
2. **Styles are copied into every cell**: if 10,000 cells use `calibri-11-b`, then `calibri`, `11` and `true` are stored 10,000 times.
3. **Empty cells still take space**: every empty cell is a `null` slot in the grid. A big sheet that is mostly empty still pays for every slot.

## Solution 2: Flyweight + Row and Column Ids

We fix these problems in two parts.

### Part 1: Stop moving cells (fixes problems 1 and 3)

Give every row a permanent id when it is created: 0, 1, 2 and so on. Do the same for columns.

Keep a list `rowIds`, where `rowIds.get(i)` is the id of the row that is **currently** at position `i`. Keep `colIds` the same way for columns.

- **Insert a row**: add one new id into `rowIds` at `index`. That is all. No cell is touched.
- **Store a cell**: save it in a map using its permanent key `"rowId,colId"`. Empty cells are not stored at all.
- **Read a cell**: turn the position into ids first, then look it up in the map.

The constructor simply calls `addRow` and `addColumn` 5 times each. Here is a small example:

```text
Start:            rowIds = [0, 1, 2, 3, 4]      colIds = [0, 1, 2, 3, 4]

addEntry(1, 1, "x", "calibri", 10, false, true)
                  position (1, 1) -> ids (1, 1) -> saved with key "1,1"

addRow(0):        rowIds = [5, 0, 1, 2, 3, 4]
                  the new row gets id 5 and goes to position 0

getEntry(2, 1):   rowIds.get(2) = 1, colIds.get(1) = 1
                  key "1,1" -> "x-calibri-10-i"
```

The cell moved from row 1 to row 2, but we never touched it. Only the list of ids changed.

### Part 2: Share styles with Flyweight (fixes problem 2)

Split every cell into two parts:

- **Shared part**: the style (`fontName`, `fontSize`, `isBold`, `isItalic`). It lives in a `CellStyle` object.
- **Own part**: the text. It lives in the `Cell`, together with a reference to its shared `CellStyle`.

`StyleFactory` keeps a map from a style key (like `"tahoma-24-true-false"`) to its `CellStyle` object. When a cell needs a style, the factory returns the saved object if it already exists. If not, it creates the style once and saves it for next time.

So 10,000 cells with the same style now share **one** `CellStyle` object.

One important rule: a `CellStyle` must never be changed after it is created, because many cells point to it. To give a cell a different style, we simply point it to a different shared style.

Real Excel works the same way. In an `.xlsx` file, a cell does not keep its own font details. It keeps a small style number that points into one shared list of styles.

### Why Flyweight and not other patterns?

You can solve this problem without any design pattern, as Solution 1 shows. But for a big sheet, repeating the same style in every cell wastes a lot of memory. Sharing repeated data is exactly what Flyweight is for, so it is the right tool here. The factory part makes sure the same style is never created twice.

Two other patterns may look good on paper, but they don't fit well here:

- **Decorator**: bold and italic sound like "decorations" on text, so wrapping the text in a `BoldDecorator` and an `ItalicDecorator` looks tempting. But that adds **extra** objects to every cell, which is the opposite of what we want. Bold and italic are just two yes/no flags that add `-b` and `-i` to the output, so two booleans are enough.
- **Builder**: `addEntry` takes 7 values, so a Builder may look useful. But all the values always arrive together in one call and none of them are optional, so a normal constructor is simpler.

### Main classes and data structures

| Name | What it holds | Why we need it |
|---|---|---|
| `CellStyle` | font name, size, bold, italic | The shared flyweight. Builds the style part of the output. |
| `StyleFactory` | `Map<String, CellStyle>` | Makes sure each unique style is created only once. |
| `Cell` | text and a reference to a `CellStyle` | One filled cell. Builds the full output string. |
| `rowIds`, `colIds` | `List<Integer>` | A list can insert at any position and read by position, which is exactly what row and column inserts need. |
| `cells` | `Map<String, Cell>` | Stores only filled cells and finds any cell in O(1) using its permanent key. |

### Code

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flyweight: one shared object for each unique
 * (fontName, fontSize, isBold, isItalic) combination.
 * Many cells point to the same object, so never change it after creation.
 */
class CellStyle {
    String fontName;
    int fontSize;
    boolean isBold;
    boolean isItalic;

    CellStyle(String fontName, int fontSize, boolean isBold, boolean isItalic) {
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.isBold = isBold;
        this.isItalic = isItalic;
    }

    /** Style part of the output, for example "tahoma-24-b-i" */
    String format() {
        String result = fontName + "-" + fontSize;
        if (isBold) {
            result += "-b";
        }
        if (isItalic) {
            result += "-i";
        }
        return result;
    }
}

/**
 * Flyweight factory: creates each unique style only once
 * and hands out the same object every time after that.
 */
class StyleFactory {
    // style key -> shared style object
    Map<String, CellStyle> styles = new HashMap<>();

    CellStyle getStyle(String fontName, int fontSize, boolean isBold, boolean isItalic) {
        String key = fontName + "-" + fontSize + "-" + isBold + "-" + isItalic;
        if (!styles.containsKey(key)) {
            styles.put(key, new CellStyle(fontName, fontSize, isBold, isItalic));
        }
        return styles.get(key);
    }
}

/**
 * One filled cell: its own text plus a reference to a shared style.
 */
class Cell {
    String text;
    CellStyle style;

    Cell(String text, CellStyle style) {
        this.text = text;
        this.style = style;
    }

    /** Returns "text-fontName-fontSize[-b][-i]" */
    String format() {
        return text + "-" + style.format();
    }
}

public class Spreadsheet {
    // rowIds.get(i) is the permanent id of the row now at position i
    List<Integer> rowIds = new ArrayList<>();
    // colIds.get(j) is the permanent id of the column now at position j
    List<Integer> colIds = new ArrayList<>();
    int nextRowId = 0;
    int nextColId = 0;

    // only filled cells are stored, key is "rowId,colId"
    Map<String, Cell> cells = new HashMap<>();
    StyleFactory styleFactory = new StyleFactory();

    public Spreadsheet() {
        for (int i = 0; i < 5; i++) {
            addRow(i);
            addColumn(i);
        }
    }

    /** The new row gets a fresh id. No existing cell is touched. */
    public void addRow(int index) {
        rowIds.add(index, nextRowId);
        nextRowId++;
    }

    /** The new column gets a fresh id. No existing cell is touched. */
    public void addColumn(int index) {
        colIds.add(index, nextColId);
        nextColId++;
    }

    public void addEntry(int row, int column, String text, String fontName, int fontSize, boolean isBold, boolean isItalic) {
        CellStyle style = styleFactory.getStyle(fontName, fontSize, isBold, isItalic);
        cells.put(cellKey(row, column), new Cell(text, style));
    }

    public String getEntry(int row, int column) {
        Cell cell = cells.get(cellKey(row, column));
        return cell == null ? "" : cell.format();
    }

    /** Converts a position (row, column) into the permanent key of that cell */
    String cellKey(int row, int column) {
        return rowIds.get(row) + "," + colIds.get(column);
    }
}
```

## Complexity

`R` = number of rows, `C` = number of columns.

| Operation | Solution 1 | Solution 2 |
|---|---|---|
| `addRow` | O(R + C) | O(R) |
| `addColumn` | O(R × C) | O(C) |
| `addEntry` | O(1) | O(1) |
| `getEntry` | O(1) | O(1) |
| Memory | R × C slots, plus a full style copy in every filled cell | R + C ids, only filled cells, one object per unique style |

In Solution 2, `addRow` is still O(R) because inserting into a list shifts the ids after it. But those are just small numbers, and the cost no longer depends on how many columns or cells the sheet has.