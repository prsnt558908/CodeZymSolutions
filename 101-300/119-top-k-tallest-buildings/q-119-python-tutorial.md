# Top K Tallest Buildings in Python

#### Problem Statement
[https://codezym.com/question/119-top-k-tallest-buildings](https://codezym.com/question/119-top-k-tallest-buildings)


The whole problem comes down to one ranking rule: **a taller building is better, and if two buildings have the same height, the one with the lower index is better.**

Once we have this rule, the task is simply "pick the best `k` buildings and list them from best to worst".

We will start with a simple brute force, then improve it with sorting, and finally use a **min-heap of size `k`** which only does the work that is really needed.


---


## Understanding the Problem

We get a list of heights and a number `k`. We must return `k` indexes.

The order of the answer matters:

1. Taller buildings come first.
2. If two buildings have the same height, the lower index comes first.

**Example:** `heights = [4, 9, 9, 2, 8]`, `k = 3`

- Height `9` is at index `1` and index `2`. Both are the tallest. Index `1` comes first.
- Next is height `8` at index `4`.

**Answer:** `[1, 2, 4]`


---


## Solution 1: Brute Force (Pick the Tallest, k Times)

### Idea

The most direct way is to repeat this `k` times:

1. Look at every building that is not picked yet.
2. Find the tallest one.
3. Mark it as picked and add it to the answer.


### Handling ties

We scan from left to right and only replace our current best when we find a building that is **strictly taller** (`>`).

So if two buildings have the same height, the first one we saw (lower index) stays as the best. This gives us the tie rule for free.


### Why a `picked` list?

We need to skip buildings that are already in the answer. A simple list of `True` / `False` values tells us in one step whether index `i` was already picked.


### Code

```python
from typing import List


class TopKTallestBuildings:
    def __init__(self):
        pass

    def topKTallestBuildingIndexes(self, heights: List[int], k: int) -> List[int]:
        """Brute force: in each of k rounds, pick the tallest building not picked yet."""
        n = len(heights)
        picked = [False] * n
        result = []

        for _ in range(k):
            best = -1
            for i in range(n):
                if picked[i]:
                    continue
                # Use '>' and not '>=' so that on a tie the lower index stays as best
                if best == -1 or heights[i] > heights[best]:
                    best = i
            picked[best] = True
            result.append(best)

        return result
```


### Complexity

- **Time:** `O(n * k)`
- **Space:** `O(n)` for the `picked` list


### Problem with this approach

Every round scans the full list again.

If `n = 100,000` and `k = 100,000`, that is about **10 billion steps**. This is far too slow.


---


## Solution 2: Sort Everything

### Idea

Instead of searching again and again, we can sort all buildings **once** using our ranking rule.

After sorting, the best building is at position `0`, the second best at position `1`, and so on. We just take the first `k`.


### Why sort indexes and not heights?

We need to return **indexes**. If we sort the heights directly, we lose track of where each height came from.

So we make a list `[0, 1, 2, ..., n-1]` and sort these indexes by looking up their heights.


### The sort key

```python
indexes.sort(key=lambda i: (-heights[i], i))
```

Python compares tuples item by item.

- `-heights[i]` makes taller buildings come first, because a bigger height becomes a smaller negative number.
- If heights are equal, Python looks at the second item `i`, so the lower index comes first.

This is our ranking rule written as code.


### Code

```python
from typing import List


class TopKTallestBuildings:
    def __init__(self):
        pass

    def topKTallestBuildingIndexes(self, heights: List[int], k: int) -> List[int]:
        """Sort all indexes by (height descending, index ascending) and return the first k."""
        indexes = list(range(len(heights)))

        # -heights[i] puts taller buildings first, i breaks ties by lower index
        indexes.sort(key=lambda i: (-heights[i], i))

        return indexes[:k]
```


### Complexity

- **Time:** `O(n log n)`
- **Space:** `O(n)`


### Problem with this approach

This is fast enough to pass, but it does extra work.

If `k = 1`, we only need the single tallest building, yet we still sort all `100,000` buildings. We are putting the whole list in order when we only care about the top few.


---


## Solution 3: Min-Heap of Size k (Best Solution)

### Idea

Think of it like a club with only `k` seats.

- We walk through the buildings one by one.
- While there are empty seats, every building gets a seat.
- Once the club is full, a new building can enter only if it is better than the **weakest** member. If it is, the weakest member leaves.

At the end, the club holds exactly the top `k` buildings.


### Why a min-heap?

To decide whether a new building gets in, we only need to compare it with the **weakest** building in the club.

A min-heap (Python's `heapq`) keeps the smallest item at `heap[0]`. We can look at it in `O(1)` and remove or add an item in `O(log k)`.

The heap never grows beyond `k` items, so each step stays cheap.


### What does "weakest" mean here?

It is the reverse of our ranking rule:

- A **shorter** building is weaker.
- For the **same height**, the building with the **higher index** is weaker.


### Why store `(height, -index)` in the heap?

`heapq` compares tuples item by item and keeps the smallest on top.

- The first item is `height`, so the **shortest** building comes on top.
- For the same height, Python compares `-index`. A **higher** index gives a **smaller** `-index`, so it comes on top.

That is exactly the weakest building, with no custom comparator needed.


### Handling ties when replacing

We scan buildings from left to right. So a new building always has a **higher index** than every building already in the heap.

That means if the new building has the **same height** as the weakest one, the new building loses the tie.

So we replace the weakest building only when the new building is **strictly taller**:

```python
elif h > heap[0][0]:
    heapq.heapreplace(heap, (h, -i))
```

`heapreplace` removes the top item and adds the new one in a single step.


### Getting the final order

Popping from a min-heap gives items from **weakest to best**.

We want **best to weakest**, so we collect them and reverse the list.


### Dry Run

`heights = [4, 9, 9, 2, 8]`, `k = 3`

| Index | Height | Action | Heap (weakest on top) |
|---|---|---|---|
| 0 | 4 | Seat free, add | `(4, 0)` |
| 1 | 9 | Seat free, add | `(4, 0), (9, -1)` |
| 2 | 9 | Seat free, add | `(4, 0), (9, -1), (9, -2)` (top = height 4) |
| 3 | 2 | 2 is not taller than 4, skip | no change |
| 4 | 8 | 8 is taller than 4, replace top | `(8, -4), (9, -1), (9, -2)` (top = height 8) |


Popping gives `(8, -4)`, then `(9, -2)`, then `(9, -1)`.

`(9, -2)` comes out before `(9, -1)` because `-2` is smaller than `-1`. In other words, index `2` is weaker than index `1`.

So the popped indexes are `4, 2, 1`. After reversing: **`[1, 2, 4]`**


### Code

```python
import heapq
from typing import List


class TopKTallestBuildings:
    def __init__(self):
        pass

    def topKTallestBuildingIndexes(self, heights: List[int], k: int) -> List[int]:
        """
        Keep only the best k buildings seen so far in a min-heap.
        The weakest of those k buildings always sits on top of the heap.
        """
        # Each item is (height, -index).
        # Smallest height comes on top. For the same height, the larger index
        # comes on top because -index is smaller. That is exactly the weakest building.
        heap = []

        for i, h in enumerate(heights):
            if len(heap) < k:
                heapq.heappush(heap, (h, -i))
            elif h > heap[0][0]:
                # New building is strictly taller than the weakest one, so swap them.
                # On equal height the new building has a higher index, so it loses.
                heapq.heapreplace(heap, (h, -i))

        # Popping gives weakest first, so reverse to get tallest first
        result = []
        while heap:
            h, neg_index = heapq.heappop(heap)
            result.append(-neg_index)
        result.reverse()
        return result
```


### Complexity

- **Time:** `O(n log k)`
- **Space:** `O(k)` for the heap

When `k` is small, this is much faster than sorting everything. When `k` is close to `n`, it performs about the same as sorting.


---


## Summary

| Solution | Time | Space | Verdict |
|---|---|---|---|
| Brute force | `O(n * k)` | `O(n)` | Too slow for large inputs |
| Sort everything | `O(n log n)` | `O(n)` | Passes, but sorts more than needed |
| Min-heap of size k | `O(n log k)` | `O(k)` | Best, only tracks what matters |


The key lessons:

- Turn the problem into a clear **ranking rule** first.
- To find the top `k` items, keep the **weakest** of them on top of a **min-heap**, so you always know who to remove next.
- Scanning left to right lets you handle the **tie rule** with a simple `>` check.