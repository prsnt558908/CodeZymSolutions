# Design Resumable Iterator for Large Dataset in Python

#### Problem Statement
[https://codezym.com/question/88-design-resumable-iterator-large-dataset](https://codezym.com/question/88-design-resumable-iterator-large-dataset)


## Core Idea

An iterator does not need its own copy of the data. It only needs to remember **where it is**.

So we keep a reference to the records list and one number, `nextIndex`, the index of the next record to return. Reading a record is just `self.records[self.nextIndex]`, then moving the index forward by one.

To pause, we write that index into a token, together with `datasetId` and `datasetVersion`. To resume, we read the token back, check that it still belongs to the same dataset and the same version, and only then jump to the saved index.

A combination of two design patterns fits this problem best:

- **Iterator**: callers read records one by one with `hasNext()` and `next()`, without knowing how the records are stored.
- **Memento**: `pause()` saves a small snapshot of the iterator's progress, and `resume()` restores it later.

The **State** pattern may look useful for the paused and running modes, but a simple boolean flag is enough here. We will see why below.


## A Simple First Try: Copy Records into a Queue

A natural first idea is to copy all records into a queue and remove one record on every `next()` call.

```python
from collections import deque

# inside __init__
self.pending = deque(records)   # copies the whole list
self.nextIndex = 0

def next(self) -> str:
    if self.paused or not self.pending:
        return ""
    self.nextIndex += 1
    return self.pending.popleft()
```

This works for small inputs, but it has two big problems:

1. **Too much memory.** The dataset can have up to 10^7 records. The copy needs O(n) extra memory, but the problem allows only O(1).
2. **Slow resume.** A removed record is gone for good. To resume at position `p`, we must pop and throw away `p` records, and if the queue has already moved past `p`, rebuild it first. That is O(n) work for a single resume.


## Better Idea: Keep Just One Index

A Python list can read any index directly, like `self.records[i]`. So we skip the copy and keep only `nextIndex`.

```python
def hasNext(self) -> bool:
    return self.nextIndex < len(self.records)

def next(self) -> str:
    if self.paused or not self.hasNext():
        return ""
    record = self.records[self.nextIndex]
    self.nextIndex += 1
    return record
```

`position()` simply returns `self.nextIndex`.

Why not name the field `position`? The class already has a `position()` method. In Python, an attribute with the same name would hide that method, and calling `it.position()` would fail.

Now the extra memory is O(1), and resuming at any position is a single assignment: `self.nextIndex = p`.

This also works for real systems. If the records lived in a file or a database table, the iterator would still remember only a position and fetch records when needed.


## What Goes Inside the Token

The simplest token would store only the position, like `position=2`. But that is not safe.

Look at Example 3. The old token points to position 2. In the old dataset `[A, B, C, D]`, that was record `C`.

Then a new record `X` was added, and the dataset became `[A, B, X, C, D]`. Resuming blindly at position 2 would return `X`, the wrong record.

Worse, a position-only token from a completely different dataset would also be accepted.

So the token carries three values:

- `datasetId`: to check that the token belongs to this dataset.
- `version`: to check that the dataset did not change after the pause.
- `position`: where to continue from.

For example: `datasetId=1,version=10,position=1`.


## The Cursor Class (Memento)

Together, these three values are the snapshot from the Memento pattern. Here is how the pattern's roles map to our design:

| Memento role | In our design |
|---|---|
| Originator: the object whose state is saved | `ResumableIterator` |
| Memento: the saved snapshot | `Cursor`, written out as the token string |
| Caretaker: whoever keeps the snapshot | The caller, who may store the token in a file or database |

Why a separate `Cursor` class instead of three loose numbers? It gives the values clear names, and its `toToken()` method builds the exact token format. So `pause()` becomes a single line, and `resume()` can check `cursor.version` instead of digging through string parts.

The caller never has to understand the token. It just keeps the string and hands it back later.


## Why Not the State Pattern?

The iterator has two modes, running and paused. The **State** pattern would turn them into a `State` base class plus `RunningState` and `PausedState` classes.

But only `next()` behaves differently while paused. `hasNext()`, `position()`, `pause()` and `resume()` work the same way in both modes. Adding three classes just to replace one `if self.paused` check makes the code longer, not clearer.

A boolean flag is the right tool here. If more modes appear later, like closed or failed, the State pattern would start to pay off.


## Pause and Resume Rules

`pause()` sets `self.paused = True` and returns the token. While paused, `next()` returns `""` and does not move. So calling `pause()` twice returns the same token.

`hasNext()` ignores the flag. It only tells whether records are left.

`resume()` is the only tricky method. It returns `False` when:

1. The token is not exactly `datasetId=<int>,version=<int>,position=<int>`, in this order.
2. `datasetId` does not match ours.
3. `version` does not match our `datasetVersion`.
4. `position` is outside `0..len(self.records)`.

All checks run **before** we touch any field. So a failed `resume()` changes nothing: the position stays the same, and a paused iterator stays paused. Only when every check passes do we set `nextIndex` and clear the `paused` flag.

A few details worth knowing:

- `resume()` also works on a fresh iterator that was never paused, like in Example 2.
- `position == len(self.records)` is valid. It means everything was read, so `hasNext()` returns `False`.
- Python's `int()` is forgiving. It accepts spaces and underscores, so `int(" 7")` and `int("1_0")` both work. The token format is strict, so `readField()` first checks with `isdecimal()` that the value is only digits, after an optional sign.


## Python Code

```python
from typing import List, Optional


class Cursor:
    """
    Memento: a small snapshot of the iterator's progress.
    It holds just enough data to resume safely later.
    """

    def __init__(self, datasetId: int, version: int, position: int):
        self.datasetId = datasetId
        self.version = version
        self.position = position

    def toToken(self) -> str:
        """Writes the cursor in the exact token format."""
        return f"datasetId={self.datasetId},version={self.version},position={self.position}"


class ResumableIterator:
    """
    Walks through a large dataset without copying it.
    It only remembers the index of the next record, so extra memory is O(1).
    """

    def __init__(self, datasetId: int, records: List[str], datasetVersion: int):
        self.datasetId = datasetId
        self.records = records  # reference to the dataset, never copied
        self.datasetVersion = datasetVersion
        self.nextIndex = 0  # index of the record that next() will return
        self.paused = False

    def hasNext(self) -> bool:
        """True if at least one record is left. Pausing does not change this."""
        return self.nextIndex < len(self.records)

    def next(self) -> str:
        """Returns the next record, or "" if paused or nothing is left."""
        if self.paused or not self.hasNext():
            return ""
        record = self.records[self.nextIndex]
        self.nextIndex += 1
        return record

    def position(self) -> int:
        return self.nextIndex

    def pause(self) -> str:
        """Pauses the iterator and returns its progress as a token."""
        self.paused = True
        return Cursor(self.datasetId, self.datasetVersion, self.nextIndex).toToken()

    def resume(self, stateToken: str) -> bool:
        """
        Resumes from a token only if it is safe.
        All checks run first, so a failed resume changes nothing.
        """
        cursor = self.readToken(stateToken)
        if cursor is None:
            return False  # broken token
        if cursor.datasetId != self.datasetId:
            return False  # token belongs to another dataset
        if cursor.version != self.datasetVersion:
            return False  # dataset changed after the pause
        if cursor.position < 0 or cursor.position > len(self.records):
            return False  # position out of range
        self.nextIndex = cursor.position
        self.paused = False
        return True

    def readToken(self, token: str) -> Optional[Cursor]:
        """
        Reads "datasetId=<int>,version=<int>,position=<int>" into a Cursor.
        Returns None if the token does not follow this exact format.
        """
        if token is None:
            return None
        parts = token.split(",")
        if len(parts) != 3:
            return None
        datasetId = self.readField(parts[0], "datasetId")
        version = self.readField(parts[1], "version")
        position = self.readField(parts[2], "position")
        if datasetId is None or version is None or position is None:
            return None
        return Cursor(datasetId, version, position)

    def readField(self, part: str, key: str) -> Optional[int]:
        """Reads one "key=<int>" part. Returns None if the key or number is wrong."""
        prefix = key + "="
        if not part.startswith(prefix):
            return None
        value = part[len(prefix):]
        # int() also accepts spaces and "_", so first check for an optional sign + digits
        digits = value[1:] if value[:1] in ("+", "-") else value
        if not digits.isdecimal():
            return None
        return int(value)
```


## Complexity

- `hasNext()`, `next()`, `position()` and `pause()` run in O(1) time.
- `resume()` runs in O(L) time, where L is the token length. A token is only a few dozen characters, so this is fast.
- Extra memory is O(1): one list reference, one index and one flag.