# Task Execution Order in Java

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

```java
class Task {
    String taskId;
    int runAt;
    int priority;
}
```

**Why a class?** It keeps the three values together under clear names. This makes the sorting code short and easy to read.

**Why store the numbers as `int`?** Comparing numbers as text gives wrong answers. As text, `"10"` comes before `"9"`, because the character `'1'` is smaller than `'9'`. As numbers, 9 is smaller, which is what we want.

Both `runAt` and `priority` are at most 1,000,000,000, so a normal `int` is big enough.


## Comparing Two Tasks

The three rules fit into one `Comparator`. It returns a negative number when `a` should run before `b`, and a positive number when `b` should run first.

```java
Comparator<Task> order = (a, b) -> {
    if (a.runAt != b.runAt) {
        return Integer.compare(a.runAt, b.runAt);       // earlier time first
    }
    if (a.priority != b.priority) {
        return Integer.compare(b.priority, a.priority); // higher priority first
    }
    return a.taskId.compareTo(b.taskId);                // smaller id first
};
```

Look at the priority line. We pass `b` first and `a` second. Swapping them flips the order, so the larger priority comes first.

We use `Integer.compare` instead of subtraction like `a.runAt - b.runAt`. It is easier to read and it can never overflow.


## Brute Force: Pick the Next Task Each Time

The most direct idea is to act like the scheduler itself. Look at all remaining tasks, pick the one that runs first, add its id to the answer and remove it. Repeat until no task is left.

Here `parsed` is the list of `Task` objects built from the input.

```java
List<Task> remaining = new ArrayList<>(parsed);
List<String> result = new ArrayList<>();

while (!remaining.isEmpty()) {
    // find the task that should run next
    int best = 0;
    for (int i = 1; i < remaining.size(); i++) {
        if (order.compare(remaining.get(i), remaining.get(best)) < 0) {
            best = i;
        }
    }
    result.add(remaining.remove(best).taskId);
}
```

This gives the right answer, but it is slow. Every pick scans the whole remaining list again.

With 100,000 tasks, that adds up to about 5 billion comparisons. Far too slow.


## Optimized Solution: Sort Once

The brute force is really sorting by hand, one task at a time. Instead, we can give the same comparator to Java's built-in sort.

A good sort needs only about `n log n` comparisons. For 100,000 tasks, that is around 1.7 million comparisons instead of 5 billion.

The full flow:

1. Parse every string into a `Task` and add it to a **new** list.
2. Sort this new list with the comparator.
3. Copy the `taskId` of each sorted task into the result.

**Why a new list?** The rules say the input list must not be modified. Also, the input may be created with `List.of(...)`, which cannot be changed at all. Calling `sort` on it directly throws an `UnsupportedOperationException`.

**Why parse before sorting?** Sorting compares the same task many times. If we split the strings inside the comparator, the same string would be split again and again. Parsing once at the start avoids this extra work.


## Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force | `O(n²)` | `O(n)` |
| Sort once | `O(n log n)` | `O(n)` |

Here `n` is the number of tasks. Each comparison is cheap. It checks two numbers, and compares the short ids (at most 20 letters) only when both numbers tie.


## Complete Code

```java
import java.util.*;

/**
 * Parsed form of one task string "taskId,runAt,priority".
 */
class Task {
    String taskId;
    int runAt;
    int priority;

    Task(String taskId, int runAt, int priority) {
        this.taskId = taskId;
        this.runAt = runAt;
        this.priority = priority;
    }
}

public class TaskExecutionPlanner {

    public TaskExecutionPlanner() {

    }

    /**
     * Returns the task ids in execution order:
     * smaller runAt first, then larger priority, then smaller taskId.
     * The input list is never modified.
     */
    public List<String> getExecutionOrder(List<String> tasks) {
        // 1. Parse every string once into a new list of Task objects
        List<Task> parsed = new ArrayList<>();
        for (String line : tasks) {
            String[] parts = line.split(",");
            String taskId = parts[0];
            int runAt = Integer.parseInt(parts[1]);
            int priority = Integer.parseInt(parts[2]);
            parsed.add(new Task(taskId, runAt, priority));
        }

        // 2. Sort the new list using the three execution rules
        Comparator<Task> order = (a, b) -> {
            if (a.runAt != b.runAt) {
                return Integer.compare(a.runAt, b.runAt);       // earlier time first
            }
            if (a.priority != b.priority) {
                return Integer.compare(b.priority, a.priority); // higher priority first
            }
            return a.taskId.compareTo(b.taskId);                // smaller id first
        };
        parsed.sort(order);

        // 3. Collect only the ids, in sorted order
        List<String> result = new ArrayList<>();
        for (Task task : parsed) {
            result.add(task.taskId);
        }
        return result;
    }
}
```