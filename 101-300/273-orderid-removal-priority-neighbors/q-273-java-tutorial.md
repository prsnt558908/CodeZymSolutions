# Order ID Removal Priority Based on Neighbors in Java

#### Problem Statement
[https://codezym.com/question/273-orderid-removal-priority-neighbors](https://codezym.com/question/273-orderid-removal-priority-neighbors)

At every step we remove the smallest "peak", an order id that is bigger than all of its current neighbors. The core idea is that removing one order changes the neighbors of only two orders: the one just before it and the one just after it. Every other order keeps the same neighbors, so its eligibility does not change.

So instead of rescanning the whole list after every removal, we only recheck those two orders. We store the list as a doubly linked list built from two plain arrays (`left` and `right`), so removing from the middle takes O(1) time. We keep all eligible orders in a min-heap, so the smallest one is always ready on top.

We will start with a simple brute force simulation, see why it is slow, and then fix its slow parts one by one.

---

## Problem in Simple Words

You get a list of unique order ids. Think of each id as the height of a hill. An order is **eligible** when it is a peak, which means it is bigger than every neighbor it currently has.

- An order in the middle must be bigger than both its left and right neighbors.
- The first and last orders have only one neighbor and must be bigger than it.
- If only one order is left, it is eligible.

At each step, remove the **smallest** eligible order. Keep going until the list is empty, and return the ids in the order they were removed.

**Example:** `orderIds = [8, 3, 6, 1, 5]`

| Step | Current list | Eligible orders | Removed |
|------|--------------|-----------------|---------|
| 1 | 8, 3, 6, 1, 5 | 8, 6, 5 | 5 |
| 2 | 8, 3, 6, 1 | 8, 6 | 6 |
| 3 | 8, 3, 1 | 8 | 8 |
| 4 | 3, 1 | 3 | 3 |
| 5 | 1 | 1 | 1 |

Output: `[5, 6, 8, 3, 1]`

Look at step 4. Order 3 was not eligible in the first three steps because a bigger order sat next to it. Once 8 was removed, 3 became the first order, and it is bigger than its only neighbor 1.

One handy fact: the biggest order in the list is always eligible. So there is always something to remove, and the process never gets stuck.

---

## Solution 1: Brute Force Simulation

Just follow the rules literally:

1. Scan the whole list and find the smallest eligible order.
2. Remove it and add it to the answer.
3. Repeat until the list is empty.

We work on a copy of the input. The input list may be read-only (the examples use `List.of(...)`), and we should not change the caller's data anyway.

```java
import java.util.*;

public class OrderIdRemovalPriority {

    public OrderIdRemovalPriority() {
    }

    public List<Integer> prioritizeOrders(List<Integer> orderIds) {
        // work on a copy so that the input list is not changed
        List<Integer> orders = new ArrayList<>(orderIds);
        List<Integer> result = new ArrayList<>();

        while (!orders.isEmpty()) {
            // scan the whole list and remember the smallest eligible order
            int best = -1;
            for (int i = 0; i < orders.size(); i++) {
                if (isEligible(orders, i) && (best == -1 || orders.get(i) < orders.get(best))) {
                    best = i;
                }
            }
            // remove it by index and add it to the answer
            result.add(orders.remove(best));
        }
        return result;
    }

    // An order is eligible if it is bigger than every neighbor it currently has
    boolean isEligible(List<Integer> orders, int i) {
        boolean biggerThanLeft = (i == 0) || orders.get(i) > orders.get(i - 1);
        boolean biggerThanRight = (i == orders.size() - 1) || orders.get(i) > orders.get(i + 1);
        return biggerThanLeft && biggerThanRight;
    }
}
```

### What is slow here?

The list can hold up to 100,000 orders, and we do one step per order.

- **Finding the smallest eligible order** scans the whole list. That is O(n) per step.
- **`orders.remove(best)`** on an `ArrayList` shifts every element after that index one place to the left. That is also O(n) per step.

O(n) work for each of the n steps gives O(n²) in total, which is billions of operations for 100,000 orders. Way too slow.

**Complexity:** Time O(n²), Space O(n).

---

## Solution 2: Linked List + Min-Heap (Optimal)

We fix the slow parts one at a time.

### Step 1: Remove from the middle in O(1) with a doubly linked list

Removing from a linked list is cheap. We just connect the left neighbor directly to the right neighbor, and nothing gets shifted.

We do not need a `Node` class for this. Two `int` arrays, indexed by the original position, are enough:

- `left[i]`: position of the current left neighbor of order `i`, or `-1` if it has none.
- `right[i]`: position of the current right neighbor of order `i`, or `-1` if it has none.

Removing the order at position `pos` is just two updates:

```java
int leftPos = left[pos];
int rightPos = right[pos];
if (leftPos != -1) right[leftPos] = rightPos;
if (rightPos != -1) left[rightPos] = leftPos;
```

**Why we need it:** after many removals we still need the *current* neighbors of any order, and these two arrays give them in O(1).

### Step 2: Recheck only the two neighbors

When an order is removed, who gets a new neighbor? Only the order on its left and the order on its right.

Every other order still has exactly the same neighbors as before, so its eligibility cannot change. So after each removal we recheck only **2 orders** instead of scanning the whole list.

### Step 3: Keep eligible orders in a min-heap

We need the smallest eligible order at every step, and new eligible orders keep showing up. A min-heap (`PriorityQueue` in Java) fits this perfectly:

- `poll()` returns the smallest eligible order in O(log n).
- `add()` inserts a newly eligible order in O(log n).

We put **positions** in the heap, not the ids, because we need the position to look up `left` and `right`. The heap compares two positions by their order ids, so the smallest id stays on top.

A sorted set would also work, but we only ever need the smallest element, so a heap is the simpler choice.

### Can an order in the heap stop being eligible?

No, and this keeps the code simple. Once an order becomes eligible, it stays eligible until it is removed.

Say order X is eligible. Both its neighbors are smaller than X, so neither of them can be eligible while X sits next to them. That means X's neighbors are never removed before X, and X keeps the same neighbors until its own turn comes.

So the heap never holds an outdated entry, and each order is added to it exactly once.

### Algorithm

1. Copy the ids into an array and build `left` and `right`.
2. Add every order that is eligible right now to the min-heap.
3. While the heap is not empty:
   - Take out the smallest eligible order and add it to the answer.
   - Unlink it from the linked list.
   - Recheck its old left and right neighbors, and add any that became eligible to the heap.
4. Return the answer.

The very last order has no neighbors at all, so it becomes eligible and gets removed too.

### Dry Run

`orderIds = [2, 7, 4, 9, 3, 6]`

| Step | Removed | List after removal | Rechecked | Added to heap | Heap after |
|------|---------|--------------------|-----------|---------------|------------|
| Start | | 2, 7, 4, 9, 3, 6 | all | 7, 9, 6 | 6, 7, 9 |
| 1 | 6 | 2, 7, 4, 9, 3 | 3 | none | 7, 9 |
| 2 | 7 | 2, 4, 9, 3 | 2, 4 | none | 9 |
| 3 | 9 | 2, 4, 3 | 4, 3 | 4 | 4 |
| 4 | 4 | 2, 3 | 2, 3 | 3 | 3 |
| 5 | 3 | 2 | 2 | 2 | 2 |
| 6 | 2 | empty | none | none | empty |

Output: `[6, 7, 9, 4, 3, 2]`

### Code

```java
import java.util.*;

public class OrderIdRemovalPriority {

    int[] ids;    // ids[i] = order id at position i of the input
    int[] left;   // left[i] = position of the current left neighbor of i, -1 if none
    int[] right;  // right[i] = position of the current right neighbor of i, -1 if none

    public OrderIdRemovalPriority() {
    }

    public List<Integer> prioritizeOrders(List<Integer> orderIds) {
        int n = orderIds.size();
        ids = new int[n];
        left = new int[n];
        right = new int[n];

        // build a doubly linked list where every position knows its two neighbors
        for (int i = 0; i < n; i++) {
            ids[i] = orderIds.get(i);
            left[i] = i - 1;
            right[i] = (i == n - 1) ? -1 : i + 1;
        }

        // min-heap of positions of eligible orders, the smallest order id stays on top
        PriorityQueue<Integer> eligible = new PriorityQueue<>((a, b) -> Integer.compare(ids[a], ids[b]));
        for (int i = 0; i < n; i++) {
            if (isEligible(i)) {
                eligible.add(i);
            }
        }

        List<Integer> result = new ArrayList<>();
        while (!eligible.isEmpty()) {
            int pos = eligible.poll();
            result.add(ids[pos]);

            // unlink pos, so its two neighbors now sit next to each other
            int leftPos = left[pos];
            int rightPos = right[pos];
            if (leftPos != -1) {
                right[leftPos] = rightPos;
            }
            if (rightPos != -1) {
                left[rightPos] = leftPos;
            }

            // only these two orders got a new neighbor, so only they can become eligible
            if (leftPos != -1 && isEligible(leftPos)) {
                eligible.add(leftPos);
            }
            if (rightPos != -1 && isEligible(rightPos)) {
                eligible.add(rightPos);
            }
        }
        return result;
    }

    // true if the order at position i is bigger than all of its current neighbors
    boolean isEligible(int i) {
        boolean biggerThanLeft = left[i] == -1 || ids[i] > ids[left[i]];
        boolean biggerThanRight = right[i] == -1 || ids[i] > ids[right[i]];
        return biggerThanLeft && biggerThanRight;
    }
}
```

### Complexity

- **Time: O(n log n).** Each order enters and leaves the heap exactly once, and each heap operation is O(log n). Unlinking an order and rechecking its two neighbors is O(1).
- **Space: O(n)** for the arrays, the heap and the answer.

---

## Summary

| Approach | Time | Space |
|----------|------|-------|
| Brute force simulation | O(n²) | O(n) |
| Linked list + min-heap | O(n log n) | O(n) |

**Key takeaways:**

- If removing an item only affects its direct neighbors, recheck just those neighbors instead of the whole list.
- Two plain arrays (`left` and `right`) make a light and fast doubly linked list.
- A min-heap is the natural fit when you keep needing the smallest item from a set that keeps changing.