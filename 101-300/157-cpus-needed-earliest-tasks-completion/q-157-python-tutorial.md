# Minimum CPUs Needed for Earliest Tasks Completion in Python

#### Problem Statement

[https://codezym.com/question/157-cpus-needed-earliest-tasks-completion](https://codezym.com/question/157-cpus-needed-earliest-tasks-completion)


First, find the earliest time when all tasks can finish. Then count how many tasks must fit into the last few available time slots. A simple counting list lets us find the minimum number of CPUs in `O(n)` time, without sorting or simulating every schedule.


## Find the target finish time

A task's given time is its earliest allowed start. We can delay it to reuse a busy CPU.


Let `latestStart` be the largest start time and `L = taskLength`. The earliest possible finish time is `latestStart + L`.


The latest task cannot finish before that time. Giving every task its own CPU achieves it. Our job is to reach this same finish time using as few CPUs as possible.


## Think backward in equal slots

Imagine placing tasks backward from the target finish time on each CPU. The possible slot start times are `latestStart`, `latestStart - L`, `latestStart - 2 * L`, and so on.


This does not lose any valid schedule. On each CPU, we can push its final task to the target finish time, then pack the earlier tasks directly before it. Each task only moves later, which is allowed.


For a task released at `start`, the number of slots it can use on one CPU is:

```text
slots = floor((latestStart - start) / taskLength) + 1
```

For example, if `latestStart = 10` and `taskLength = 5`, a task released at `10` can use only the slot starting at `10`. A task released at `5` can use the slots starting at `5` or `10`.


Suppose `count` tasks can each use only the last `limit` slots or fewer. They must all fit inside those last `limit` slots on the available CPUs.


Each CPU contributes `limit` places, so we need at least `ceil(count / limit)` CPUs. We take the largest requirement across all limits.


There are only `n` tasks. We can safely cap every slot count at `n`, because no CPU ever needs to run more than `n` tasks.


## Simple approach: Count again for every limit

For each limit from `1` to `n`, scan all tasks and count those with at most that many eligible slots. Here, `n` and `latestStart` have already been calculated.

```python
answer = 1

for limit in range(1, n + 1):
    count = 0

    for start in startTimes:
        slots = (latestStart - start) // taskLength + 1
        slots = min(slots, n)

        if slots <= limit:
            count += 1

    needed = (count + limit - 1) // limit
    answer = max(answer, needed)
```

This takes `O(n²)` time. The repeated scans do the same counting work again and again.


## Better approach: Count once

Use a list named `frequency`, where `frequency[s]` stores how many tasks have exactly `s` eligible slots after capping.


Scan this list from `1` to `n`. Keep adding its entries to `count`. At each limit, `count` is now the number of tasks restricted to that many slots or fewer, so the same calculation gives the required CPUs.


The expression `(count + limit - 1) // limit` rounds the division upward using integer arithmetic.


## Walk through an example

Take `startTimes = [0, 0, 0, 5, 10]` and `taskLength = 5`. The target finish time is `15`, and the slot counts are `[3, 3, 3, 2, 1]`.

- At limit `1`, one task is restricted to the final slot. We need `1` CPU.
- At limit `2`, two tasks must fit into the final two slots. We still need `1` CPU.
- At limit `3`, all five tasks must fit into three slots per CPU. We need `ceil(5 / 3) = 2` CPUs.

The answer is `2`. One CPU can run tasks during `0 to 5`, `5 to 10`, and `10 to 15`. The other can run the remaining two tasks during `0 to 5` and `5 to 10`.


## Why the answer is correct

Every limit gives a necessary bound. If `count` tasks have only `limit` slots available per CPU, fewer than `ceil(count / limit)` CPUs cannot hold them.


These bounds are also enough. Imagine assigning tasks from the fewest eligible slots to the most. For a task with `limit` eligible slots, all previously assigned tasks occupy places within those same last `limit` slots.


Our chosen CPU count provides enough places for the entire group with at most `limit` slots. Therefore, a free eligible place always remains for the next task. Every task can finish by the target time, and any smaller CPU count fails at least one bound.


## Complexity and implementation

The final solution takes `O(n)` time and `O(n)` extra space. Reading all tasks already requires `O(n)` time, so this is optimal in time.


`TaskCpuScheduler` keeps the required interface. It needs no fields because each call is independent. The input list is not changed. All division uses `//` to keep the calculations in integers.


## Final code

```python
from typing import List


class TaskCpuScheduler:
    def __init__(self):
        pass

    def minCpusNeeded(self, startTimes: List[int], taskLength: int) -> int:
        n = len(startTimes)
        latestStart = max(startTimes)
        frequency = [0] * (n + 1)

        for start in startTimes:
            # Count usable slots backward from the target finish time.
            slots = (latestStart - start) // taskLength + 1
            slots = min(slots, n)
            frequency[slots] += 1

        answer = 1
        count = 0

        for limit in range(1, n + 1):
            # These tasks must fit within the final 'limit' slots per CPU.
            count += frequency[limit]
            needed = (count + limit - 1) // limit
            answer = max(answer, needed)

        return answer
```
