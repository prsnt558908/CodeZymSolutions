# Trim Tree To Complete Binary Tree in Java

#### Problem Statement
[https://codezym.com/question/167-trim-tree-complete-binary-tree](https://codezym.com/question/167-trim-tree-complete-binary-tree)


## Core Idea

We are not allowed to move nodes. So once we pick a root, its complete tree is already decided: read its subtree level by level, left to right, and keep nodes until the first empty spot.

So the whole problem becomes: for every node, count how many nodes it can keep before the first gap. The node that keeps the most becomes the root of our answer. Every node outside that tree goes to the trash.

We first solve it with a simple BFS from every node. Then we improve it to a single bottom-up pass, where every node builds its answer from the answers of its two children.


## Key Observations

### 1. For a fixed root, the biggest complete tree is fixed

Take Example 1:

```
        1
      /   \
     2     3
    / \     \
   4   5     7
```

Reading from node `1` level by level, with `_` for an empty spot:

```
1, 2, 3, 4, 5, _, 7
```

A complete tree never has a gap in its level order. So we keep `1, 2, 3, 4, 5` and stop at the first gap. Node `7` comes after the gap, so it goes to the trash.

### 2. A tie is decided by the root id

For one root, the biggest complete tree is fixed. So if two different trees tie for the biggest size, they must have different roots.

A level-order traversal starts with the root, and all ids are unique. So the two traversals already differ at the very first position.

That means the tie rule is simply: **pick the smaller root id**. In Example 4, roots `50` and `20` both keep 2 nodes, so `20` wins.


## Approach 1: BFS From Every Node

First we store the tree in two maps, `leftChild` and `rightChild` (node id to child id). Node ids go up to `10^9`, so an array indexed by id would be far too big. A map gives us any child in `O(1)`.

The best root is not always the original root. In Example 3, node `10` can keep only itself, but node `20` keeps 3 nodes. So we try every node as the root.

For each node we run a BFS. A queue gives us nodes level by level, left to right. When a child is missing, we still add it to the queue as `-1` (real ids are never negative). The first `-1` that comes out of the queue is the first gap, so we stop there.

```java
// Size of the largest complete tree rooted at 'root'.
int completeTreeSize(int root) {
    Queue<Integer> queue = new ArrayDeque<>();
    queue.add(root);
    int count = 0;
    while (true) {
        int node = queue.poll();
        if (node == -1) return count; // first gap, stop here
        count++;
        // add both children, -1 marks a missing child
        queue.add(leftChild.getOrDefault(node, -1));
        queue.add(rightChild.getOrDefault(node, -1));
    }
}

// Try every node from the input as the root. Keep the biggest, smaller id on a tie.
int bestRoot = -1, bestSize = 0;
for (int node : allNodeIds) {
    int size = completeTreeSize(node);
    if (size > bestSize || (size == bestSize && node < bestRoot)) {
        bestSize = size;
        bestRoot = node;
    }
}
```

**What is the problem?**

Every node starts a fresh BFS, so the same nodes get walked over again and again.

A BFS can only go `d` levels down if all the levels above are full, which needs at least `2^d` nodes. With `10^5` nodes, `d` is at most 16. So a node is visited by its own BFS and by at most 16 ancestors, which gives `O(n log n)` time.

This fits the limits, but the repeated walks are not needed. A node's answer can be built from its children's answers, so every node can be processed just once.


## Approach 2: One Bottom-Up Pass (Optimal)

For every node `x` we store two numbers, each in its own map:

- `fullLevels(x)`: how many levels, starting at `x`, are completely full. A leaf has `1`. A missing node has `0`.
- `completeSize(x)`: the size of the largest complete tree rooted at `x`. A missing node has `0`.

### Step 1: Full levels

The levels below `x` are made of its children's levels. So `x` stays full only as long as both children stay full, and the child with fewer full levels decides.

```
fullLevels(x) = 1 + min(fullLevels(left), fullLevels(right))
```

### Step 2: Complete size

Let `L = fullLevels(left)` and `R = fullLevels(right)`.

A complete tree is some full levels plus one last level that fills from the left. That last level is shared by the left side and the right side. We only need to know on which side it stops.

- If `L > R`, the left side is full deep enough. The last level fills the whole left side and stops inside the right side.
- Otherwise, the left side runs out of full levels first (or at the same time as the right side). Since the last level fills from the left, it stops inside the left side.

The side where it stops gives its own complete tree. The other side gives a perfect (completely full) tree. A perfect tree with `h` levels has `2^h - 1` nodes.

| Case | Left side gives | Right side gives |
|---|---|---|
| `L > R` | perfect tree with `R + 1` levels | `completeSize(right)` |
| `L <= R` | `completeSize(left)` | perfect tree with `L` levels |

Then add `1` for `x` itself.

Why `R + 1` on the left but only `L` on the right? When the tree stops on the right, the left side gets `R` full levels plus its whole part of the last level. When the tree stops on the left, the right side gets no part of the last level, only the `L` full levels.

**Example 1, node `1`:**

- Node `2` has `L = 2` full levels (`2`, then `4, 5`).
- Node `3` has `R = 1` full level (only `3`, its left child is missing).
- `L > R`, so the left gives a perfect tree with 2 levels (`2, 4, 5`), and the right gives `completeSize(3) = 1`.
- `completeSize(1) = 1 + 3 + 1 = 5`

### Step 3: Children before parents

To use the children's answers, we must handle children first. Recursion would do that, but the tree can be one long chain of `10^5` nodes, which can overflow the call stack.

A simple trick: do one normal BFS from the original root. In BFS order every parent comes before its children. So walking that list **backwards** always handles children before their parents.

### Step 4: Build the output

- Pick the node with the biggest `completeSize`, smaller id on a tie.
- Run a normal BFS from it (no `-1` markers needed) and keep the first `completeSize` nodes. The chosen tree has no gaps, so these are exactly its nodes.
- Split them into levels of size `1, 2, 4, 8 ...`. Only the last level can be shorter.
- Put the kept nodes in a `Set`, so checking "is this node kept?" takes `O(1)`. Every other node goes to the trash, which we then sort.

Everything is computed once in the constructor. The getters return a copy, so every call gives the same result.

### Complexity

- **Time:** `O(n)` to build the maps and run the bottom-up pass. Sorting the trash adds `O(n log n)`.
- **Space:** `O(n)` for the maps and lists.

### Java Code

```java
import java.util.*;

public class CompleteBinaryTreeTrimmer {

    // Node id -> child id. Ids can be up to 10^9, so maps work better than arrays.
    Map<Integer, Integer> leftChild = new HashMap<>();
    Map<Integer, Integer> rightChild = new HashMap<>();

    // Node id -> number of completely full levels, starting at that node.
    Map<Integer, Integer> fullLevels = new HashMap<>();

    // Node id -> size of the largest complete tree rooted at that node.
    Map<Integer, Integer> completeSize = new HashMap<>();

    // Final answers, computed once in the constructor.
    List<String> completeTreeLevels = new ArrayList<>();
    List<Integer> trashNodeIds = new ArrayList<>();

    public CompleteBinaryTreeTrimmer(List<String> nodeConfigs) {
        int originalRoot = buildTree(nodeConfigs);
        if (originalRoot == -1) return; // empty tree

        // In BFS order a parent always comes before its children.
        // Walking it backwards gives us children before parents.
        List<Integer> allNodes = levelOrder(originalRoot, nodeConfigs.size());
        for (int i = allNodes.size() - 1; i >= 0; i--) {
            computeSizes(allNodes.get(i));
        }

        // Biggest complete tree wins, smaller root id breaks a tie.
        int bestRoot = originalRoot;
        for (int node : allNodes) {
            int size = completeSize.get(node);
            int bestSize = completeSize.get(bestRoot);
            if (size > bestSize || (size == bestSize && node < bestRoot)) {
                bestRoot = node;
            }
        }

        // The chosen tree has no gaps, so its nodes are exactly
        // the first completeSize(bestRoot) nodes of a BFS from bestRoot.
        List<Integer> keptNodes = levelOrder(bestRoot, completeSize.get(bestRoot));
        buildLevels(keptNodes);
        buildTrash(allNodes, keptNodes);
    }

    public List<String> getCompleteTreeLevels() {
        return new ArrayList<>(completeTreeLevels);
    }

    public List<Integer> getTrashNodeIds() {
        return new ArrayList<>(trashNodeIds);
    }

    // Fills the child maps. Returns the original root, or -1 if the tree is empty.
    int buildTree(List<String> nodeConfigs) {
        int root = -1;
        for (String config : nodeConfigs) {
            String[] parts = config.split(",");
            int nodeId = Integer.parseInt(parts[0].trim());
            int parentId = Integer.parseInt(parts[1].trim());
            String side = parts[2].trim();

            if (side.equals("ROOT")) {
                root = nodeId;
            } else if (side.equals("L")) {
                leftChild.put(parentId, nodeId);
            } else {
                rightChild.put(parentId, nodeId);
            }
        }
        return root;
    }

    // Returns up to 'limit' nodes of the subtree of 'root', level by level, left to right.
    List<Integer> levelOrder(int root, int limit) {
        List<Integer> order = new ArrayList<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && order.size() < limit) {
            int node = queue.poll();
            order.add(node);
            if (leftChild.containsKey(node)) queue.add(leftChild.get(node));
            if (rightChild.containsKey(node)) queue.add(rightChild.get(node));
        }
        return order;
    }

    // Computes fullLevels and completeSize of a node from its two children.
    void computeSizes(int node) {
        int left = leftChild.getOrDefault(node, -1); // -1 means missing
        int right = rightChild.getOrDefault(node, -1);

        // A missing child has 0 full levels and an empty complete tree.
        int leftFull = fullLevels.getOrDefault(left, 0);
        int rightFull = fullLevels.getOrDefault(right, 0);
        int leftSize = completeSize.getOrDefault(left, 0);
        int rightSize = completeSize.getOrDefault(right, 0);

        fullLevels.put(node, 1 + Math.min(leftFull, rightFull));

        if (leftFull > rightFull) {
            // Last level fills the whole left side and stops inside the right side.
            completeSize.put(node, 1 + perfectTreeSize(rightFull + 1) + rightSize);
        } else {
            // Last level stops inside the left side.
            completeSize.put(node, 1 + leftSize + perfectTreeSize(leftFull));
        }
    }

    // A perfect (completely full) tree with 'levels' levels has 2^levels - 1 nodes.
    int perfectTreeSize(int levels) {
        return (1 << levels) - 1;
    }

    // Splits nodes into levels of size 1, 2, 4, 8 ... (the last one may be shorter).
    void buildLevels(List<Integer> keptNodes) {
        int start = 0;
        int levelSize = 1;
        while (start < keptNodes.size()) {
            int end = Math.min(start + levelSize, keptNodes.size());
            StringBuilder level = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) level.append(",");
                level.append(keptNodes.get(i));
            }
            completeTreeLevels.add(level.toString());
            start = end;
            levelSize *= 2;
        }
    }

    // Every node that is not kept goes to the trash, sorted by id.
    void buildTrash(List<Integer> allNodes, List<Integer> keptNodes) {
        Set<Integer> kept = new HashSet<>(keptNodes);
        for (int node : allNodes) {
            if (!kept.contains(node)) trashNodeIds.add(node);
        }
        Collections.sort(trashNodeIds);
    }
}
```