# Count String Prefixes on Tree Paths in Java

#### Problem Statement
[https://codezym.com/question/137-string-prefix-on-tree-paths](https://codezym.com/question/137-string-prefix-on-tree-paths)


A matching path is built one letter at a time. If a path spells `s[0..i-1]`, it can grow by one step to a new neighbor whose letter is `s[i]`.

The simple solution starts a walk from every node. It works, but many of these walks repeat the same work.

The better solution grows all matching paths together, one letter per level. Paths that end with the same step (same last node, same node before it) will grow in exactly the same way, so we store all of them as one count.


## Solution 1: Walk From Every Node

Try every node as the first node of the path and run a DFS from it. The node at depth `index` must have the letter `s[index]`. As soon as a letter does not match, we stop, because any longer path that starts the same way cannot match either.

In a tree, the only way to visit a node twice is to step back to the node we just came from. So skipping the parent keeps every path simple.

```java
// count[i] = number of paths that spell s[0..i]
for (int start = 0; start < n; start++) {
    dfs(start, -1, 0);
}

void dfs(int node, int parent, int index) {
    if (letters.charAt(node) != s.charAt(index)) {
        return; // the path stopped matching
    }
    count[index]++; // the path start -> ... -> node spells s[0..index]
    if (index + 1 == s.length()) {
        return;
    }
    for (int next : adj.get(node)) {
        if (next != parent) { // never step back
            dfs(next, node, index + 1);
        }
    }
}
```

### Why It Is Slow

Take a star: one center node joined to every other node, every letter is `'a'`, and `s = "aaa"`.

Every leaf starts its own walk, goes to the center, and then visits all the other leaves. That is about `n × n` steps.

But once these walks reach the center, they are almost the same. The only difference is the leaf each walk came from, because it cannot go back there. We keep repeating the same work.

Also, on a long chain the recursion can go 20,000 calls deep, which can overflow the stack.


## Solution 2: Grow All Paths Together

### Key Observation

To grow a path by one more node, we only need two things:

1. The node where the path ends.
2. The node it came from, because it cannot go back there.

The rest of the path does not matter. So all matching paths of the same length that end with the same step `x -> y` will grow in exactly the same way. We do not need to walk them one by one. We only keep how many of them there are.

So we grow all paths together, one level at a time. At level `i`, every path has `i + 1` nodes and spells `s[0..i]`.

### Growing by One Letter

Say some paths end at `node`, and `next` is a neighbor whose letter is `s[i]`. The number of paths that can step to `next` is:

```
ways = (paths ending at node) - (paths ending at node that came from next)
```

We subtract the second part because those paths would visit `next` twice.

Why is checking only the previous node enough? In a tree, the only node of the path that is joined to `node` is the one just before it. Any other neighbor is a new node, because a tree has no cycles.

This "all minus the one we came from" trick also keeps busy nodes cheap. In the star, the center collects `n - 1` paths and sends them to `n - 1` leaves with one subtraction per leaf, instead of checking every pair of leaves.

Finally, `answer[i]` is the sum of all `ways` at level `i`. For level 0, it is just the number of nodes with letter `s[0]`.

### Dry Run on Example 1

Edges are `0-1, 1-2, 1-3, 3-4`. Letters are `a b a b a` (node 0 to node 4). `s = "aba"`.

| Level | Letter | What happens | answer |
|---|---|---|---|
| 0 | a | Paths end at nodes 0, 2 and 4 | 3 |
| 1 | b | Steps 0 -> 1, 2 -> 1 and 4 -> 3. Node 1 has 2 paths, node 3 has 1 path | 3 |
| 2 | a | 1 -> 0: 2 - 1 = 1. 1 -> 2: 2 - 1 = 1. 3 -> 4: 1 - 1 = 0, because that path came from 4 | 2 |

### Data Structures Used

**One way edges:** we need a separate count for each direction of an edge. So edge `i` gets two ids: `2 * i` means `a -> b` and `2 * i + 1` means `b -> a`.

The reverse of edge `e` is simply `e ^ 1`, because flipping the last bit turns `2 * i` into `2 * i + 1` and back.

**pathsEndingAt[v]:** how many matching paths of the current length end at node `v`.

**pathsEndingWith[e]:** how many of those paths have `e` as their last step. This is the count we subtract.

**activeNodes:** the nodes where at least one path ends. Only these nodes can grow, so all other nodes are skipped. Once it is empty, all remaining answers are `0`.

We keep it in a plain `int` array with a counter. This is faster than a `List<Integer>` here, because the list is rebuilt at every level.

**Two copies of each array:** one for the current level and one for the next level. We swap them after every level.

Each old count is read exactly once, so we reset it to `0` right after reading it. The arrays are then already clean when we reuse them. We never clear a whole array, which would cost `O(n)` per level.

### Complexity

- **Time:** each level visits only the active nodes and their edges. A matching path has at most `min(n, |s|)` nodes, so the worst case is `O(n × min(n, |s|) + |s|)`. This happens on a long chain where every letter matches.
- It never does more steps than Solution 1, and it is much faster when many paths meet at the same node. The star example drops from `O(n²)` to `O(n)`.
- **Extra space:** `O(n + |s|)`.

### Java Code

```java
import java.util.*;

public class OccurrencesOfEveryPrefixInUndirectedTree {

    public OccurrencesOfEveryPrefixInUndirectedTree() {

    }

    /**
     * answer[i] = number of ordered simple paths whose letters spell s[0..i].
     * All matching paths grow together, one letter at a time.
     * Paths that end with the same step (from -> to) are stored as one number.
     */
    public List<Integer> countPrefixOccurrences(int n, List<String> edges, String letters, String s) {
        char[] letter = letters.toCharArray();
        char[] text = s.toCharArray();

        // Every undirected edge i is stored as two one way edges:
        // id 2 * i goes a -> b and id 2 * i + 1 goes b -> a.
        // So the reverse of one way edge e is always e ^ 1.
        int[] edgeTo = new int[2 * (n - 1)];
        int[] degree = new int[n];
        for (int i = 0; i < n - 1; i++) {
            String[] parts = edges.get(i).trim().split("\\s+");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            edgeTo[2 * i] = b;
            edgeTo[2 * i + 1] = a;
            degree[a]++;
            degree[b]++;
        }

        // outEdges[v] = ids of the one way edges that leave node v
        int[][] outEdges = new int[n][];
        for (int v = 0; v < n; v++) {
            outEdges[v] = new int[degree[v]];
        }
        int[] filled = new int[n];
        for (int i = 0; i < n - 1; i++) {
            int a = edgeTo[2 * i + 1];
            int b = edgeTo[2 * i];
            outEdges[a][filled[a]++] = 2 * i;
            outEdges[b][filled[b]++] = 2 * i + 1;
        }

        // pathsEndingAt[v]   = matching paths of the current length that end at node v
        // pathsEndingWith[e] = matching paths of the current length whose last step is edge e
        int[] pathsEndingAt = new int[n];
        int[] pathsEndingWith = new int[edgeTo.length];
        int[] nextEndingAt = new int[n];
        int[] nextEndingWith = new int[edgeTo.length];

        // Nodes where at least one matching path ends. Only these nodes can grow.
        // A plain int array is used as a list, with a separate size counter.
        int[] activeNodes = new int[n];
        int activeCount = 0;
        int[] nextActiveNodes = new int[n];

        List<Integer> answer = new ArrayList<>();

        // Length 1: every node with letter s[0] is a matching path on its own.
        for (int v = 0; v < n; v++) {
            if (letter[v] == text[0]) {
                pathsEndingAt[v] = 1;
                activeNodes[activeCount++] = v;
            }
        }
        answer.add(activeCount);

        for (int i = 1; i < text.length; i++) {
            char need = text[i]; // every path grows by one node with this letter
            int nextCount = 0;
            int total = 0;

            for (int k = 0; k < activeCount; k++) {
                int node = activeNodes[k];
                int endingHere = pathsEndingAt[node];
                pathsEndingAt[node] = 0; // reset now, so the array is clean for reuse

                for (int e : outEdges[node]) {
                    int reverse = e ^ 1; // same edge, but next -> node
                    int cameFromNext = pathsEndingWith[reverse];
                    pathsEndingWith[reverse] = 0; // reset now as well

                    int next = edgeTo[e];
                    if (letter[next] != need) {
                        continue;
                    }
                    // Every path ending at node can step to next,
                    // except the ones that just came from next.
                    int ways = endingHere - cameFromNext;
                    if (ways == 0) {
                        continue;
                    }
                    nextEndingWith[e] = ways;
                    if (nextEndingAt[next] == 0) {
                        nextActiveNodes[nextCount++] = next;
                    }
                    nextEndingAt[next] += ways;
                    total += ways;
                }
            }
            answer.add(total);

            // The next level becomes the current level.
            int[] temp = pathsEndingAt;
            pathsEndingAt = nextEndingAt;
            nextEndingAt = temp;

            temp = pathsEndingWith;
            pathsEndingWith = nextEndingWith;
            nextEndingWith = temp;

            temp = activeNodes;
            activeNodes = nextActiveNodes;
            nextActiveNodes = temp;
            activeCount = nextCount;
        }
        return answer;
    }
}
```