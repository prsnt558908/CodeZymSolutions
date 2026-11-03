# Employee Schedule Common Free Time in Python

#### Problem Statement
[https://codezym.com/question/188-employee-schedule-common-free-time](https://codezym.com/question/188-employee-schedule-common-free-time)


An employee is free whenever they are not working. So **all** employees are free at the same time only when **nobody** is working. This means we don't care *who* is working. We only care whether *someone* is working.


So we can put the working intervals of all employees into one list, join the ones that overlap or touch into bigger busy blocks, and read the common free time from the gaps between these blocks. This is a small twist on the classic merge intervals problem.


We will start with a brute force that marks every time unit, then improve it by sorting all intervals and scanning them once, and finally use a min heap that takes advantage of each employee's schedule already being sorted.


---


## Understanding the Problem

Let's look at Example 1: `["1,3|6,7", "2,4", "2,5|9,12"]`

```
Time         1  2  3  4  5  6  7  8  9  10 11 12
Employee 1   [-----)        [--)
Employee 2      [-----)
Employee 3      [--------)           [--------)
Busy         [-----------)  [--)     [--------)
Free                     [--)  [-----)
```


`[1,3)` means the employee works from time `1` up to time `3`, and is free again exactly at `3`.


When we stack all intervals together, we get three busy blocks: `[1,5)`, `[6,7)` and `[9,12)`. The gaps between them, `[5,6)` and `[7,9)`, give the answer `["5,6", "7,9"]`.


Time before `1` and after `12` is also free, but it goes on forever, so we ignore it.


---


## Approach 1: Brute Force, Mark Every Time Unit

The most direct idea is to draw the timeline above in code.


Take a list `busy`, where `busy[t]` is `True` if at least one employee works during `[t, t + 1)`. Mark every interval in it. Then walk from the first start time to the last end time. Every run of free cells is a common free interval.

```python
# busy[t] is True when at least one employee works during [t, t + 1)
busy = [False] * last_end
for start, end in intervals:
    for t in range(start, end):
        busy[t] = True

# every run of free cells between first_start and last_end is common free time
t = first_start
while t < last_end:
    if busy[t]:
        t += 1
    else:
        free_start = t
        while not busy[t]:
            t += 1
        free_times.append(f"{free_start},{t}")
```

Here `intervals` holds the working intervals of all employees, `first_start` is the smallest start time and `last_end` is the largest end time.


### Problems with this approach

- Time can go up to `10^8`, so the `busy` list can need 100 million cells. That is a lot of memory.
- Marking cells one by one can take billions of steps when the intervals are long.
- Most of this work is wasted. Free time can only begin where some interval ends, and can only finish where some interval starts. So we only need the interval boundaries, not every single time unit.


---


## Approach 2: Sort All Intervals and Scan

Let's sort all intervals by start time and walk through them from left to right.


While walking, we keep just one number, `busy_until`: the end time of the current busy block. This single number replaces the whole `busy` list from Approach 1.


For every interval `(start, end)`:

1. If `start > busy_until`, nobody is working between `busy_until` and `start`. So `busy_until,start` is a common free interval.
2. Update `busy_until = max(busy_until, end)`.


**Why does sorting make this work?** Every interval we saw earlier ends at or before `busy_until`. Every interval we see later starts at or after `start`. So when `start > busy_until`, no interval can cover the time between them.


**A few small details**

- We use `>` and not `>=`. If `start == busy_until`, the gap has zero length (like `5,5`), so we skip it. This is what happens in Example 3.
- We use `max` because an interval can sit fully inside an earlier, longer interval. In Example 4, `[12,15)` is inside `[10,20)`, so `busy_until` must stay `20` and not drop to `15`.
- `busy_until` starts at the earliest start time, so the infinite free time before it is never reported. The loop stops after the last interval, so the infinite free time after it is never reported either.


**Why a list of tuples?** Each interval is just a pair of numbers, so a `(start, end)` tuple is enough. We don't need to remember which employee it came from.


### Dry Run on Example 1

After sorting, the intervals are `[1,3) [2,4) [2,5) [6,7) [9,12)` and `busy_until` starts at `1`.

| Interval | `start > busy_until`? | Free interval | `busy_until` after |
|---|---|---|---|
| `[1,3)` | `1 > 1`, no | | `3` |
| `[2,4)` | `2 > 3`, no | | `4` |
| `[2,5)` | `2 > 4`, no | | `5` |
| `[6,7)` | `6 > 5`, yes | `5,6` | `7` |
| `[9,12)` | `9 > 7`, yes | `7,9` | `12` |


### Code

```python
from typing import List, Tuple


class EmployeeScheduleCommonFreeTime:

    def __init__(self):
        pass

    def employeeFreeTime(self, schedule: List[str]) -> List[str]:
        # working intervals of all employees in one list.
        # We don't need to remember which employee an interval belongs to.
        intervals = []
        for employee_schedule in schedule:
            intervals.extend(self.parseIntervals(employee_schedule))

        free_times = []
        if not intervals:
            return free_times

        # sort by start time, so we can scan the timeline from left to right
        intervals.sort(key=lambda interval: interval[0])

        # end time of the current busy block.
        # Starting at the earliest start time means the infinite
        # free time before it is never reported.
        busy_until = intervals[0][0]

        for start, end in intervals:
            # strictly greater, so zero length gaps like "5,5" are skipped
            if start > busy_until:
                free_times.append(f"{busy_until},{start}")
            busy_until = max(busy_until, end)

        return free_times

    # converts "1,3|6,7" into [(1, 3), (6, 7)]
    def parseIntervals(self, employee_schedule: str) -> List[Tuple[int, int]]:
        intervals = []
        for part in employee_schedule.split("|"):
            text = part.strip()
            if not text:
                continue
            start, end = text.split(",")
            intervals.append((int(start), int(end)))
        return intervals
```


### Complexity

- **Time:** `O(N log N)`, where `N` is the total number of intervals. Sorting is the slowest part. The scan itself is only `O(N)`.
- **Space:** `O(N)` to keep all intervals in one list.


---


## Approach 3: Min Heap Over the Sorted Schedules

A natural follow up question is: can we use the fact that each employee's intervals are already sorted? Approach 2 ignores this and sorts everything again.


We can use it the same way we merge K sorted lists. Keep only the **next unprocessed interval of each employee** in a min heap ordered by start time. The top of the heap is then always the interval with the smallest start time among all employees, which is exactly the next interval Approach 2 would have picked after sorting.


Steps:

1. Put the first interval of every employee into the heap.
2. Take out the interval with the smallest start time.
3. Do the same `busy_until` check as in Approach 2.
4. Put the next interval of that same employee into the heap, if there is one.
5. Repeat until the heap is empty.


**Why a min heap (`heapq`)?** `heapq` keeps a normal list as a min heap. Taking out the smallest interval and putting in the next one both cost only `O(log K)`, and the heap never holds more than `K` intervals, one per employee.


Each heap entry is a tuple `(start, end, employee, index)`. Tuples are compared starting from their first value, so the entry with the smallest start time is always on top. We keep `employee` and `index` so we know which interval to push next.


### Code

```python
import heapq
from typing import List, Tuple


class EmployeeScheduleCommonFreeTime:

    def __init__(self):
        pass

    def employeeFreeTime(self, schedule: List[str]) -> List[str]:
        # working intervals of each employee, already sorted by start time
        employees = [self.parseIntervals(employee_schedule) for employee_schedule in schedule]

        # heap entry: (start, end, employee, index), smallest start on top.
        # It holds at most one interval per employee: the next unprocessed one.
        heap = []
        for employee, intervals in enumerate(employees):
            if intervals:
                start, end = intervals[0]
                heapq.heappush(heap, (start, end, employee, 0))

        free_times = []
        if not heap:
            return free_times

        # end time of the current busy block, starts at the earliest start time
        busy_until = heap[0][0]

        while heap:
            start, end, employee, index = heapq.heappop(heap)

            # same gap check as the sort and scan solution
            if start > busy_until:
                free_times.append(f"{busy_until},{start}")
            busy_until = max(busy_until, end)

            # push the next interval of the same employee, if there is one
            intervals = employees[employee]
            if index + 1 < len(intervals):
                next_start, next_end = intervals[index + 1]
                heapq.heappush(heap, (next_start, next_end, employee, index + 1))

        return free_times

    # converts "1,3|6,7" into [(1, 3), (6, 7)]
    def parseIntervals(self, employee_schedule: str) -> List[Tuple[int, int]]:
        intervals = []
        for part in employee_schedule.split("|"):
            text = part.strip()
            if not text:
                continue
            start, end = text.split(",")
            intervals.append((int(start), int(end)))
        return intervals
```


### Complexity

- **Time:** `O(N log K)`, where `K` is the number of employees. Each interval goes in and out of the heap once.
- **Space:** `O(N)` to keep the parsed schedules. The heap itself holds at most `K` entries.