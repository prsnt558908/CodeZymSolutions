# Design Resumable Iterator for Large Dataset in Java

#### Problem Statement
[https://codezym.com/question/88-design-resumable-iterator-large-dataset](https://codezym.com/question/88-design-resumable-iterator-large-dataset)


## Core Idea

An iterator does not need its own copy of the data. It only needs to remember **where it is**.

So we keep a reference to the records list and one number, `nextIndex`, the index of the next record to return. Reading a record is just `records.get(nextIndex)`, then moving the index forward by one.

To pause, we write that index into a token, together with `datasetId` and `datasetVersion`. To resume, we read the token back, check that it still belongs to the same dataset and the same version, and only then jump to the saved index.

A combination of two design patterns fits this problem best:

- **Iterator**: callers read records one by one with `hasNext()` and `next()`, without knowing how the records are stored.
- **Memento**: `pause()` saves a small snapshot of the iterator's progress, and `resume()` restores it later.

The **State** pattern may look useful for the paused and running modes, but a simple boolean flag is enough here. We will see why below.


## A Simple First Try: Copy Records into a Queue

A natural first idea is to copy all records into a queue and remove one record on every `next()` call.

```java
Queue<String> pending = new LinkedList<>(records);   // copies the whole list
int nextIndex = 0;

public String next() {
    if (paused || pending.isEmpty()) {
        return "";
    }
    nextIndex++;
    return pending.poll();
}
```

This works for small inputs, but it has two big problems:

1. **Too much memory.** The dataset can have up to 10^7 records. The copy needs O(n) extra memory, but the problem allows only O(1).
2. **Slow resume.** A polled record is gone for good. To resume at position `p`, we must poll and throw away `p` records, and if the queue has already moved past `p`, rebuild it first. That is O(n) work for a single resume.


## Better Idea: Keep Just One Index

The records already sit in a `List`, and a list can read any index directly with `get(index)`. So we skip the copy and keep only `nextIndex`.

```java
public boolean hasNext() {
    return nextIndex < records.size();
}

public String next() {
    if (paused || !hasNext()) {
        return "";
    }
    String record = records.get(nextIndex);
    nextIndex++;
    return record;
}
```

`position()` simply returns `nextIndex`.

Now the extra memory is O(1), and resuming at any position is a single assignment: `nextIndex = p`.

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

The iterator has two modes, running and paused. The **State** pattern would turn them into a `State` interface plus `RunningState` and `PausedState` classes.

But only `next()` behaves differently while paused. `hasNext()`, `position()`, `pause()` and `resume()` work the same way in both modes. Adding an interface and two classes just to replace one `if (paused)` check makes the code longer, not clearer.

A boolean flag is the right tool here. If more modes appear later, like closed or failed, the State pattern would start to pay off.


## Pause and Resume Rules

`pause()` sets `paused = true` and returns the token. While paused, `next()` returns `""` and does not move. So calling `pause()` twice returns the same token.

`hasNext()` ignores the flag. It only tells whether records are left.

`resume()` is the only tricky method. It returns `false` when:

1. The token is not exactly `datasetId=<int>,version=<int>,position=<int>`, in this order.
2. `datasetId` does not match ours.
3. `version` does not match our `datasetVersion`.
4. `position` is outside `0..records.size()`.

All checks run **before** we touch any field. So a failed `resume()` changes nothing: the position stays the same, and a paused iterator stays paused. Only when every check passes do we set `nextIndex` and clear the `paused` flag.

A few details worth knowing:

- `resume()` also works on a fresh iterator that was never paused, like in Example 2.
- `position == records.size()` is valid. It means everything was read, so `hasNext()` returns `false`.
- We split the token with `split(",", -1)`. The `-1` keeps empty parts, so a token with an extra comma at the end gives four parts and is rejected.
- `Integer.parseInt` throws an exception for text like `abc`, or for numbers too big for an `int`. We catch it and treat the token as broken.


## Java Code

```java
import java.util.*;

/**
 * Memento: a small snapshot of the iterator's progress.
 * It holds just enough data to resume safely later.
 */
class Cursor {
    int datasetId;
    int version;
    int position;

    Cursor(int datasetId, int version, int position) {
        this.datasetId = datasetId;
        this.version = version;
        this.position = position;
    }

    /** Writes the cursor in the exact token format. */
    String toToken() {
        return "datasetId=" + datasetId + ",version=" + version + ",position=" + position;
    }
}

/**
 * Walks through a large dataset without copying it.
 * It only remembers the index of the next record, so extra memory is O(1).
 */
public class ResumableIterator {
    int datasetId;
    List<String> records;   // reference to the dataset, never copied
    int datasetVersion;
    int nextIndex;          // index of the record that next() will return
    boolean paused;

    public ResumableIterator(int datasetId, List<String> records, int datasetVersion) {
        this.datasetId = datasetId;
        this.records = records;
        this.datasetVersion = datasetVersion;
        this.nextIndex = 0;
        this.paused = false;
    }

    /** True if at least one record is left. Pausing does not change this. */
    public boolean hasNext() {
        return nextIndex < records.size();
    }

    /** Returns the next record, or "" if paused or nothing is left. */
    public String next() {
        if (paused || !hasNext()) {
            return "";
        }
        String record = records.get(nextIndex);
        nextIndex++;
        return record;
    }

    public int position() {
        return nextIndex;
    }

    /** Pauses the iterator and returns its progress as a token. */
    public String pause() {
        paused = true;
        return new Cursor(datasetId, datasetVersion, nextIndex).toToken();
    }

    /**
     * Resumes from a token only if it is safe.
     * All checks run first, so a failed resume changes nothing.
     */
    public boolean resume(String stateToken) {
        Cursor cursor = readToken(stateToken);
        if (cursor == null) {
            return false;   // broken token
        }
        if (cursor.datasetId != datasetId) {
            return false;   // token belongs to another dataset
        }
        if (cursor.version != datasetVersion) {
            return false;   // dataset changed after the pause
        }
        if (cursor.position < 0 || cursor.position > records.size()) {
            return false;   // position out of range
        }
        nextIndex = cursor.position;
        paused = false;
        return true;
    }

    /**
     * Reads "datasetId=<int>,version=<int>,position=<int>" into a Cursor.
     * Returns null if the token does not follow this exact format.
     */
    Cursor readToken(String token) {
        if (token == null) {
            return null;
        }
        // -1 keeps empty parts, so a token with an extra comma at the end is rejected
        String[] parts = token.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        Integer id = readField(parts[0], "datasetId");
        Integer version = readField(parts[1], "version");
        Integer position = readField(parts[2], "position");
        if (id == null || version == null || position == null) {
            return null;
        }
        return new Cursor(id, version, position);
    }

    /** Reads one "key=<int>" part. Returns null if the key or the number is wrong. */
    Integer readField(String part, String key) {
        String prefix = key + "=";
        if (!part.startsWith(prefix)) {
            return null;
        }
        try {
            return Integer.parseInt(part.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```


## Complexity

- `hasNext()`, `next()`, `position()` and `pause()` run in O(1) time. (`get(index)` is O(1) on lists like `ArrayList`.)
- `resume()` runs in O(L) time, where L is the token length. A token is only a few dozen characters, so this is fast.
- Extra memory is O(1): one list reference, one index and one flag.