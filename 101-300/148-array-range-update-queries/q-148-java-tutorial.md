# Array Range Update Queries in Java

#### Problem Statement
[https://codezym.com/question/148-array-range-update-queries](https://codezym.com/question/148-array-range-update-queries)


## Core Idea

Each query sets all values from index `l` to index `r` to `k`. Updating every index of every query one by one is too slow when there are many big ranges.


The trick is to handle **each index only a few times**, no matter how many queries cover it.


- **Same `k` for all queries:** the order of the queries does not matter. An index becomes `k` if at least one range covers it. A **difference array** finds all such indices in one pass.

- **Different `k` for each query:** only the **last** query that covers an index decides its final value. So we read the queries **from last to first**, set each index the first time we reach it, and never touch it again. A **`nextFree` array** lets us jump over indices that are already set.


## Brute Force

Do exactly what the problem says. For every query, walk from `l` to `r` and write `k`.

```java
List<Integer> result = new ArrayList<>(arr);
for (String query : queries) {
    String[] parts = query.split(",");
    int l = Integer.parseInt(parts[0].trim());
    int r = Integer.parseInt(parts[1].trim());
    int k = Integer.parseInt(parts[2].trim());
    for (int i = l; i <= r; i++) {
        result.set(i, k);
    }
}
```


This works for both variations, but it is slow. One query can touch up to `n` indices, so the total work is `O(n * q)`. With `n = q = 10^5`, that is up to `10^10` steps.


The waste is easy to see: an index may be written many times, but only the **last** write survives.


## Same k for All Queries: Difference Array

When every query uses the same `k`, writing `k` twice is the same as writing it once. So the order of the queries does not matter.


For each index we only need to know one thing: **is it covered by at least one range?** If yes, its final value is `k`. If no, it keeps its original value.


### Why a Difference Array?

Marking every index of every range would be the slow brute force again. Instead, we mark only the **two ends** of each range in an array `diff`:

- `diff[l]++` means "a range starts at `l`".
- `diff[r + 1]--` means "a range ended just before `r + 1`".


`diff` has `n + 1` slots, so `diff[r + 1]` is safe even when `r` is the last index.


Then we walk from left to right and keep a running sum called `covered`. At index `i`, `covered` is the number of ranges that cover `i`. If it is more than `0`, the answer at `i` is `k`.


Example 1: `arr = [1, 2, 3, 4, 5]`, ranges `"1,3"` and `"0,1"`, `k = 9`.

| index | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| diff | 1 | 1 | -1 | 0 | -1 |
| covered | 1 | 2 | 1 | 1 | 0 |
| result | 9 | 9 | 9 | 9 | 5 |


Each range takes `O(1)` work, and one pass builds the answer.


## Different k for Each Query

The difference array does not help here. It tells us **how many** ranges cover an index, but not **which one came last**.


### Step 1: The Last Query Wins

Look at one index. Many queries may cover it, but each later query overwrites the earlier one. So only the **last** query covering it matters.


So we process the queries in **reverse order**. The first time we reach an index, we already know its final value. We set it and mark the index as done.


Example 2: queries `"1,3,7"` and `"2,4,0"`, read from last to first:

- `"2,4,0"`: indices 2, 3 and 4 are free. Set them to `0`.
- `"1,3,7"`: index 1 is free, so set it to `7`. Indices 2 and 3 are done, so skip them.

Final array: `[1, 7, 0, 0, 0]`.


### Step 2: Jump Over Indices That Are Done

Now each index is **set** only once. But if we still **check** every index from `l` to `r`, a big range full of done indices costs many steps. In the worst case we are back to `O(n * q)`.


We need to jump straight to the next free index. That is the job of the `nextFree` array, which has `n + 1` slots:

- If `nextFree[i] == i`, index `i` is still free.
- Otherwise, `nextFree[i]` points to a later index. Following the pointers from there leads to the next free index.
- The extra slot `nextFree[n] = n` is a stop sign. It is always free and lies past every range.


At the start every index is free, so `nextFree[i] = i`.


`findFree(i)` follows the pointers from `i` until it reaches an index that points to itself. That is the first free index at or after `i`.


When index `i` gets its final value, we set `nextFree[i] = findFree(i + 1)`. In plain words: "`i` is done, continue from the next free index".


In Example 2, after `"2,4,0"` the pointers form a chain `2 -> 3 -> 4 -> 5`. While handling `"1,3,7"`, we set index 1, then the search from index 2 follows the chain to `5`. Since `5 > 3`, this query is finished. Indices 2 and 3 were never set again.


### Step 3: Shortcuts Keep the Jumps Short

Chains like `0 -> 1 -> 2 -> 3 -> 4` can get long, and walking them again and again would be slow.


So after `findFree` finds the free index, it walks the same path once more and points every index on it **straight to the answer**:

```text
before:  0 -> 1 -> 2 -> 3 -> 4 (free)
after:   0 -> 4,  1 -> 4,  2 -> 4,  3 -> 4
```


The next search from any of these indices takes a single jump. If you know Union Find, this is its path compression trick.


Now every index is set at most once, and searches stay short. That makes this solution fast even for the largest inputs.


## Final Code

```java
import java.util.*;

public class ArrayRangeUpdateQueries {

    public ArrayRangeUpdateQueries() {
    }

    /**
     * Same k for all ranges. Order does not matter here,
     * so an index becomes k if at least one range covers it.
     */
    public List<Integer> updateArrayWithSameK(List<Integer> arr, List<String> ranges, int k) {
        int n = arr.size();

        // diff[i] = change in the number of covering ranges at index i.
        // n + 1 slots, so diff[r + 1] is safe when r is the last index.
        int[] diff = new int[n + 1];
        for (String range : ranges) {
            String[] parts = range.split(",");
            int l = Integer.parseInt(parts[0].trim());
            int r = Integer.parseInt(parts[1].trim());
            diff[l]++;     // a range starts at l
            diff[r + 1]--; // a range ended just before r + 1
        }

        List<Integer> result = new ArrayList<>();
        int covered = 0; // how many ranges cover the current index
        for (int i = 0; i < n; i++) {
            covered += diff[i];
            result.add(covered > 0 ? k : arr.get(i));
        }
        return result;
    }

    /**
     * Different k for each query. Only the last query covering an index
     * decides its value, so we go from the last query to the first
     * and set every index at most once.
     */
    public List<Integer> updateArray(List<Integer> arr, List<String> queries) {
        int n = arr.size();
        List<Integer> result = new ArrayList<>(arr);

        // nextFree[i] == i means index i is not set yet.
        // Otherwise it points to a later index to continue the search from.
        // nextFree[n] = n is a stop sign that is always free.
        int[] nextFree = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            nextFree[i] = i;
        }

        for (int j = queries.size() - 1; j >= 0; j--) {
            String[] parts = queries.get(j).split(",");
            int l = Integer.parseInt(parts[0].trim());
            int r = Integer.parseInt(parts[1].trim());
            int k = Integer.parseInt(parts[2].trim());

            // visit only the free indices inside [l, r]
            int i = findFree(nextFree, l);
            while (i <= r) {
                // no later query covers i, so i keeps the value k
                result.set(i, k);
                // i is done: point it to the next free index and jump there
                nextFree[i] = findFree(nextFree, i + 1);
                i = nextFree[i];
            }
        }
        return result;
    }

    /**
     * Returns the first free index at or after i.
     * It also points every index on the walked path straight to that
     * free index, so later searches need just one jump.
     */
    int findFree(int[] nextFree, int i) {
        // follow the pointers until an index points to itself
        int free = i;
        while (nextFree[free] != free) {
            free = nextFree[free];
        }

        // shortcut: point every index on the path straight to the free index
        int current = i;
        while (current != free) {
            int next = nextFree[current];
            nextFree[current] = free;
            current = next;
        }
        return free;
    }
}
```


## Complexity

Let `n` be the array size and `q` be the number of queries.

| Method | Time | Extra Space |
|---|---|---|
| `updateArrayWithSameK` | `O(n + q)` | `O(n)` |
| `updateArray` | `O((n + q) log n)` in the worst case, close to `O(n + q)` in practice | `O(n)` |