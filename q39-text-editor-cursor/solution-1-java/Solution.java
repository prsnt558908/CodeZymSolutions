import java.util.*;

public class NotepadEditor {

    // Number of lines to move on pageUp/pageDown
    private final int linesPerPage;

    // Document represented as list of mutable lines
    private final List<StringBuilder> lines;

    // Cursor position (0-based)
    private int row;
    private int col;

    public NotepadEditor(int linesPerPage) {
        this.linesPerPage = linesPerPage;
        this.lines = new ArrayList<>();
        // Initially: one empty line, cursor at (0, 0)
        this.lines.add(new StringBuilder());
        this.row = 0;
        this.col = 0;
    }

    /**
     * Insert a string at the current cursor position.
     * If cursor is beyond last character of the line, fill the gap with '-' first.
     * Cursor ends just after the last inserted character.
     */
    public void addString(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        StringBuilder line = lines.get(row);
        int len = line.length();

        // Hyphen-fill if cursor is beyond last character
        if (col > len) {
            int toFill = col - len;
            for (int i = 0; i < toFill; i++) {
                line.append('-');
            }
            len = line.length(); // now len == col
        }

        // Insert the text at current column
        line.insert(col, text);

        // Cursor moves just after the last inserted character
        col += text.length();
    }

    /**
     * Delete character at column-1 relative to cursor.
     * - If col > 0: delete char at col-1 within current line (if it exists) and move cursor to col-1.
     * - If col == 0 and row > 0: remove current row, move cursor to end of previous row.
     * - If at (0, 0): no-op.
     */
    public void deleteChar() {
        // If at very start of document, nothing to delete
        if (row == 0 && col == 0) {
            return;
        }

        if (col > 0) {
            StringBuilder line = lines.get(row);
            int indexToDelete = col - 1;

            // Delete if within current line
            if (indexToDelete >= 0 && indexToDelete < line.length()) {
                line.deleteCharAt(indexToDelete);
            }

            // Cursor moves left by one column (even if no character actually deleted)
            col--;
        } else {
            // col == 0 and row > 0
            // Remove current line and move cursor to end of previous line
            lines.remove(row);
            row--;
            StringBuilder previousLine = lines.get(row);
            col = previousLine.length();
        }
    }

    /**
     * Move cursor one position left within the current line.
     * - If col > 0: col--.
     * - If at column 0: no-op.
     * Cursor never moves to a different line.
     */
    public void moveLeft() {
        if (col > 0) {
            col--;
        }
        // else: at column 0, do nothing
    }

    /**
     * Move cursor one position right within the current line.
     * - Always increments col by 1, even if already after the last character.
     *   (Cursor can move arbitrarily far to the right within this line.
     *    addString() will hyphen-fill any gap if needed.)
     * Cursor never moves to a different line.
     */
    public void moveRight() {
        col++;
    }

    /**
     * Move cursor one line up, trying to keep the same column.
     * If target line is shorter, place cursor just after last character of that line.
     * If already on first line, no-op.
     */
    public void moveUp() {
        if (row == 0) {
            // No line above
            return;
        }

        row--;
        StringBuilder lineAbove = lines.get(row);
        int len = lineAbove.length();

        // Try to keep same column; clamp to line length (just after last character)
        if (col > len) {
            col = len;
        }
    }

    /**
     * Move cursor one line down, trying to keep the same column.
     * If there is no row below, create an empty row below and place cursor at column 0.
     * If there is a row below but shorter, clamp to that line's length.
     */
    public void moveDown() {
        if (row + 1 < lines.size()) {
            // Move to existing next line
            row++;
            StringBuilder lineBelow = lines.get(row);
            int len = lineBelow.length();
            if (col > len) {
                col = len;
            }
        } else {
            // No row below: create a new empty line and move to its beginning
            lines.add(new StringBuilder());
            row++;
            col = 0;
        }
    }

    /**
     * Move cursor up by up to linesPerPage lines.
     * If there are not enough lines above to complete a full page jump, this is a no-op.
     * Otherwise, equivalent to calling moveUp() linesPerPage times.
     */
    public void pageUp() {
        if (linesPerPage <= 0) {
            return;
        }

        // If not enough lines above for a full jump, no-op
        if (row < linesPerPage) {
            return;
        }

        for (int i = 0; i < linesPerPage; i++) {
            moveUp(); // safe: we know we can go up linesPerPage times
        }
    }

    /**
     * Move cursor down by up to linesPerPage lines.
     * Equivalent to calling moveDown() linesPerPage times.
     * If there are not enough rows below, new empty rows are created and cursor
     * is placed at the beginning of the final row.
     */
    public void pageDown() {
        if (linesPerPage <= 0) {
            return;
        }

        for (int i = 0; i < linesPerPage; i++) {
            moveDown();
        }
    }

    /**
     * Return characters from index 0 up to cursorCol - 1 on the current line.
     * If the logical line is shorter than cursorCol, return up to the last character.
     */
    public String readLeft() {
        StringBuilder line = lines.get(row);
        int len = line.length();

        // Number of characters to return is min(col, len)
        int end = Math.min(col, len);
        return line.substring(0, end);
    }
}
