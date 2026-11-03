# Alternating Color Binary Tree Nodes in Java

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

```java
// Try every node as the root
for (int root = 0; root < nodeCount; root++) {
    if (isValidRoot(root, graph, colors)) {
        validRoots.add(root);
    }
}
```

```java
boolean isValidRoot(int root, List<List<Integer>> graph, String colors) {
    boolean[] visited = new boolean[graph.size()];
    visited[root] = true;
    List<Integer> level = new ArrayList<>();
    level.add(root);
    char expectedColor = colors.charAt(root);

    while (!level.isEmpty()) {
        List<Integer> nextLevel = new ArrayList<>();
        for (int node : level) {
            if (colors.charAt(node) != expectedColor) {
                return false;
            }
            int children = 0;
            for (int neighbor : graph.get(node)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    nextLevel.add(neighbor);
                    children++;
                }
            }
            if (children > 2) {
                return false;
            }
        }
        // The next level must have the other color
        expectedColor = (expectedColor == 'B') ? 'W' : 'B';
        level = nextLevel;
    }
    return true;
}
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

We don't even need to store the neighbors here. An `int[] degree` array is enough, because we only care about *how many* neighbors a node has, not *which* ones.


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

- `int[] degree`: number of edges of each node. This is all we need to count children.
- `List<List<Integer>> graph`: neighbors of each node. Only the follow-up needs it, for BFS.
- `int[] depth`: distance of each node from node `0`. It starts at `-1`, so it also tells BFS which nodes are already visited.

We use BFS with a queue instead of recursive DFS. The tree can be one long path of 200,000 nodes, and deep recursion could overflow the stack.


### Java Code

```java
import java.util.*;

public class AlternatingColorBinaryTree {

    public AlternatingColorBinaryTree() {
    }

    /**
     * Returns all nodes that can be the root, in increasing order.
     * A valid root gives a binary tree whose level colors alternate.
     */
    public List<Integer> findValidRoots(int nodeCount, List<String> edges, String colors) {
        List<Integer> validRoots = new ArrayList<>();
        int[] degree = new int[nodeCount];

        for (String edge : edges) {
            int[] nodes = parseEdge(edge);
            int first = nodes[0];
            int second = nodes[1];

            // Color rule: every edge must join a 'B' node and a 'W' node
            if (colors.charAt(first) == colors.charAt(second)) {
                return validRoots;
            }
            degree[first]++;
            degree[second]++;
        }

        // A node with 4 or more edges has at least 3 children for every root
        if (hasNodeWithTooManyEdges(degree)) {
            return validRoots;
        }

        // Every edge of the root leads to a child, so the root needs degree <= 2
        for (int node = 0; node < nodeCount; node++) {
            if (degree[node] <= 2) {
                validRoots.add(node);
            }
        }
        return validRoots;
    }

    /**
     * Returns the fewest color changes needed so that at least one valid root exists.
     * Returns -1 if no root can form a binary tree.
     */
    public int minimumColorChanges(int nodeCount, List<String> edges, String colors) {
        List<List<Integer>> graph = new ArrayList<>();
        for (int node = 0; node < nodeCount; node++) {
            graph.add(new ArrayList<>());
        }
        int[] degree = new int[nodeCount];

        for (String edge : edges) {
            int[] nodes = parseEdge(edge);
            int first = nodes[0];
            int second = nodes[1];

            graph.get(first).add(second);
            graph.get(second).add(first);
            degree[first]++;
            degree[second]++;
        }

        // Changing colors cannot fix the shape of the tree
        if (hasNodeWithTooManyEdges(degree)) {
            return -1;
        }

        int[] depth = findDepths(graph, nodeCount);

        // Pattern A: even depth is 'B', odd depth is 'W'
        int changesForPatternA = 0;
        for (int node = 0; node < nodeCount; node++) {
            char expectedColor = (depth[node] % 2 == 0) ? 'B' : 'W';
            if (colors.charAt(node) != expectedColor) {
                changesForPatternA++;
            }
        }

        // Pattern B is the exact opposite, so every node mismatches exactly one pattern
        int changesForPatternB = nodeCount - changesForPatternA;
        return Math.min(changesForPatternA, changesForPatternB);
    }

    /** Converts "firstNode,secondNode" into {firstNode, secondNode}. */
    int[] parseEdge(String edge) {
        String[] parts = edge.split(",");
        int firstNode = Integer.parseInt(parts[0].trim());
        int secondNode = Integer.parseInt(parts[1].trim());
        return new int[] { firstNode, secondNode };
    }

    /** True if some node has 4 or more edges. Such a node always has 3 or more children. */
    boolean hasNodeWithTooManyEdges(int[] degree) {
        for (int edgeCount : degree) {
            if (edgeCount >= 4) {
                return true;
            }
        }
        return false;
    }

    /** BFS from node 0. depth[node] is the number of edges between node 0 and node. */
    int[] findDepths(List<List<Integer>> graph, int nodeCount) {
        int[] depth = new int[nodeCount];
        Arrays.fill(depth, -1);
        depth[0] = 0;

        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : graph.get(node)) {
                if (depth[neighbor] == -1) {
                    depth[neighbor] = depth[node] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return depth;
    }
}
```


### Complexity

- **Time:** `O(n)`. Each edge is read once, and BFS visits each node once.
- **Space:** `O(n)` for the degree array, the graph and the depth array.