# Find Kth Largest Element From Chef's Collection in Java

#### Problem Statement
[https://codezym.com/question/176-kth-largest-element-chefs-collection](https://codezym.com/question/176-kth-largest-element-chefs-collection)


## Core Idea

We need a collection that supports two things: add a value, and find the `k`-th largest value.


If `k` were fixed, a min heap of size `k` would be enough. Here `k` can change on every query, so we need something that answers any `k` quickly.


The trick is in the constraints. Every value lies between `-1,000,000` and `1,000,000`, which is a small and fixed range.


So instead of keeping the values in sorted order, we count how many copies of each value we have. On top of these counts we build a **segment tree**: a tree where every node knows how many values fall inside its range of numbers.


To find the `k`-th largest, we start at the root and keep stepping into the half that holds the answer, checking the half with bigger values first. Both `insert` and `findKthLargest` take only about `21` steps.


We will get there in three steps: brute force, then a count array, then the segment tree.


## Solution 1: Brute Force, Sort on Every Query

Keep every value in a list. When asked for the `k`-th largest, sort a copy from largest to smallest and return the value at position `k - 1`.

```java
List<Integer> values = new ArrayList<>();

public KthLargestElement(List<Integer> initialValues) {
    values.addAll(initialValues);
}

public void insert(int value) {
    values.add(value);
}

public int findKthLargest(int k) {
    // Sort a copy from largest to smallest, then pick position k - 1
    List<Integer> sorted = new ArrayList<>(values);
    sorted.sort(Collections.reverseOrder());
    return sorted.get(k - 1);
}
```

Duplicates work out on their own, because every copy stays in the list.


**Why it is slow:** every `findKthLargest` call sorts the whole list. With `100,000` values and `100,000` queries, that is more than `100` billion steps.


Keeping the list always sorted does not fix it either. Find becomes instant, but every insert has to shift many elements to make room for the new value. One of the two operations always stays slow.


## Solution 2: Count Array

Look at the constraints again. Every value is between `-1,000,000` and `1,000,000`, so there are only `2,000,001` possible values.


This means we do not need to keep the values in order at all. We just count how many copies of each value we have, in a plain array.


Array positions cannot be negative, so value `v` is stored at position `v + OFFSET`, where `OFFSET = 1,000,000`. Value `-1,000,000` goes to position `0` and value `1,000,000` goes to position `2,000,000`.


To find the `k`-th largest, walk from the biggest possible value down to the smallest and subtract each count from `k`. The moment `k` drops to `0` or below, we are standing on the answer.

```java
int OFFSET = 1_000_000;

// count[v + OFFSET] = how many copies of value v we have
int[] count = new int[2 * OFFSET + 1];

public KthLargestElement(List<Integer> initialValues) {
    for (int value : initialValues) {
        insert(value);
    }
}

public void insert(int value) {
    count[value + OFFSET]++;
}

public int findKthLargest(int k) {
    // Walk from the biggest possible value down to the smallest
    for (int index = count.length - 1; index >= 0; index--) {
        k -= count[index];
        if (k <= 0) {
            return index - OFFSET;
        }
    }
    return -1; // never reached, k is always valid
}
```

Duplicates are easy here too. A value with count `3` uses up `3` of `k` in one go.


**Why it is still slow:** insert is now a single step, but find may walk over all `2,000,001` positions. With `100,000` queries, that is up to `200` billion steps.


The real problem is that find looks at values one at a time. If we also knew the total count of a whole group of values, we could skip that entire group in a single step.


## Solution 3: Segment Tree of Counts (Optimal)

Let us take the grouping idea all the way. Put every two neighboring values in a group, put every two groups in a bigger group, and keep going until one group covers every value.


This is a segment tree. Every node covers a range of values and stores how many values of the collection fall inside that range.


The leaves are single values with their counts, exactly like the count array. Every other node is the sum of its two children, so the root holds the size of the whole collection.


Here is a tiny tree for values `0` to `7`, holding the collection `[1, 3, 3, 6]`:

```
                    [0-7]=4
                 /           \
         [0-3]=3               [4-7]=1
         /     \               /     \
    [0-1]=1   [2-3]=2     [4-5]=0   [6-7]=1
     /   \     /   \       /   \     /   \
    0     1   2     3     4     5   6     7    <- value
    0     1   0     2     0     0   1     0    <- count
```


### Finding the k-th largest

Start at the root. Its right child covers the bigger values, so look there first.

- If the right child holds at least `k` values, the answer is among them. Move right.
- Otherwise, all of those bigger values come before the answer. Subtract their count from `k` and move left.

Repeat until you reach a leaf. That leaf is the answer.


Let us find the second largest (`k = 2`) in the tree above:

| Node | k | Right child | Move |
|---|---|---|---|
| `[0-7]` | 2 | `[4-7]` has 1 | 1 < 2, so k = 2 - 1 = 1, go left |
| `[0-3]` | 1 | `[2-3]` has 2 | 2 >= 1, go right |
| `[2-3]` | 1 | leaf `3` has 2 | 2 >= 1, go right |
| leaf `3` | 1 | | answer is `3` |

In descending order the collection is `[6, 3, 3, 1]`, and its second value is indeed `3`.


### Inserting a value

Add `1` to the leaf of the value, then add `1` to every node on the path up to the root. Only one node per level changes.


For example, inserting `5` above changes leaf `5`, `[4-5]`, `[4-7]` and `[0-7]`.


### Storing the tree in a plain array

We do not need node objects or links between them. The whole tree lives in one `int` array:

- the root is at index `1`
- the children of node `i` are at `2 * i` and `2 * i + 1`
- the parent of node `i` is at `i / 2`


We make the number of leaves a power of two: `LEAVES = 2^21 = 2,097,152`. It is the smallest power of two with room for all `2,000,001` values, and it gives a full tree where every level is complete.


The leaves sit at indexes `LEAVES` to `2 * LEAVES - 1`, so the leaf of value `v` is at index `LEAVES + v + OFFSET`. Bigger values sit further right, which is exactly what find needs. The leftover leaves at the end never get a value, so their count stays `0`.


In the tiny tree above, `LEAVES = 8` and no offset is needed, so value `3` is at index `8 + 3 = 11`. Its parent is `11 / 2 = 5`, which is `[2-3]`, and the parent of that is `5 / 2 = 2`, which is `[0-3]`.


**Why a plain array:** the range of values is fixed, so the size of the tree is known from the start. Moving to a child or a parent is simple index math, which keeps the code short and fast.


**Why not a sorted set or sorted map:** a sorted set drops duplicates. A sorted map of counts keeps duplicates, but it still has to walk through its keys one by one to reach the `k`-th value, just like the count array.


### Code

```java
import java.util.*;

public class KthLargestElement {

    // Values lie in [-1,000,000, 1,000,000].
    // Adding OFFSET shifts them to [0, 2,000,000], so a value can be used as a position.
    int OFFSET = 1_000_000;

    // Number of leaves: 2^21 = 2,097,152.
    // The smallest power of two that has room for all 2,000,001 possible values.
    int LEAVES = 1 << 21;

    // tree[node] = how many values of the collection fall inside the range of that node.
    // tree[1] is the root. Children of node i are 2 * i and 2 * i + 1.
    // The leaf of value v is at index LEAVES + v + OFFSET.
    int[] tree = new int[2 * LEAVES];

    public KthLargestElement(List<Integer> initialValues) {
        for (int value : initialValues) {
            insert(value);
        }
    }

    /**
     * Adds one copy of value.
     * The leaf of value and every node above it grow by 1.
     */
    public void insert(int value) {
        int node = LEAVES + value + OFFSET;
        while (node >= 1) {
            tree[node]++;
            node = node / 2; // move up to the parent
        }
    }

    /**
     * Walks down from the root to the leaf that holds the answer.
     * The right child covers bigger values, so it is checked first.
     */
    public int findKthLargest(int k) {
        int node = 1;
        while (node < LEAVES) {
            int left = 2 * node;
            int right = 2 * node + 1;
            if (tree[right] >= k) {
                // the answer is among the bigger values
                node = right;
            } else {
                // skip all the bigger values, the answer is on the left
                k -= tree[right];
                node = left;
            }
        }
        // node is now a leaf, turn its index back into a value
        return node - LEAVES - OFFSET;
    }
}
```


## Complexity

Let `n` be the number of values in the collection and `R = 2,000,001` be the number of possible values.

| | Brute Force | Count Array | Segment Tree |
|---|---|---|---|
| `insert` | O(1) | O(1) | O(log R) |
| `findKthLargest` | O(n log n) | O(R) | O(log R) |
| Space | O(n) | O(R) | O(R) |

`log R` is about `21`, so each segment tree operation takes about `21` steps.


Building the tree from `initialValues` is just `n` inserts, so it costs O(n log R). The tree array has about `4.2` million ints, around `16` MB, no matter how many values we insert.