# Design a Text Editor with Cursor Operations and Basic Editing in Java

#### Problem Statement
[https://codezym.com/question/39-design-text-editor-with-cursor-edit](https://codezym.com/question/39-design-text-editor-with-cursor-edit)


We are building **NotepadEditor**, a small in-memory notepad. It stores text as lines and keeps a cursor that can move around, type text and delete characters.

The core idea is simple: **keep every line as its own `StringBuilder` in a list, and keep the cursor as two numbers, `row` and `col`.** Each operation then works on just one line. Most of this problem is about one question: where does the cursor land after each move?

No design pattern is needed here. One class with one small helper method is the clearest design. Command and Memento are the usual text editor patterns, but they exist for undo and redo, which this problem does not ask for.


## Key Rules

These are the rules that are easy to miss:

- **The cursor can sit past the end of its line.** `moveRight()` always moves one column right, even after the last character. It never jumps to the next line.
- **Moving up or down keeps the column if it can.** If the new line is shorter, the cursor lands just after its last character. The old column is not remembered: in Example 3, `(1,5)` goes up to `(0,2)` and then back down to `(1,2)`. `moveUp()` on the first row does nothing.
- **Moving down never fails.** `moveDown()` on the last row creates an empty row, so the cursor lands at column `0`. `pageDown()` creates as many rows as it needs.
- **`pageUp()` moves all the way or not at all.** If there are fewer than `linesPerPage` rows above, nothing happens.
- **Typing past the end fills the gap with `-`.** On `"abcde"` with the cursor at column `8`, `addString("pqr")` gives `"abcde---pqr"`.
- **`deleteChar()` at column `0` removes the whole row, along with its text.** The text is not moved up to the row above. The cursor goes to the end of the row above.
- **`deleteChar()` past the end** finds no character at `col - 1`, so it deletes nothing. The cursor still moves one column left.
- **`readLeft()` past the end** returns the whole line.


## Solution 1: The Whole Document as One String

The most direct picture of a document is the text itself: one `String`, with `'\n'` between the lines. So `"hi\nworld"` has two lines. The cursor is still `row` and `col`.

Before we can do anything, we have to find where the cursor's row starts. We walk the text from the beginning and jump over one `'\n'` per row.

```java
String doc = "";   // the whole document, lines joined by '\n'
int row = 0;
int col = 0;

/** Index in doc where row r starts. */
int rowStart(int r) {
    int index = 0;
    for (int i = 0; i < r; i++) {
        index = doc.indexOf('\n', index) + 1;   // jump past the next '\n'
    }
    return index;
}

/** Number of characters in row r. */
int rowLength(int r) {
    int start = rowStart(r);
    int end = doc.indexOf('\n', start);
    if (end == -1) {
        end = doc.length();   // the last row has no '\n' after it
    }
    return end - start;
}
```

Typing then cuts the document at the cursor and glues the new text in between.

```java
public void addString(String text) {
    if (text == null || text.isEmpty()) {
        return;
    }
    int start = rowStart(row);
    int length = rowLength(row);
    StringBuilder toInsert = new StringBuilder();
    for (int i = length; i < col; i++) {
        toInsert.append('-');   // fill the gap when the cursor is past the end
    }
    toInsert.append(text);
    int at = start + Math.min(col, length);
    doc = doc.substring(0, at) + toInsert + doc.substring(at);   // copies the whole document
    col += text.length();
}
```

`deleteChar()` works the same way: find the row, then cut one character out.

This gives correct answers, but it is slow:

- **Every call scans from the top.** To reach row 50,000, `rowStart()` walks past 50,000 lines. Even `moveUp()` and `moveDown()` need `rowLength()` to place the cursor, so a simple move scans too.
- **Every edit copies the whole document.** A `String` can't be changed, so adding one letter builds a new string of the full text.

So most calls cost O(N), where N is the size of the whole document, even though only one line ever changes.


## Solution 2: A List of Lines

### Idea

Every operation touches only the cursor's line, and moves go up or down one row at a time. So let's store each line on its own: `List<StringBuilder> lines`.

- `lines.get(row)` gives the cursor's line at once, with no scanning.
- An edit changes only that line. The rest of the document is never copied.

**Why `StringBuilder`?** It is a string we can change in place. `insert()` and `deleteCharAt()` edit the line directly, instead of building a new string for every change.

**Why `ArrayList`?** It gives any row by its index in O(1). Adding a row at the end, which is what `moveDown()` does, is O(1) too. Removing a row shifts the rows below it, but that only happens on a backspace at column `0`.

**Why no `Cursor` class?** The cursor is just two numbers. Every cursor rule also needs line lengths, so the cursor logic sits best right next to `lines`. A separate class would only add getters and setters.


### Which design pattern fits?

**None.** The whole state is a list of lines and two numbers. One class keeps it all in one place and is easy to follow.

**Command, not needed.** Each action could become a command object, like `MoveUpCommand` or `AddStringCommand`, with an `execute()` method. That pays off when actions must be stored, undone, redone or replayed. Here every action runs once and is forgotten. We would add a class for each of the eight actions and gain nothing.

**Memento, not needed.** Memento saves snapshots of the editor so we can go back to one later. That is also a tool for undo, and here we never go back.


### How it works

**One helper for every row change.** `moveUp()`, `moveDown()`, `pageUp()` and `pageDown()` all end the same way: go to the new row, then pull the column back if that row is too short. That is `moveToRow()`:

```java
void moveToRow(int newRow) {
    row = newRow;
    col = Math.min(col, lines.get(row).length());
}
```

`moveDown()` on the last row first adds an empty row, then calls the same helper. The new row is empty, so `col` becomes `0` with no extra code.

**Paging is a loop.** `pageDown()` simply calls `moveDown()` `linesPerPage` times. Jumping straight to `row + linesPerPage` looks faster, but it gives wrong answers. Every short row on the way pulls the cursor left, and a jump skips those rows.

Take rows `"abcde"`, `""` and `"abcde"`, with the cursor at `(0,5)` and `linesPerPage = 2`:

| Step | Cursor |
|---|---|
| start | `(0,5)` |
| `moveDown()` | `(1,0)`, because row 1 is empty |
| `moveDown()` | `(2,0)` |

The right answer is `(2,0)`. A direct jump to row 2 would wrongly give `(2,5)`.

`pageUp()` works the same way, after first checking that there are at least `linesPerPage` rows above.


### Code

```java
import java.util.*;

/**
 * An in-memory notepad.
 * The text is kept as a list of lines and the cursor is just (row, col).
 * Every operation works on the cursor's line only.
 */
public class NotepadEditor {
    // how many rows pageUp() and pageDown() move
    int linesPerPage;
    // lines.get(r) is row r. A StringBuilder lets us insert and delete characters in place.
    List<StringBuilder> lines = new ArrayList<>();
    // cursor position, both 0-based. col can be past the end of its line.
    int row = 0;
    int col = 0;

    public NotepadEditor(int linesPerPage) {
        this.linesPerPage = linesPerPage;
        lines.add(new StringBuilder()); // the editor starts with one empty line
    }

    /**
     * Inserts text at the cursor and places the cursor just after it.
     * If the cursor is past the end of the line, the gap is filled with '-' first.
     */
    public void addString(String text) {
        if (text == null || text.isEmpty()) {
            return; // nothing to insert, so no '-' filling either
        }
        StringBuilder line = lines.get(row);
        while (line.length() < col) {
            line.append('-');
        }
        line.insert(col, text);
        col += text.length();
    }

    /**
     * Works like backspace.
     * col > 0: deletes the character at col - 1 (if the line has one) and moves one column left.
     * col == 0: removes the whole current row and moves to the end of the row above.
     */
    public void deleteChar() {
        if (col > 0) {
            StringBuilder line = lines.get(row);
            if (col - 1 < line.length()) {
                line.deleteCharAt(col - 1);
            }
            col--;
        } else if (row > 0) {
            lines.remove(row);
            row--;
            col = lines.get(row).length();
        }
        // at (0, 0) there is nothing to delete
    }

    /** Moves one column left. Does nothing at column 0. */
    public void moveLeft() {
        if (col > 0) {
            col--;
        }
    }

    /** Moves one column right, even past the last character. The row never changes. */
    public void moveRight() {
        col++;
    }

    /** Moves one row up. Does nothing on the first row. */
    public void moveUp() {
        if (row > 0) {
            moveToRow(row - 1);
        }
    }

    /** Moves one row down. On the last row, an empty row is created first. */
    public void moveDown() {
        if (row == lines.size() - 1) {
            // the new row is empty, so moveToRow() puts the cursor at column 0
            lines.add(new StringBuilder());
        }
        moveToRow(row + 1);
    }

    /** Same as calling moveUp() linesPerPage times, but only if there are enough rows above. */
    public void pageUp() {
        if (row < linesPerPage) {
            return;
        }
        for (int i = 0; i < linesPerPage; i++) {
            moveUp();
        }
    }

    /** Same as calling moveDown() linesPerPage times. Missing rows are created on the way. */
    public void pageDown() {
        for (int i = 0; i < linesPerPage; i++) {
            moveDown();
        }
    }

    /** Text of the current line before the cursor, or the whole line if the cursor is past its end. */
    public String readLeft() {
        StringBuilder line = lines.get(row);
        return line.substring(0, Math.min(col, line.length()));
    }

    /**
     * Puts the cursor on another row and keeps its column if it can.
     * If that row is shorter, the cursor lands just after its last character.
     */
    void moveToRow(int newRow) {
        row = newRow;
        col = Math.min(col, lines.get(row).length());
    }
}
```


### Complexity

Let `L` be the length of the cursor's line (after any `-` filling), `T` the length of the inserted text, `K` the value of `linesPerPage` and `R` the number of rows.

| Method | Time |
|---|---|
| `addString` | O(L + T) |
| `deleteChar` | O(L), or O(R) when it removes a row |
| `moveLeft`, `moveRight`, `moveUp`, `moveDown` | O(1) |
| `pageUp`, `pageDown` | O(K) |
| `readLeft` | O(L) |

Space is O(total characters + R).