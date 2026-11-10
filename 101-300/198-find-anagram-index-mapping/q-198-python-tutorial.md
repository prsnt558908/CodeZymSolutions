# Find Anagram Index Mapping in Python

#### Problem Statement
[https://codezym.com/question/198-find-anagram-index-mapping](https://codezym.com/question/198-find-anagram-index-mapping)

For every number in `A`, we need the index where the same number sits in `B`. The only tricky part is duplicates: their positions in `B` must be given out from left to right, smallest first. We first solve it with a simple brute force that scans `B` again and again. Then we make it fast by saving the positions of every number in a dictionary of queues, so each lookup takes O(1) time and the whole solution runs in O(n).


## Understanding the Problem

Take `A = [7, 7, 2, 9]` and `B = [9, 7, 2, 7]`.

The value `7` sits at indices `1` and `3` in `B`.

- The first `7` in `A` gets the smallest free index, `1`.
- The second `7` gets the next free index, `3`.
- `2` gets index `2`, and `9` gets index `0`.

So the answer is `[1, 3, 2, 0]`.


## Solution 1: Brute Force

For each `A[i]`, scan `B` from the start. Pick the first index `j` where `B[j]` equals `A[i]` and `j` is not used yet. Then mark `j` as used.

A `used` list of booleans remembers which indices of `B` are already taken.

Since we scan from left to right and stop at the first free match, we always get the smallest unused index.

```python
def anagramMappings(self, A: List[int], B: List[int]) -> List[int]:
    n = len(A)
    used = [False] * n
    result = []

    for i in range(n):
        for j in range(n):
            if not used[j] and B[j] == A[i]:
                used[j] = True
                result.append(j)
                break
    return result
```

### The Problem With Brute Force

For every element of `A`, we may scan the whole of `B`. That is **O(n²)** time.

Many of these checks are wasted. Take `A = B = [5, 5, 5, 5, 5]`. Every new search walks past all the indices that are already used before it finds a free one.

With `n` at most `100` this still passes, but it will not scale to big inputs.

**Complexity:** O(n²) time and O(n) space.


## Solution 2: Dictionary of Queues (Optimal)

### The Key Idea

The answer for a value `x` depends only on where `x` appears in `B`.

So instead of scanning `B` again and again, we read `B` **once** and note down the positions of every value, from left to right. After that, each number in `A` simply takes the next position waiting for it.

### Data Structure: `dict` of `deque`

- **Key:** a value from `B`.
- **Value:** a `deque` (a queue from the `collections` module) of all indices where that value appears in `B`.

**Why a dictionary?** It takes us straight to the positions of a value. No more scanning `B`.

**Why a queue?** A queue is first in, first out. We add indices from left to right, so the front of the queue is always the smallest unused index. `popleft()` returns that index and removes it, so it is never given out twice. We no longer need the `used` list.

**Why `deque` and not a normal list?** Removing the first item of a list with `pop(0)` is slow, because every other item has to shift one step left. `deque.popleft()` does it in O(1).

### Steps

1. Walk through `B` from left to right. Add each index `j` to the queue of `B[j]`.
2. Walk through `A` from left to right. For each value, `popleft()` from its queue and add that index to the answer.

We never need to check for an empty queue. `B` is an anagram of `A`, so every value has exactly as many positions waiting in `B` as the number of times it appears in `A`.

### Dry Run

`A = [5, 1, 5, 1, 5]` and `B = [1, 5, 5, 1, 5]`

After step 1, the dictionary looks like this:

```
1 -> [0, 3]
5 -> [1, 2, 4]
```

Step 2 gives out the indices:

| i | A[i] | Queue before | Picked index | Queue after |
|---|------|--------------|--------------|-------------|
| 0 | 5 | [1, 2, 4] | 1 | [2, 4] |
| 1 | 1 | [0, 3] | 0 | [3] |
| 2 | 5 | [2, 4] | 2 | [4] |
| 3 | 1 | [3] | 3 | [] |
| 4 | 5 | [4] | 4 | [] |

Final answer: `[1, 0, 2, 3, 4]`

### Complexity

- **Time:** O(n). Each index of `B` goes into a queue once and comes out once.
- **Space:** O(n) for the dictionary and its queues.

### Final Code

```python
from collections import deque
from typing import List


class AnagramIndexMapping:

    def __init__(self):
        pass

    def anagramMappings(self, A: List[int], B: List[int]) -> List[int]:
        """
        Returns P where B[P[i]] == A[i] for every i.
        Repeated values get their indices in B from left to right.
        """
        # value -> queue of indices where this value appears in B.
        # Indices are added left to right, so the front is always the smallest.
        positions = {}

        for j, value in enumerate(B):
            if value not in positions:
                positions[value] = deque()
            positions[value].append(j)

        result = []
        for value in A:
            # popleft() gives the smallest unused index and removes it,
            # so the same index is never used twice.
            result.append(positions[value].popleft())
        return result
```