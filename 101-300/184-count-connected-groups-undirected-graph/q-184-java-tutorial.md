# Count Connected Groups in Undirected Graph in Java

#### Problem Statement
[https://codezym.com/question/184-count-connected-groups-undirected-graph](https://codezym.com/question/184-count-connected-groups-undirected-graph)

---

## Core Idea

Think of every edge as a rope that ties two nodes together. A connected group is a set of nodes that are tied together, either directly or through other nodes.

At the start, every node is alone, so there are `nodeCount` groups. Each edge can glue two groups into one. The answer is the number of groups left after all edges are used.

We will start with a simple brute force, see where it wastes time, and then fix it in two ways: **BFS**, which visits each group exactly once, and **Union-Find**, which makes gluing two groups very cheap.

---

## Reading the Input

Each edge comes as a string like `"2,5"`. We split it at the comma and turn both parts into numbers.

```java
String[] parts = link.split(",");
int u = Integer.parseInt(parts[0].trim());
int v = Integer.parseInt(parts[1].trim());
```

`trim()` is just a safety net in case a string has extra spaces.

Here is Example 2, with `nodeCount = 6` and `links = ["0,3", "3,5", "1,2"]`:

```
0 --- 3 --- 5      1 --- 2      4
```

There are 3 groups: `{0, 3, 5}`, `{1, 2}` and `{4}`. Node `4` has no edges, but it still counts as a group by itself.

---

## Solution 1: Brute Force (Repaint Group Labels)

### Idea

Give every node a group label. At the start, node `i` gets label `i`, so every node is in its own group.

Then, for every edge `u,v`:

- If `u` and `v` already have the same label, they are already in one group. Nothing to do.
- Otherwise, repaint every node that has `v`'s label with `u`'s label. Two groups became one, so the count goes down by 1.

### Code Snippet

```java
int[] label = new int[nodeCount];
for (int i = 0; i < nodeCount; i++) {
    label[i] = i; // every node starts in its own group
}

int groups = nodeCount;
for (String link : links) {
    String[] parts = link.split(",");
    int u = Integer.parseInt(parts[0].trim());
    int v = Integer.parseInt(parts[1].trim());

    int keepLabel = label[u];
    int oldLabel = label[v]; // save it first, the repaint changes label[v]
    if (keepLabel == oldLabel) {
        continue; // already in the same group
    }

    // repaint the whole group of v with the label of u
    for (int i = 0; i < nodeCount; i++) {
        if (label[i] == oldLabel) {
            label[i] = keepLabel;
        }
    }
    groups--; // two groups became one
}
return groups;
```

A quick run with `nodeCount = 3` and `links = ["0,1", "1,2", "0,2"]`:

| Edge | Labels after this edge | Groups |
|------|------------------------|--------|
| start | `[0, 1, 2]` | 3 |
| `0,1` | `[0, 0, 2]` | 2 |
| `1,2` | `[0, 0, 0]` | 1 |
| `0,2` | same label, skip | 1 |

The last edge changes nothing, because `0` and `2` are already connected through `1`. That is why we only count down when the labels are different.

### Why is this slow?

Every merge scans **all** nodes, even the ones that have nothing to do with the current edge.

There can be up to `nodeCount - 1` merges, and each one scans all `nodeCount` labels. That is `O(nodeCount²)` work, about 100 million steps for 10,000 nodes, and most of it is wasted.

We can fix this in two ways:

- Stop merging edge by edge. Find each whole group in one go. (**BFS**)
- Keep merging edge by edge, but make each merge cheap. (**Union-Find**)

---

## Solution 2: BFS (Visit Each Group Once)

### Idea

Pick any node we have not visited yet. No earlier walk reached it, so it belongs to a new group. Count it.

Then walk from it to every node it can reach and mark them all as visited. This one walk covers the whole group.

Move to the next unvisited node and repeat. The number of walks we start is the answer.

We do the walk with **BFS** (Breadth First Search): visit the start node, then its neighbors, then their neighbors, and so on.

### Data structures we need

- **Neighbor list (`List<List<Integer>>`):** `neighbors.get(i)` holds every node that shares an edge with node `i`. Without it, finding the neighbors of one node would mean scanning every edge. Nodes are numbered `0` to `nodeCount - 1`, so a plain list indexed by node is enough, no map needed.
- **`visited` array:** makes sure each node is counted in exactly one group, and stops the walk from going around in circles.
- **Queue:** holds nodes we have found but not explored yet. Using a queue instead of recursion means a long chain of 10,000 nodes cannot overflow the call stack.

Edges are undirected, so each edge is added on both ends: `v` goes into the list of `u`, and `u` goes into the list of `v`.

### Walkthrough (Example 2)

Neighbor list: `0: [3]`, `1: [2]`, `2: [1]`, `3: [0, 5]`, `4: []`, `5: [3]`

| Start node | Already visited? | Walk visits | Groups |
|------------|------------------|-------------|--------|
| 0 | no | 0, 3, 5 | 1 |
| 1 | no | 1, 2 | 2 |
| 2 | yes | skip | 2 |
| 3 | yes | skip | 2 |
| 4 | no | 4 | 3 |
| 5 | yes | skip | 3 |

Answer: `3`

### Java Code

```java
import java.util.*;

public class CountConnectedGroups {

    public CountConnectedGroups() {
    }

    public int countConnectedGroups(int nodeCount, List<String> links) {
        // neighbors.get(i) holds every node that shares an edge with node i
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            neighbors.add(new ArrayList<>());
        }

        for (String link : links) {
            String[] parts = link.split(",");
            int u = Integer.parseInt(parts[0].trim());
            int v = Integer.parseInt(parts[1].trim());
            // undirected edge, so add it on both ends
            neighbors.get(u).add(v);
            neighbors.get(v).add(u);
        }

        boolean[] visited = new boolean[nodeCount];
        int groups = 0;

        for (int start = 0; start < nodeCount; start++) {
            if (!visited[start]) {
                groups++; // not reached by any earlier walk, so a new group
                visitGroup(start, neighbors, visited);
            }
        }
        return groups;
    }

    // BFS from start: marks every node in the same group as visited
    void visitGroup(int start, List<List<Integer>> neighbors, boolean[] visited) {
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start] = true;

        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : neighbors.get(node)) {
                if (!visited[neighbor]) {
                    // mark it now, so it is never added to the queue twice
                    visited[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
    }
}
```

### Complexity

- **Time:** `O(nodeCount + links.size())`. Each node enters the queue once, and each edge is checked twice, once from each end.
- **Space:** `O(nodeCount + links.size())`, mostly for the neighbor list.

---

## Solution 3: Union-Find (Cheap Merging)

### Idea

Union-Find (also called Disjoint Set Union) fixes the slow repaint from Solution 1.

Instead of writing a label on every node, each node only remembers one other node: its **parent**. A node that is its own parent is the **leader** of its group. To find a node's group, we follow parents up until we reach the leader.

Now merging two groups is one step: point one leader at the other leader. No repainting.

The counting stays the same as in Solution 1. Start with `nodeCount` groups and subtract 1 every time an edge connects two different leaders. Nodes with no edges are never touched, so they stay counted as groups of one.

```
union(3, 5): the leader of 3 is 0, and 5 is its own leader

   before          after

   0     5           0
   |                / \
   3               3   5
```

### Keeping the chains short

If we link leaders carelessly, the parent chains can grow into one long line, and finding a leader becomes slow again. Two small tricks stop that:

1. **Union by size:** always hang the smaller group under the leader of the bigger group.
2. **Path compression:** while finding a leader, point every node on the way straight at the leader. Next time it is just one step.

With both tricks, each `find` takes practically constant time. Union by size also keeps every chain under 14 steps for 10,000 nodes, so the recursive `find` is safe.

### Data structures we need

- **`parent` array:** `parent[i]` is the node that `i` points to. A leader points to itself. This replaces the label array from Solution 1.
- **`size` array:** `size[leader]` is the number of nodes in that leader's group. It tells us which group is bigger during union by size.

No neighbor list is needed. Each edge is handled the moment we read it.

### Java Code

```java
import java.util.*;

public class CountConnectedGroups {

    // parent[i] is the node that i points to. A leader points to itself.
    int[] parent;
    // size[leader] is the number of nodes in that leader's group
    int[] size;

    public CountConnectedGroups() {
    }

    public int countConnectedGroups(int nodeCount, List<String> links) {
        parent = new int[nodeCount];
        size = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            parent[i] = i; // every node starts as the leader of its own group
            size[i] = 1;
        }

        int groups = nodeCount; // start with nodeCount groups of one node each
        for (String link : links) {
            String[] parts = link.split(",");
            int u = Integer.parseInt(parts[0].trim());
            int v = Integer.parseInt(parts[1].trim());
            if (union(u, v)) {
                groups--; // two different groups became one
            }
        }
        return groups;
    }

    // Returns the leader of the group that node belongs to
    int find(int node) {
        if (parent[node] != node) {
            // path compression: point node straight at its leader
            parent[node] = find(parent[node]);
        }
        return parent[node];
    }

    // Joins the groups of a and b. Returns false if they were already in one group.
    boolean union(int a, int b) {
        int leaderA = find(a);
        int leaderB = find(b);
        if (leaderA == leaderB) {
            return false;
        }

        // union by size: hang the smaller group under the bigger one
        if (size[leaderA] < size[leaderB]) {
            int temp = leaderA;
            leaderA = leaderB;
            leaderB = temp;
        }
        parent[leaderB] = leaderA;
        size[leaderA] += size[leaderB];
        return true;
    }
}
```

### Complexity

- **Time:** almost `O(nodeCount + links.size())`, since each `find` is practically `O(1)` with the two tricks.
- **Space:** `O(nodeCount)` for `parent` and `size`. That is less than BFS, because the edges are never stored.

---

## Comparing the Solutions

| Solution | Time | Extra Space |
|----------|------|-------------|
| Brute Force (repaint) | `O(nodeCount² + links.size())` | `O(nodeCount)` |
| BFS | `O(nodeCount + links.size())` | `O(nodeCount + links.size())` |
| Union-Find | almost `O(nodeCount + links.size())` | `O(nodeCount)` |

Both BFS and Union-Find are fast enough here.

BFS feels natural if you picture the input as a graph. Union-Find uses less memory, and it still works if edges arrive one at a time and you need the group count after every new edge.