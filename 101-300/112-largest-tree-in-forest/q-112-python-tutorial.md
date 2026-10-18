# Largest Tree in a Forest in Python

#### Problem Statement
[https://codezym.com/question/112-largest-tree-in-forest](https://codezym.com/question/112-largest-tree-in-forest)

---

## Core Idea

A forest is just a group of separate trees. We get a list of `child,parent` pairs and we must find the tree with the most nodes.


The whole solution rests on one simple fact: **a root is a node that never appears as a child.**


Once we know the roots, we only need to count how many nodes sit below each root. We will start with a simple idea where every node climbs up to find its root. Then we will flip the direction and walk down from each root using BFS, so every node is visited only once.

---

## Understanding the Problem

Each string like `"7,6"` means node `7` is a child of node `6`.


Take Example 2: `"1,4", "2,4", "3,5", "6,5", "7,6"`

```
    4          5
   / \        / \
  1   2      3   6
                 |
                 7
```

Tree rooted at `4` has 3 nodes. Tree rooted at `5` has 4 nodes. So the answer is `5`.


Two small things to remember:

- **Tie rule:** if two trees have the same size, return the higher root ID.
- **Negative IDs:** IDs can be negative, so "higher" is normal number comparison. For example `-1` is higher than `-3`.

---

## Solution 1: Brute Force (Climb Up to the Root)

### Idea

Every node belongs to exactly one tree. If we start at any node and keep moving to its parent, we will finally reach the root of its tree.


So for every node we do this:

1. Keep climbing to the parent until there is no parent left.
2. The node where we stop is the root.
3. Add 1 to that root's count.


At the end, we pick the root with the biggest count.

### Important Data Structures

- **`parent_of` (dict)** : maps child to parent. It lets us jump one step up in O(1). We need this because climbing up is the main operation here.
- **`all_nodes` (set)** : stores every node exactly once. A parent with many children appears in many relations, and the set makes sure we still count it only once.
- **`tree_size` (dict)** : maps root to the number of nodes in its tree.

### Code

```python
class LargestTreeInForest:
    def __init__(self):
        pass

    def largestTreeRoot(self, relations):
        parent_of = {}      # child -> parent
        all_nodes = set()   # every distinct node we have seen

        for relation in relations:
            child_str, parent_str = relation.split(",")
            child = int(child_str.strip())
            parent = int(parent_str.strip())
            parent_of[child] = parent
            all_nodes.add(child)
            all_nodes.add(parent)

        tree_size = {}  # root -> number of nodes in its tree

        for node in all_nodes:
            # keep climbing up until we reach a node with no parent
            current = node
            while current in parent_of:
                current = parent_of[current]
            tree_size[current] = tree_size.get(current, 0) + 1

        return self.pick_best_root(tree_size)

    def pick_best_root(self, tree_size):
        """Largest tree wins. On a tie, the higher root id wins."""
        best_root = 0
        best_size = -1
        for root, size in tree_size.items():
            if size > best_size or (size == best_size and root > best_root):
                best_size = size
                best_root = root
        return best_root
```

### Complexity

- **Time:** O(N × H), where `N` is the number of nodes and `H` is the height of the tallest tree. In the worst case this becomes O(N²).
- **Space:** O(N) for the dicts and the set.

### What Is Wrong With This Approach?

Nodes in the same tree climb the **same path again and again.**


Think about a single long chain: `1,0`, `2,1`, `3,2` ... `100000,99999`.


Node `100000` climbs 100000 steps. Node `99999` climbs 99999 steps. And so on. That adds up to about 5 billion steps, which is far too slow.


The fix is to stop walking up from every node. Let us walk down from the roots instead.

---

## Solution 2: Optimized (Walk Down From Each Root Using BFS)

### Idea

Instead of every node climbing up, we start at each root and walk **down** through its children.


Trees in a forest never share a node. So when we walk down from every root, each node gets visited **exactly once**. No repeated work.


Steps:

1. Build a dict from each parent to its list of children.
2. Put every child into a set.
3. Any parent that is not in the child set is a root.
4. For each root, run BFS and count the nodes.
5. Keep the best root, using the tie rule.

### Why Do We Only Check Parents for Roots?

A root has no parent, so it never shows up on the left side of a pair.


But every node shows up in at least one pair. So a root must show up on the right side, as a parent. That means looping over parents is enough to find every root.

### Why BFS and Not Recursion?

A tree can be one long chain of 100000 nodes.


Python stops recursion at about 1000 calls by default. A recursive DFS on a deep chain would crash with `RecursionError`. BFS uses a queue instead of the call stack, so depth does not matter.

### Important Data Structures

- **`children_of` (dict of lists)** : maps a parent to all its direct children. This gives us the "downward" view of the tree, which is exactly what BFS needs to move from a node to its children.
- **`child_nodes` (set)** : gives an O(1) check for "has this node ever been a child?". If the answer is no, the node is a root.
- **`parent_nodes` (set)** : the root candidates. A parent with 5 children appears 5 times in the input, and the set makes sure we check it only once.
- **`queue` (deque)** : holds the nodes waiting to be visited during BFS. We use `deque` because removing from the front is O(1). Doing `list.pop(0)` would be O(N) each time.

### Dry Run (Example 2)

Input: `"1,4", "2,4", "3,5", "6,5", "7,6"`


After reading all pairs:

| Structure | Value |
|---|---|
| `children_of` | `4 -> [1, 2]`, `5 -> [3, 6]`, `6 -> [7]` |
| `child_nodes` | `{1, 2, 3, 6, 7}` |
| `parent_nodes` | `{4, 5, 6}` |


Finding roots: `4` and `5` are roots. `6` is skipped because it is also a child.


| Root | BFS visits | Size |
|---|---|---|
| `4` | `4, 1, 2` | 3 |
| `5` | `5, 3, 6, 7` | 4 |


The biggest tree has size 4, so the answer is `5`.

### Code

```python
from collections import deque


class LargestTreeInForest:
    def __init__(self):
        pass

    def largestTreeRoot(self, relations):
        children_of = {}      # parent -> list of its direct children
        child_nodes = set()   # nodes that appear as a child at least once
        parent_nodes = set()  # nodes that appear as a parent at least once

        for relation in relations:
            child_str, parent_str = relation.split(",")
            child = int(child_str.strip())
            parent = int(parent_str.strip())

            children_of.setdefault(parent, []).append(child)
            child_nodes.add(child)
            parent_nodes.add(parent)

        best_root = 0
        best_size = -1

        # A root is a parent that is never a child.
        for root in parent_nodes:
            if root in child_nodes:
                continue

            size = self.count_nodes(root, children_of)

            # Bigger tree wins. On a tie, the higher root id wins.
            if size > best_size or (size == best_size and root > best_root):
                best_size = size
                best_root = root

        return best_root

    def count_nodes(self, root, children_of):
        """Counts all nodes under root using BFS.

        A queue is used instead of recursion, so a very deep tree
        (like a single chain of 100000 nodes) cannot hit the recursion limit.
        """
        queue = deque([root])
        count = 0

        while queue:
            node = queue.popleft()
            count += 1
            queue.extend(children_of.get(node, []))

        return count
```

### Complexity

- **Time:** O(N). We read each pair once, and across all BFS runs each node enters the queue exactly once.
- **Space:** O(N) for the dict, the two sets and the queue.

---

## Comparing Both Solutions

| Approach | Time | Space | Handles a 100000 node chain? |
|---|---|---|---|
| Brute Force (climb up) | O(N × H), worst O(N²) | O(N) | No, too slow |
| BFS from roots (walk down) | O(N) | O(N) | Yes |

---

## Key Takeaways

- A root is a node that never appears as a child.
- Walking up from every node repeats the same work. Walking down from each root visits every node only once.
- Use BFS with a queue for trees that can be very deep, so the program never hits the recursion limit.
- Always apply the tie rule: when sizes are equal, the higher root ID wins, and IDs can be negative.