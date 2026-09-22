

# Design a Text Editor with Undo and Redo : Python Solution using the Command Pattern

#### Problem Statement

[https://codezym.com/question/27-design-text-editor-undo-redo](https://codezym.com/question/27-design-text-editor-undo-redo)



## The core idea in one paragraph

The whole problem turns on one small shift in thinking. The obvious way to support undo is to keep a copy of the document before every edit, and put that copy back when the user presses undo. That works, but it makes every keystroke pay for text it never touched. 

The better way is to stop saving the *document* and start saving the *change* itself. An insert is fully described by three small facts : which row, which column, and what text. And if you know those three facts, you also know exactly how to take it back : delete that many characters from that spot. A delete is the mirror image, you only need to remember the piece you removed. 

Once each edit is a tiny self contained object that can both apply itself and reverse itself, undo and redo become two stacks and about four lines of code each. That object is the **Command design pattern**, and it is the right pattern here for a very specific reason : undo and redo need to work on *any* edit without knowing what kind of edit it is, and a shared `execute` plus `undo` contract is what gives them that. 

A second pattern, **Memento**, is the other classic answer for undo and we will build it first as our brute force, then see exactly where it starts to hurt. Nothing exotic is needed on the Python side either, just lists, slice assignment, and two plain lists used as stacks.

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

## Solution 1 : Brute force, save a copy of the whole document

The most direct idea. Before every edit, push a copy of the document onto an undo stack. Undo pops that copy and makes it the current document, after first parking the current document on a redo stack. Redo does the same thing in the other direction.

This is the **Memento pattern** in its plainest form, the editor takes a snapshot of its own state, and restoring a snapshot rewinds time.

### Code : Solution 1

```python
class TextEditor:

    def __init__(self):
        # The document. One string per row.
        self.rows = []
        # Older copies of the whole document. The last item is the top of the stack.
        self.undo_stack = []
        # Copies we moved away from while undoing.
        self.redo_stack = []

    def addText(self, row, column, text):
        self.undo_stack.append(list(self.rows))        # remember the whole document
        if row == len(self.rows):
            self.rows.append("")                       # a brand new row at the end
        line = self.rows[row]
        self.rows[row] = line[:column] + text + line[column:]
        self.redo_stack.clear()                        # a fresh edit kills the redo history

    def deleteText(self, row, startColumn, length):
        self.undo_stack.append(list(self.rows))
        line = self.rows[row]
        self.rows[row] = line[:startColumn] + line[startColumn + length:]
        self.redo_stack.clear()

    def undo(self):
        if not self.undo_stack:
            return
        self.redo_stack.append(list(self.rows))
        self.rows = self._restore(self.undo_stack.pop())

    def redo(self):
        if not self.redo_stack:
            return
        self.undo_stack.append(list(self.rows))
        self.rows = self._restore(self.redo_stack.pop())

    def readLine(self, row):
        return self.rows[row]

    def _restore(self, snapshot):
        """A row, once created, is never taken away. So an older copy that has
        fewer rows than we have now is padded back up with empty rows."""
        while len(snapshot) < len(self.rows):
            snapshot.append("")
        return snapshot
```

This passes every test. So why look further.

### What is wrong with it

**It stores the wrong thing.** Notice the `_restore` helper. It exists only because a snapshot of the past also remembers *how many rows existed in the past*, and that is a fact we are not allowed to rewind. We had to patch the snapshot back up. That awkwardness is a hint that a whole-document copy carries more information than the problem actually wants to undo.

**Every edit walks the entire document.** `list(self.rows)` copies row references, not the characters, so it is cheaper than it looks. But it is still one list slot per row, created on every single keystroke. Type 10,000 characters into a 1,000 row file and the history is holding ten million slots, to describe edits that together touched 10,000 characters.

**Every edit rebuilds a whole line.** A Python `str` is immutable, so `line[:column] + text + line[column:]` builds a fresh string the size of the entire row. Inserting one character into a 5,000 character paragraph copies 5,000 characters.

**It does not describe anything.** The history is a pile of past worlds. It cannot tell you *what changed*, so any future feature, grouping several keystrokes into one undo step, showing a diff, sending the edit to another user, has nothing to work with.

> One Python detail that quietly makes this version safe : `list(self.rows)` is a **shallow** copy. It works here only because each row is an immutable `str`, so sharing a row between the snapshot and the live document can never bite us. Keep this in mind, it becomes the reason Solution 1 stores rows as strings while Solution 2 stores them as something mutable.

---

## Step towards better : save only the row that changed

The first easy win. An edit only ever touches one row, so there is no reason to remember the other 999. Push `(row_index, old_text_of_that_row)` instead of the whole document. Undo puts the old text back into that one row.

```python
# Idea sketch, not the final code.
self.undo_stack.append((row, self.rows[row]))   # just this one line's old text
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

We now have a bag of little facts per edit : a kind, a row, a column, some text. The question is how to package them.

### Command pattern, the fit

Wrap each edit in an object that exposes exactly two methods, `execute` and `undo`. Put those objects on two stacks.

```
undo()  ->  pop from undo_stack, call undo() on it, append it to redo_stack
redo()  ->  pop from redo_stack, call execute() on it, append it to undo_stack
```

Read those two lines again. **Neither one mentions insert or delete.** That is the entire payoff. Undo and redo are written once, against the `execute` plus `undo` contract, and they keep working no matter how many kinds of edits the editor grows later. Every operation keeps its forward logic and its reverse logic side by side in one small class, which is exactly where a reader looks to check that the reverse is correct.

The pattern also matches the problem's shape naturally. A command is a *record of an action*, and undo history is literally a list of actions. We are not bending the problem to fit a pattern, the problem already is that pattern.

### Memento pattern, tempting but not the best fit here

Memento is the other textbook answer for undo, and it is what Solution 1 was. It works, so why is it second best for this problem.

Memento earns its keep when the reverse of an action is **hard or impossible to compute**, think of an operation that loses information, like reformatting a document or applying a filter to an image. There, saving the old state is the only honest way back.

Here the reverse is trivially computable. Insert reverses delete, delete reverses insert, and the reversal needs only the small slice of text involved. Paying for a whole-state snapshot to avoid a computation you can do in one line is a bad trade. You saw the concrete cost above : a snapshot per keystroke, plus a padding hack to stop the snapshot from rewinding a fact it should not rewind.

Worth noticing : our `DeleteCommand` does keep a tiny bit of remembered state, the removed text. That is the *smallest possible memento*, carried inside the command. This is the useful way to think about it, we did not throw Memento away, we shrank it down to only the part that could not be recomputed, and let Command carry it.

### A tuple with a kind tag, works, ages badly

The no-pattern option, and in Python it is genuinely tempting because a tuple is so cheap to write. Store `(kind, row, column, text)` and branch on `kind`.

```python
# What we are avoiding.
def undo(self):
    kind, row, column, text = self.undo_stack.pop()
    if kind == "add":
        del self.rows[row][column:column + len(text)]
    elif kind == "delete":
        self.rows[row][column:column] = list(text)
    # ... and every new operation adds another branch, here AND in redo()
```

For exactly two operations this is short and it passes the tests, so it is not wrong. The cost shows up as the editor grows. The reverse logic for every operation piles up in one function, far from the forward logic it mirrors. Add `replaceText` tomorrow and you must remember to edit two branch chains. That "remember to also edit the other place" is where undo bugs are born. Command removes the branching entirely, the object on the stack already knows what it is.

---

## Solution 2 : Command pattern (final solution)

### The pieces and why each one exists

**The `Command` base class.** Python has no `interface` keyword, and thanks to duck typing the editor would run perfectly well even if `AddCommand` and `DeleteCommand` shared no parent at all. The tiny base class still earns its place : it writes the contract down in one visible spot so a reader knows exactly what the two stacks hold, and `raise NotImplementedError` makes a half finished command fail loudly instead of silently doing nothing. If you want the stricter version, `abc.ABC` with `@abstractmethod` refuses to even build an incomplete command, which is nice but not needed here.

**`AddCommand`** holds `row`, `column`, `text`. Its `execute` creates the row if the edit lands one past the end, then inserts. Its `undo` deletes exactly `len(text)` characters at `column`. Nothing else is stored, because nothing else is needed.

**`DeleteCommand`** holds `row`, `startColumn`, `length`, and a `removed` field filled in when it runs. It captures the removed text **inside `execute`, not in `__init__`**, so the command stays a plain description until the moment it is applied. On redo, `execute` runs again against the restored document and simply re-captures the same slice.

**`self.rows` as a list of character lists.** Python `str` is immutable, exactly like Java's `String`, so if a row were a plain string then every insert would rebuild the whole line. A list of single characters is Python's mutable text buffer, and slice assignment gives us insert and delete in place :

```python
line[i:i] = list("abc")   # insert "abc" just before position i
del line[i:j]             # delete the characters from i up to j
```

Being honest about the trade : a character list uses more memory per character than a `str`, since it is a slot per character rather than a packed byte. Plain strings would also pass every test and save a line of code. The buffer is the right call when edits are constant and reads are occasional, which is what a text editor is. `readLine` pays for it with a single `"".join(...)`.

**Two plain lists as stacks.** Undo and redo are last-in-first-out by definition. A Python `list` already *is* a stack, `append` and `pop` from the end are both constant time, so there is nothing to import. `collections.deque` is for cheap pops from the *front*, which we never do, so reaching for it here would add an import for no gain.

**The private `_apply` helper.** Every fresh edit must run, be recorded, and clear the redo history. Putting those three steps in one place means the "clear redo" rule can never be forgotten by a future operation.

### Code : Solution 2

```python
class Command:
    """One edit that knows how to apply itself and how to take itself back.

    Undo and redo only ever call these two methods, so they never need to know
    which kind of edit they are holding.
    """

    def execute(self, rows):
        raise NotImplementedError

    def undo(self, rows):
        raise NotImplementedError


class AddCommand(Command):
    """Insert text into a row. Undoing it deletes exactly what we inserted."""

    def __init__(self, row, column, text):
        self.row = row
        self.column = column
        self.text = text

    def execute(self, rows):
        # row == len(rows) means the edit lands just past the last row,
        # so the row has to be born first.
        if self.row == len(rows):
            rows.append([])
        rows[self.row][self.column:self.column] = list(self.text)

    def undo(self, rows):
        del rows[self.row][self.column:self.column + len(self.text)]


class DeleteCommand(Command):
    """Delete a slice of a row. Undoing it puts the deleted slice back."""

    def __init__(self, row, startColumn, length):
        self.row = row
        self.startColumn = startColumn
        self.length = length
        self.removed = ""                # filled in by execute, needed by undo

    def execute(self, rows):
        line = rows[self.row]
        end = self.startColumn + self.length
        self.removed = "".join(line[self.startColumn:end])
        del line[self.startColumn:end]

    def undo(self, rows):
        rows[self.row][self.startColumn:self.startColumn] = list(self.removed)


class TextEditor:

    def __init__(self):
        # The document. Each row is a list of single characters, so an edit
        # splices in place instead of rebuilding the whole line.
        self.rows = []
        # Edits that have been applied, newest on top.
        self.undo_stack = []
        # Edits that have been undone, newest on top.
        self.redo_stack = []

    def addText(self, row, column, text):
        self._apply(AddCommand(row, column, text))

    def deleteText(self, row, startColumn, length):
        self._apply(DeleteCommand(row, startColumn, length))

    def _apply(self, command):
        """Every fresh edit runs, is remembered, and clears the redo history."""
        command.execute(self.rows)
        self.undo_stack.append(command)
        self.redo_stack.clear()

    def undo(self):
        if not self.undo_stack:              # nothing to take back
            return
        command = self.undo_stack.pop()
        command.undo(self.rows)
        self.redo_stack.append(command)

    def redo(self):
        if not self.redo_stack:              # nothing to re-apply
            return
        command = self.redo_stack.pop()
        command.execute(self.rows)
        self.undo_stack.append(command)

    def readLine(self, row):
        return "".join(self.rows[row])
```

---

## Dry run on the sample test

Reading `A` as an `AddCommand` and `D` as a `DeleteCommand`, with stack tops on the right.

| Call | Row 0 after the call | undo_stack | redo_stack |
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

The last two rows are the interesting ones. The new `addText` cleared the redo history, so `D4` is gone forever and the final `redo()` does nothing. That is exactly the rule from the problem statement, and it falls out of the single `self.redo_stack.clear()` inside `_apply`.

Now the row-creation case from the second sample test. `addText(2, 0, "row2 first")` creates row 2. Undoing it calls `AddCommand.undo`, which deletes 10 characters at column 0, and that is *all* it does. The row itself stays, now empty, so `readLine(2)` returns `""`. The command never removes a row, which is why Solution 2 needs no padding hack at all. Redo then calls `execute` again. This time `self.row == len(rows)` is false, because row 2 still exists, so it skips the creation step and just inserts. Correct on both passes.

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

Solution 2 does pay a small price on `readLine`, because `"".join(...)` builds the line fresh. That is a fair trade, reads are occasional and edits are constant.

---

## Gotchas worth remembering

- **Clear the redo stack in exactly one place.** Both `addText` and `deleteText` must do it, so route both through a single helper and the rule cannot be missed later.
- **Undo never removes a row.** Rows are created but never destroyed, even by undo. `AddCommand.undo` deletes characters only.
- **Capture the deleted text inside `execute`.** If it were captured in `__init__`, the command would be reaching into the document before it is supposed to run. Capturing in `execute` also means redo re-captures naturally.
- **Empty stacks are no-ops.** Guard both `undo` and `redo` with a falsy check and return quietly.
- **Watch shallow copies.** `list(self.rows)` only copies the outer list. That is safe in Solution 1 because rows are immutable strings, and it would be a silent bug in Solution 2 where rows are mutable lists, since the snapshot and the live row would be the same object. Solution 2 sidesteps the question entirely by never snapshotting.
- **Inserting at `column == len(line)` is just an append.** No special case needed, `line[i:i] = ...` at the end of the list already handles it.

---

## Where this design goes next

The real reason to reach for Command here is what it makes easy afterwards, with no change to `undo` or `redo`.

- **A new operation**, say `replaceText`, is one new class with `execute` and `undo`. Nothing else in the file changes.
- **Grouping edits** so that a whole typed word undoes in one step becomes a `CompositeCommand` holding a list of commands, whose `execute` runs them forward and whose `undo` runs them backward. It has the same two methods, so the stacks accept it as-is.
- **Capping history** to the last N edits is a trim on the undo stack, and the memory it frees is real because each entry is small.

That is the mark of the right pattern for a problem : the code you did not have to touch.