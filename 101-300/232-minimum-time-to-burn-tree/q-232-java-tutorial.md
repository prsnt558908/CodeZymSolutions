# Minimum Time To Burn Tree in Java

#### Problem Statement
[https://codezym.com/question/232-minimum-time-to-burn-tree](https://codezym.com/question/232-minimum-time-to-burn-tree)

The whole problem comes down to one simple idea: **the fire has to reach both ends of the longest path in the tree**. So the best place to light the fire is the middle of that longest path, and the answer is half of its length, rounded up.

We will start with a simple brute force that tries every node as the starting point. It is correct, but too slow. Then we will see that only the longest path matters, and we will find it with just two BFS runs. That gives us an `O(n)` solution.

---

## Understanding the Problem

We get a tree with `n` nodes and `n - 1` edges. Each edge comes as a string like `"1,3"`.

We light one node. In every time unit, the fire jumps from each burning node to all of its neighbors.

So for a chosen start node, the burn time is simply **the distance to the node farthest from it**. We want the start node that makes this time as small as possible.

Here is the tree from Example 1:

```
0 --- 1 --- 3 --- 4
      |
      2
```

If we start at node `1`, nodes `0`, `2` and `3` catch fire at time `1`, and node `4` catches fire at time `2`. No start node does better, so the answer is `2`.

---

## Approach 1: Brute Force (Try Every Start Node)

The most direct idea is to try every node as the starting point.

For each start node, we run a BFS. BFS explores the tree level by level, exactly the way the fire spreads. The largest distance it finds is the burn time for that start node.

We keep the smallest burn time over all start nodes.

```java
int best = Integer.MAX_VALUE;
for (int start = 0; start < n; start++) {
    int[] dist = bfs(graph, start); // distance of every node from start
    int burnTime = 0;
    for (int d : dist) {
        burnTime = Math.max(burnTime, d);
    }
    best = Math.min(best, burnTime);
}
return best;
```

### What is wrong with it?

One BFS takes `O(n)` time, and we run it `n` times. That is `O(n²)` in total.

With `n = 100,000`, this means about `10^10` (10 billion) steps. Far too slow.

Instead of trying every start node, we need a way to find the best one directly.

---

## Key Observation: Only the Longest Path Matters

Take the longest path in the tree. It is also called the **diameter** of the tree. Say it has `D` edges, and its two ends are `A` and `B`.

**1. We can never finish faster than `D / 2`, rounded up.**

Wherever the fire starts, it must reach both `A` and `B`. The distance from the start to `A`, plus the distance from the start to `B`, is at least `D`.

So at least one of the two ends is `D / 2` or more steps away. Distances are whole numbers, so this means `D / 2` rounded up.

**2. Starting in the middle of the longest path reaches exactly this time.**

Light the fire at the middle node of the longest path. Both `A` and `B` burn within `D / 2` steps, rounded up. When `D` is odd there are two middle nodes, and either one works. This is why Example 1 says node `1` or node `3`.

What about all the other nodes? None of them can be more than `D / 2` (rounded up) steps away from the middle.

If one were, we could walk from it, through the middle, to the end on the other side. That walk would be longer than `D`. This is impossible, because `D` is already the longest path.

So the answer is:

```
answer = (D + 1) / 2    // D / 2 rounded up, using integer division
```

Let us check it on the examples:

| Example | Longest path | D | Answer |
|---|---|---|---|
| 1 | `0 → 1 → 3 → 4` | 3 | 2 |
| 2 | `0 → 1 → 2 → 3 → 4 → 5` | 5 | 3 |
| 3 | just node `0` | 0 | 0 |

The problem is now much smaller. **We only need the length of the longest path.**

---

## Approach 2: Find the Longest Path With Two BFS (Optimal)

There is a neat, well-known trick to find the longest path in a tree:

1. Run BFS from any node, say node `0`. The farthest node it finds is `A`. This node is always one end of a longest path.
2. Run BFS again, this time from `A`. The farthest node it finds is `B`, and the distance from `A` to `B` is `D`.
3. Return `(D + 1) / 2`.

Just two BFS runs, no matter how big the tree is.

### Why is the farthest node always an end of a longest path?

`A` is the farthest node from node `0`, so it sits at an outer tip of the tree.

Take any longest path and swap one of its ends for `A`. Since nothing is farther from node `0` than `A`, the branch out to `A` is at least as long as the piece it replaces. So the new path is never shorter.

This means some longest path always ends at `A`. The second BFS, started from `A`, finds the other end of it.

### Dry run on Example 1

- BFS from `0` gives distances `0:0, 1:1, 2:2, 3:2, 4:3`. The farthest node is `4`, so `A = 4`.
- BFS from `4` gives distances `4:0, 3:1, 1:2, 0:3, 2:3`. The largest distance is `3`, so `D = 3`.
- The answer is `(3 + 1) / 2 = 2`.

### Data structures we need

- **Adjacency list (`List<List<Integer>>`)**: `graph.get(u)` holds all neighbors of node `u`. BFS needs to jump from a node to its neighbors quickly, and for a tree this list takes only `O(n)` memory.

- **Queue (`ArrayDeque`)**: BFS must handle nodes in the order they were found, nearest first. A queue gives exactly this order, which is also the order in which the fire spreads.

- **Distance array (`int[] dist`)**: stores how far each node is from the BFS start. A value of `-1` means "not visited yet", so we do not need a separate visited set.

### Complexity

- **Time:** `O(n)`. Each BFS visits every node and edge once, and we run only two of them.
- **Space:** `O(n)` for the graph, the queue and the distance array.

---

## Java Code

```java
import java.util.*;

public class TreeBurning {

    public TreeBurning() {
    }

    /**
     * Returns the minimum time to burn the whole tree.
     * It is half the length of the longest path, rounded up.
     */
    public int minimumBurnTime(int n, List<String> edges) {
        List<List<Integer>> graph = buildGraph(n, edges);

        // 1st BFS: the farthest node from node 0 is one end (A) of the longest path
        int[] distFromZero = bfs(graph, 0);
        int endA = farthestNode(distFromZero);

        // 2nd BFS: the farthest node from A is the other end (B)
        int[] distFromA = bfs(graph, endA);
        int endB = farthestNode(distFromA);
        int longestPath = distFromA[endB];

        // Light the fire in the middle of the longest path
        return (longestPath + 1) / 2;
    }

    /** Builds an adjacency list from edges like "u,v". */
    List<List<Integer>> buildGraph(int n, List<String> edges) {
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        for (String edge : edges) {
            String[] parts = edge.split(",");
            int u = Integer.parseInt(parts[0]);
            int v = Integer.parseInt(parts[1]);
            graph.get(u).add(v);
            graph.get(v).add(u);
        }
        return graph;
    }

    /**
     * BFS from start. Returns the distance of every node from start.
     * This is also the time at which each node catches fire.
     */
    int[] bfs(List<List<Integer>> graph, int start) {
        int[] dist = new int[graph.size()];
        Arrays.fill(dist, -1); // -1 means "not visited yet"

        Queue<Integer> queue = new ArrayDeque<>();
        dist[start] = 0;
        queue.add(start);

        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : graph.get(node)) {
                if (dist[neighbor] == -1) {
                    dist[neighbor] = dist[node] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return dist;
    }

    /** Returns the node that has the largest distance. */
    int farthestNode(int[] dist) {
        int farthest = 0;
        for (int i = 1; i < dist.length; i++) {
            if (dist[i] > dist[farthest]) {
                farthest = i;
            }
        }
        return farthest;
    }
}
```