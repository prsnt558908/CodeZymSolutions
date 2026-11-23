# Maximum Requests in Continuous Time Window in Python

#### Problem Statement
[https://codezym.com/question/256-max-requests-continuous-time-window](https://codezym.com/question/256-max-requests-continuous-time-window)


This is a **sliding window** problem. We sort the timestamps first, so the requests inside any time window become one continuous block of the list. Then we move that block across the list with two pointers: the right pointer adds a new request, and the left pointer drops old requests that no longer fit.

The largest block we see along the way is the answer. Sorting costs O(n log n) and the sliding part is only O(n).


## Understanding the Problem

Each number in `timestamp` is one request. A window of length `windowSize` starting at minute `start` contains every request `t` with `start <= t < start + windowSize`.

The end is **not** included. For `windowSize = 5`, the window `[10, 15)` covers minutes 10 to 14, but not 15. That is why Example 4 returns `1`.

Two more details:

- Timestamps can come in any order.
- If the same time appears twice, it counts as two requests.


## Key Observation

There can be about a billion possible start minutes, so we cannot try them all. Luckily, we don't need to.

Take any window with some requests inside. Slide its start to the right until it reaches the earliest request inside it. The window keeps its length, so its end also moves right. Every request that was inside is still inside, and the window may even pick up a few more.

So **a best window always starts exactly at some request time**. We only need to try `n` starts, not a billion.


## Solution 1: Brute Force

Try every request time as the window start. For each start, count how many requests fall inside the window.

```python
best = 0
for start in timestamp:
    count = 0
    for t in timestamp:
        if start <= t < start + windowSize:
            count += 1
    best = max(best, count)
return best
```

This gives the right answer, but it is slow. For each of the `n` starts, we scan all `n` requests again. That is **O(n²)** time.

With 100,000 requests, that is 10 billion checks. Way too slow.

The waste: nearby windows share many of the same requests, yet we recount every window from scratch.


## Solution 2: Sort + Sliding Window

### Step 1: Sort the timestamps

Once the times are sorted, the requests inside any window sit next to each other in the list. So every window is just a continuous block of the sorted list.

A block is valid when:

**newest time - oldest time < windowSize**

This is the window rule from the problem, with the window starting at the oldest request of the block.


### Step 2: Slide two pointers

- `right` moves forward one request at a time and adds it to the window.
- While the new request is too far from the request at `left`, move `left` forward.
- Now `right - left + 1` is the size of a valid window. Keep the largest one.

**Why does `left` only move forward?** If the request at `left` is too far from the current request, it is even farther from every later request, since times only grow. So a dropped request never comes back.

Also, `left` never passes `right`. A single request always fits in a window by itself, since `windowSize` is at least 1.

Each pointer moves at most `n` times, so this step is O(n).


### Dry Run

Example 2: `timestamp = [8, 1, 6, 2, 3]`, `windowSize = 4`

After sorting: `[1, 2, 3, 6, 8]`

| right | new time | left moves to | window | size | best |
|-------|----------|---------------|--------|------|------|
| 0 | 1 | 0 | [1] | 1 | 1 |
| 1 | 2 | 0 | [1, 2] | 2 | 2 |
| 2 | 3 | 0 | [1, 2, 3] | 3 | 3 |
| 3 | 6 | 2 | [3, 6] | 2 | 3 |
| 4 | 8 | 3 | [6, 8] | 2 | 3 |

At `right = 3`, time 6 is too far from 1 (6 - 1 = 5) and from 2 (6 - 2 = 4, which is not less than 4). So both are dropped.

Final answer: **3**


### Why `sorted()` and not `.sort()`?

`timestamp.sort()` would sort the caller's list in place and change their data.

`sorted(timestamp)` builds a new sorted list instead, so the input stays exactly as it was.


### Python Code

```python
from typing import List


class MaximumRequestsInWindow:

    def __init__(self):
        pass

    def maximumRequestsInWindow(self, timestamp: List[int], windowSize: int) -> int:
        """
        Returns the maximum number of requests inside any window
        [start, start + windowSize).
        """
        # sorted() returns a new list, so the caller's list is not changed
        times = sorted(timestamp)

        left = 0  # index of the oldest request in the current window
        best = 0

        for right in range(len(times)):
            # The window starts at the oldest request. Drop requests from the left
            # until the newest request is less than windowSize minutes away from it.
            while times[right] - times[left] >= windowSize:
                left += 1
            best = max(best, right - left + 1)

        return best
```


### Complexity

- **Time:** O(n log n) for sorting. The sliding window part is O(n).
- **Space:** O(n) for the sorted copy.