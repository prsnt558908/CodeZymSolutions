# Correctly Arranged Conveyor Segments in Python

#### Problem Statement
[https://codezym.com/question/423-arranged-conveyor-systems](https://codezym.com/question/423-arranged-conveyor-systems)


## Core Idea

For each package we only care about one thing: is its code even or odd. Even means domestic and odd means international.


A segment is correctly arranged when no two neighbors inside it have the same type. Let us call two neighbors with the same type a **break**.


Breaks never change from one query to the next. So we find all of them once, in a single pass over the belt. During that pass, we give every package a **group number** that goes up by 1 at every break.


Now a segment is correctly arranged exactly when its first and last packages have the same group number. Each query becomes one simple comparison.


## Solution 1: Brute Force

For every range, walk from `start` to `end` and compare each package with the one just before it.

```python
# Returns True if packages from start to end alternate between even and odd
def isArranged(self, packageCodes: List[int], start: int, end: int) -> bool:
    for i in range(start + 1, end + 1):
        if packageCodes[i] % 2 == packageCodes[i - 1] % 2:
            return False  # two neighbors of the same type
    return True
```


This gives correct answers, but it is slow. One range can cover all 100,000 packages, and there can be 100,000 ranges. That is up to 10 billion checks.


The waste is easy to spot. We check the same neighbor pairs again and again for different ranges, even though the result for a pair never changes.


## Solution 2: Group Numbers (Optimal)

### Step 1: Give every package a group number

Walk the belt once from left to right.

- Position 0 gets group 0.
- Every next package gets the same group number as the package before it.
- But if both of them have the same type, there is a break. So the package gets the next group number instead.


Inside one group, packages always alternate.


Let us try Example 2: `packageCodes = [3, 8, 14, 11, 6, 2]`

| Position | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| Code | 3 | 8 | 14 | 11 | 6 | 2 |
| Type | Odd | Even | Even | Odd | Even | Even |
| Group | 0 | 0 | 1 | 1 | 1 | 2 |


There are two breaks: `8, 14` are both even, and `6, 2` are both even.


### Step 2: Answer each range

The group number goes up only at a break. So if `start` and `end` have the same group number, there is no break inside the segment. If the numbers are different, at least one break is inside it.

| Range | Group of start | Group of end | Answer |
|---|---|---|---|
| `"0,1"` | 0 | 0 | `True` |
| `"1,3"` | 0 | 1 | `False` |
| `"2,4"` | 1 | 1 | `True` |
| `"4,5"` | 1 | 2 | `False` |


Look closely at `"2,4"`. There is a break between positions 1 and 2, but position 1 is outside the segment. Both ends are in group 1, so `[14, 11, 6]` is correctly arranged.


A segment with only one package has the same position at both ends, so it is always `True`.


### Why a list?

`group_id[i]` stores the group number of position `i`. A query only needs the group numbers of its two ends, and a list gives us any of them in O(1).


If you know prefix sums, `group_id[i]` is just the count of breaks from the start of the belt up to position `i`.


### Complexity

Let `n` be the number of packages and `q` be the number of ranges.

- **Time:** O(n + q). One pass to build the groups, then O(1) per range.
- **Space:** O(n) for the `group_id` list.


### Python Code

```python
from typing import List


class ConveyorInspector:

    def __init__(self):
        pass

    def validateSegments(self, packageCodes: List[int], inspectionRanges: List[str]) -> List[bool]:
        """Returns one boolean per range: True if the packages in that range
        alternate between even and odd codes."""
        group_id = self.buildGroupIds(packageCodes)

        result = []
        for segment in inspectionRanges:
            parts = segment.split(",")
            start = int(parts[0])
            end = int(parts[1])

            # Same group at both ends means there is no break inside the segment
            result.append(group_id[start] == group_id[end])
        return result

    def buildGroupIds(self, packageCodes: List[int]) -> List[int]:
        """Gives every position a group number. The number goes up by 1
        whenever two neighbors are both even or both odd."""
        n = len(packageCodes)
        group_id = [0] * n

        for i in range(1, n):
            group_id[i] = group_id[i - 1]
            if packageCodes[i] % 2 == packageCodes[i - 1] % 2:
                group_id[i] += 1  # a break, so a new group starts here
        return group_id
```