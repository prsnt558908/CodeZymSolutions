# Design Time Versioned Key Value Store in Python

#### Problem Statement
[https://codezym.com/question/87-design-time-versioned-key-value-store](https://codezym.com/question/87-design-time-versioned-key-value-store)

The idea behind this solution is simple. For every key, we keep a list of all its versions in the order they were written. Each item in this list holds a value and the timestamp of that write.

This one list is enough for every method. The position of an item in the list is its version number. And because timestamps never go down, the list is always sorted by time, so we can use binary search to find the value at any past moment.

We will start with a simple solution that checks versions one by one, see where it gets slow, and then make it fast with binary search.

## Solution 1: Simple Linear Search

### Storing the Versions

```python
class VersionEntry:
    def __init__(self, value: str, timestamp: int):
        self.value = value
        self.timestamp = timestamp


class TimeVersionedKeyValueStore:
    def __init__(self):
        # key -> all versions of that key, oldest first
        self.versionsByKey = {}
```

**Why a `VersionEntry` class?** A version is made of two things that belong together: a value and the time it was written. Keeping both in one object means the list holds complete versions, and we never have to keep two separate lists in sync.

**Why a dictionary of lists?** Every key has its own history and its own version numbers. The dictionary takes us to a key's history in O(1), and the list keeps that history in the order of writes.

Versions start at 0 and go up by 1 with every successful write. So the first write of a key sits at index 0, the second at index 1, and so on. **The list index is the version number**, so we never need to store it.

### The Easy Methods

Four of the five methods are simple list operations:

- `put`: if the key or value is `None` or blank, return `False` and save nothing. Otherwise, add a new `VersionEntry` at the end of the key's list.
- `get`: return the value of the last item, `versions[-1]`.
- `getByVersion`: return the value at index `version`, or `""` if that index does not exist. Python lists accept negative indexes like `-1`, so we must reject `version < 0` ourselves.
- `listVersionTimestamps`: loop over the list and build one `"version,timestamp"` row per item.

If a key was never written, the read methods return `""` or an empty list.

### Finding the Value at a Timestamp

`getWithTimestamp(key, T)` must return the value from the latest write made at or before time `T`.

The simplest way is to walk backward from the newest version. The first one with `timestamp <= T` is the answer.

```python
for entry in reversed(versions):
    if entry.timestamp <= timestamp:
        return entry.value
return ""  # every version was written after this timestamp
```

### The Problem

This takes O(n) time per call, where n is the number of versions of the key.

If a key has a million versions and we ask about a very old timestamp, one call checks almost a million items. The problem asks for this lookup to stay fast even when there are many versions.

## Solution 2: Binary Search on Timestamps (Optimal)

### The Key Observation

Each key's list is already sorted by timestamp, without us ever sorting it. New versions always go at the end, and `currentTimestamp` never goes down.

So finding "the last version with `timestamp <= T`" is a classic binary search. It cuts the search range in half at every step, so a million versions need only about 20 checks.

### How the Binary Search Works

We keep a variable `answer = -1` and look at the middle version of the current range:

- **Its timestamp is `<= T`:** this version is a valid answer. Save its index, then search the right half, because a newer valid version may still be there.
- **Its timestamp is `> T`:** this version is too new. Search the left half.

When the range becomes empty, `answer` holds the index of the latest version written at or before `T`. If it is still `-1`, every version is newer than `T`, so we return `""`.

Python's `bisect` module can also do this search, but writing it by hand makes the idea easy to see.

### Dry Run

From Example 3, key `"k"` has three versions: `"one"` at 10, `"two"` at 20 and `"three"` at 30. Let's call `getWithTimestamp("k", 25)`.

| low | high | mid | timestamp at mid | what happens |
|---|---|---|---|---|
| 0 | 2 | 1 | 20 | 20 <= 25, so answer = 1, search right |
| 2 | 2 | 2 | 30 | 30 > 25, so search left |

Now `low > high`, so the search stops with `answer = 1`, and we return `"two"`.

### Writes with the Same Timestamp

Two writes to the same key can share a timestamp. Both are kept as separate versions, in the order they happened.

Our search keeps moving right while it finds `timestamp <= T`, so it lands on the **last** of these writes. That is the newest value at that moment, which is exactly what we want.

### Why Not a Dictionary from Timestamp to Value?

Storing `timestamp -> value` for each key looks tempting. But two writes with the same timestamp would overwrite each other, and one version would be lost. We would also need extra work to find version numbers.

A list keeps every write, and its index gives us the version number for free.

## Complexity

n is the number of versions of the given key.

| Method | Solution 1 | Solution 2 |
|---|---|---|
| `put` | O(1) | O(1) |
| `get` | O(1) | O(1) |
| `getWithTimestamp` | O(n) | **O(log n)** |
| `getByVersion` | O(1) | O(1) |
| `listVersionTimestamps` | O(n) | O(n) |

Both solutions use O(total successful writes) space.

## Python Code

```python
from typing import List


class VersionEntry:
    """One saved version of a key: the value and the time it was written.
    Its position in the key's list is its version number."""

    def __init__(self, value: str, timestamp: int):
        self.value = value
        self.timestamp = timestamp


class TimeVersionedKeyValueStore:

    def __init__(self):
        # key -> all versions of that key, oldest first.
        # A list is created only on the first successful put, so it is never empty.
        self.versionsByKey = {}

    def put(self, key: str, value: str, currentTimestamp: int) -> bool:
        if self.isNullOrBlank(key) or self.isNullOrBlank(value):
            return False
        if key not in self.versionsByKey:
            self.versionsByKey[key] = []
        # Timestamps never go down, so adding at the end keeps the list sorted by time.
        self.versionsByKey[key].append(VersionEntry(value, currentTimestamp))
        return True

    def get(self, key: str) -> str:
        versions = self.versionsByKey.get(key)
        if versions is None:
            return ""
        return versions[-1].value

    def getWithTimestamp(self, key: str, timestamp: int) -> str:
        versions = self.versionsByKey.get(key)
        if versions is None:
            return ""
        index = self.findLastIndexAtOrBefore(versions, timestamp)
        if index == -1:
            return ""  # every version was written after this timestamp
        return versions[index].value

    def getByVersion(self, key: str, version: int) -> str:
        versions = self.versionsByKey.get(key)
        if versions is None or version < 0 or version >= len(versions):
            return ""
        return versions[version].value

    def listVersionTimestamps(self, key: str) -> List[str]:
        versions = self.versionsByKey.get(key)
        if versions is None:
            return []
        rows = []
        for version, entry in enumerate(versions):
            rows.append(f"{version},{entry.timestamp}")
        return rows

    def findLastIndexAtOrBefore(self, versions: List[VersionEntry], timestamp: int) -> int:
        """Binary search over versions sorted by timestamp.
        Returns the index of the last version with timestamp <= given timestamp,
        or -1 if every version is newer than the given timestamp."""
        low = 0
        high = len(versions) - 1
        answer = -1
        while low <= high:
            mid = (low + high) // 2
            if versions[mid].timestamp <= timestamp:
                answer = mid  # valid answer, a later one may still be on the right
                low = mid + 1
            else:
                high = mid - 1  # too new, look on the left
        return answer

    def isNullOrBlank(self, text: str) -> bool:
        return text is None or text.strip() == ""
```