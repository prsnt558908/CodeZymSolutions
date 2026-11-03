# Alternating Color Binary Tree Nodes in Python

#### Problem Statement
[https://codezym.com/question/411-alternating-color-binary-tree-nodes](https://codezym.com/question/411-alternating-color-binary-tree-nodes)


## Core Idea

We get a tree where every node is `B` or `W`. We must find every node that can be the root, so that no node has more than two children, every level has one color, and the color flips from one level to the next.

The simple way is to try every node as the root and check the whole tree. That is too slow for 200,000 nodes.

The key insight is that we never need to pick a root at all:

1. The color rule only means that **every edge must join a `B` node and a `W` node**. This is true or false for the whole tree, whatever the root is.
2. The number of children of a node depends only on its **degree** (how many edges touch it) and on whether it is the root.

So the main method only needs one pass over the edges and one pass over the nodes. The follow-up adds one BFS: a tree has only two possible good colorings, so we count the changes for both and pick the smaller.


## Solution 1: Brute Force (Try Every Root)

Pick a node as the root and walk the tree level by level with BFS. On every level we check:

- each node has the expected color, and the expected color flips for the next level
- each node has at most 2 children (a child is any neighbor we have not visited yet)

```python
# Try every node as the root
for root in range(nodeCount):
    if self.isValidRoot(root, graph, colors):
        validRoots.append(root)
```

```python
def isValidRoot(self, root: int, graph: List[List[int]], colors: str) -> bool:
    visited = [False] * len(graph)
    visited[root] = True
    level = [root]
    expectedColor = colors[root]

    while level:
        nextLevel = []
        for node in level:
            if colors[node] != expectedColor:
                return False
            children = 0
            for neighbor in graph[node]:
                if not visited[neighbor]:
                    visited[neighbor] = True
                    nextLevel.append(neighbor)
                    children += 1
            if children > 2:
                return False
        # The next level must have the other color
        expectedColor = 'W' if expectedColor == 'B' else 'B'
        level = nextLevel
    return True
```

The follow-up can be brute forced in the same way. For every root that passes the children check, try both colors for the root and count the nodes that do not match their level.

**The problem:** one check visits all `n` nodes, and we repeat it for all `n` roots. That is `O(n²)`, around 40 billion steps for 200,000 nodes.

Most of this work is repeated. Changing the root does not change the tree, it only flips the direction of some edges. So let us find out what really depends on the root.


## Solution 2: Optimal O(n) Solution

### Observation 1: The color rule does not depend on the root

In a rooted tree, every edge joins a parent and its child. They are on adjacent levels, and adjacent levels must have different colors. So **every edge must join a `B` node and a `W` node**.

The reverse is also true. If every edge joins two different colors, the color flips at every step down from the root. So all nodes on the same level get the same color.

This check never mentions the root. The colors are either fine for every root, or fine for none.


### Observation 2: Count children using the degree

The **degree** of a node is the number of edges touching it.

- The **root** has no parent, so all its neighbors are children: `children = degree`
- **Any other node** has exactly one parent: `children = degree - 1`

A binary tree allows at most 2 children. So the root needs degree **at most 2**, and every other node needs degree **at most 3**.

If some node has degree **4 or more**, it gets at least 3 children whoever the root is. Then no root works.

Here is Example 1:

```
0(B) --- 1(W) --- 3(B) --- 4(W)
          |        |
         2(B)     5(W)
```

Every edge joins `B` and `W`. Nodes `1` and `3` have degree 3. They are fine as normal nodes with 2 children each, but as the root they would have 3 children. All other nodes have degree 1, so the answer is `[0, 2, 4, 5]`.


### Putting It Together: findValidRoots

1. Go over every edge. If both ends have the same color, return an empty list. Otherwise add 1 to the degree of both ends.
2. If any node has degree 4 or more, return an empty list.
3. Every node with degree at most 2 is a valid root. Looping from `0` to `nodeCount - 1` already gives them in increasing order.

We don't even need to store the neighbors here. A `degree` list is enough, because we only care about *how many* neighbors a node has, not *which* ones.


### Follow-up: minimumColorChanges

Changing colors can never fix the shape. If any node has degree 4 or more, return `-1`.

Otherwise a valid root always exists, because a leaf has degree at most 1. Now we only need the cheapest way to make every edge join two different colors.

Run BFS from node `0` to find the **depth** of each node (its distance from node `0`). Since the color flips across every edge:

- all nodes at an even depth must share one color
- all nodes at an odd depth must have the other color

So the whole tree has only **two** good colorings:

- **Pattern A:** even depth is `B`, odd depth is `W`
- **Pattern B:** even depth is `W`, odd depth is `B`

Count the nodes that differ from Pattern A. Pattern B is the exact opposite, so every node differs from exactly one of the two patterns. That gives `changesForPatternB = nodeCount - changesForPatternA`, and the answer is the smaller one.

Starting BFS from node `0` is fine even if node `0` cannot be the root. By Observation 1 the color rule is the same for every root, so node `0` is only used to split the nodes into an "even" group and an "odd" group.

For Example 2 (`0 - 1 - 2 - 3 - 4` with colors `BBWBW`), Pattern A is `BWBWB` and needs 4 changes. Pattern B is `WBWBW` and needs `5 - 4 = 1` change. So the answer is `1`.


### Important Data Structures

- `degree`: a list with the number of edges of each node. This is all we need to count children.
- `graph`: a list of neighbor lists. Only the follow-up needs it, for BFS.
- `depth`: distance of each node from node `0`. It starts at `-1`, so it also tells BFS which nodes are already visited.
- `deque`: the BFS queue. Taking an item from the front of a `deque` is fast, while `list.pop(0)` is slow.

We use BFS with a queue instead of recursive DFS. The tree can be one long path of 200,000 nodes, which is far deeper than the default recursion limit of 1000.


### Python Code

```python
from collections import deque
from typing import List


class AlternatingColorBinaryTree:

    def __init__(self):
        pass

    def findValidRoots(self, nodeCount: int, edges: List[str], colors: str) -> List[int]:
        """Returns all nodes that can be the root, in increasing order."""
        degree = [0] * nodeCount

        for edge in edges:
            first, second = self.parseEdge(edge)

            # Color rule: every edge must join a 'B' node and a 'W' node
            if colors[first] == colors[second]:
                return []
            degree[first] += 1
            degree[second] += 1

        # A node with 4 or more edges has at least 3 children for every root
        if self.hasNodeWithTooManyEdges(degree):
            return []

        # Every edge of the root leads to a child, so the root needs degree <= 2
        validRoots = []
        for node in range(nodeCount):
            if degree[node] <= 2:
                validRoots.append(node)
        return validRoots

    def minimumColorChanges(self, nodeCount: int, edges: List[str], colors: str) -> int:
        """Returns the fewest color changes needed so that at least one valid root exists.
        Returns -1 if no root can form a binary tree."""
        graph = [[] for _ in range(nodeCount)]
        degree = [0] * nodeCount

        for edge in edges:
            first, second = self.parseEdge(edge)
            graph[first].append(second)
            graph[second].append(first)
            degree[first] += 1
            degree[second] += 1

        # Changing colors cannot fix the shape of the tree
        if self.hasNodeWithTooManyEdges(degree):
            return -1

        depth = self.findDepths(graph, nodeCount)

        # Pattern A: even depth is 'B', odd depth is 'W'
        changesForPatternA = 0
        for node in range(nodeCount):
            expectedColor = 'B' if depth[node] % 2 == 0 else 'W'
            if colors[node] != expectedColor:
                changesForPatternA += 1

        # Pattern B is the exact opposite, so every node mismatches exactly one pattern
        changesForPatternB = nodeCount - changesForPatternA
        return min(changesForPatternA, changesForPatternB)

    def parseEdge(self, edge: str) -> List[int]:
        """Converts "firstNode,secondNode" into [firstNode, secondNode]."""
        parts = edge.split(",")
        return [int(parts[0]), int(parts[1])]

    def hasNodeWithTooManyEdges(self, degree: List[int]) -> bool:
        """True if some node has 4 or more edges. Such a node always has 3 or more children."""
        for edgeCount in degree:
            if edgeCount >= 4:
                return True
        return False

    def findDepths(self, graph: List[List[int]], nodeCount: int) -> List[int]:
        """BFS from node 0. depth[node] is the number of edges between node 0 and node."""
        depth = [-1] * nodeCount
        depth[0] = 0

        queue = deque([0])
        while queue:
            node = queue.popleft()
            for neighbor in graph[node]:
                if depth[neighbor] == -1:
                    depth[neighbor] = depth[node] + 1
                    queue.append(neighbor)
        return depth
```


### Complexity

- **Time:** `O(n)`. Each edge is read once, and BFS visits each node once.
- **Space:** `O(n)` for the degree list, the graph and the depth list.