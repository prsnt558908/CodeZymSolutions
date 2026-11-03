# Employee Schedule Common Free Time in Java

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


Take a boolean array `busy`, where `busy[t]` is `true` if at least one employee works during `[t, t + 1)`. Mark every interval in it. Then walk from the first start time to the last end time. Every run of free cells is a common free interval.

```java
// busy[t] = true when at least one employee works during [t, t + 1)
boolean[] busy = new boolean[lastEnd];
for (int[] interval : intervals) {
    for (int t = interval[0]; t < interval[1]; t++) {
        busy[t] = true;
    }
}

// every run of free cells between firstStart and lastEnd is common free time
int t = firstStart;
while (t < lastEnd) {
    if (busy[t]) {
        t++;
    } else {
        int freeStart = t;
        while (!busy[t]) {
            t++;
        }
        freeTimes.add(freeStart + "," + t);
    }
}
```

Here `intervals` holds the working intervals of all employees, `firstStart` is the smallest start time and `lastEnd` is the largest end time.


### Problems with this approach

- Time can go up to `10^8`, so the `busy` array can need 100 million cells. That is a lot of memory.
- Marking cells one by one can take billions of steps when the intervals are long.
- Most of this work is wasted. Free time can only begin where some interval ends, and can only finish where some interval starts. So we only need the interval boundaries, not every single time unit.


---


## Approach 2: Sort All Intervals and Scan

Let's sort all intervals by start time and walk through them from left to right.


While walking, we keep just one number, `busyUntil`: the end time of the current busy block. This single number replaces the whole `busy` array from Approach 1.


For every interval `[start, end)`:

1. If `start > busyUntil`, nobody is working between `busyUntil` and `start`. So `busyUntil,start` is a common free interval.
2. Update `busyUntil = max(busyUntil, end)`.


**Why does sorting make this work?** Every interval we saw earlier ends at or before `busyUntil`. Every interval we see later starts at or after `start`. So when `start > busyUntil`, no interval can cover the time between them.


**A few small details**

- We use `>` and not `>=`. If `start == busyUntil`, the gap has zero length (like `5,5`), so we skip it. This is what happens in Example 3.
- We use `max` because an interval can sit fully inside an earlier, longer interval. In Example 4, `[12,15)` is inside `[10,20)`, so `busyUntil` must stay `20` and not drop to `15`.
- `busyUntil` starts at the earliest start time, so the infinite free time before it is never reported. The loop stops after the last interval, so the infinite free time after it is never reported either.


**Why `List<int[]>`?** Each interval is just a pair of numbers, so a small `int[]` of size 2 is enough. We don't need to remember which employee it came from.


### Dry Run on Example 1

After sorting, the intervals are `[1,3) [2,4) [2,5) [6,7) [9,12)` and `busyUntil` starts at `1`.

| Interval | `start > busyUntil`? | Free interval | `busyUntil` after |
|---|---|---|---|
| `[1,3)` | `1 > 1`, no | | `3` |
| `[2,4)` | `2 > 3`, no | | `4` |
| `[2,5)` | `2 > 4`, no | | `5` |
| `[6,7)` | `6 > 5`, yes | `5,6` | `7` |
| `[9,12)` | `9 > 7`, yes | `7,9` | `12` |


### Code

```java
import java.util.*;

public class EmployeeScheduleCommonFreeTime {

    public EmployeeScheduleCommonFreeTime() {
    }

    public List<String> employeeFreeTime(List<String> schedule) {
        // working intervals of all employees in one list.
        // We don't need to remember which employee an interval belongs to.
        List<int[]> intervals = new ArrayList<>();
        for (String employeeSchedule : schedule) {
            intervals.addAll(parseIntervals(employeeSchedule));
        }

        List<String> freeTimes = new ArrayList<>();
        if (intervals.isEmpty()) {
            return freeTimes;
        }

        // sort by start time, so we can scan the timeline from left to right
        intervals.sort((a, b) -> Integer.compare(a[0], b[0]));

        // end time of the current busy block.
        // Starting at the earliest start time means the infinite
        // free time before it is never reported.
        int busyUntil = intervals.get(0)[0];

        for (int[] interval : intervals) {
            int start = interval[0];
            int end = interval[1];

            // strictly greater, so zero length gaps like "5,5" are skipped
            if (start > busyUntil) {
                freeTimes.add(busyUntil + "," + start);
            }
            busyUntil = Math.max(busyUntil, end);
        }
        return freeTimes;
    }

    // converts "1,3|6,7" into [[1, 3], [6, 7]]
    List<int[]> parseIntervals(String employeeSchedule) {
        List<int[]> intervals = new ArrayList<>();
        for (String part : employeeSchedule.split("\\|")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            String[] times = text.split(",");
            int start = Integer.parseInt(times[0].trim());
            int end = Integer.parseInt(times[1].trim());
            intervals.add(new int[]{start, end});
        }
        return intervals;
    }
}
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
3. Do the same `busyUntil` check as in Approach 2.
4. Put the next interval of that same employee into the heap, if there is one.
5. Repeat until the heap is empty.


**Why a min heap (`PriorityQueue`)?** Taking out the smallest interval and putting in the next one both cost only `O(log K)`, and the heap never holds more than `K` intervals, one per employee.


Each heap entry is an `int[]` of `{start, end, employee, index}`. We keep `employee` and `index` so we know which interval to push next.


### Code

```java
import java.util.*;

public class EmployeeScheduleCommonFreeTime {

    public EmployeeScheduleCommonFreeTime() {
    }

    public List<String> employeeFreeTime(List<String> schedule) {
        // working intervals of each employee, already sorted by start time
        List<List<int[]>> employees = new ArrayList<>();
        for (String employeeSchedule : schedule) {
            employees.add(parseIntervals(employeeSchedule));
        }

        // heap entry: {start, end, employee, index}, smallest start on top.
        // It holds at most one interval per employee: the next unprocessed one.
        PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        for (int employee = 0; employee < employees.size(); employee++) {
            List<int[]> intervals = employees.get(employee);
            if (!intervals.isEmpty()) {
                int[] first = intervals.get(0);
                heap.add(new int[]{first[0], first[1], employee, 0});
            }
        }

        List<String> freeTimes = new ArrayList<>();
        if (heap.isEmpty()) {
            return freeTimes;
        }

        // end time of the current busy block, starts at the earliest start time
        int busyUntil = heap.peek()[0];

        while (!heap.isEmpty()) {
            int[] top = heap.poll();
            int start = top[0];
            int end = top[1];
            int employee = top[2];
            int index = top[3];

            // same gap check as the sort and scan solution
            if (start > busyUntil) {
                freeTimes.add(busyUntil + "," + start);
            }
            busyUntil = Math.max(busyUntil, end);

            // push the next interval of the same employee, if there is one
            List<int[]> intervals = employees.get(employee);
            if (index + 1 < intervals.size()) {
                int[] next = intervals.get(index + 1);
                heap.add(new int[]{next[0], next[1], employee, index + 1});
            }
        }
        return freeTimes;
    }

    // converts "1,3|6,7" into [[1, 3], [6, 7]]
    List<int[]> parseIntervals(String employeeSchedule) {
        List<int[]> intervals = new ArrayList<>();
        for (String part : employeeSchedule.split("\\|")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            String[] times = text.split(",");
            int start = Integer.parseInt(times[0].trim());
            int end = Integer.parseInt(times[1].trim());
            intervals.add(new int[]{start, end});
        }
        return intervals;
    }
}
```


### Complexity

- **Time:** `O(N log K)`, where `K` is the number of employees. Each interval goes in and out of the heap once.
- **Space:** `O(N)` to keep the parsed schedules. The heap itself holds at most `K` entries.