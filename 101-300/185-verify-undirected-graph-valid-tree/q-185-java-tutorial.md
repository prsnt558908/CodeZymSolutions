# Verify Undirected Graph Valid Tree in Java

#### Problem Statement
[https://codezym.com/question/185-verify-undirected-graph-valid-tree](https://codezym.com/question/185-verify-undirected-graph-valid-tree)


## Core Idea

We are given `n` nodes and a list of undirected connections like `"0,1"`. We need to tell if they form a valid tree: all nodes connected, and no cycle.

The trick that makes this easy is **counting**. A tree with `n` nodes always has exactly `n - 1` connections. Once the count is right, checking just one of the two rules is enough.

We will start with a brute force that searches the graph before adding each connection. Then we will improve it in two ways:

- **BFS:** after the count check, one search tells us if all nodes are connected.
- **Union Find:** it remembers which nodes are already joined, so each cycle check is almost instant.


## Brute Force: Search Before Adding Each Connection

The most direct idea is to follow the two rules as they are written.

Add the connections one by one. Before adding `a,b`, ask: can `a` already reach `b`?

If yes, `a` and `b` are already joined by some path. The new connection would close that path into a loop, which is a cycle.

At the end, one more search from node `0` tells us if all `n` nodes are connected.

```java
// neighbors.get(i) holds the nodes connected to node i, for the connections added so far
for (String connection : connections) {
    String[] parts = connection.split(",");
    int a = Integer.parseInt(parts[0].trim());
    int b = Integer.parseInt(parts[1].trim());

    // A full DFS, just to answer one yes or no question
    if (canReach(neighbors, a, b)) {
        return false; // a can already reach b, so this connection closes a cycle
    }
    neighbors.get(a).add(b);
    neighbors.get(b).add(a);
}

// One last DFS from node 0: did it reach all n nodes?
return countReachable(neighbors, 0) == n;
```

`canReach` and `countReachable` are plain DFS helpers.

**Problems with this approach:**

- Every connection starts a brand new search. Each search forgets what the earlier ones found.
- A search can visit up to `n` nodes. With `E` connections, the time is `O(E × n)`.

Let's remove this repeated work.


## Key Observation: A Tree Has Exactly n - 1 Connections

Imagine every node starts as its own small group. So we begin with `n` groups.

A connection between two **different** groups merges them. The number of groups drops by one.

A connection inside the **same** group merges nothing. Its two nodes were already connected, so it closes a cycle.

To go from `n` groups down to `1`, we need exactly `n - 1` merges. That gives three cases:

- **Fewer than `n - 1` connections:** even if every one is a merge, we end with more than one group. Some nodes are not connected.
- **More than `n - 1` connections:** only `n - 1` of them can be merges, so at least one closes a cycle.
- **Exactly `n - 1` connections:** if there is no cycle, all of them are merges, so we end with one group. And if we end with one group, all of them were merges, so there is no cycle. Checking one rule is enough.

So we first return `false` if the count is not `n - 1`. Then we check just one rule.

The count alone is not enough though. With `n = 4` and `["0,1", "1,2", "2,0"]`, the count is 3, which looks right. But `0`, `1`, `2` form a cycle and node `3` is left alone.


## Solution 1: Count + BFS

After the count check, we only need to confirm that all nodes are connected.

We run one BFS from node `0` and count the nodes we reach. If we reach all `n` nodes, it is a valid tree.

The count check also protects us from huge inputs. The list can have millions of connections, but anything other than `n - 1` is rejected right away, before we build anything.

**Why these data structures?**

- **`neighbors` (adjacency list):** for each node, a list of the nodes it is directly connected to. BFS keeps asking "who are the neighbors of this node?", and this answers it in one lookup.
- **`queue`:** holds nodes we have found but not explored yet. It lets BFS spread out from node `0` one step at a time.
- **`visited` array:** makes sure each node is counted only once.

### Java Code

```java
import java.util.*;

public class VerifyUndirectedGraphValidTree {

    public VerifyUndirectedGraphValidTree() {
    }

    /**
     * Returns true if the connections form a valid tree.
     * Step 1: a tree with n nodes must have exactly n - 1 connections.
     * Step 2: with n - 1 connections, it is a tree if all nodes are connected.
     */
    public boolean validTree(int n, List<String> connections) {
        // Step 1: the wrong count can never be a tree
        if (connections.size() != n - 1) {
            return false;
        }

        // neighbors.get(i) holds every node directly connected to node i
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            neighbors.add(new ArrayList<>());
        }
        for (String connection : connections) {
            String[] parts = connection.split(",");
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            // Undirected connection, so store it in both directions
            neighbors.get(a).add(b);
            neighbors.get(b).add(a);
        }

        // Step 2: BFS from node 0 and count how many nodes we reach
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new ArrayDeque<>();
        visited[0] = true;
        queue.add(0);
        int visitedCount = 1;

        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : neighbors.get(node)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    visitedCount++;
                    queue.add(neighbor);
                }
            }
        }

        // n - 1 connections and all n nodes reached means no cycle, so it is a tree
        return visitedCount == n;
    }
}
```

### Complexity

- **Time:** `O(n)`. After the count check there are only `n - 1` connections, so building the list and running BFS both take `O(n)`.
- **Space:** `O(n)` for the adjacency list, the queue and the `visited` array.


## Solution 2: Count + Union Find

Union Find fixes the slow part of the brute force. It answers "are `a` and `b` already connected?" almost instantly, without searching the graph.

It keeps track of the groups from the key observation:

- Every group has one leader node, called its **root**.
- `parent[i]` points from node `i` toward the root of its group. A root is its own parent.
- `find(x)` follows the parent links from `x` until it reaches the root.
- `union(a, b)` finds both roots. If they are the same, `a` and `b` are already in one group, so it returns `false`. Otherwise it points one root to the other, which merges the two groups.

For each connection we call `union(a, b)`. If it returns `false`, this connection closes a cycle, so the answer is `false`.

If we get through all `n - 1` connections, every one was a merge. So there is no cycle, and by the key observation all nodes are connected.

**Path compression:** while `find` walks up, it points each node it visits to its grandparent. This keeps paths short, so later `find` calls are faster.

**Why a `parent` array?** Nodes are numbered `0` to `n - 1`, so a plain array is all we need. Each node stores one link, and following the links always leads to its group's root.

Union Find is also handy when connections arrive one at a time, because it never needs the full graph.

### Java Code

```java
import java.util.*;

public class VerifyUndirectedGraphValidTree {

    // parent[i] is the parent of node i.
    // A node that is its own parent is the root of its group.
    int[] parent;

    public VerifyUndirectedGraphValidTree() {
    }

    /**
     * Returns true if the connections form a valid tree.
     * With exactly n - 1 connections, it is a tree if no connection closes a cycle.
     */
    public boolean validTree(int n, List<String> connections) {
        // A tree with n nodes must have exactly n - 1 connections
        if (connections.size() != n - 1) {
            return false;
        }

        // At the start, every node is a group of its own
        parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        for (String connection : connections) {
            String[] parts = connection.split(",");
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            // a and b already in the same group means this connection closes a cycle
            if (!union(a, b)) {
                return false;
            }
        }

        // All n - 1 connections were merges, so every node is in one group
        return true;
    }

    /** Returns the root of the group that node belongs to. */
    int find(int node) {
        while (parent[node] != node) {
            // Path compression: point the node to its grandparent to shorten the path
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }

    /**
     * Merges the groups of a and b.
     * Returns false if a and b were already in the same group.
     */
    boolean union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        if (rootA == rootB) {
            return false;
        }
        parent[rootA] = rootB;
        return true;
    }
}
```

### Complexity

- **Time:** `O(n log n)` in the worst case, and close to `O(n)` in practice. Each of the `n - 1` connections does two `find` calls, and path compression keeps them short.
- **Space:** `O(n)` for the `parent` array.