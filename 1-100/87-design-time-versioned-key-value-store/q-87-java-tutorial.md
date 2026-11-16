# Design Time Versioned Key Value Store in Java

#### Problem Statement
[https://codezym.com/question/87-design-time-versioned-key-value-store](https://codezym.com/question/87-design-time-versioned-key-value-store)

The idea behind this solution is simple. For every key, we keep a list of all its versions in the order they were written. Each item in this list holds a value and the timestamp of that write.

This one list is enough for every method. The position of an item in the list is its version number. And because timestamps never go down, the list is always sorted by time, so we can use binary search to find the value at any past moment.

We will start with a simple solution that checks versions one by one, see where it gets slow, and then make it fast with binary search.

## Solution 1: Simple Linear Search

### Storing the Versions

```java
class VersionEntry {
    String value;
    long timestamp;
}

// key -> all versions of that key, oldest first
Map<String, List<VersionEntry>> versionsByKey = new HashMap<>();
```

**Why a `VersionEntry` class?** A version is made of two things that belong together: a value and the time it was written. Keeping both in one object means the list holds complete versions, and we never have to keep two separate lists in sync.

**Why a map of lists?** Every key has its own history and its own version numbers. The `HashMap` takes us to a key's history in O(1), and the list keeps that history in the order of writes.

Versions start at 0 and go up by 1 with every successful write. So the first write of a key sits at index 0, the second at index 1, and so on. **The list index is the version number**, so we never need to store it.

### The Easy Methods

Four of the five methods are simple list operations:

- `put`: if the key or value is null or blank, return `false` and save nothing. Otherwise, add a new `VersionEntry` at the end of the key's list.
- `get`: return the value of the last item.
- `getByVersion`: return the value at index `version`, or `""` if that index does not exist.
- `listVersionTimestamps`: loop over the list and build one `"version,timestamp"` row per item.

If a key was never written, the read methods return `""` or an empty list.

### Finding the Value at a Timestamp

`getWithTimestamp(key, T)` must return the value from the latest write made at or before time `T`.

The simplest way is to walk backward from the newest version. The first one with `timestamp <= T` is the answer.

```java
for (int i = versions.size() - 1; i >= 0; i--) {
    if (versions.get(i).timestamp <= timestamp) {
        return versions.get(i).value;
    }
}
return ""; // every version was written after this timestamp
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

### Why Not a Map from Timestamp to Value?

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

## Java Code

```java
import java.util.*;

/**
 * One saved version of a key: the value and the time it was written.
 * Its position in the key's list is its version number.
 */
class VersionEntry {
    String value;
    long timestamp;

    VersionEntry(String value, long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }
}

public class TimeVersionedKeyValueStore {

    // key -> all versions of that key, oldest first.
    // A list is created only on the first successful put, so it is never empty.
    Map<String, List<VersionEntry>> versionsByKey;

    public TimeVersionedKeyValueStore() {
        versionsByKey = new HashMap<>();
    }

    public boolean put(String key, String value, long currentTimestamp) {
        if (isNullOrBlank(key) || isNullOrBlank(value)) {
            return false;
        }
        if (!versionsByKey.containsKey(key)) {
            versionsByKey.put(key, new ArrayList<>());
        }
        // Timestamps never go down, so adding at the end keeps the list sorted by time.
        versionsByKey.get(key).add(new VersionEntry(value, currentTimestamp));
        return true;
    }

    public String get(String key) {
        List<VersionEntry> versions = versionsByKey.get(key);
        if (versions == null) {
            return "";
        }
        return versions.get(versions.size() - 1).value;
    }

    public String getWithTimestamp(String key, long timestamp) {
        List<VersionEntry> versions = versionsByKey.get(key);
        if (versions == null) {
            return "";
        }
        int index = findLastIndexAtOrBefore(versions, timestamp);
        if (index == -1) {
            return ""; // every version was written after this timestamp
        }
        return versions.get(index).value;
    }

    public String getByVersion(String key, int version) {
        List<VersionEntry> versions = versionsByKey.get(key);
        if (versions == null || version < 0 || version >= versions.size()) {
            return "";
        }
        return versions.get(version).value;
    }

    public List<String> listVersionTimestamps(String key) {
        List<String> rows = new ArrayList<>();
        List<VersionEntry> versions = versionsByKey.get(key);
        if (versions == null) {
            return rows;
        }
        for (int version = 0; version < versions.size(); version++) {
            rows.add(version + "," + versions.get(version).timestamp);
        }
        return rows;
    }

    /**
     * Binary search over versions sorted by timestamp.
     * Returns the index of the last version with timestamp <= given timestamp,
     * or -1 if every version is newer than the given timestamp.
     */
    int findLastIndexAtOrBefore(List<VersionEntry> versions, long timestamp) {
        int low = 0;
        int high = versions.size() - 1;
        int answer = -1;
        while (low <= high) {
            int mid = low + (high - low) / 2; // same as (low + high) / 2, but cannot overflow
            if (versions.get(mid).timestamp <= timestamp) {
                answer = mid;    // valid answer, a later one may still be on the right
                low = mid + 1;
            } else {
                high = mid - 1;  // too new, look on the left
            }
        }
        return answer;
    }

    boolean isNullOrBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
```