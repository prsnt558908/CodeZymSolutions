# Trim Tree To Complete Binary Tree in Python

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

First we store the tree in two dicts, `leftChild` and `rightChild` (node id to child id). Node ids go up to `10^9`, so a list indexed by id would be far too big. A dict gives us any child in `O(1)`.

The best root is not always the original root. In Example 3, node `10` can keep only itself, but node `20` keeps 3 nodes. So we try every node as the root.

For each node we run a BFS. A queue (`deque`) gives us nodes level by level, left to right. When a child is missing, we still add it to the queue as `-1` (real ids are never negative). The first `-1` that comes out of the queue is the first gap, so we stop there.

```python
# Size of the largest complete tree rooted at 'root'.
def completeTreeSize(self, root: int) -> int:
    queue = deque([root])
    count = 0
    while True:
        node = queue.popleft()
        if node == -1:
            return count  # first gap, stop here
        count += 1
        # add both children, -1 marks a missing child
        queue.append(self.leftChild.get(node, -1))
        queue.append(self.rightChild.get(node, -1))

# Try every node from the input as the root. Keep the biggest, smaller id on a tie.
bestRoot, bestSize = -1, 0
for node in allNodeIds:
    size = self.completeTreeSize(node)
    if size > bestSize or (size == bestSize and node < bestRoot):
        bestSize = size
        bestRoot = node
```

**What is the problem?**

Every node starts a fresh BFS, so the same nodes get walked over again and again.

A BFS can only go `d` levels down if all the levels above are full, which needs at least `2^d` nodes. With `10^5` nodes, `d` is at most 16. So a node is visited by its own BFS and by at most 16 ancestors, which gives `O(n log n)` time.

This fits the limits, but the repeated walks are not needed. A node's answer can be built from its children's answers, so every node can be processed just once.


## Approach 2: One Bottom-Up Pass (Optimal)

For every node `x` we store two numbers, each in its own dict:

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

To use the children's answers, we must handle children first. Recursion would do that, but the tree can be one long chain of `10^5` nodes, and Python's default recursion limit is only about 1000.

A simple trick: do one normal BFS from the original root. In BFS order every parent comes before its children. So walking that list **backwards** always handles children before their parents.

### Step 4: Build the output

- Pick the node with the biggest `completeSize`, smaller id on a tie.
- Run a normal BFS from it (no `-1` markers needed) and keep the first `completeSize` nodes. The chosen tree has no gaps, so these are exactly its nodes.
- Split them into levels of size `1, 2, 4, 8 ...`. Only the last level can be shorter.
- Put the kept nodes in a `set`, so checking "is this node kept?" takes `O(1)`. Every other node goes to the trash, which we then sort.

Everything is computed once in the constructor. The getters return a copy, so every call gives the same result.

### Complexity

- **Time:** `O(n)` to build the dicts and run the bottom-up pass. Sorting the trash adds `O(n log n)`.
- **Space:** `O(n)` for the dicts and lists.

### Python Code

```python
from collections import deque
from typing import List


class CompleteBinaryTreeTrimmer:

    def __init__(self, nodeConfigs: List[str]):
        # Node id -> child id. Ids can be up to 10^9, so dicts work better than lists.
        self.leftChild = {}
        self.rightChild = {}

        # Node id -> number of completely full levels, starting at that node.
        self.fullLevels = {}

        # Node id -> size of the largest complete tree rooted at that node.
        self.completeSize = {}

        # Final answers, computed once in the constructor.
        self.completeTreeLevels = []
        self.trashNodeIds = []

        originalRoot = self.buildTree(nodeConfigs)
        if originalRoot == -1:
            return  # empty tree

        # In BFS order a parent always comes before its children.
        # Walking it backwards gives us children before parents.
        allNodes = self.levelOrder(originalRoot, len(nodeConfigs))
        for node in reversed(allNodes):
            self.computeSizes(node)

        # Biggest complete tree wins, smaller root id breaks a tie.
        bestRoot = originalRoot
        for node in allNodes:
            size = self.completeSize[node]
            bestSize = self.completeSize[bestRoot]
            if size > bestSize or (size == bestSize and node < bestRoot):
                bestRoot = node

        # The chosen tree has no gaps, so its nodes are exactly
        # the first completeSize[bestRoot] nodes of a BFS from bestRoot.
        keptNodes = self.levelOrder(bestRoot, self.completeSize[bestRoot])
        self.buildLevels(keptNodes)
        self.buildTrash(allNodes, keptNodes)

    def getCompleteTreeLevels(self) -> List[str]:
        return list(self.completeTreeLevels)

    def getTrashNodeIds(self) -> List[int]:
        return list(self.trashNodeIds)

    # Fills the child dicts. Returns the original root, or -1 if the tree is empty.
    def buildTree(self, nodeConfigs: List[str]) -> int:
        root = -1
        for config in nodeConfigs:
            parts = config.split(",")
            nodeId = int(parts[0].strip())
            parentId = int(parts[1].strip())
            side = parts[2].strip()

            if side == "ROOT":
                root = nodeId
            elif side == "L":
                self.leftChild[parentId] = nodeId
            else:
                self.rightChild[parentId] = nodeId
        return root

    # Returns up to 'limit' nodes of the subtree of 'root', level by level, left to right.
    def levelOrder(self, root: int, limit: int) -> List[int]:
        order = []
        queue = deque([root])
        while queue and len(order) < limit:
            node = queue.popleft()
            order.append(node)
            if node in self.leftChild:
                queue.append(self.leftChild[node])
            if node in self.rightChild:
                queue.append(self.rightChild[node])
        return order

    # Computes fullLevels and completeSize of a node from its two children.
    def computeSizes(self, node: int) -> None:
        left = self.leftChild.get(node, -1)  # -1 means missing
        right = self.rightChild.get(node, -1)

        # A missing child has 0 full levels and an empty complete tree.
        leftFull = self.fullLevels.get(left, 0)
        rightFull = self.fullLevels.get(right, 0)
        leftSize = self.completeSize.get(left, 0)
        rightSize = self.completeSize.get(right, 0)

        self.fullLevels[node] = 1 + min(leftFull, rightFull)

        if leftFull > rightFull:
            # Last level fills the whole left side and stops inside the right side.
            self.completeSize[node] = 1 + self.perfectTreeSize(rightFull + 1) + rightSize
        else:
            # Last level stops inside the left side.
            self.completeSize[node] = 1 + leftSize + self.perfectTreeSize(leftFull)

    # A perfect (completely full) tree with 'levels' levels has 2^levels - 1 nodes.
    def perfectTreeSize(self, levels: int) -> int:
        return 2 ** levels - 1

    # Splits nodes into levels of size 1, 2, 4, 8 ... (the last one may be shorter).
    def buildLevels(self, keptNodes: List[int]) -> None:
        start = 0
        levelSize = 1
        while start < len(keptNodes):
            end = min(start + levelSize, len(keptNodes))
            level = keptNodes[start:end]
            self.completeTreeLevels.append(",".join(str(node) for node in level))
            start = end
            levelSize *= 2

    # Every node that is not kept goes to the trash, sorted by id.
    def buildTrash(self, allNodes: List[int], keptNodes: List[int]) -> None:
        kept = set(keptNodes)
        self.trashNodeIds = sorted(node for node in allNodes if node not in kept)
```