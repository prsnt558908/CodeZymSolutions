# Count String Prefixes on Tree Paths in Python

#### Problem Statement
[https://codezym.com/question/137-string-prefix-on-tree-paths](https://codezym.com/question/137-string-prefix-on-tree-paths)


A matching path is built one letter at a time. If a path spells `s[0..i-1]`, it can grow by one step to a new neighbor whose letter is `s[i]`.

The simple solution starts a walk from every node. It works, but many of these walks repeat the same work.

The better solution grows all matching paths together, one letter per level. Paths that end with the same step (same last node, same node before it) will grow in exactly the same way, so we store all of them as one count.


## Solution 1: Walk From Every Node

Try every node as the first node of the path and run a DFS from it. The node at depth `index` must have the letter `s[index]`. As soon as a letter does not match, we stop, because any longer path that starts the same way cannot match either.

In a tree, the only way to visit a node twice is to step back to the node we just came from. So skipping the parent keeps every path simple.

```python
# count[i] = number of paths that spell s[0..i]
def dfs(node, parent, index):
    if letters[node] != s[index]:
        return  # the path stopped matching
    count[index] += 1  # the path start -> ... -> node spells s[0..index]
    if index + 1 == len(s):
        return
    for next_node in adj[node]:
        if next_node != parent:  # never step back
            dfs(next_node, node, index + 1)


for start in range(n):
    dfs(start, -1, 0)
```

### Why It Is Slow

Take a star: one center node joined to every other node, every letter is `'a'`, and `s = "aaa"`.

Every leaf starts its own walk, goes to the center, and then visits all the other leaves. That is about `n × n` steps.

But once these walks reach the center, they are almost the same. The only difference is the leaf each walk came from, because it cannot go back there. We keep repeating the same work.

Also, on a long chain the recursion can go 20,000 calls deep, which is far more than the default recursion limit allows.


## Solution 2: Grow All Paths Together

### Key Observation

To grow a path by one more node, we only need two things:

1. The node where the path ends.
2. The node it came from, because it cannot go back there.

The rest of the path does not matter. So all matching paths of the same length that end with the same step `x -> y` will grow in exactly the same way. We do not need to walk them one by one. We only keep how many of them there are.

So we grow all paths together, one level at a time. At level `i`, every path has `i + 1` nodes and spells `s[0..i]`.

### Growing by One Letter

Say some paths end at `node`, and `next_node` is a neighbor whose letter is `s[i]`. The number of paths that can step to `next_node` is:

```
ways = (paths ending at node) - (paths ending at node that came from next_node)
```

We subtract the second part because those paths would visit `next_node` twice.

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

**paths_ending_at[v]:** how many matching paths of the current length end at node `v`.

**paths_ending_with[e]:** how many of those paths have `e` as their last step. This is the count we subtract.

**active_nodes:** the nodes where at least one path ends. Only these nodes can grow, so all other nodes are skipped. Once it is empty, all remaining answers are `0`.

**Two copies of each list:** one for the current level and one for the next level. We swap them after every level.

Each old count is read exactly once, so we reset it to `0` right after reading it. The lists are then already clean when we reuse them. We never clear a whole list, which would cost `O(n)` per level.

### Complexity

- **Time:** each level visits only the active nodes and their edges. A matching path has at most `min(n, |s|)` nodes, so the worst case is `O(n × min(n, |s|) + |s|)`. This happens on a long chain where every letter matches.
- It never does more steps than Solution 1, and it is much faster when many paths meet at the same node. The star example drops from `O(n²)` to `O(n)`.
- **Extra space:** `O(n + |s|)`.

### Python Code

```python
from typing import List


class OccurrencesOfEveryPrefixInUndirectedTree:

    def __init__(self):
        pass

    def countPrefixOccurrences(self, n: int, edges: List[str], letters: str, s: str) -> List[int]:
        """
        answer[i] = number of ordered simple paths whose letters spell s[0..i].
        All matching paths grow together, one letter at a time.
        Paths that end with the same step (from -> to) are stored as one number.
        """
        # Every undirected edge i is stored as two one way edges:
        # id 2 * i goes a -> b and id 2 * i + 1 goes b -> a.
        # So the reverse of one way edge e is always e ^ 1.
        edge_to = [0] * (2 * (n - 1))
        out_edges = [[] for _ in range(n)]  # out_edges[v] = ids of the one way edges that leave v
        for i in range(n - 1):
            a, b = map(int, edges[i].split())
            edge_to[2 * i] = b
            edge_to[2 * i + 1] = a
            out_edges[a].append(2 * i)
            out_edges[b].append(2 * i + 1)

        # paths_ending_at[v]   = matching paths of the current length that end at node v
        # paths_ending_with[e] = matching paths of the current length whose last step is edge e
        paths_ending_at = [0] * n
        paths_ending_with = [0] * len(edge_to)
        next_ending_at = [0] * n
        next_ending_with = [0] * len(edge_to)

        # Nodes where at least one matching path ends. Only these nodes can grow.
        active_nodes = []

        # Length 1: every node with letter s[0] is a matching path on its own.
        for v in range(n):
            if letters[v] == s[0]:
                paths_ending_at[v] = 1
                active_nodes.append(v)
        answer = [len(active_nodes)]

        for i in range(1, len(s)):
            need = s[i]  # every path grows by one node with this letter
            next_active_nodes = []
            total = 0

            for node in active_nodes:
                ending_here = paths_ending_at[node]
                paths_ending_at[node] = 0  # reset now, so the list is clean for reuse

                for e in out_edges[node]:
                    reverse = e ^ 1  # same edge, but next_node -> node
                    came_from_next = paths_ending_with[reverse]
                    paths_ending_with[reverse] = 0  # reset now as well

                    next_node = edge_to[e]
                    if letters[next_node] != need:
                        continue
                    # Every path ending at node can step to next_node,
                    # except the ones that just came from next_node.
                    ways = ending_here - came_from_next
                    if ways == 0:
                        continue
                    next_ending_with[e] = ways
                    if next_ending_at[next_node] == 0:
                        next_active_nodes.append(next_node)
                    next_ending_at[next_node] += ways
                    total += ways

            answer.append(total)

            # The next level becomes the current level.
            paths_ending_at, next_ending_at = next_ending_at, paths_ending_at
            paths_ending_with, next_ending_with = next_ending_with, paths_ending_with
            active_nodes = next_active_nodes

        return answer
```