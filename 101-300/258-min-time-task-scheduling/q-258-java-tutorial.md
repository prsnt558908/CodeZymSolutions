# Minimum Time Task Scheduling with Constraints in Java

#### Problem Statement
[https://codezym.com/question/258-min-time-task-scheduling](https://codezym.com/question/258-min-time-task-scheduling)

Think of each time unit as a box. A box can hold any tasks whose total memory is at most `serverMemoryLimit`, as long as it has at most 2 tasks of the same type. So the real question is: what is the smallest number of boxes that can hold all the tasks?

There are at most 20 tasks, so any set of tasks fits in one integer called a bitmask (bit `i` is 1 when task `i` is in the set). This makes bitmask DP the right tool. We start with a brute force, improve it to a bitmask DP over groups, and finish with a faster DP that fills one box at a time. For every set of placed tasks, it remembers the fewest boxes used and the emptiest open box.

---

## Quick Example

Example 4: `type = [A, A, B, B, C]`, `memory = [2, 2, 3, 1, 2]`, `serverMemoryLimit = 5`.

- Box 1: A(2), A(2), B(1). Memory 5, two A tasks. Valid.
- Box 2: B(3), C(2). Memory 5. Valid.

The total memory is 10 and one box holds at most 5, so 2 boxes is the best we can do. Answer: `2`.

---

## Approach 1: Brute Force

Go through the tasks one by one. Put the current task into any box where it fits, or start a new box with it. Try every choice and keep the smallest number of boxes.

```java
// place task i, then continue with task i + 1
void place(int i, List<List<Integer>> boxes) {
    if (boxes.size() >= bestAnswer) return;          // cannot beat the best answer
    if (i == n) {
        bestAnswer = boxes.size();
        return;
    }
    for (int b = 0; b < boxes.size(); b++) {         // try every open box
        List<Integer> box = boxes.get(b);
        if (fits(box, i)) {
            box.add(i);
            place(i + 1, boxes);
            box.remove(box.size() - 1);
        }
    }
    boxes.add(new ArrayList<>(List.of(i)));          // or start a new box
    place(i + 1, boxes);
    boxes.remove(boxes.size() - 1);
}
```

`fits(box, i)` checks two things: the memory of the box plus task `i` stays within the limit, and the box has fewer than 2 tasks of the same type as task `i`.

**Why it is slow:** 20 tasks can be split into groups in about 51 trillion ways, and the search may try a large part of them.

Notice that once we fix the group for one time unit, what is left is just a smaller set of tasks. Many different choices leave the same set, so we can solve each set only once and remember the answer.

---

## Approach 2: Bitmask DP Over Groups

**Step 1: Which groups can run together?**

For every set of tasks `group`, we precompute `canRun[group]`. We build it from a smaller set: drop the lowest task of `group`, reuse the answer of the smaller set, then check the memory sum and the count of the dropped task's type.

`sameType[i]` is a bitmask of all tasks with the same type as task `i`. So `Integer.bitCount(group & sameType[low])` counts the tasks of that type inside `group`.

**Step 2: The DP.**

`dp[mask]` is the fewest time units needed to finish exactly the tasks in `mask`.

The lowest task in `mask` has to run in some time unit, so we only try groups that contain it. This stops us from trying the same split again in a different order.

```java
canRun[0] = true;

// canRun[group]: can all tasks of this group run in one time unit?
for (int group = 1; group <= fullMask; group++) {
    int low = Integer.numberOfTrailingZeros(group);    // lowest task in the group
    int smaller = group & (group - 1);                 // the group without that task
    memorySum[group] = memorySum[smaller] + memory.get(low);
    canRun[group] = canRun[smaller]
            && memorySum[group] <= serverMemoryLimit
            && Integer.bitCount(group & sameType[low]) <= 2;
}

// dp[mask]: fewest time units to finish exactly the tasks in mask
for (int mask = 1; mask <= fullMask; mask++) {
    int lowest = mask & -mask;                         // must be in the next group
    int rest = mask ^ lowest;
    dp[mask] = n;
    for (int sub = rest; ; sub = (sub - 1) & rest) {   // every subset of rest
        int group = sub | lowest;
        if (canRun[group]) {
            dp[mask] = Math.min(dp[mask], 1 + dp[mask ^ group]);
        }
        if (sub == 0) break;
    }
}
```

`(sub - 1) & rest` jumps to the next smaller subset of `rest`, so the inner loop visits every subset once.

**Why it is still slow:** Each task has 3 options: outside the mask, inside the group, or inside the mask but not in the group. So the loops run about `3^20` times (half of that thanks to the lowest task trick). That is roughly 1.7 billion steps for 20 tasks, which takes seconds.

---

## Approach 3: Fill One Box at a Time (Optimal)

### The Idea

Put the tasks in a line and place them one by one:

- If the task fits in the current open box, put it there.
- If it does not fit, start a new box with it.

Does some order give the best answer? Yes. Take the best schedule and line up its tasks box after box.

While we place the tasks of one box of the best schedule, we start at most one new box. Once a new box starts, all the remaining tasks of that best box fit in it, because they already fit together in the best schedule. So this order never needs more boxes than the best schedule.

We do not know that order, so the DP tries all orders at the same time. The state is the set of tasks placed so far, as a bitmask.

### What We Remember for Each Mask

- `boxes`: boxes used so far, counting the open box.
- `used`: memory used in the open box.

Fewer boxes is better. With the same number of boxes, less used memory is better, because it leaves more room for later tasks.

A state with fewer boxes is never worse, even if its open box is fuller. In the worst case its next task starts a new box. After that it has no more boxes than the other state, and its open box is not fuller.

We keep both in one number: `value = boxes * BIG + used`, with `BIG = 10^10`. Since `used` is at most `10^9`, a smaller value always means fewer boxes first, and less used memory second.

### Handling the Type Rule

The open box also holds types, and we need them for the "at most 2" rule. Tracking every type would make the state too big. Two small tricks fix this.

**Trick 1: Only risky types matter.** A type with 1 or 2 tasks can never have 3 tasks in one box. Only a type with 3 or more tasks can break the rule. We call these risky types and give them ids 0, 1, 2 and so on, in order of first appearance. With 20 tasks there are at most 6 risky types, since each needs at least 3 tasks.

**Trick 2: A fixed order inside each box.** Inside a box, we add risky tasks in increasing order of their risky id. Any valid box can be filled in this order, so we lose nothing. Now the box only needs to remember its last risky type and whether it holds 1 or 2 tasks of it.

| Box state | Meaning |
|---|---|
| `0` | no risky task in the open box |
| `1 + 2r` | last risky type is `r`, with 1 task of it |
| `2 + 2r` | last risky type is `r`, with 2 tasks of it |

Adding a task whose risky type is `r`:

- Safe task (not risky): allowed, the state does not change.
- State `0`, or `r` is bigger than the last risky type: allowed, new state `1 + 2r`.
- `r` is the last risky type and the box has 1 task of it: allowed, new state `2 + 2r`.
- Anything else: not allowed, so the task starts a new box.

So the full DP state is (mask, box state). We store it in one flat array `best`, at index `mask * states + state`.

### Two Speed-ups

**Skip a state that a more open state beats.** A smaller box state number never blocks a task that a bigger one allows. So if a smaller state of the same mask already has a value that is less or equal, we skip the bigger state.

**Start with task 0.** Task 0 is in some box. We can place that box first and put task 0 first in it (if task 0 is risky, its id is 0, the smallest). So every mask we reach contains task 0, and we only visit odd masks. This halves the work.

### Dry Run (Example 2)

`type = [A, A, A, A]`, `memory = [1, 1, 1, 1]`, `serverMemoryLimit = 10`. Type A has 4 tasks, so it is risky with id 0. Here is one order the DP tries:

- Start: task 0 in box 1. State `1` (A once).
- Task 1: A again, only 1 so far. State `2`. Still 1 box.
- Task 2: a third A is not allowed. It starts box 2 with state `1`.
- Task 3: A again, only 1 in this box. State `2`.

Answer: `2`.

### Key Data Structures

- `HashMap<String, Integer>`: counts tasks per type and stores risky ids. A map is the simplest way to look up a type by its name.
- `joinState[s][task]`: the type rule worked out in advance for every box state and task. The main loop just reads it.
- `long[] best`: one number per (mask, box state). A plain array is fast and uses little memory.

### Code

```java
import java.util.*;

public class MinimumTimeTaskScheduler {

    public MinimumTimeTaskScheduler() {

    }

    public int minimumExecutionTime(List<String> type, List<Integer> memory, int serverMemoryLimit) {
        int n = type.size();

        // Count how many tasks each type has.
        Map<String, Integer> typeCount = new HashMap<>();
        for (String t : type) {
            typeCount.put(t, typeCount.getOrDefault(t, 0) + 1);
        }

        // Only a type with 3 or more tasks can break the "at most 2" rule. We call it risky.
        // Ids follow the order of first appearance, so task 0's type gets id 0 if it is risky.
        Map<String, Integer> riskyId = new HashMap<>();
        for (String t : type) {
            if (typeCount.get(t) >= 3 && !riskyId.containsKey(t)) {
                riskyId.put(t, riskyId.size());
            }
        }

        int[] risky = new int[n];   // risky type id of each task, -1 if its type is safe
        int[] mem = new int[n];
        for (int i = 0; i < n; i++) {
            risky[i] = riskyId.getOrDefault(type.get(i), -1);
            mem[i] = memory.get(i);
        }

        // Box state: 0 = no risky task yet, 1 + 2r = risky type r once, 2 + 2r = risky type r twice.
        int states = 1 + 2 * riskyId.size();

        // joinState[s][task] = box state after adding task to a box in state s, -1 if not allowed.
        // joinState[0][task] is the state of a new box that holds only this task.
        int[][] joinState = new int[states][n];
        for (int s = 0; s < states; s++) {
            for (int task = 0; task < n; task++) {
                joinState[s][task] = addToBox(s, risky[task]);
            }
        }

        // One number keeps (boxes used, memory used in the open box).
        // A smaller number means fewer boxes, and then less used memory.
        long BIG = 10_000_000_000L;
        long INF = Long.MAX_VALUE;

        int fullMask = (1 << n) - 1;
        long[] best = new long[(fullMask + 1) * states];
        Arrays.fill(best, INF);

        // Mask 1 means only task 0 is placed. It sits alone in the first box.
        best[1 * states + joinState[0][0]] = 1 * BIG + mem[0];

        // Every mask we reach contains task 0, so we only visit odd masks.
        for (int mask = 1; mask < fullMask; mask += 2) {
            long betterValue = INF;   // best value among the more open states of this mask

            for (int s = 0; s < states; s++) {
                long value = best[mask * states + s];
                if (value >= betterValue) {
                    continue;   // unreachable, or a more open state is at least as good
                }
                betterValue = value;

                long boxes = value / BIG;
                long used = value % BIG;

                for (int task = 1; task < n; task++) {
                    if ((mask & (1 << task)) != 0) {
                        continue;   // already placed
                    }

                    int nextState;
                    long nextValue;
                    if (joinState[s][task] != -1 && used + mem[task] <= serverMemoryLimit) {
                        // the task joins the open box
                        nextState = joinState[s][task];
                        nextValue = value + mem[task];
                    } else {
                        // the task starts a new box
                        nextState = joinState[0][task];
                        nextValue = (boxes + 1) * BIG + mem[task];
                    }

                    int index = (mask | (1 << task)) * states + nextState;
                    if (nextValue < best[index]) {
                        best[index] = nextValue;
                    }
                }
            }
        }

        long answer = INF;
        for (int s = 0; s < states; s++) {
            answer = Math.min(answer, best[fullMask * states + s]);
        }
        return (int) (answer / BIG);
    }

    // Returns the box state after adding a task of risky type r to a box in this state.
    // Returns -1 if the type rule does not allow it.
    int addToBox(int state, int r) {
        if (r == -1) {
            return state;            // safe task, nothing to track
        }
        if (state == 0) {
            return 1 + 2 * r;        // first risky task in the box
        }
        int lastType = (state - 1) / 2;
        int lastCount = (state - 1) % 2 + 1;
        if (r > lastType) {
            return 1 + 2 * r;        // next risky type, in increasing order
        }
        if (r == lastType && lastCount == 1) {
            return 2 + 2 * r;        // second task of the same type
        }
        return -1;                   // a 3rd task of this type, or the wrong order
    }
}
```

### Complexity

- **Time:** `O(2^n * S * n)`, where `S = 1 + 2 * (number of risky types)` is at most 13. The skip rule makes it much faster in practice.
- **Space:** `O(2^n * S)` for the `best` array.

---

## Complexity Summary

| Approach | Time | Space |
|---|---|---|
| Brute force | grows with the number of ways to split tasks into groups (about 51 trillion for 20 tasks) | `O(n)` |
| Bitmask DP over groups | `O(3^n)` | `O(2^n)` |
| Fill one box at a time | `O(2^n * S * n)` | `O(2^n * S)` |