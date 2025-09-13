import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spreadsheet data model (5x5 initial, 0-based indices).
 * - addRow(index): inserts an empty row and shifts rows at/after index down by 1.
 * - addColumn(index): inserts an empty column and shifts columns at/after index right by 1.
 * - addEntry(r,c,text,fontName,fontSize,isBold,isItalic): creates/replaces content at (r,c).
 *   * Constraints: text has no '-', fontName is [a-z]+, 8 <= fontSize <= 72.
 * - getEntry(r,c): "text-fontName-fontSize[-b][-i]" or "" if cell empty.
 *
 * Single-threaded, deterministic behavior.
 */
public class Spreadsheet {

    private static final int INITIAL_SIZE = 5;

    private final List<List<Cell>> grid;

    public Spreadsheet() {
        grid = new ArrayList<>(INITIAL_SIZE);
        for (int r = 0; r < INITIAL_SIZE; r++) {
            // Start with 5 columns, all cells empty (null entries are considered empty)
            grid.add(new ArrayList<>(Collections.nCopies(INITIAL_SIZE, (Cell) null)));
        }
    }

    /** Insert a new empty row at index; 0 <= index <= currentRows */
    public void addRow(int index) {
        int currentCols = getCurrentCols();
        validateAddRowIndex(index);
        grid.add(index, new ArrayList<>(Collections.nCopies(currentCols, (Cell) null)));
    }

    /** Insert a new empty column at index; 0 <= index <= currentCols */
    public void addColumn(int index) {
        validateAddColumnIndex(index);
        for (List<Cell> row : grid) {
            row.add(index, null);
        }
    }

    /**
     * Create/replace the entry at (row, column) with given text and style.
     * Validity: 0 <= row < currentRows && 0 <= column < currentCols
     * Constraints:
     * - text contains no '-' (hyphen)
     * - fontName matches [a-z]+
     * - 8 <= fontSize <= 72
     */
    public void addEntry(int row, int column,
                         String text,
                         String fontName,
                         int fontSize,
                         boolean isBold,
                         boolean isItalic) {

        validateCellPosition(row, column);
        validateEntryInput(text, fontName, fontSize);

        grid.get(row).set(column, new Cell(text, fontName, fontSize, isBold, isItalic));
    }

    /**
     * Return serialized content at (row, column) as:
     * "text-fontName-fontSize[-b][-i]"
     * If empty, return "".
     * Validity: 0 <= row < currentRows && 0 <= column < currentCols
     */
    public String getEntry(int row, int column) {
        validateCellPosition(row, column);
        Cell cell = grid.get(row).get(column);
        if (cell == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(cell.text).append('-')
          .append(cell.fontName).append('-')
          .append(cell.fontSize);

        if (cell.isBold) sb.append("-b");
        if (cell.isItalic) sb.append("-i");

        return sb.toString();
    }

    // ---------- Helpers ----------

    private int getCurrentRows() {
        return grid.size();
    }

    private int getCurrentCols() {
        return grid.isEmpty() ? 0 : grid.get(0).size();
    }

    private void validateAddRowIndex(int index) {
        int rows = getCurrentRows();
        if (index < 0 || index > rows) {
            throw new IllegalArgumentException("addRow index out of range: " + index + " (rows=" + rows + ")");
        }
    }

    private void validateAddColumnIndex(int index) {
        int cols = getCurrentCols();
        if (index < 0 || index > cols) {
            throw new IllegalArgumentException("addColumn index out of range: " + index + " (cols=" + cols + ")");
        }
    }

    private void validateCellPosition(int row, int column) {
        int rows = getCurrentRows();
        int cols = getCurrentCols();
        if (row < 0 || row >= rows) {
            throw new IllegalArgumentException("Row out of range: " + row + " (rows=" + rows + ")");
        }
        if (column < 0 || column >= cols) {
            throw new IllegalArgumentException("Column out of range: " + column + " (cols=" + cols + ")");
        }
    }

    private void validateEntryInput(String text, String fontName, int fontSize) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (text.indexOf('-') >= 0) {
            throw new IllegalArgumentException("text must not contain hyphen '-': " + text);
        }
        if (fontName == null || !fontName.matches("[a-z]+")) {
            throw new IllegalArgumentException("fontName must be lowercase a-z only: " + fontName);
        }
        if (fontSize < 8 || fontSize > 72) {
            throw new IllegalArgumentException("fontSize must be in [8,72]: " + fontSize);
        }
    }

    // ---------- Cell model ----------

    private static final class Cell {
        final String text;
        final String fontName;
        final int fontSize;
        final boolean isBold;
        final boolean isItalic;

        Cell(String text, String fontName, int fontSize, boolean isBold, boolean isItalic) {
            this.text = text;
            this.fontName = fontName;
            this.fontSize = fontSize;
            this.isBold = isBold;
            this.isItalic = isItalic;
        }
    }
}
