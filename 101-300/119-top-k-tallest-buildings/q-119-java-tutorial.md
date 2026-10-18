# Top K Tallest Buildings in Java

#### Problem Statement
[https://codezym.com/question/119-top-k-tallest-buildings](https://codezym.com/question/119-top-k-tallest-buildings)


The whole problem comes down to one ranking rule: **a taller building is better, and if two buildings have the same height, the one with the lower index is better.**

Once we have this rule, the task is simply "pick the best `k` buildings and list them from best to worst".

We will start with a simple brute force, then improve it with sorting, and finally use a **min-heap of size `k`** which only does the work that is really needed.


---


## Understanding the Problem

We get a list of heights and a number `k`. We must return `k` indexes.

The order of the answer matters:

1. Taller buildings come first.
2. If two buildings have the same height, the lower index comes first.

**Example:** `heights = [4, 9, 9, 2, 8]`, `k = 3`

- Height `9` is at index `1` and index `2`. Both are the tallest. Index `1` comes first.
- Next is height `8` at index `4`.

**Answer:** `[1, 2, 4]`


---


## Solution 1: Brute Force (Pick the Tallest, k Times)

### Idea

The most direct way is to repeat this `k` times:

1. Look at every building that is not picked yet.
2. Find the tallest one.
3. Mark it as picked and add it to the answer.


### Handling ties

We scan from left to right and only replace our current best when we find a building that is **strictly taller** (`>`).

So if two buildings have the same height, the first one we saw (lower index) stays as the best. This gives us the tie rule for free.


### Why a `boolean[] picked` array?

We need to skip buildings that are already in the answer. A simple boolean array tells us in one step whether index `i` was already picked.


### Code

```java
import java.util.*;

public class TopKTallestBuildings {

    public TopKTallestBuildings() {
    }

    /**
     * Brute force: run k rounds. In each round pick the tallest building
     * that is not picked yet.
     */
    public List<Integer> topKTallestBuildingIndexes(List<Integer> heights, int k) {
        int n = heights.size();
        boolean[] picked = new boolean[n];
        List<Integer> result = new ArrayList<>();

        for (int round = 0; round < k; round++) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (picked[i]) {
                    continue;
                }
                // Use '>' and not '>=' so that on a tie the lower index stays as best
                if (best == -1 || heights.get(i) > heights.get(best)) {
                    best = i;
                }
            }
            picked[best] = true;
            result.add(best);
        }
        return result;
    }
}
```


### Complexity

- **Time:** `O(n * k)`
- **Space:** `O(n)` for the `picked` array


### Problem with this approach

Every round scans the full list again.

If `n = 100,000` and `k = 100,000`, that is about **10 billion steps**. This is far too slow.


---


## Solution 2: Sort Everything

### Idea

Instead of searching again and again, we can sort all buildings **once** using our ranking rule.

After sorting, the best building is at position `0`, the second best at position `1`, and so on. We just take the first `k`.


### Why sort indexes and not heights?

We need to return **indexes**. If we sort the heights directly, we lose track of where each height came from.

So we make a list `[0, 1, 2, ..., n-1]` and sort these indexes by looking up their heights.


### Why copy heights into an `int[]`?

`heights.get(i)` returns an `Integer` object. Comparing two `Integer` objects with `!=` compares the objects, not the numbers, and can give wrong results for large values.

Copying into a plain `int[]` avoids this problem and also makes lookups faster.


### The comparator

```java
if (h[a] != h[b]) {
    return Integer.compare(h[b], h[a]); // taller building first
}
return Integer.compare(a, b);           // same height: lower index first
```

This is our ranking rule written as code.


### Code

```java
import java.util.*;

public class TopKTallestBuildings {

    public TopKTallestBuildings() {
    }

    /**
     * Sort all indexes by (height descending, index ascending)
     * and return the first k.
     */
    public List<Integer> topKTallestBuildingIndexes(List<Integer> heights, int k) {
        int n = heights.size();

        // Copy heights into a plain array for fast lookups
        int[] h = new int[n];
        for (int i = 0; i < n; i++) {
            h[i] = heights.get(i);
        }

        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            indexes.add(i);
        }

        indexes.sort((a, b) -> {
            if (h[a] != h[b]) {
                return Integer.compare(h[b], h[a]); // taller building first
            }
            return Integer.compare(a, b); // same height: lower index first
        });

        return new ArrayList<>(indexes.subList(0, k));
    }
}
```


### Complexity

- **Time:** `O(n log n)`
- **Space:** `O(n)`


### Problem with this approach

This is fast enough to pass, but it does extra work.

If `k = 1`, we only need the single tallest building, yet we still sort all `100,000` buildings. We are putting the whole list in order when we only care about the top few.


---


## Solution 3: Min-Heap of Size k (Best Solution)

### Idea

Think of it like a club with only `k` seats.

- We walk through the buildings one by one.
- While there are empty seats, every building gets a seat.
- Once the club is full, a new building can enter only if it is better than the **weakest** member. If it is, the weakest member leaves.

At the end, the club holds exactly the top `k` buildings.


### Why a min-heap?

To decide whether a new building gets in, we only need to compare it with the **weakest** building in the club.

A min-heap (`PriorityQueue` in Java) keeps the weakest item on top. We can look at it in `O(1)` and remove or add an item in `O(log k)`.

The heap never grows beyond `k` items, so each step stays cheap.


### What does "weakest" mean here?

It is the reverse of our ranking rule:

- A **shorter** building is weaker.
- For the **same height**, the building with the **higher index** is weaker.

```java
PriorityQueue<Integer> heap = new PriorityQueue<>((a, b) -> {
    if (h[a] != h[b]) {
        return Integer.compare(h[a], h[b]); // shorter on top
    }
    return Integer.compare(b, a);           // same height: higher index on top
});
```


### Handling ties when replacing

We scan buildings from left to right. So a new building always has a **higher index** than every building already in the heap.

That means if the new building has the **same height** as the weakest one, the new building loses the tie.

So we replace the weakest building only when the new building is **strictly taller**:

```java
else if (h[i] > h[heap.peek()]) {
    heap.poll();
    heap.add(i);
}
```


### Getting the final order

Removing items from a min-heap gives them from **weakest to best**.

We want **best to weakest**, so we collect them and reverse the list.


### Dry Run

`heights = [4, 9, 9, 2, 8]`, `k = 3`

| Index | Height | Action | Heap (weakest on top) |
|---|---|---|---|
| 0 | 4 | Seat free, add | `0` |
| 1 | 9 | Seat free, add | `0, 1` |
| 2 | 9 | Seat free, add | `0, 1, 2` (top = index 0, height 4) |
| 3 | 2 | 2 is not taller than 4, skip | `0, 1, 2` |
| 4 | 8 | 8 is taller than 4, remove index 0, add index 4 | `4, 1, 2` (top = index 4, height 8) |


Removing from the heap gives `4, 2, 1`.

Index `2` comes out before index `1` because both have height `9` and index `2` is the weaker one.

After reversing: **`[1, 2, 4]`**


### Code

```java
import java.util.*;

public class TopKTallestBuildings {

    public TopKTallestBuildings() {
    }

    /**
     * Keep only the best k buildings seen so far in a min-heap.
     * The weakest of those k buildings always sits on top of the heap,
     * so we can quickly decide if a new building deserves its place.
     */
    public List<Integer> topKTallestBuildingIndexes(List<Integer> heights, int k) {
        int n = heights.size();

        // Copy heights into a plain array for fast lookups
        int[] h = new int[n];
        for (int i = 0; i < n; i++) {
            h[i] = heights.get(i);
        }

        // Weakest building on top:
        // shorter is weaker, and for the same height the higher index is weaker
        PriorityQueue<Integer> heap = new PriorityQueue<>((a, b) -> {
            if (h[a] != h[b]) {
                return Integer.compare(h[a], h[b]);
            }
            return Integer.compare(b, a);
        });

        for (int i = 0; i < n; i++) {
            if (heap.size() < k) {
                heap.add(i);
            } else if (h[i] > h[heap.peek()]) {
                // New building is strictly taller than the weakest one, so swap them.
                // On equal height the new building has a higher index, so it loses.
                heap.poll();
                heap.add(i);
            }
        }

        // Heap gives weakest first, so reverse to get tallest first
        List<Integer> result = new ArrayList<>();
        while (!heap.isEmpty()) {
            result.add(heap.poll());
        }
        Collections.reverse(result);
        return result;
    }
}
```


### Complexity

- **Time:** `O(n log k)`
- **Space:** `O(n)` for the copied heights, plus `O(k)` for the heap

When `k` is small, this is much faster than sorting everything. When `k` is close to `n`, it performs about the same as sorting.


---


## Summary

| Solution | Time | Space | Verdict |
|---|---|---|---|
| Brute force | `O(n * k)` | `O(n)` | Too slow for large inputs |
| Sort everything | `O(n log n)` | `O(n)` | Passes, but sorts more than needed |
| Min-heap of size k | `O(n log k)` | `O(n)` | Best, only tracks what matters |


The key lessons:

- Turn the problem into a clear **ranking rule** first.
- To find the top `k` items, keep the **weakest** of them on top of a **min-heap**, so you always know who to remove next.
- Scanning left to right lets you handle the **tie rule** with a simple `>` check.