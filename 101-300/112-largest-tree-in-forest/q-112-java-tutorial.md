# Largest Tree in a Forest in Java

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

- **`Map<Integer, Integer> parentOf`** : maps child to parent. It lets us jump one step up in O(1). We need this because climbing up is the main operation here.
- **`Set<Integer> allNodes`** : stores every node exactly once. A parent with many children appears in many relations, and the set makes sure we still count it only once.
- **`Map<Integer, Integer> treeSize`** : maps root to the number of nodes in its tree.

### Code

```java
import java.util.*;

public class LargestTreeInForest {

    public LargestTreeInForest() {
    }

    public int largestTreeRoot(List<String> relations) {
        // child -> parent
        Map<Integer, Integer> parentOf = new HashMap<>();
        // every distinct node we have seen
        Set<Integer> allNodes = new HashSet<>();

        for (String relation : relations) {
            String[] parts = relation.split(",");
            int child = Integer.parseInt(parts[0].trim());
            int parent = Integer.parseInt(parts[1].trim());
            parentOf.put(child, parent);
            allNodes.add(child);
            allNodes.add(parent);
        }

        // root -> number of nodes in its tree
        Map<Integer, Integer> treeSize = new HashMap<>();

        for (int node : allNodes) {
            // keep climbing up until we reach a node with no parent
            int current = node;
            while (parentOf.containsKey(current)) {
                current = parentOf.get(current);
            }
            treeSize.put(current, treeSize.getOrDefault(current, 0) + 1);
        }

        return pickBestRoot(treeSize);
    }

    // Largest tree wins. On a tie, the higher root id wins.
    int pickBestRoot(Map<Integer, Integer> treeSize) {
        int bestRoot = 0;
        int bestSize = -1;
        for (int root : treeSize.keySet()) {
            int size = treeSize.get(root);
            if (size > bestSize || (size == bestSize && root > bestRoot)) {
                bestSize = size;
                bestRoot = root;
            }
        }
        return bestRoot;
    }
}
```

### Complexity

- **Time:** O(N × H), where `N` is the number of nodes and `H` is the height of the tallest tree. In the worst case this becomes O(N²).
- **Space:** O(N) for the maps and the set.

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

1. Build a map from each parent to its list of children.
2. Put every child into a set.
3. Any parent that is not in the child set is a root.
4. For each root, run BFS and count the nodes.
5. Keep the best root, using the tie rule.

### Why Do We Only Check Parents for Roots?

A root has no parent, so it never shows up on the left side of a pair.


But every node shows up in at least one pair. So a root must show up on the right side, as a parent. That means looping over parents is enough to find every root.

### Why BFS and Not Recursion?

A tree can be one long chain of 100000 nodes.


A recursive DFS would go 100000 calls deep and can crash with `StackOverflowError`. BFS uses a queue instead of the call stack, so depth does not matter.

### Important Data Structures

- **`Map<Integer, List<Integer>> childrenOf`** : maps a parent to all its direct children. This gives us the "downward" view of the tree, which is exactly what BFS needs to move from a node to its children.
- **`Set<Integer> childNodes`** : gives an O(1) check for "has this node ever been a child?". If the answer is no, the node is a root.
- **`Set<Integer> parentNodes`** : the root candidates. A parent with 5 children appears 5 times in the input, and the set makes sure we check it only once.
- **`Deque<Integer> queue`** : holds the nodes waiting to be visited during BFS. We use `ArrayDeque` because it is a simple and fast queue.

### Dry Run (Example 2)

Input: `"1,4", "2,4", "3,5", "6,5", "7,6"`


After reading all pairs:

| Structure | Value |
|---|---|
| `childrenOf` | `4 -> [1, 2]`, `5 -> [3, 6]`, `6 -> [7]` |
| `childNodes` | `{1, 2, 3, 6, 7}` |
| `parentNodes` | `{4, 5, 6}` |


Finding roots: `4` and `5` are roots. `6` is skipped because it is also a child.


| Root | BFS visits | Size |
|---|---|---|
| `4` | `4, 1, 2` | 3 |
| `5` | `5, 3, 6, 7` | 4 |


The biggest tree has size 4, so the answer is `5`.

### Code

```java
import java.util.*;

public class LargestTreeInForest {

    public LargestTreeInForest() {
    }

    public int largestTreeRoot(List<String> relations) {
        // parent -> list of its direct children
        Map<Integer, List<Integer>> childrenOf = new HashMap<>();
        // all nodes that appear as a child at least once
        Set<Integer> childNodes = new HashSet<>();
        // all nodes that appear as a parent at least once
        Set<Integer> parentNodes = new HashSet<>();

        for (String relation : relations) {
            String[] parts = relation.split(",");
            int child = Integer.parseInt(parts[0].trim());
            int parent = Integer.parseInt(parts[1].trim());

            childrenOf.putIfAbsent(parent, new ArrayList<>());
            childrenOf.get(parent).add(child);
            childNodes.add(child);
            parentNodes.add(parent);
        }

        int bestRoot = 0;
        int bestSize = -1;

        // A root is a parent that is never a child.
        for (int root : parentNodes) {
            if (childNodes.contains(root)) continue;

            int size = countNodes(root, childrenOf);

            // Bigger tree wins. On a tie, the higher root id wins.
            if (size > bestSize || (size == bestSize && root > bestRoot)) {
                bestSize = size;
                bestRoot = root;
            }
        }
        return bestRoot;
    }

    // Counts all nodes in the tree under root using BFS.
    // We use a queue instead of recursion, so a very deep tree
    // (like a single chain of 100000 nodes) cannot overflow the stack.
    int countNodes(int root, Map<Integer, List<Integer>> childrenOf) {
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;

        while (!queue.isEmpty()) {
            int node = queue.poll();
            count++;
            List<Integer> children = childrenOf.get(node);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return count;
    }
}
```

### Complexity

- **Time:** O(N). We read each pair once, and across all BFS runs each node enters the queue exactly once.
- **Space:** O(N) for the map, the two sets and the queue.

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
- Use BFS with a queue for trees that can be very deep, so the program never runs out of stack space.
- Always apply the tie rule: when sizes are equal, the higher root ID wins, and IDs can be negative.