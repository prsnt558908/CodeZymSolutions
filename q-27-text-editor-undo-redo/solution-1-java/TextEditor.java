import java.util.*;

/**
 * In-memory Text Editor with Undo/Redo.
 *
 * Rules (per problem statement):
 * - Document starts with 0 rows; each row starts empty.
 * - addText(row == currentRowCount) creates a new empty row, then inserts.
 * - deleteText never removes rows; empty rows persist.
 * - New edits clear the redo stack.
 * - undo/redo are no-ops if their stacks are empty.
 *
 * Implementation:
 * - Rows stored as List<StringBuilder>.
 * - History stored as two stacks of immutable Action records.
 * - Inverse operations:
 *     * ADD  -> inverse is DEL at same row/col for payload length
 *     * DEL  -> inverse is ADD at same row/col with deleted payload
 */
public class TextEditor {

    private final List<StringBuilder> rows = new ArrayList<>();
    private final Deque<Action> undo = new ArrayDeque<>();
    private final Deque<Action> redo = new ArrayDeque<>();

    private enum Type { ADD, DEL }

    private static final class Action {
        final Type type;
        final int row;
        final int col;
        final String payload; // for ADD: inserted text; for DEL: deleted text

        Action(Type type, int row, int col, String payload) {
            this.type = type;
            this.row = row;
            this.col = col;
            this.payload = payload;
        }
    }

    public TextEditor() {
        // starts empty: 0 rows
    }

    /**
     * Insert 'text' into given row at 'column'.
     * If row == current row count, create a new empty row first.
     * Clears redo history.
     */
    public void addText(int row, int column, String text) {
        ensureRowForAdd(row);
        StringBuilder sb = rows.get(row);
        // Assuming inputs are valid per spec: 0 ≤ column ≤ sb.length()
        sb.insert(column, text);

        // Record action and clear redo
        undo.push(new Action(Type.ADD, row, column, text));
        redo.clear();
    }

    /**
     * Delete 'length' characters starting at (row, startColumn).
     * Row persists even if it becomes empty. Clears redo history.
     */
    public void deleteText(int row, int startColumn, int length) {
        StringBuilder sb = rows.get(row);
        // Assuming inputs are valid per spec: 0 ≤ startColumn+length ≤ sb.length()
        String deleted = sb.substring(startColumn, startColumn + length);
        sb.delete(startColumn, startColumn + length);

        // Record action and clear redo
        undo.push(new Action(Type.DEL, row, startColumn, deleted));
        redo.clear();
    }

    /**
     * Revert the most recent change (ADD or DEL).
     * Pushes the reverted action onto the redo stack.
     */
    public void undo() {
        if (undo.isEmpty()) return;
        Action a = undo.pop();
        StringBuilder sb = rows.get(a.row);

        if (a.type == Type.ADD) {
            // Inverse of ADD is delete of the same payload at the same position
            sb.delete(a.col, a.col + a.payload.length());
        } else { // Type.DEL
            // Inverse of DEL is re-insert the deleted payload at the same position
            sb.insert(a.col, a.payload);
        }
        // Move this action to redo (so redo can re-apply it)
        redo.push(a);
    }

    /**
     * Reapply the most recently undone change.
     * Pushes the action back onto the undo stack.
     */
    public void redo() {
        if (redo.isEmpty()) return;
        Action a = redo.pop();
        StringBuilder sb = rows.get(a.row);

        if (a.type == Type.ADD) {
            // Re-apply original ADD
            sb.insert(a.col, a.payload);
        } else { // Type.DEL
            // Re-apply original DEL
            sb.delete(a.col, a.col + a.payload.length());
        }
        // Back onto undo history
        undo.push(a);
    }

    /**
     * Return the entire content of the specified row (possibly "").
     */
    public String readLine(int row) {
        return rows.get(row).toString();
    }

    // --- helpers ---

    // Ensures the target row exists for addText:
    // - If row == rows.size(): append a new empty row
    // - If row < rows.size(): OK
    // - If row > rows.size(): invalid per spec; we guard to avoid silent bugs
    private void ensureRowForAdd(int row) {
        if (row == rows.size()) {
            rows.add(new StringBuilder());
        } else if (row < rows.size()) {
            // nothing to do
        } else {
            throw new IllegalArgumentException("Row index skips rows: row=" + row + ", currentRowCount=" + rows.size());
        }
    }
}
