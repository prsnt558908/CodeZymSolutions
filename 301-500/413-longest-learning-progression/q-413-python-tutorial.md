# Longest Learning Progression in Python

#### Problem Statement
[https://codezym.com/question/413-longest-learning-progression](https://codezym.com/question/413-longest-learning-progression)


Both tasks ask for the longest list of lessons we can pick, in their original order, where every picked level is a little harder than the one before it.


The core idea is simple. We walk through the lessons once, from left to right. For every level, we remember the longest progression seen so far that ends at that level. When a new lesson arrives, we attach it to the best progression it is allowed to follow.


For the main task, a lesson at level `x` can only follow level `x - 1`. So one dictionary lookup per lesson is enough, and the solution runs in O(n).


For the follow-up, a lesson can follow any level from `x - maxLevelGap` to `x - 1`. Now we need the best value in a range of levels. A segment tree gives us that in O(log L) time per lesson, where `L` is the largest level.


## Main Task: Step-by-Step Progression

### Brute Force: Check Every Earlier Lesson

Let `dp[i]` be the longest progression that ends at lesson `i`.


For each lesson, we look back at every earlier lesson. If an earlier lesson is exactly one level lower, we can extend its progression.

```python
n = len(lessonLevels)
# dp[i] = longest progression ending at lesson i (one lesson alone is valid)
dp = [1] * n
answer = 0

for i in range(n):
    for j in range(i):
        # lesson j is exactly one level lower, so lesson i can follow it
        if lessonLevels[j] == lessonLevels[i] - 1:
            dp[i] = max(dp[i], dp[j] + 1)
    answer = max(answer, dp[i])
```

This is correct, but it has two nested loops. With 100,000 lessons that is about 5 billion checks, which is far too slow.


### Better Solution: Remember the Best Progression for Each Level

Look at what the inner loop is really searching for. For a lesson at level `x`, it wants the longest progression that ends at level `x - 1`. It does not care which lesson that was.


So we keep a dictionary called `bestEndingAt`:

- **key:** a level
- **value:** the longest progression seen so far that ends at this level


For each lesson at level `x`:

1. `length = bestEndingAt[x - 1] + 1`. If level `x - 1` is not in the dictionary, `length` is 1.
2. Store `length` for level `x` if it is bigger than the value already there.
3. Update the answer.


The dictionary only holds lessons we have already passed. So a lesson is never attached to a later one, and the original order is kept automatically.


**Dry run** on `[5, 2, 3, 8, 4, 6, 5]`:

| Level | Best at `level - 1` | New length | Progression |
|:-----:|:-------------------:|:----------:|:-----------:|
| 5 | 0 | 1 | [5] |
| 2 | 0 | 1 | [2] |
| 3 | 1 | 2 | [2, 3] |
| 8 | 0 | 1 | [8] |
| 4 | 2 | 3 | [2, 3, 4] |
| 6 | 1 | 2 | [5, 6] |
| 5 | 3 | 4 | [2, 3, 4, 5] |

The second `5` gets length 4 and replaces the 1 stored by the first `5`. The answer is 4.


**Why a dictionary?** It finds the stored value of any level in O(1) time on average. Each lesson needs only a few dictionary operations, so the whole method runs in O(n) time.


## Follow-up: Flexible Progression

Now a lesson at level `x` can follow any earlier lesson whose level is between `x - maxLevelGap` and `x - 1`. The main task is just the special case `maxLevelGap = 1`.


We keep the same idea: the best progression ending at each level. The only change is that we now need the **best value over a range of levels**, not just one level.


### Simple Version: Scan the Allowed Range

Levels are at most 100,000, so a plain list indexed by level can replace the dictionary here.

```python
bestEndingAt = [0] * 100001  # levels go up to 100,000
answer = 0

for level in lessonLevels:
    best = 0
    # check every level this lesson is allowed to follow
    for prev in range(max(1, level - maxLevelGap), level):
        best = max(best, bestEndingAt[prev])
    bestEndingAt[level] = max(bestEndingAt[level], best + 1)
    answer = max(answer, best + 1)
```

The range can hold up to 100,000 levels. So the inner loop can run up to 100,000 times for every lesson, which is again billions of steps.


We need two operations to be fast:

1. Get the best value in a range of levels.
2. Change the value of one level.


A dictionary or a sorted list finds one level quickly. But to get the best value in a range, it still has to visit every level inside it. A **segment tree** is built exactly for these two operations.


### Better Solution: Segment Tree

A segment tree is a binary tree over all levels. Each leaf is one level. Every box above the leaves stores the best value among all the levels below it.

```
                         [0..7]
             [0..3]                  [4..7]
       [0..1]      [2..3]      [4..5]      [6..7]
      0     1     2     3     4     5     6     7
```

- To get the best of levels 1 to 6, we only read 4 boxes: leaf `1`, `[2..3]`, `[4..5]` and leaf `6`.
- To update level 5, we only touch the boxes on its path to the top: leaf `5`, `[4..5]`, `[4..7]` and `[0..7]`.


The tree has about log(L) rows, so both operations take O(log L) time.


**How we store it.** We build the tree ourselves from one plain list:

- `tree[1]` is the root.
- Box `i` has children `2 * i` and `2 * i + 1`. So the parent of box `i` is `i // 2`.
- The number of leaves, `size`, is a power of 2 bigger than the largest level. Level `x` lives at `tree[size + x]`.


**`update(level, length)`** starts at the leaf of `level` and climbs to the root, raising each box to `length`. Values only ever go up, so once a box is already big enough, every box above it is too, and we stop.


**`queryMax(left, right)`** starts with `lo` and `hi` at the two leaves and climbs one row at a time:

- If `lo` is a right child, its parent also covers levels to the left of our range. So we take `lo` alone and move it one step right.
- If `hi` is a left child, its parent also covers levels to the right of our range. So we take `hi` alone and move it one step left.
- Everything still between `lo` and `hi` is fully inside the range, so both can move up to their parents.


**Putting it together.** For each lesson at level `x`:

1. `best = queryMax(max(1, x - maxLevelGap), x - 1)`
2. `length = best + 1`
3. `update(x, length)` and update the answer.


The range starts at 1 or later, because there are no levels below 1. It stops at `x - 1`, so two lessons with the same level are never picked together. This keeps the levels strictly increasing.


For level 1 the range is empty, and `queryMax` simply returns 0.


**Dry run** on `[4, 12, 6, 9, 7, 10]` with `maxLevelGap = 2`:

| Level | Levels it can follow | Best found | New length |
|:-----:|:--------------------:|:----------:|:----------:|
| 4 | 2 to 3 | 0 | 1 |
| 12 | 10 to 11 | 0 | 1 |
| 6 | 4 to 5 | 1 (level 4) | 2 |
| 9 | 7 to 8 | 0 | 1 |
| 7 | 5 to 6 | 2 (level 6) | 3 |
| 10 | 8 to 9 | 1 (level 9) | 2 |

The answer is 3, from the progression `[4, 6, 7]`.


## Final Code

```python
from typing import List


class MaxSegmentTree:
    """
    Segment tree over lesson levels.
    Each box keeps the longest progression that ends at a level inside it.
    """

    def __init__(self, maxLevel: int):
        # size must be bigger than maxLevel so every level gets its own leaf
        self.size = 1  # number of leaves (a power of 2)
        while self.size <= maxLevel:
            self.size *= 2
        # tree[1] is the root, box i has children 2 * i and 2 * i + 1
        self.tree = [0] * (2 * self.size)

    def update(self, level: int, length: int) -> None:
        """Stores length for this level if it beats the current value."""
        node = self.size + level  # leaf of this level
        # Raise every box on the path up to the root.
        # If a box is already big enough, all boxes above are too: stop.
        while node >= 1 and self.tree[node] < length:
            self.tree[node] = length
            node //= 2

    def queryMax(self, left: int, right: int) -> int:
        """
        Returns the longest length stored for any level in [left, right].
        Returns 0 if the range is empty.
        """
        best = 0
        lo = self.size + left
        hi = self.size + right

        while lo <= hi:
            # lo is a right child: its parent also covers levels on the left,
            # so take lo alone and step right
            if lo % 2 == 1:
                best = max(best, self.tree[lo])
                lo += 1
            # hi is a left child: its parent also covers levels on the right,
            # so take hi alone and step left
            if hi % 2 == 0:
                best = max(best, self.tree[hi])
                hi -= 1
            # the rest lies fully inside the range: move one row up
            lo //= 2
            hi //= 2
        return best


class LearningProgressPlanner:

    def __init__(self):
        pass

    def longestStepByStepProgression(self, lessonLevels: List[int]) -> int:
        # level -> longest progression seen so far that ends at this level
        bestEndingAt = {}
        answer = 0

        for level in lessonLevels:
            # attach this lesson to the best progression ending at level - 1
            length = bestEndingAt.get(level - 1, 0) + 1
            stored = bestEndingAt.get(level, 0)
            bestEndingAt[level] = max(stored, length)
            answer = max(answer, length)
        return answer

    def longestFlexibleProgression(self, lessonLevels: List[int],
                                   maxLevelGap: int) -> int:
        # same "best progression per level" idea, stored in a segment tree
        bestEndingAt = MaxSegmentTree(max(lessonLevels))
        answer = 0

        for level in lessonLevels:
            # previous level must be in [level - maxLevelGap, level - 1]
            low = max(1, level - maxLevelGap)
            length = bestEndingAt.queryMax(low, level - 1) + 1

            bestEndingAt.update(level, length)
            answer = max(answer, length)
        return answer
```


## Complexity

| Method | Time | Space |
|:------:|:----:|:-----:|
| `longestStepByStepProgression` | O(n) | O(n) |
| `longestFlexibleProgression` | O(L + n log L) | O(L) |

Here `n` is the number of lessons and `L` is the largest level. Building the empty tree takes O(L), then each lesson takes O(log L).