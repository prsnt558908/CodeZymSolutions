import java.util.*;

public class NotepadEditor {

    private final List<String> lines;
    private final int linesPerPage;

    private int cursorRow;
    private int cursorCol;

    public NotepadEditor(List<String> lines, int linesPerPage) {
        if (lines == null || lines.isEmpty()) {
            this.lines = new ArrayList<>();
            this.lines.add("");
        } else {
            this.lines = new ArrayList<>(lines.size());
            for (String s : lines) {
                this.lines.add(s == null ? "" : s);
            }
        }

        this.linesPerPage = Math.max(1, linesPerPage);

        this.cursorRow = 0;
        this.cursorCol = 0;
    }

    // move cursor one position left (within the same line)
    public void moveLeft() {
        if (cursorCol > 0) {
            cursorCol--;
        }
        // If at column 0, do nothing (no move to previous line).
    }

    // move cursor one position right (within the same line)
    public void moveRight() {
        String line = currentLine();
        int len = line.length();
        // Cursor can go from 0..len, but cannot go past len
        if (cursorCol < len) {
            cursorCol++;
        }
        // If already after the rightmost character, do nothing.
    }

    // move cursor one line up, trying to keep current column;
    // if target line is shorter, clamp to its end
    public void moveUp() {
        if (cursorRow == 0) {
            // No row above
            return;
        }
        int desiredCol = cursorCol;
        int targetRow = cursorRow - 1;
        int targetLen = lineLength(targetRow);

        cursorRow = targetRow;
        cursorCol = clampColumn(desiredCol, targetLen);
    }

    // move cursor one line down, trying to keep current column;
    // if target line is shorter, clamp to its end
    public void moveDown() {
        if (cursorRow >= lines.size() - 1) {
            // No row below
            return;
        }
        int desiredCol = cursorCol;
        int targetRow = cursorRow + 1;
        int targetLen = lineLength(targetRow);

        cursorRow = targetRow;
        cursorCol = clampColumn(desiredCol, targetLen);
    }

    // move cursor up by linesPerPage lines if possible; otherwise no-op
    public void pageUp() {
        int targetRow = cursorRow - linesPerPage;
        if (targetRow < 0) {
            // Not enough lines above for a full page jump
            return;
        }

        int desiredCol = cursorCol;
        int targetLen = lineLength(targetRow);

        cursorRow = targetRow;
        cursorCol = clampColumn(desiredCol, targetLen);
    }

    // move cursor down by linesPerPage lines if possible; otherwise no-op
    public void pageDown() {
        int targetRow = cursorRow + linesPerPage;
        if (targetRow >= lines.size()) {
            // Not enough lines below for a full page jump
            return;
        }

        int desiredCol = cursorCol;
        int targetLen = lineLength(targetRow);

        cursorRow = targetRow;
        cursorCol = clampColumn(desiredCol, targetLen);
    }

    // Returns characters from index 0 up to (cursorCol - 1) of current line.
    // If cursorCol is beyond line length, we return the full line.
    public String readLeft() {
        String line = currentLine();
        int len = line.length();

        int endExclusive = cursorCol;
        if (endExclusive < 0) {
            endExclusive = 0;
        } else if (endExclusive > len) {
            endExclusive = len;
        }

        return line.substring(0, endExclusive);
    }

    // -------- helpers --------

    private String currentLine() {
        if (cursorRow < 0 || cursorRow >= lines.size()) {
            return "";
        }
        String s = lines.get(cursorRow);
        return (s == null) ? "" : s;
    }

    private int lineLength(int row) {
        if (row < 0 || row >= lines.size()) {
            return 0;
        }
        String s = lines.get(row);
        return (s == null) ? 0 : s.length();
    }

    private int clampColumn(int desiredCol, int lineLen) {
        int col = desiredCol;
        if (col < 0) {
            col = 0;
        } else if (col > lineLen) {
            col = lineLen;
        }
        return col;
    }
}
