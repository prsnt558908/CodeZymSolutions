# Locate Parcel Weight Group in Python

#### Problem Statement
[https://codezym.com/question/429-locate-parcel-weight-group](https://codezym.com/question/429-locate-parcel-weight-group)


## Core Idea

The parcels are already sorted from lightest to heaviest. So all parcels with the same weight sit **next to each other** in one block.


That means we do not need to find every matching parcel. We only need two things: the **left edge** of the block and the **right edge** of the block.


A sorted list is the perfect place for **binary search**. We run binary search two times. The first run keeps moving left after it finds a match, so it lands on the left edge. The second run keeps moving right after a match, so it lands on the right edge.


Each run takes `O(log n)` time, so the whole solution is `O(log n)`.


We will first build a simple linear scan, see why it is too slow, and then improve it step by step.


---


## Problem in Simple Words

You get a sorted list of weights and one `wantedWeight`.


Return `[first, last]`, the first and last index where `wantedWeight` appears.


If it does not appear at all, return `[-1, -1]`.


Example: `[150, 220, 220, 220, 480, 650]` with `wantedWeight = 220` gives `[1, 3]`.


---


## Solution 1: Simple Linear Scan

### Idea

Walk through the list from start to end.


- The first time we see `wantedWeight`, save that index as `first`.
- Every time we see `wantedWeight`, update `last` to that index.


When the loop ends, `last` holds the final match. If we never saw the weight, both stay `-1`.


### Code

```python
class ParcelRow:
    def __init__(self):
        pass

    def locateWeightGroup(self, parcelWeights, wantedWeight):
        """
        Walks through every parcel once.
        The first match gives the first position, the last match gives the last position.
        """
        first = -1
        last = -1

        for i in range(len(parcelWeights)):
            if parcelWeights[i] == wantedWeight:
                if first == -1:
                    first = i  # first time we see this weight
                last = i  # keeps moving forward, ends at the last match

        return [first, last]
```


### Complexity

- **Time:** `O(n)`, we may look at every parcel.
- **Space:** `O(1)`


### What is the problem?

This answer is correct, but the problem asks for `O(log n)` time.


Also, this code never uses the fact that the list is **sorted**. When a list is sorted, looking at every item is usually wasted work.


---


## A First Improvement Attempt (Still Not Enough)

A common next thought is:


1. Use normal binary search to find **any** position of `wantedWeight`.
2. From there, walk left until the weight changes, then walk right until the weight changes.


Step 1 is fast. But step 2 can be slow.


Imagine all 100,000 parcels weigh `7` and we search for `7`. Binary search finds the middle quickly, but then we walk 50,000 steps left and 50,000 steps right. That is `O(n)` again.


So walking is the weak part. We need binary search to find the **edges** directly.


---


## Solution 2: Binary Search Twice (Optimal)

### Key Observation

Normal binary search stops as soon as it finds a match. But a match in the middle of the block is not enough for us.


The trick is simple: **when we find a match, save it and keep searching.**


- To find the **first** position: after a match, keep searching on the **left** side. Maybe there is an earlier match.
- To find the **last** position: after a match, keep searching on the **right** side. Maybe there is a later match.


The search keeps shrinking until no range is left. The last saved match is the edge we want.


### Finding the First Position

We keep `left`, `right` and `answer = -1`. At each step we look at the middle index `mid`.


| Middle weight vs wanted | What we do | Why |
|---|---|---|
| Equal | `answer = mid`, then `right = mid - 1` | Save it, and check if an earlier match exists |
| Smaller | `left = mid + 1` | Wanted weight must be on the right |
| Bigger | `right = mid - 1` | Wanted weight must be on the left |


### Finding the Last Position

This is the same search with **one change**. When the middle weight is equal, we do `left = mid + 1` instead, so we keep going right.


| Middle weight vs wanted | What we do | Why |
|---|---|---|
| Equal | `answer = mid`, then `left = mid + 1` | Save it, and check if a later match exists |
| Smaller | `left = mid + 1` | Wanted weight must be on the right |
| Bigger | `right = mid - 1` | Wanted weight must be on the left |


### Dry Run on Example 1

`parcelWeights = [150, 220, 220, 220, 480, 650]`, `wantedWeight = 220`


**First position search**

| left | right | mid | weight at mid | action | answer |
|---|---|---|---|---|---|
| 0 | 5 | 2 | 220 | match, go left | 2 |
| 0 | 1 | 0 | 150 | smaller, go right | 2 |
| 1 | 1 | 1 | 220 | match, go left | 1 |


Now `left = 1` and `right = 0`, so the loop stops. **First = 1**.


**Last position search**

| left | right | mid | weight at mid | action | answer |
|---|---|---|---|---|---|
| 0 | 5 | 2 | 220 | match, go right | 2 |
| 3 | 5 | 4 | 480 | bigger, go left | 2 |
| 3 | 3 | 3 | 220 | match, go right | 3 |


Now `left = 4` and `right = 3`, so the loop stops. **Last = 3**.


Final answer: `[1, 3]`


### Small Details Worth Knowing

- **Early exit:** if the first search returns `-1`, the weight is not in the list. We return `[-1, -1]` right away and skip the second search.
- **Empty list:** `right` starts at `-1`, so the loop never runs and we get `-1`. No special check is needed.
- **Integer middle:** we use `//` so `mid` is always a whole number that can be used as an index.
- **Only one match:** both searches land on the same index, so we return it twice, like `[2, 2]`.


### Code

```python
class ParcelRow:
    def __init__(self):
        pass

    def locateWeightGroup(self, parcelWeights, wantedWeight):
        """
        Returns the first and last positions of wantedWeight in the sorted row.
        Returns [-1, -1] when no parcel has that weight.
        """
        first = self.findFirstPosition(parcelWeights, wantedWeight)

        # Weight is not in the row, so there is no group to find
        if first == -1:
            return [-1, -1]

        last = self.findLastPosition(parcelWeights, wantedWeight)
        return [first, last]

    def findFirstPosition(self, parcelWeights, wantedWeight):
        """
        Binary search that keeps going LEFT after a match.
        Ends at the leftmost position of wantedWeight, or -1 if not found.
        """
        left = 0
        right = len(parcelWeights) - 1
        answer = -1

        while left <= right:
            mid = (left + right) // 2
            weight = parcelWeights[mid]

            if weight == wantedWeight:
                answer = mid  # save it, but an earlier match may exist
                right = mid - 1  # keep looking on the left side
            elif weight < wantedWeight:
                left = mid + 1  # wanted weight is on the right side
            else:
                right = mid - 1  # wanted weight is on the left side

        return answer

    def findLastPosition(self, parcelWeights, wantedWeight):
        """
        Binary search that keeps going RIGHT after a match.
        Ends at the rightmost position of wantedWeight, or -1 if not found.
        """
        left = 0
        right = len(parcelWeights) - 1
        answer = -1

        while left <= right:
            mid = (left + right) // 2
            weight = parcelWeights[mid]

            if weight == wantedWeight:
                answer = mid  # save it, but a later match may exist
                left = mid + 1  # keep looking on the right side
            elif weight < wantedWeight:
                left = mid + 1  # wanted weight is on the right side
            else:
                right = mid - 1  # wanted weight is on the left side

        return answer
```


### Complexity

- **Time:** `O(log n)`. Each binary search cuts the range in half every step, and we run at most two of them.
- **Space:** `O(1)`. We only use a few integer variables.


---


## Summary

| Approach | Time | Space | Meets `O(log n)`? |
|---|---|---|---|
| Linear scan | `O(n)` | `O(1)` | No |
| Find one match, then walk | `O(n)` worst case | `O(1)` | No |
| Binary search twice | `O(log n)` | `O(1)` | Yes |


The main lesson: when a list is sorted and you need the **edge** of a group, do not stop at the first match. Save it, then keep searching in the direction of the edge you want.