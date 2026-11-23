# Ranged Key Value Storage in Python

#### Problem Statement
[https://codezym.com/question/235-ranged-key-value-storage](https://codezym.com/question/235-ranged-key-value-storage)


We need to build a key-value store with three methods. `set` saves a value, `get` reads it back, and `getRange` returns every key-value pair whose key lies between two given keys, sorted by key.


`set` and `get` are easy with a dictionary. The hard part is `getRange`, because a `dict` keeps its keys in the order they were added, not in sorted order.


The core idea is to combine two simple data structures. A `dict` stores the values, so reads and updates are instant. A list of keys, which we always keep sorted, makes range queries fast with the help of binary search.


We will start with a simple brute force solution, see where it gets slow, and then improve it.


## Solution 1: Brute Force

Keep everything in one `dict` called `values`. `set` is just `self.values[key] = value`, and `get` is just `self.values.get(key, "")`.


For `getRange`, check every key in the dict, keep the ones inside the range, sort them, and build the output.

```python
def getRange(self, startKey: str, endKey: str) -> List[str]:
    # collect every key that falls inside the range
    keys = [key for key in self.values if startKey <= key <= endKey]

    # a dict keeps insertion order, not sorted order, so sort the matching keys
    keys.sort()

    return [key + "," + self.values[key] for key in keys]
```

Python compares strings character by character, using character codes. So uppercase letters come before lowercase ones, and `"Zoo"` comes before `"apple"`. This is the lexicographic order the problem asks for.


### Problem with this approach

Every `getRange` call looks at every key in the storage, even when only two keys are in the range. It also sorts the matching keys again on every call.


With 50,000 keys and 50,000 range queries, that is 2.5 billion key checks, which is far too slow.


## Solution 2: Dict + Sorted List

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

- **`values`** is a `dict` from key to value. It gives O(1) `get`, and O(1) update of an existing key.
- **`sortedKeys`** is a plain `list` that holds every key exactly once, always in sorted order. It exists only to make `getRange` fast.


Values are stored only in the dict. So when an existing key gets a new value, we update the dict and do not touch the list at all.


### Adding a new key

When `set` gets a brand new key, we insert it into `sortedKeys` at the index of the first key that is `>=` the new key. This keeps the list sorted.


For example, setting a new key `"c"` in the list above finds index 2, so `"c"` goes between `"b"` and `"d"`.


This is the same search that `getRange` does for `startKey`. So one binary search helper, `findPosition`, serves both methods.


### How `findPosition` works

`findPosition(target)` returns the index of the first key that is `>= target`. If every key is smaller than `target`, it returns `len(sortedKeys)`.


It keeps a search window from `low` to `high`, and checks the middle key each time:

- If the middle key is smaller than `target`, the answer is on its right, so `low = mid + 1`.
- Otherwise, the middle key could be the answer, so we keep it: `high = mid`.


When `low` and `high` meet, `low` is the answer. Here is `findPosition("c")` on the same list:

```
low=0, high=5   mid=2   "d" >= "c"   so high=2
low=0, high=2   mid=1   "b" <  "c"   so low=2
low=2, high=2   stop and return 2
```


Python's built-in `bisect.bisect_left(self.sortedKeys, target)` returns the same index, and you can use it in real code. We write the search by hand here so every step is easy to see.


### Complexity

`n` is the number of stored keys, and `k` is the number of keys returned by `getRange`.

| Method | Brute Force | Dict + Sorted List |
| --- | --- | --- |
| `set` (existing key) | O(1) | O(1) |
| `set` (new key) | O(1) | O(log n) search + O(n) shift |
| `get` | O(1) | O(1) |
| `getRange` | O(n + k log k) | O(log n + k) |


Inserting into the middle of a list shifts the later keys one place to the right. `list.insert` does this shift in fast C code, so it stays quick in practice, even with 100,000 keys.


A balanced tree could make this insert O(log n), but Python has no built-in one. A `dict` plus a sorted list is simple, and fast enough for this problem.


### Final Code

```python
from typing import List


class RangedKeyValueStorage:
    """
    Key-value storage that also answers sorted range queries.

    values     : key -> value, for O(1) get and update
    sortedKeys : every key exactly once, always kept in sorted order, for range queries
    """

    def __init__(self):
        self.values = {}
        self.sortedKeys = []

    def set(self, key: str, value: str) -> None:
        """Stores value for key. A brand new key is also inserted at its sorted position."""
        if key not in self.values:
            self.sortedKeys.insert(self.findPosition(key), key)
        self.values[key] = value

    def get(self, key: str) -> str:
        """Returns the value for key, or an empty string if key does not exist."""
        return self.values.get(key, "")

    def getRange(self, startKey: str, endKey: str) -> List[str]:
        """Returns "key,value" for every key where startKey <= key <= endKey, sorted by key."""
        result = []
        # jump straight to the first key >= startKey, then walk forward
        for i in range(self.findPosition(startKey), len(self.sortedKeys)):
            key = self.sortedKeys[i]
            if key > endKey:
                break  # went past endKey, so every later key is out of range too
            result.append(key + "," + self.values[key])
        return result

    def findPosition(self, target: str) -> int:
        """
        Binary search on sortedKeys.
        Returns the index of the first key that is >= target,
        or len(sortedKeys) if every key is smaller than target.
        This is also the index where target should be inserted to keep the list sorted.
        """
        low = 0
        high = len(self.sortedKeys)
        while low < high:
            mid = (low + high) // 2
            if self.sortedKeys[mid] < target:
                low = mid + 1  # mid is too small, the answer is on its right
            else:
                high = mid  # mid could be the answer, keep it in the window
        return low
```