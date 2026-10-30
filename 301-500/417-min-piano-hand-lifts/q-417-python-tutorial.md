# Shortest Word Tile Group in Python


#### Problem Statement


[https://codezym.com/question/417-min-piano-hand-lifts](https://codezym.com/question/417-min-piano-hand-lifts)


Use a sliding window, a continuous group whose two ends move forward. Expand the group until it has all the required letters, then shrink it to find a shorter answer. Two small lists keep the letter counts, so we can update the group without counting its letters again.


## What Must the Answer Contain?


Return a substring of `tileRow` that supplies every letter needed for `targetWord`. The letters can be rearranged, and extra letters are allowed.


Repeated letters matter. To form `book`, we need one `b`, two `o` letters, and one `k`.


Return an empty string if no valid group exists. If several shortest groups exist, return the one that starts first.


## 1. Start with a Simple Search


Try every starting index. Extend the group one tile at a time until it contains all the required letters. This is the shortest valid group for that starting index, so record it and try the next start.


The main loop looks like this. In this sketch, `covers_target` checks whether all 26 letter counts meet their required counts.


```python
for left in range(len(tileRow)):
    count = [0] * 26
    for right in range(left, len(tileRow)):
        count[ord(tileRow[right]) - ord("a")] += 1
        if covers_target(count, needed):
            # Record this group if it is shorter than the best group.
            break
```


Let `n` be the tile row length and `m` be the target length. This approach takes **O(n² + m)** time because it can examine O(n²) groups. Checking 26 counts takes constant time.


With up to 100,000 tiles, we need to avoid scanning the same tiles repeatedly.


## 2. Improve It with a Sliding Window


Keep one group from `left` to `right`, both inclusive. Use:

- `needed`: the required count of each letter.
- `window`: the count of each letter in the current group.
- `matched`: the number of required letter occurrences already supplied, including repeated letters.


Both count lists have 26 entries because the input contains only lowercase English letters. Index 0 represents `a`, index 1 represents `b`, and so on.


For each new rightmost tile, increase its window count. Increase `matched` only if this copy is needed. Extra copies do not increase it.


When `matched` equals the target length, the group is valid. Record it if it is shorter, then remove the leftmost tile and move `left` forward. Keep shrinking while the group remains valid.


If removing a tile makes its count fall below the required count, decrease `matched`. We must then expand the right end to find another valid group.


Only replace the answer when the new length is **strictly smaller**. Since `left` moves forward, this keeps the earliest group when lengths tie.


Store the best start and length, then create the result substring once at the end. This avoids copying every candidate group.


### Walk Through an Example


For `tileRow = "aoboklobo"` and `targetWord = "book"`:

1. Expanding to `"aobok"` gives all the required letters.
2. Removing the first `a` leaves `"obok"`, a valid group of length 4.
3. Removing the next `o` leaves only one `o`, so we stop shrinking and continue expanding.


The answer is `"obok"`. Its length equals the target length, so no shorter group can contain all the required letters.


### Why This Finds the Shortest Group


Whenever a group is valid, shrinking checks shorter candidates before the group becomes invalid.


A discarded starting index has already produced a valid group. Using that same start with a later right end would only make the group longer. Therefore, moving `left` forward cannot skip a better answer.


### Complexity


Building the required counts takes **O(m)** time. Each tile enters the window once and leaves at most once, so processing the row takes **O(n)** time, including both loops.


The total time is **O(n + m)**. The two fixed-size lists use **O(1)** extra space, excluding the returned string. A result of length `k` needs **O(k)** output space.


## Python Solution


```python
class WordTileFinder:
    def __init__(self):
        pass

    def findShortestTileGroup(self, tileRow, targetWord):
        n = len(tileRow)
        if len(targetWord) > n:
            return ""

        # Store the required count and the current group count for each letter.
        needed = [0] * 26
        window = [0] * 26
        for letter in targetWord:
            needed[ord(letter) - ord("a")] += 1

        matched = 0
        left = 0
        best_start = 0
        best_length = n + 1

        for right in range(n):
            index = ord(tileRow[right]) - ord("a")
            window[index] += 1
            if window[index] <= needed[index]:
                matched += 1

            # A valid group may become shorter by removing its leftmost tile.
            while matched == len(targetWord):
                length = right - left + 1
                # Keep the earlier group when lengths are equal.
                if length < best_length:
                    best_start = left
                    best_length = length

                left_index = ord(tileRow[left]) - ord("a")
                window[left_index] -= 1
                if window[left_index] < needed[left_index]:
                    matched -= 1
                left += 1

        if best_length == n + 1:
            return ""
        return tileRow[best_start:best_start + best_length]
```
