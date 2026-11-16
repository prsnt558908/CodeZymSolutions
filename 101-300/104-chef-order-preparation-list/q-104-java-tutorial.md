# Chef Order Preparation List in Java

#### Problem Statement
[https://codezym.com/question/104-chef-order-preparation-list](https://codezym.com/question/104-chef-order-preparation-list)

---

An order is eligible when it is bigger than all of its current neighbors, like a small **peak** in the list. The chef always removes the smallest eligible order, and every removal changes the list, so new orders can become eligible. The key observation is simple: removing an order only changes the neighbors of the two orders right next to it. Everything else stays the same.


So we keep all eligible orders in a **min-heap**, which always gives us the smallest one quickly. We keep the list itself as a **doubly linked list** built from two arrays, so we can remove an order and find its new neighbors in `O(1)`. After every removal, we only re-check those two neighbors. We start with a direct brute force simulation and then fix its slow parts one by one.

---

## Understanding the Problem

The problem gives four rules for eligibility. They are really just one rule:


> An order is eligible if it is **bigger than every neighbor it currently has**.


- The first order has no left neighbor, so only its right neighbor matters.
- The last order has no right neighbor, so only its left neighbor matters.
- A middle order must be bigger than both neighbors.
- A single order has no neighbors at all, so it is eligible automatically.

---

## Approach 1: Brute Force

Just do exactly what the chef does:

1. Scan the whole list and find the smallest eligible order.
2. Remove it and add it to the answer.
3. Repeat until the list is empty.

```java
List<Integer> current = new ArrayList<>(orderIds);
List<Integer> result = new ArrayList<>();

while (!current.isEmpty()) {
    int best = -1; // position of the smallest eligible order
    for (int i = 0; i < current.size(); i++) {
        boolean biggerThanLeft = i == 0 || current.get(i) > current.get(i - 1);
        boolean biggerThanRight = i == current.size() - 1 || current.get(i) > current.get(i + 1);
        if (biggerThanLeft && biggerThanRight) {
            if (best == -1 || current.get(i) < current.get(best)) {
                best = i;
            }
        }
    }
    result.add(current.remove(best)); // remove by position
}
```

### Why is it slow?

- Every step scans the whole list to find eligible orders: `O(n)`.
- Removing from the middle of an `ArrayList` shifts every element after it: `O(n)`.
- We repeat this `n` times, so the total is `O(n^2)`.


With up to `100,000` orders, that is billions of operations. Far too slow.

---

## Approach 2: Min-Heap + Doubly Linked List

Let's fix the slow parts one at a time.

### Fix 1: Only two orders can change after a removal

Whether an order is eligible depends only on its two neighbors.


When we remove an order, only the order on its left and the order on its right get a new neighbor (they now sit next to each other). Every other order keeps the same neighbors, so its status cannot change.


So after a removal, we re-check just **those two orders**, not the whole list.

### Fix 2: A min-heap to get the smallest eligible order

We need the smallest eligible order at every step, and new eligible orders keep appearing. A **min-heap** (`PriorityQueue` in Java) is made exactly for this:

- `add()` puts a new eligible order in, in `O(log n)`.
- `poll()` takes out the smallest one, in `O(log n)`.


We store **positions** in the heap, but compare them by their order ids. This way, when we take an order out, we also know where it sits in the list.

### Fix 3: A doubly linked list to remove an order in `O(1)`

After a removal we need the new neighbors fast, and we do not want to shift elements around. A **doubly linked list** gives us both. We build it with two plain arrays:

- `left[pos]`: position of the nearest remaining order on the left, `-1` if none.
- `right[pos]`: position of the nearest remaining order on the right, `-1` if none.


To remove the order at `pos`, we simply connect its two neighbors to each other (skipping a side if it is `-1`):

```java
right[leftPos] = rightPos;
left[rightPos] = leftPos;
```


Why not Java's `LinkedList`? It cannot jump straight to a given position, so reaching an order takes `O(n)`. Our arrays reach any position in `O(1)`.

### Why an eligible order stays eligible

If an order is eligible, both its neighbors are **smaller** than it. A smaller neighbor can never be eligible, because it has a bigger neighbor (our order). So no neighbor can be removed before our order.


This means **once an order becomes eligible, it stays eligible until the chef picks it**. Two nice results:

- The heap never holds an outdated entry, so we never need to skip or delete anything from it.
- The two neighbors we re-check were smaller than the removed order, so they were not eligible before. No order is ever added to the heap twice.

### Putting it together

1. Build the `left` and `right` arrays.
2. Add every order that is eligible at the start to the heap.
3. While the heap is not empty:
   - Take out the smallest order and add it to the answer.
   - Unlink it from the list.
   - Re-check its old left and right neighbors. Add any that just became eligible.

### Dry Run

`orderIds = [5, 1, 4, 2, 3]`

| Step | Removed | List after removal | Neighbors re-checked | Heap after step (order ids) |
|------|---------|--------------------|----------------------|-----------------------------|
| Start | | `[5, 1, 4, 2, 3]` | | `3, 4, 5` |
| 1 | `3` | `[5, 1, 4, 2]` | `2`: not eligible | `4, 5` |
| 2 | `4` | `[5, 1, 2]` | `1`: not eligible, `2`: eligible | `2, 5` |
| 3 | `2` | `[5, 1]` | `1`: not eligible | `5` |
| 4 | `5` | `[1]` | `1`: eligible (alone) | `1` |
| 5 | `1` | `[]` | | empty |


Answer: `[3, 4, 2, 5, 1]`

### Java Code

```java
import java.util.*;

public class ChefOrderPreparation {

    int[] ids;   // ids[pos] = order id at position pos
    int[] left;  // left[pos] = nearest remaining position on the left, -1 if none
    int[] right; // right[pos] = nearest remaining position on the right, -1 if none

    public ChefOrderPreparation() {
    }

    /**
     * Repeatedly removes the smallest eligible order and
     * returns the order ids in the order they were removed.
     */
    public List<Integer> getPreparationOrder(List<Integer> orderIds) {
        int n = orderIds.size();
        ids = new int[n];
        left = new int[n];
        right = new int[n];

        // Link every position to its neighbors, like a doubly linked list
        for (int pos = 0; pos < n; pos++) {
            ids[pos] = orderIds.get(pos);
            left[pos] = pos - 1;
            right[pos] = (pos + 1 < n) ? pos + 1 : -1;
        }

        // Min-heap of positions of eligible orders.
        // Positions are compared by their order ids, so the smallest order id is on top.
        PriorityQueue<Integer> eligible =
                new PriorityQueue<>((a, b) -> Integer.compare(ids[a], ids[b]));
        for (int pos = 0; pos < n; pos++) {
            if (isEligible(pos)) {
                eligible.add(pos);
            }
        }

        List<Integer> result = new ArrayList<>();
        while (!eligible.isEmpty()) {
            int pos = eligible.poll();
            result.add(ids[pos]);

            // Remove pos from the list: its two neighbors now point to each other
            int leftPos = left[pos];
            int rightPos = right[pos];
            if (leftPos != -1) {
                right[leftPos] = rightPos;
            }
            if (rightPos != -1) {
                left[rightPos] = leftPos;
            }

            // Only these two orders got a new neighbor, so only they can become eligible
            if (leftPos != -1 && isEligible(leftPos)) {
                eligible.add(leftPos);
            }
            if (rightPos != -1 && isEligible(rightPos)) {
                eligible.add(rightPos);
            }
        }
        return result;
    }

    /**
     * An order is eligible if no current neighbor is bigger than it.
     * A missing neighbor (-1) never blocks it.
     */
    boolean isEligible(int pos) {
        int leftPos = left[pos];
        int rightPos = right[pos];
        if (leftPos != -1 && ids[leftPos] > ids[pos]) {
            return false;
        }
        if (rightPos != -1 && ids[rightPos] > ids[pos]) {
            return false;
        }
        return true;
    }
}
```

### Complexity

- **Time:** `O(n log n)`. Each order is added to the heap once and taken out once. Each linked list update is `O(1)`.
- **Space:** `O(n)` for the arrays, the heap and the answer.

---

## Key Takeaways

- An order's status depends only on its neighbors, so after a removal only **two** orders need a re-check.
- "Always pick the smallest" is a strong hint for a **min-heap**.
- "Remove from the middle and find the new neighbors" is a strong hint for a **doubly linked list**. Two arrays, `left` and `right`, are the easiest way to build one.