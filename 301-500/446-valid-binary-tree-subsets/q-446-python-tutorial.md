# Valid Binary Tree Subsets in Python

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

```python
# edges holds (parent_index, child_index) for every parent-child pair
count = 0
for mask in range(1 << n):
    valid = True
    for parent, child in edges:
        parent_picked = ((mask >> parent) & 1) == 1
        child_picked = ((mask >> child) & 1) == 1
        if parent_picked and child_picked:
            valid = False
            break
    if valid:
        count += 1
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


These counts grow very fast. Python integers never overflow, but huge numbers make every step slow. So we still take `% MOD` after every multiplication to keep the numbers small.

Recursion fits this idea naturally, because a node needs its children's answers first:

```python
def solve(self, node_id):
    """Returns (pick, skip) for the subtree of node_id."""
    pick, skip = 1, 1
    for child in self.children[node_id]:
        if child == -1:
            continue
        child_pick, child_skip = self.solve(child)
        pick = pick * child_skip % self.MOD                 # node picked: child must be skipped
        skip = skip * (child_pick + child_skip) % self.MOD  # node skipped: child is free
    return pick, skip
```

**Why it can fail:** calls go as deep as the tree is tall. A tree can be one long chain of `200,000` nodes, which means `200,000` nested calls.

Python allows only about `1,000` nested calls by default, so the program crashes with `RecursionError`.


## Solution 3: Tree DP without Recursion (Final Solution)

Recursion was giving us only one thing: every child is finished before its parent. We can get the same order with a plain list.

1. Walk the tree level by level (BFS) starting from the root, and add every node to a list called `order`. In this list, a parent always comes before its children.
2. Go through `order` from the end to the start. Now every child is handled before its parent.
3. Apply the same `pick` and `skip` formulas as before.

Same math, no recursion.


### Important Data Structures

- **`children` (dict)** maps each node id to `[leftChildId, rightChildId]`. Node ids can be as large as `1,000,000,000`, so they can't be list indexes. Records also come in any order, so we load all of them into this dict first. After that, any node's children are found in `O(1)`.

- **`order` (list)** holds the BFS order. It also works as the BFS queue: index `i` moves forward while new children are added at the end. So no separate queue is needed.

- **`pick` and `skip` (dicts)** store the two counts of every finished node. A parent reads its children's counts from these dicts.

The `value` part of each record is ignored, because values don't decide whether a subset is valid.


### Code

```python
from typing import List


class BinaryTreeSubsetCounter:

    def __init__(self):
        self.MOD = 1_000_000_007

    def countValidSubsets(self, nodes: List[str], rootId: int) -> int:
        """
        Counts subsets of nodes where no parent and child are both picked.
        Runs in O(n) time without recursion, so even very deep trees are safe.
        """
        # Step 1: node id -> [leftChildId, rightChildId]
        # The value field is skipped because it does not affect the answer.
        children = {}
        for record in nodes:
            parts = record.split(",")
            node_id = int(parts[0])
            left_id = int(parts[2])
            right_id = int(parts[3])
            children[node_id] = [left_id, right_id]

        # Step 2: BFS order, every parent appears before its children.
        # The list itself works as the queue: index i is the front.
        order = [rootId]
        i = 0
        while i < len(order):
            for child in children[order[i]]:
                if child != -1:
                    order.append(child)
            i += 1

        # Step 3: walk the order backwards, so children finish before parents.
        # pick = valid subsets of this subtree where the node is picked
        # skip = valid subsets of this subtree where the node is skipped
        pick = {}
        skip = {}

        for node_id in reversed(order):
            # start at 1: a leaf has exactly one way for each case
            pick_count = 1
            skip_count = 1

            for child in children[node_id]:
                if child == -1:
                    continue
                # node picked: the child must be skipped
                pick_count = pick_count * skip[child] % self.MOD
                # node skipped: the child can be picked or skipped
                skip_count = skip_count * (pick[child] + skip[child]) % self.MOD

            pick[node_id] = pick_count
            skip[node_id] = skip_count

        return (pick[rootId] + skip[rootId]) % self.MOD
```


## Complexity

- **Time:** `O(n)`. Each record is parsed once, and each node is added to `order` once and processed once.
- **Space:** `O(n)` for the dicts and the list.