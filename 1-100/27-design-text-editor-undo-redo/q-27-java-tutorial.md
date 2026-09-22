

# Design a Text Editor with Undo and Redo, Java Solution using the Command Pattern

#### Problem Statement

[https://codezym.com/question/27-design-text-editor-undo-redo](https://codezym.com/question/27-design-text-editor-undo-redo)

---

## The Core Idea

The whole problem turns on one small shift in thinking. The obvious way to support undo is to keep a copy of the document before every edit, and put that copy back when the user presses undo. That works, but it makes every keystroke pay for text it never touched. 

The better way is to stop saving the *document* and start saving the *change* itself. An insert is fully described by three small facts, which row, which column, and what text. And if you know those three facts, you also know exactly how to take it back : delete that many characters from that spot. A delete is the mirror image, you only need to remember the piece you removed. 

Once each edit is a tiny self contained object that can both apply itself and reverse itself, undo and redo become two stacks and about four lines of code each. That object is the **Command design pattern**, and it is the right pattern here for a very specific reason, undo and redo need to work on *any* edit without knowing what kind of edit it is, and the Command interface is what gives them that. 

A second pattern, **Memento**, is the other classic answer for undo and we will build it first as our brute force, then see exactly where it starts to hurt. We will not need anything fancier than a `List`, a `StringBuilder`, and two stacks.

---

## What we are building

An in-memory editor that stores text row by row.

| Method | What it does |
| --- | --- |
| `addText(row, column, text)` | Insert `text` into `row` at `column`. If `row` equals the current row count, a new empty row is born first. |
| `deleteText(row, startColumn, length)` | Remove exactly `length` characters from `row` starting at `startColumn`. |
| `undo()` | Take back the most recent edit. No-op if there is nothing to take back. |
| `redo()` | Re-apply the most recently undone edit. No-op if there is nothing to re-apply. |
| `readLine(row)` | Return the full text of that row, or `""` if the row is empty. |

Three rules are easy to miss and every one of them shows up in the tests.

1. Any fresh `addText` or `deleteText` **wipes the redo history**. Once you type something new, the future you undid is gone.
2. **Row count never goes down.** Deleting every character of a row leaves an empty row behind. Even undoing the very `addText` that created a row leaves that row in place, now empty.
3. `undo` and `redo` on empty history are silent no-ops, not errors.

---

## Solution 1, Brute force : save a copy of the whole document

The most direct idea. Before every edit, push a copy of the document onto an undo stack. Undo pops that copy and makes it the current document, after first parking the current document on a redo stack. Redo does the same thing in the other direction.

This is the **Memento pattern** in its plainest form, the editor takes a snapshot of its own state, and restoring a snapshot rewinds time.

### Code, Solution 1

```java
import java.util.ArrayList;
import java.util.List;

public class TextEditor {

    // The document. One String per row.
    private List<String> rows = new ArrayList<>();

    // Older copies of the whole document. The last element is the top of the stack.
    private final List<List<String>> undoStack = new ArrayList<>();

    // Copies we moved away from while undoing.
    private final List<List<String>> redoStack = new ArrayList<>();

    public TextEditor() {
    }

    public void addText(int row, int column, String text) {
        undoStack.add(new ArrayList<>(rows));          // remember the whole document
        if (row == rows.size()) rows.add("");          // a brand new row at the end
        String line = rows.get(row);
        rows.set(row, line.substring(0, column) + text + line.substring(column));
        redoStack.clear();                             // a fresh edit kills the redo history
    }

    public void deleteText(int row, int startColumn, int length) {
        undoStack.add(new ArrayList<>(rows));
        String line = rows.get(row);
        rows.set(row, line.substring(0, startColumn) + line.substring(startColumn + length));
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.add(new ArrayList<>(rows));
        rows = restore(undoStack.remove(undoStack.size() - 1));
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.add(new ArrayList<>(rows));
        rows = restore(redoStack.remove(redoStack.size() - 1));
    }

    public String readLine(int row) {
        return rows.get(row);
    }

    /**
     * A row, once created, is never taken away. So an older copy that has fewer
     * rows than we have now is padded back up with empty rows.
     */
    private List<String> restore(List<String> snapshot) {
        while (snapshot.size() < rows.size()) snapshot.add("");
        return snapshot;
    }
}
```

This passes every test. So why look further.

### What is wrong with it

**It stores the wrong thing.** Notice the `restore` helper. It exists only because a snapshot of the past also remembers *how many rows existed in the past*, and that is a fact we are not allowed to rewind. We had to patch the snapshot back up. That awkwardness is a hint that a whole-document copy carries more information than the problem actually wants to undo.

**Every edit walks the entire document.** `new ArrayList<>(rows)` copies row references, not the characters, so it is cheaper than it looks. But it is still one list entry per row, created on every single keystroke. Type 10,000 characters into a 1,000 row file and the history is holding ten million entries, to describe edits that together touched 10,000 characters.

**Every edit rebuilds a whole line.** Java `String` is immutable, so `line.substring(0, column) + text + line.substring(column)` allocates a fresh string the size of the entire row. Inserting one character into a 5,000 character paragraph copies 5,000 characters.

**It does not describe anything.** The history is a pile of past worlds. It cannot tell you *what changed*, so any future feature, grouping several keystrokes into one undo step, showing a diff, sending the edit to another user, has nothing to work with.

---

## Step towards better : save only the row that changed

The first easy win. An edit only ever touches one row, so there is no reason to remember the other 999. Push `(rowIndex, oldTextOfThatRow)` instead of the whole document. Undo puts the old text back into that one row.

```java
// Idea sketch, not the final code.
undoStack.add(new Object[]{ row, rows.get(row) });   // just this one line's old text
```

That kills the per-row cost completely and the padding hack disappears, because we never touch the row count on undo. Good progress. But we are still copying a **whole line** to undo a change that may be one character long. If a row holds a long paragraph, every keystroke still copies the paragraph.

---

## The real insight : save the difference, not the state

Look at what an insert actually is. You put the string `"-there"` at row 0, column 5. To undo it you do not need the old line at all, you just delete 6 characters starting at column 5. The string you inserted *is* the recipe for its own removal.

A delete is the same story running backwards. Delete 6 characters at column 5, and the only thing you need to keep is those 6 characters. To undo, insert them back at column 5.

So the memory per edit drops from "one whole document" to "one whole line" to **"just the text that was typed or deleted"**. For normal editing that is a handful of characters.

And there is a second gift hiding here. Because each edit now knows how to apply itself, **redo is free**. Redo is not a special operation at all, it is just "run this edit again".

---

## Why the Command pattern, and why not the others

We now have a bag of little facts per edit, a kind, a row, a column, some text. The question is how to package them.

### Command pattern, the fit

Wrap each edit in an object that exposes exactly two methods, `execute` and `undo`. Put those objects on two stacks.

```
undo()  ->  pop from undoStack, call undo() on it, push it to redoStack
redo()  ->  pop from redoStack, call execute() on it, push it to undoStack
```

Read those two lines again. **Neither one mentions insert or delete.** That is the entire payoff. Undo and redo are written once, against the `Command` interface, and they keep working no matter how many kinds of edits the editor grows later. Every operation keeps its forward logic and its reverse logic side by side in one small class, which is exactly where a reader looks to check that the reverse is correct.

The pattern also matches the problem's shape naturally. A command is a *record of an action*, and undo history is literally a list of actions. We are not bending the problem to fit a pattern, the problem already is that pattern.

### Memento pattern, tempting, but not the best fit here

Memento is the other textbook answer for undo, and it is what Solution 1 was. It works, so why is it second best for this problem.

Memento earns its keep when the reverse of an action is **hard or impossible to compute**, think of an operation that loses information, like reformatting a document or applying a filter to an image. There, saving the old state is the only honest way back.

Here the reverse is trivially computable. Insert reverses delete, delete reverses insert, and the reversal needs only the small slice of text involved. Paying for a whole-state snapshot to avoid a computation you can do in one line is a bad trade. You saw the concrete cost above, a snapshot per keystroke, plus a padding hack to stop the snapshot from rewinding a fact it should not rewind.

Worth noticing : our `DeleteCommand` does keep a tiny bit of remembered state, the removed text. That is the *smallest possible memento*, carried inside the command. This is the useful way to think about it, we did not throw Memento away, we shrank it down to only the part that could not be recomputed, and let Command carry it.

### Plain code with a type tag, works, ages badly

The no-pattern option. Store an `Edit` object with a `type` field and branch on it.

```java
// What we are avoiding.
void undo() {
    Edit e = undoStack.pop();
    if (e.type == ADD)    rows.get(e.row).delete(e.col, e.col + e.text.length());
    else if (e.type == DELETE) rows.get(e.row).insert(e.col, e.removed);
    // ... and every new operation adds another branch, here AND in redo()
}
```

This passes the tests too, so it is not wrong. It is just that the reverse logic for every operation piles up in one method, far from the forward logic it mirrors. Add `replaceText` tomorrow and you must remember to edit two switch blocks. That "remember to also edit the other place" is where undo bugs are born. Command removes the branching entirely, the object on the stack already knows what it is.

---

## Solution 2, Command pattern (final solution)

### The pieces and why each one exists

**`interface Command`**, the reason `undo()` and `redo()` are four lines and will never grow. The stacks are typed as `Command`, so they hold any edit without caring which.

**`AddCommand`**, holds `row`, `column`, `text`. Its `execute` creates the row if the edit lands one past the end, then inserts. Its `undo` deletes exactly `text.length()` characters at `column`. Nothing else is stored, because nothing else is needed.

**`DeleteCommand`**, holds `row`, `startColumn`, `length`, and a `removed` field filled in when it runs. It captures the removed text **inside `execute`, not in the constructor**, so the command is a plain description until the moment it is applied. On redo, `execute` runs again against the restored document and simply re-captures the same slice.

**`List<StringBuilder> rows`**, a `List` because rows are dense and numbered `0..n-1`, which is exactly what a list is. A map keyed by row number would buy nothing here. `StringBuilder` instead of `String` because a `String` is immutable, so every insert would rebuild the line. `StringBuilder` shifts characters in place and hands us `insert` and `delete` methods that read like the problem statement.

**`Deque<Command>` for both stacks**, undo and redo are last-in-first-out by definition, so a stack is the natural fit. `ArrayDeque` is the plain, modern stack in Java. We avoid the old `Stack` class because it is synchronized, and this editor is single threaded.

**The private `apply` helper**, every fresh edit must run, be recorded, and clear the redo history. Putting those three steps in one place means the "clear redo" rule can never be forgotten by a future operation.

### Code, Solution 2

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TextEditor {

    /**
     * One edit that knows how to apply itself and how to take itself back.
     * Undo and redo only ever talk to this interface, so they never need to
     * know which kind of edit they are holding.
     */
    private interface Command {
        void execute(List<StringBuilder> rows);

        void undo(List<StringBuilder> rows);
    }

    /** Insert text into a row. Undoing it deletes exactly what we inserted. */
    private static class AddCommand implements Command {
        private final int row;
        private final int column;
        private final String text;

        AddCommand(int row, int column, String text) {
            this.row = row;
            this.column = column;
            this.text = text;
        }

        @Override
        public void execute(List<StringBuilder> rows) {
            // row == rows.size() means the edit lands just past the last row,
            // so the row has to be born first.
            if (row == rows.size()) rows.add(new StringBuilder());
            rows.get(row).insert(column, text);
        }

        @Override
        public void undo(List<StringBuilder> rows) {
            rows.get(row).delete(column, column + text.length());
        }
    }

    /** Delete a slice of a row. Undoing it puts the deleted slice back. */
    private static class DeleteCommand implements Command {
        private final int row;
        private final int startColumn;
        private final int length;
        private String removed;   // filled in by execute, needed by undo

        DeleteCommand(int row, int startColumn, int length) {
            this.row = row;
            this.startColumn = startColumn;
            this.length = length;
        }

        @Override
        public void execute(List<StringBuilder> rows) {
            StringBuilder line = rows.get(row);
            removed = line.substring(startColumn, startColumn + length);
            line.delete(startColumn, startColumn + length);
        }

        @Override
        public void undo(List<StringBuilder> rows) {
            rows.get(row).insert(startColumn, removed);
        }
    }

    // The document. StringBuilder so inserting and deleting inside a row is cheap.
    private final List<StringBuilder> rows = new ArrayList<>();

    // Edits that have been applied, newest on top.
    private final Deque<Command> undoStack = new ArrayDeque<>();

    // Edits that have been undone, newest on top.
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public TextEditor() {
    }

    public void addText(int row, int column, String text) {
        apply(new AddCommand(row, column, text));
    }

    public void deleteText(int row, int startColumn, int length) {
        apply(new DeleteCommand(row, startColumn, length));
    }

    /** Every fresh edit runs, is remembered, and throws away the redo history. */
    private void apply(Command command) {
        command.execute(rows);
        undoStack.push(command);
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;          // nothing to take back
        Command command = undoStack.pop();
        command.undo(rows);
        redoStack.push(command);
    }

    public void redo() {
        if (redoStack.isEmpty()) return;          // nothing to re-apply
        Command command = redoStack.pop();
        command.execute(rows);
        undoStack.push(command);
    }

    public String readLine(int row) {
        return rows.get(row).toString();
    }
}
```

---

## Dry run on the sample test

Reading `A` as an `AddCommand` and `D` as a `DeleteCommand`, with stack tops on the right.

| Call | Row 0 after the call | undoStack | redoStack |
| --- | --- | --- | --- |
| `addText(0, 0, "hello")` | `hello` | `A1` | empty |
| `addText(1, 0, "world")` | `hello` | `A1 A2` | empty |
| `addText(0, 5, "-there")` | `hello-there` | `A1 A2 A3` | empty |
| `deleteText(0, 5, 6)` | `hello` | `A1 A2 A3 D4` | empty |
| `undo()` | `hello-there` | `A1 A2 A3` | `D4` |
| `redo()` | `hello` | `A1 A2 A3 D4` | empty |
| `undo()` | `hello-there` | `A1 A2 A3` | `D4` |
| `addText(0, 11, "!")` | `hello-there!` | `A1 A2 A3 A5` | empty |
| `redo()` | `hello-there!` | `A1 A2 A3 A5` | empty |

The last two rows are the interesting ones. The new `addText` cleared the redo history, so `D4` is gone forever and the final `redo()` does nothing. That is exactly the rule from the problem statement, and it falls out of the single `redoStack.clear()` inside `apply`.

Now the row-creation case from the second sample test. `addText(2, 0, "row2 first")` creates row 2. Undoing it calls `AddCommand.undo`, which deletes 10 characters at column 0, and that is *all* it does. The row itself stays, now empty, so `readLine(2)` returns `""`. The command never removes a row, which is why Solution 2 needs no padding hack at all. Redo then calls `execute` again. This time `row == rows.size()` is false, because row 2 still exists, so it skips the creation step and just inserts. Correct on both passes.

---

## Complexity

Let `R` be the number of rows, `L` the length of the row being touched, and `t` the length of the text being inserted or deleted.

| Operation | Solution 1, snapshots | Solution 2, commands |
| --- | --- | --- |
| `addText` | `O(R + L)` time, `O(R)` added to history | `O(L)` time, `O(t)` added to history |
| `deleteText` | `O(R + L)` time, `O(R)` added to history | `O(L)` time, `O(t)` added to history |
| `undo` / `redo` | `O(R)` | `O(L)` |
| `readLine` | `O(1)` | `O(L)` |
| History after `M` edits | `O(M * R)` | `O(total characters typed or deleted)` |

The row-level work is `O(L)` either way, since shifting characters inside a line costs what it costs. The win is in the last row of that table. Solution 1's history grows with the size of the *document* multiplied by the number of edits. Solution 2's history grows only with the size of the *edits*, which is the thing a user actually did.

Solution 2 does pay a small price on `readLine`, because `StringBuilder.toString()` copies the line. That is a fair trade, reads are occasional, edits are constant.

---

## Gotchas worth remembering

- **Clear the redo stack in exactly one place.** Both `addText` and `deleteText` must do it, so route both through a single helper and the rule cannot be missed later.
- **Undo never removes a row.** Rows are created but never destroyed, even by undo. `AddCommand.undo` deletes characters only.
- **Capture the deleted text inside `execute`.** If it were captured in the constructor, the command would be reaching into the document before it is supposed to run. Capturing in `execute` also means redo re-captures naturally.
- **Empty stacks are no-ops.** Guard both `undo` and `redo` with an `isEmpty()` check and return quietly.
- **Inserting at `column == line length` is just an append.** No special case needed, `StringBuilder.insert` already handles it.

---

## Where this design goes next

The real reason to reach for Command here is what it makes easy afterwards, with no change to `undo` or `redo`.

- **A new operation**, say `replaceText`, is one new class implementing `Command`. Nothing else in the file changes.
- **Grouping edits** so that a whole typed word undoes in one step becomes a `CompositeCommand` holding a list of commands, whose `execute` runs them forward and whose `undo` runs them backward. It is itself a `Command`, so the stacks accept it as-is.
- **Capping history** to the last N edits is a trim on the undo stack, and the memory it frees is real because each entry is small.

That is the mark of the right pattern for a problem, the code you did not have to touch.