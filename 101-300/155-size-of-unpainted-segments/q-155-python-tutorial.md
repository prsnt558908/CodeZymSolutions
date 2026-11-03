# Size of Unpainted Segments in Python

#### Problem Statement
[https://codezym.com/question/155-size-of-unpainted-segments](https://codezym.com/question/155-size-of-unpainted-segments)


The main observation is that only the start and end values matter. If we cut the number line at every start and end value, we get a small number of **pieces**. Every interval covers whole pieces, so a piece is always either fully painted or fully unpainted.


So instead of painting single numbers, we paint whole pieces. To avoid walking over painted pieces again and again, every piece keeps a pointer that lets us jump over the painted ones.


Each piece gets painted only once, and the whole solution runs in `O(n log n)` time. We will reach it in three small steps, starting from brute force.


---


## Approach 1: Brute Force (Paint Every Cell)

Think of the number line as unit cells. Cell `x` is the segment `[x, x + 1)`.


An interval `[start, end)` is made of the cells `start, start + 1, ..., end - 1`. This is also why `[1, 5)` and `[5, 8)` do not overlap: they share no cell.


For each interval, we walk over all of its cells. If a cell is not painted yet, we count it and paint it.

```python
# painted[x] is True if cell [x, x + 1) is already painted
# max_end is the largest end value among all intervals
painted = [False] * max_end

for start, end in zip(starts, ends):
    newly_painted = 0
    for x in range(start, end):
        if not painted[x]:
            painted[x] = True
            newly_painted += 1
    result.append(newly_painted)
```

This is easy to follow, but it is too slow for the given limits:

- One interval can be `10^8` cells long. With `10^5` intervals, that is up to `10^13` steps.
- The `painted` list needs one slot for every number up to `10^8`.


---


## Approach 2: Cut the Number Line into Pieces

We do not need every single cell.


Sort all start and end values and pick two neighbours `a` and `b` from that sorted list. No interval starts or ends strictly between them. So every interval either covers all of `[a, b)` or none of it.


This means `[a, b)` behaves like one single block. We call it a **piece**. (This trick is often called coordinate compression.)


1. Collect every start and end value, remove duplicates and sort them. Call this list `points`.
2. Piece `k` is `[points[k], points[k + 1])`. Its length is `points[k + 1] - points[k]`.
3. If `index_of[v]` is the position of `v` in `points`, then an interval `[start, end)` covers exactly the pieces from `index_of[start]` to `index_of[end] - 1`.


For `["3,7", "1,4", "6,9"]` we get `points = [1, 3, 4, 6, 7, 9]` and these pieces:

| Piece | Segment | Length |
|:-----:|:-------:|:------:|
| 0 | `[1, 3)` | 2 |
| 1 | `[3, 4)` | 1 |
| 2 | `[4, 6)` | 2 |
| 3 | `[6, 7)` | 1 |
| 4 | `[7, 9)` | 2 |


Now we paint pieces instead of cells:

```python
# painted[k] is True if piece k is already painted
painted = [False] * len(points)

newly_painted = 0
for k in range(index_of[start], index_of[end]):
    if not painted[k]:
        painted[k] = True
        newly_painted += points[k + 1] - points[k]
```

There are at most `2n` points, so memory is no longer a problem.


But time still is. One interval can cover up to `2n - 1` pieces, and we keep walking over pieces that are already painted. If many big intervals cover the same area, each of them scans the same painted pieces again. That is `O(n^2)` in the worst case.


---


## Approach 3: Jump Over Painted Pieces (Final Solution)

We want to skip painted pieces without visiting them one by one.


So every piece `k` keeps one pointer, `next_unpainted[k]`:

- If piece `k` is unpainted, it points to itself.
- If piece `k` is painted, it points to some piece on its right.


To find the first unpainted piece starting from `k`, we keep jumping until we reach a piece that points to itself. Every jump skips only painted pieces, so the piece we land on is the first unpainted one.

- At the start, all pieces are unpainted, so `next_unpainted[k] = k`.
- When we paint piece `k`, we set `next_unpainted[k] = k + 1`.


Painting an interval is now simple: find the first unpainted piece inside it, add its length, mark it painted and jump to the next unpainted piece. Stop as soon as the jump lands outside the interval.


**The shortcut:** following a long chain of painted pieces again and again would still be slow. So after we find the unpainted piece, we make every piece on the way point directly to it. Next time, it is a single jump. (This trick is called path compression.)


The last index of `points` is not a real piece, because no piece starts there. It is never painted, so it always points to itself and every search stops there at the latest.


### Dry Run

Using the pieces from the table above:

1. `[3, 7)` covers pieces `1` to `3`. All of them are unpainted, so the answer is `1 + 2 + 1 = 4`. Their pointers are now `1 → 2`, `2 → 3` and `3 → 4`.
2. `[1, 4)` covers pieces `0` to `1`. Piece `0` is unpainted, so we add `2`. From piece `1` we jump `1 → 2 → 3 → 4`. Piece `4` is outside this interval, so we stop. The answer is `2`, and the shortcut now points pieces `1`, `2` and `3` directly to `4`.
3. `[6, 9)` covers pieces `3` to `4`. Piece `3` jumps straight to `4`, which is unpainted, so the answer is `2`.


Final result: `[4, 2, 2]`.


### Why These Data Structures

- **`points` (sorted list):** turns a piece index into its real length. We build it with `sorted(set(...))`, which removes duplicates and sorts the values.
- **`index_of` (dict):** gives the index of any start or end value in `O(1)`.
- **`next_unpainted` (list):** lets us jump over painted pieces instead of visiting them one by one.


### Complexity

- **Time:** `O(n log n)`. Sorting the points takes `O(n log n)`. Each piece is painted only once, and the shortcut keeps every search fast.
- **Space:** `O(n)` for `points`, `index_of` and `next_unpainted`.


### Python Code

```python
from typing import List


class IntervalPainting:

    def __init__(self):
        # Sorted unique start and end values. Piece k is [points[k], points[k + 1])
        self.points = []
        # If piece k is unpainted, next_unpainted[k] == k.
        # If it is painted, it points to a piece on its right.
        self.next_unpainted = []

    def amountPainted(self, intervals: List[str]) -> List[int]:
        # 1. Parse "start,end" into two lists
        starts = []
        ends = []
        for interval in intervals:
            start, end = interval.split(",")
            starts.append(int(start))
            ends.append(int(end))

        # 2. Sorted unique points and value -> its index in points
        self.points = sorted(set(starts + ends))
        index_of = {value: k for k, value in enumerate(self.points)}

        # 3. Every piece starts unpainted, so it points to itself.
        # The last index is not a real piece. It always points to itself,
        # so every search stops there at the latest.
        self.next_unpainted = list(range(len(self.points)))

        # 4. Paint the intervals in the given order
        result = []
        for start, end in zip(starts, ends):
            result.append(self.paint(index_of[start], index_of[end]))
        return result

    def paint(self, start_index: int, end_index: int) -> int:
        """Paints every unpainted piece in [start_index, end_index)
        and returns their total length."""
        newly_painted = 0
        piece = self.findUnpainted(start_index)

        while piece < end_index:
            newly_painted += self.points[piece + 1] - self.points[piece]
            self.next_unpainted[piece] = piece + 1  # painted now, so point to the next piece
            piece = self.findUnpainted(piece + 1)
        return newly_painted

    def findUnpainted(self, index: int) -> int:
        """Returns the first unpainted piece at index or after it."""
        # follow the pointers until we reach a piece that points to itself
        target = index
        while self.next_unpainted[target] != target:
            target = self.next_unpainted[target]

        # shortcut: point every piece on the way directly to target
        while self.next_unpainted[index] != target:
            next_index = self.next_unpainted[index]
            self.next_unpainted[index] = target
            index = next_index
        return target
```