# Valid Binary Tree Subsets in Java

#### Problem Statement
[https://codezym.com/question/446-valid-binary-tree-subsets](https://codezym.com/question/446-valid-binary-tree-subsets)


## Core Idea

Every node has only two choices: it is either picked or skipped.

So instead of listing subsets one by one, we keep two counts for every node: how many valid subsets its subtree has when the node is picked, and how many when it is skipped.

A parent can build its two counts using only its children's counts. So we fill the counts from the leaves up to the root, and the answer is the root's two counts added together. This is called **tree DP**.

We will start with brute force, then build the tree DP with recursion, and finally remove the recursion so that very deep trees don't crash.


## Solution 1: Brute Force (Try Every Subset)

The most direct idea is to try every subset and check that no edge has both of its ends picked.

Give each node an index from `0` to `n - 1`. Then every number `mask` from `0` to `2^n - 1` stands for one subset. If bit `i` of `mask` is `1`, node `i` is picked.

```java
// edges holds {parentIndex, childIndex} for every parent-child pair
int count = 0;
for (int mask = 0; mask < (1 << n); mask++) {
    boolean valid = true;
    for (int[] edge : edges) {
        boolean parentPicked = ((mask >> edge[0]) & 1) == 1;
        boolean childPicked = ((mask >> edge[1]) & 1) == 1;
        if (parentPicked && childPicked) {
            valid = false;
            break;
        }
    }
    if (valid) count++;
}
```

**Why it is slow:** there are `2^n` subsets, and each one is checked against every edge. That is `O(2^n * n)`.

Even `n = 40` gives about a trillion subsets, and `n` can be `200,000`. We need a way to count subsets without listing them.


## Solution 2: Tree DP with Recursion

Look at a single node.

- If the node is **picked**, none of its children can be picked.
- If the node is **skipped**, each child can be picked or skipped.

So for every node we store two numbers about its subtree:

- `pick` = number of valid subsets of the subtree where the node is picked
- `skip` = number of valid subsets of the subtree where the node is skipped


**Why multiply?** The left subtree and the right subtree have no edge between them. So any valid choice on the left can be paired with any valid choice on the right.

If the left side has 3 ways and the right side has 2 ways, together they have `3 * 2 = 6` ways.

This gives us two simple formulas:

```
pick(node) = skip(left) * skip(right)
skip(node) = (pick(left) + skip(left)) * (pick(right) + skip(right))
```

A missing child is simply left out of the product. So a leaf has `pick = 1` (just the leaf) and `skip = 1` (the empty subset).

The final answer is `pick(root) + skip(root)`.


Let's check it on Example 1. The edges are `10-20`, `10-30` and `20-40`.

| Node | Children | pick | skip | pick + skip |
|------|----------|------|------|-------------|
| 40 | none | 1 | 1 | 2 |
| 30 | none | 1 | 1 | 2 |
| 20 | 40 | 1 | 2 | 3 |
| 10 | 20, 30 | 2 * 1 = 2 | 3 * 2 = 6 | **8** |

The answer is `8`, which matches the expected output.


These counts grow very fast, so we take `% MOD` after every multiplication. We use `long` so that multiplying two numbers below `MOD` never overflows.

Recursion fits this idea naturally, because a node needs its children's answers first:

```java
// Returns {pick, skip} for the subtree of nodeId
long[] solve(int nodeId) {
    long pick = 1, skip = 1;
    for (int child : children.get(nodeId)) {
        if (child == -1) continue;
        long[] result = solve(child);
        long childPick = result[0], childSkip = result[1];
        pick = pick * childSkip % MOD;                       // node picked: child must be skipped
        skip = skip * ((childPick + childSkip) % MOD) % MOD; // node skipped: child is free
    }
    return new long[]{pick, skip};
}
```

**Why it can fail:** calls go as deep as the tree is tall. A tree can be one long chain of `200,000` nodes, which means `200,000` nested calls.

That many nested calls overflow Java's call stack, and the program crashes with `StackOverflowError`.


## Solution 3: Tree DP without Recursion (Final Solution)

Recursion was giving us only one thing: every child is finished before its parent. We can get the same order with a plain list.

1. Walk the tree level by level (BFS) starting from the root, and add every node to a list called `order`. In this list, a parent always comes before its children.
2. Go through `order` from the end to the start. Now every child is handled before its parent.
3. Apply the same `pick` and `skip` formulas as before.

Same math, no recursion.


### Important Data Structures

- **`Map<Integer, int[]> children`** maps each node id to `{leftChildId, rightChildId}`. Node ids can be as large as `1,000,000,000`, so they can't be array indexes. Records also come in any order, so we load all of them into this map first. After that, any node's children are found in `O(1)`.

- **`List<Integer> order`** holds the BFS order. It also works as the BFS queue: index `i` moves forward while new children are added at the end. So no separate queue is needed.

- **`Map<Integer, Long> pick` and `skip`** store the two counts of every finished node. A parent reads its children's counts from these maps.

The `value` part of each record is ignored, because values don't decide whether a subset is valid.


### Code

```java
import java.util.*;

public class BinaryTreeSubsetCounter {

    long MOD = 1_000_000_007L;

    public BinaryTreeSubsetCounter() {
    }

    /**
     * Counts subsets of nodes where no parent and child are both picked.
     * Runs in O(n) time without recursion, so even very deep trees are safe.
     */
    public int countValidSubsets(List<String> nodes, int rootId) {
        // Step 1: node id -> {leftChildId, rightChildId}
        // The value field is skipped because it does not affect the answer.
        Map<Integer, int[]> children = new HashMap<>();
        for (String record : nodes) {
            String[] parts = record.split(",");
            int nodeId = Integer.parseInt(parts[0].trim());
            int leftId = Integer.parseInt(parts[2].trim());
            int rightId = Integer.parseInt(parts[3].trim());
            children.put(nodeId, new int[]{leftId, rightId});
        }

        // Step 2: BFS order, every parent appears before its children.
        // The list itself works as the queue: index i is the front.
        List<Integer> order = new ArrayList<>();
        order.add(rootId);
        for (int i = 0; i < order.size(); i++) {
            for (int child : children.get(order.get(i))) {
                if (child != -1) {
                    order.add(child);
                }
            }
        }

        // Step 3: walk the order backwards, so children finish before parents.
        // pick = valid subsets of this subtree where the node is picked
        // skip = valid subsets of this subtree where the node is skipped
        Map<Integer, Long> pick = new HashMap<>();
        Map<Integer, Long> skip = new HashMap<>();

        for (int i = order.size() - 1; i >= 0; i--) {
            int nodeId = order.get(i);

            // start at 1: a leaf has exactly one way for each case
            long pickCount = 1;
            long skipCount = 1;

            for (int child : children.get(nodeId)) {
                if (child == -1) {
                    continue;
                }
                // node picked: the child must be skipped
                pickCount = pickCount * skip.get(child) % MOD;
                // node skipped: the child can be picked or skipped
                skipCount = skipCount * ((pick.get(child) + skip.get(child)) % MOD) % MOD;
            }

            pick.put(nodeId, pickCount);
            skip.put(nodeId, skipCount);
        }

        return (int) ((pick.get(rootId) + skip.get(rootId)) % MOD);
    }
}
```


## Complexity

- **Time:** `O(n)`. Each record is parsed once, and each node is added to `order` once and processed once.
- **Space:** `O(n)` for the maps and the list.