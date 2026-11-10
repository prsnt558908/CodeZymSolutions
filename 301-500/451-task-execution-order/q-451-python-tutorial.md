# Task Execution Order in Python

#### Problem Statement
[https://codezym.com/question/451-task-execution-order](https://codezym.com/question/451-task-execution-order)


## Core Idea

This is a **custom sorting** problem. We have a list of tasks, and we need to line them up using three rules.

The key insight is simple. If we can decide **which of two tasks runs first**, a sorting algorithm can arrange all of them for us.

So the solution has three parts: turn each string into a small `Task` object, sort a copy of these tasks using the three rules, and then read out the task ids in their new order.


## The Three Rules

For any two tasks:

1. The smaller `runAt` runs first.
2. If `runAt` is the same, the larger `priority` runs first.
3. If both are the same, the alphabetically smaller `taskId` runs first.

All task ids are unique, so two tasks are never fully equal. This means there is always exactly one correct order.

Example 2 uses all three rules:

| Order | Task | runAt | priority | Why |
|---|---|---|---|---|
| 1 | setup | 1 | 1 | Earliest time |
| 2 | beta | 7 | 9 | Highest priority at time 7 |
| 3 | alpha | 7 | 3 | Ties with gamma, but "alpha" comes first alphabetically |
| 4 | gamma | 7 | 3 | Last one left |


## Turning a String into a Task

Each task comes as one string, like `"build,2,4"`. We split it at the commas and keep the three parts in a small `Task` class.

```python
class Task:
    def __init__(self, taskId: str, runAt: int, priority: int):
        self.taskId = taskId
        self.runAt = runAt
        self.priority = priority
```

**Why a class?** It keeps the three values together under clear names. This makes the sorting code short and easy to read.

**Why convert the numbers with `int()`?** Comparing numbers as text gives wrong answers. As text, `"10"` comes before `"9"`, because the character `"1"` is smaller than `"9"`. As numbers, 9 is smaller, which is what we want.


## Comparing Two Tasks

Python can compare tuples directly. It compares them item by item, from left to right, and moves to the next item only when the current items are equal. This matches our three rules exactly.

So we describe each task with a tuple, called a **sort key**:

```python
lambda task: (task.runAt, -task.priority, task.taskId)
```

**Why `-task.priority`?** Sorting puts smaller values first, but we want the **larger** priority first. Making it negative flips the order. Priority 9 becomes -9, priority 3 becomes -3, and -9 comes before -3.

For example, `beta` becomes `(7, -9, "beta")` and `alpha` becomes `(7, -3, "alpha")`. The first items are equal, and -9 is smaller than -3, so `beta` comes first.


## Brute Force: Pick the Next Task Each Time

The most direct idea is to act like the scheduler itself. Look at all remaining tasks, pick the one that runs first, add its id to the answer and remove it. Repeat until no task is left.

Here `parsed` is the list of `Task` objects built from the input.

```python
remaining = list(parsed)
result = []

while remaining:
    # find the task that should run next
    best = min(remaining, key=lambda task: (task.runAt, -task.priority, task.taskId))
    remaining.remove(best)
    result.append(best.taskId)
```

This gives the right answer, but it is slow. Every pick scans the whole remaining list, once in `min` and again in `remove`.

With 100,000 tasks, `min` alone does about 5 billion comparisons. Far too slow.


## Optimized Solution: Sort Once

The brute force is really sorting by hand, one task at a time. Instead, we can give the same key to Python's built-in sort.

A good sort needs only about `n log n` comparisons. For 100,000 tasks, that is around 1.7 million comparisons instead of 5 billion.

Python also computes the key only **once per task**, and then reuses it during the whole sort.

The full flow:

1. Parse every string into a `Task` and add it to a **new** list.
2. Sort this new list with the key.
3. Collect the `taskId` of each sorted task.

**Why a new list?** The rules say the input list must not be modified. Calling `tasks.sort()` would reorder the caller's list. Sorting our own `parsed` list keeps `tasks` untouched.


## Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force | `O(n²)` | `O(n)` |
| Sort once | `O(n log n)` | `O(n)` |

Here `n` is the number of tasks. Each comparison is cheap. It checks two numbers, and compares the short ids (at most 20 letters) only when both numbers tie.


## Complete Code

```python
from typing import List


class Task:
    """Parsed form of one task string "taskId,runAt,priority"."""

    def __init__(self, taskId: str, runAt: int, priority: int):
        self.taskId = taskId
        self.runAt = runAt
        self.priority = priority


class TaskExecutionPlanner:

    def __init__(self):
        pass

    def getExecutionOrder(self, tasks: List[str]) -> List[str]:
        """
        Returns the task ids in execution order:
        smaller runAt first, then larger priority, then smaller taskId.
        The input list is never modified.
        """
        # 1. Parse every string once into a new list of Task objects
        parsed = []
        for line in tasks:
            taskId, runAt, priority = line.split(",")
            parsed.append(Task(taskId, int(runAt), int(priority)))

        # 2. Sort the new list using the three execution rules.
        #    -priority makes a larger priority come first.
        parsed.sort(key=lambda task: (task.runAt, -task.priority, task.taskId))

        # 3. Collect only the ids, in sorted order
        return [task.taskId for task in parsed]
```