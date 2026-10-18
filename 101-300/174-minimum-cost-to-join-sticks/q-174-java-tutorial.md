# Minimum Cost to Join Sticks in Java

#### Problem Statement
[https://codezym.com/question/174-minimum-cost-to-join-sticks](https://codezym.com/question/174-minimum-cost-to-join-sticks)


The core idea is simple: **always join the two shortest sticks first**.


Every time a stick takes part in a join, its length is added to the cost. So we want the long sticks to be joined as few times as possible, which means joining them as late as possible.


We will first solve it in a simple way by sorting the list in every round. Then we will make it fast with a **min-heap** (`PriorityQueue`), which gives us the shortest stick very quickly.

---

## Understanding the Cost

When we join two sticks, we pay their total length.


The new stick stays in the pile. If it gets joined again, we pay for its length again.


So a stick's length is paid once for **every join it takes part in**.


Let's see this with `[1, 2, 7]`.


**Plan A: join the small ones first**
- Join `1 + 2 = 3`, cost `3`
- Join `3 + 7 = 10`, cost `10`
- Total = `13`


**Plan B: join a big one early**
- Join `2 + 7 = 9`, cost `9`
- Join `9 + 1 = 10`, cost `10`
- Total = `19`


In Plan A, the stick `7` is paid for only once. In Plan B, it is paid for twice.


Small sticks are cheap to pay for again and again. Big sticks are expensive. So in every round, we should pick the **two shortest sticks**.

---

## Solution 1: Sort the List in Every Round

### Idea

The easiest way to find the two shortest sticks is to sort the list.

1. Sort the list.
2. Remove the first two sticks (the two shortest).
3. Add their sum to the total cost.
4. Put the new stick back into the list.
5. Repeat until only one stick is left.

### Code

```java
import java.util.*;

public class MinimumCostToJoinSticks {

    public MinimumCostToJoinSticks() {
    }

    /**
     * Simple approach: in every round, sort the sticks,
     * join the two shortest ones and put the new stick back.
     */
    public int joinSticks(List<Integer> sticks) {
        // Copy so we do not change the caller's list
        List<Integer> list = new ArrayList<>(sticks);
        int totalCost = 0;

        while (list.size() > 1) {
            // Bring the two shortest sticks to the front
            Collections.sort(list);

            int first = list.remove(0);
            int second = list.remove(0);

            int joined = first + second;
            totalCost += joined;

            // The new stick can be joined again in a later round
            list.add(joined);
        }
        return totalCost;
    }
}
```

### Complexity

- **Time:** `O(n² log n)`. There are `n - 1` rounds and each round sorts the whole list.
- **Space:** `O(n)` for the copy of the list.

### What is the problem here?

After each join, only **one** new stick is added. Everything else is already in sorted order.


But we still sort the whole list again. That is a lot of repeated work.


Also, `list.remove(0)` on an `ArrayList` shifts every other element one step to the left, which is slow too.


What we really need is something that can:
- give us the smallest stick quickly
- let us add a new stick quickly


That is exactly what a min-heap does.

---

## Solution 2: Min-Heap (Optimal)

### Why a Min-Heap?

A min-heap always keeps the smallest value at the top.


In Java, `PriorityQueue` is a min-heap by default.

| Operation | What it does | Time |
|---|---|---|
| `poll()` | removes and returns the smallest stick | `O(log n)` |
| `offer(x)` | adds a new stick | `O(log n)` |
| `size()` | number of sticks left | `O(1)` |


It does not keep the whole list sorted. It only makes sure the smallest stick is always on top. That is all we need here.

### Idea

1. Put all sticks into a min-heap.
2. While there is more than one stick:
   - Take out the two shortest sticks.
   - Join them and add the sum to the total cost.
   - Put the new stick back into the heap.
3. Return the total cost.

### Dry Run

Input: `[6, 1, 2, 7]`

| Step | Two shortest | Joined | Total cost | Sticks left |
|---|---|---|---|---|
| Start | | | `0` | `1, 2, 6, 7` |
| 1 | `1, 2` | `3` | `3` | `3, 6, 7` |
| 2 | `3, 6` | `9` | `12` | `7, 9` |
| 3 | `7, 9` | `16` | `28` | `16` |


Only one stick is left, so the answer is `28`.

### Code

```java
import java.util.*;

public class MinimumCostToJoinSticks {

    public MinimumCostToJoinSticks() {
    }

    /**
     * Returns the minimum total cost to join all sticks into one.
     * Greedy idea: always join the two shortest sticks available.
     */
    public int joinSticks(List<Integer> sticks) {
        // Min-heap: the shortest stick is always at the top
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(sticks);

        int totalCost = 0;

        // Keep joining until only one stick is left
        while (minHeap.size() > 1) {
            int first = minHeap.poll();   // shortest stick
            int second = minHeap.poll();  // second shortest stick

            int joined = first + second;
            totalCost += joined;

            // Put the new stick back, it may be joined again later
            minHeap.offer(joined);
        }
        return totalCost;
    }
}
```

### Complexity

- **Time:** `O(n log n)`. Building the heap from the list takes `O(n)`. Then there are `n - 1` rounds, and each round does a few `O(log n)` heap operations.
- **Space:** `O(n)` for the heap.

### Edge Cases

- **Only one stick:** the loop never runs, so the answer is `0`.
- **Large answer:** the biggest possible answer is about `1.34 × 10⁹` (all `10⁴` sticks of length `10⁴`). This still fits in an `int`, whose limit is about `2.1 × 10⁹`.