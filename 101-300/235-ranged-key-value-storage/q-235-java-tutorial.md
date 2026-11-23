# Ranged Key Value Storage in Java

#### Problem Statement
[https://codezym.com/question/235-ranged-key-value-storage](https://codezym.com/question/235-ranged-key-value-storage)


We need to build a key-value store with three methods. `set` saves a value, `get` reads it back, and `getRange` returns every key-value pair whose key lies between two given keys, sorted by key.


`set` and `get` are easy with a `HashMap`. The hard part is `getRange`, because a `HashMap` keeps its keys in no particular order.


The core idea is to combine two simple data structures. A `HashMap` stores the values, so reads and updates are instant. A list of keys, which we always keep sorted, makes range queries fast with the help of binary search.


We will start with a simple brute force solution, see where it gets slow, and then improve it.


## Solution 1: Brute Force

Keep everything in one `HashMap` called `values`. `set` is just `values.put(key, value)`, and `get` is just `values.getOrDefault(key, "")`.


For `getRange`, check every key in the map, keep the ones inside the range, sort them, and build the output.

```java
public List<String> getRange(String startKey, String endKey) {
    // collect every key that falls inside the range
    List<String> keys = new ArrayList<>();
    for (String key : values.keySet()) {
        if (key.compareTo(startKey) >= 0 && key.compareTo(endKey) <= 0) {
            keys.add(key);
        }
    }

    // a HashMap has no order, so sort the matching keys
    Collections.sort(keys);

    List<String> result = new ArrayList<>();
    for (String key : keys) {
        result.add(key + "," + values.get(key));
    }
    return result;
}
```

`compareTo` compares two strings character by character, using character codes. So uppercase letters come before lowercase ones, and `"Zoo"` comes before `"apple"`. This is the lexicographic order the problem asks for.


### Problem with this approach

Every `getRange` call looks at every key in the storage, even when only two keys are in the range. It also sorts the matching keys again on every call.


With 50,000 keys and 50,000 range queries, that is 2.5 billion key checks, which is far too slow.


## Solution 2: HashMap + Sorted List

### Core Idea

What if the keys were always kept in sorted order? Then the keys of any range would sit side by side, in one continuous block.


Here is `getRange("c", "g")` on a sorted list of keys:

```
index:         0     1     2     3     4
sortedKeys:   "a"   "b"   "d"   "f"   "k"
                           ^           ^
                         start       stop
```

`"c"` is not in the list, and that is fine. We start at `"d"`, the first key that is `>= "c"`. We take `"d"` and `"f"`, then stop at `"k"` because `"k" > "g"`.


So `getRange` needs just two steps:

1. Find the first key that is `>= startKey`. Binary search does this in O(log n).
2. Walk forward and collect keys until a key is `> endKey`.


We never look at keys outside the range, except the one key where we stop.


### Why two data structures?

- **`values`** is a `HashMap` from key to value. It gives O(1) `get`, and O(1) update of an existing key.
- **`sortedKeys`** is an `ArrayList` that holds every key exactly once, always in sorted order. It exists only to make `getRange` fast.


Values are stored only in the map. So when an existing key gets a new value, we update the map and do not touch the list at all.


### Adding a new key

When `set` gets a brand new key, we insert it into `sortedKeys` at the index of the first key that is `>=` the new key. This keeps the list sorted.


For example, setting a new key `"c"` in the list above finds index 2, so `"c"` goes between `"b"` and `"d"`.


This is the same search that `getRange` does for `startKey`. So one binary search helper, `findPosition`, serves both methods.


### How `findPosition` works

`findPosition(target)` returns the index of the first key that is `>= target`. If every key is smaller than `target`, it returns `sortedKeys.size()`.


It keeps a search window from `low` to `high`, and checks the middle key each time:

- If the middle key is smaller than `target`, the answer is on its right, so `low = mid + 1`.
- Otherwise, the middle key could be the answer, so we keep it: `high = mid`.


When `low` and `high` meet, `low` is the answer. Here is `findPosition("c")` on the same list:

```
low=0, high=5   mid=2   "d" >= "c"   so high=2
low=0, high=2   mid=1   "b" <  "c"   so low=2
low=2, high=2   stop and return 2
```


Java's `Collections.binarySearch` can also find this position, but it returns a negative number when the key is missing, which is harder to read. A small helper keeps the code clear.


### Complexity

`n` is the number of stored keys, and `k` is the number of keys returned by `getRange`.

| Method | Brute Force | HashMap + Sorted List |
| --- | --- | --- |
| `set` (existing key) | O(1) | O(1) |
| `set` (new key) | O(1) | O(log n) search + O(n) shift |
| `get` | O(1) | O(1) |
| `getRange` | O(n + k log k) | O(log n + k) |


Inserting into the middle of an `ArrayList` shifts the later keys one place to the right. Java does this with a single fast memory copy, so it stays quick in practice, even with 100,000 keys.


A balanced tree such as Java's `TreeMap` could make this insert O(log n). But a `HashMap` plus a sorted list is easier to follow, and fast enough for this problem.


### Final Code

```java
import java.util.*;

/**
 * Key-value storage that also answers sorted range queries.
 *
 * values     : key -> value, for O(1) get and update
 * sortedKeys : every key exactly once, always kept in sorted order, for range queries
 */
public class RangedKeyValueStorage {

    Map<String, String> values;
    List<String> sortedKeys;

    public RangedKeyValueStorage() {
        values = new HashMap<>();
        sortedKeys = new ArrayList<>();
    }

    /** Stores value for key. A brand new key is also inserted at its sorted position. */
    public void set(String key, String value) {
        if (!values.containsKey(key)) {
            sortedKeys.add(findPosition(key), key);
        }
        values.put(key, value);
    }

    /** Returns the value for key, or an empty string if key does not exist. */
    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    /** Returns "key,value" for every key where startKey <= key <= endKey, sorted by key. */
    public List<String> getRange(String startKey, String endKey) {
        List<String> result = new ArrayList<>();
        // jump straight to the first key >= startKey, then walk forward
        for (int i = findPosition(startKey); i < sortedKeys.size(); i++) {
            String key = sortedKeys.get(i);
            if (key.compareTo(endKey) > 0) {
                break; // went past endKey, so every later key is out of range too
            }
            result.add(key + "," + values.get(key));
        }
        return result;
    }

    /**
     * Binary search on sortedKeys.
     * Returns the index of the first key that is >= target,
     * or sortedKeys.size() if every key is smaller than target.
     * This is also the index where target should be inserted to keep the list sorted.
     */
    int findPosition(String target) {
        int low = 0;
        int high = sortedKeys.size();
        while (low < high) {
            int mid = (low + high) / 2;
            if (sortedKeys.get(mid).compareTo(target) < 0) {
                low = mid + 1; // mid is too small, the answer is on its right
            } else {
                high = mid;    // mid could be the answer, keep it in the window
            }
        }
        return low;
    }
}
```