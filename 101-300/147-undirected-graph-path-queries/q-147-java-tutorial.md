# Undirected Graph Path Queries in Java

#### Problem Statement
[https://codezym.com/question/147-undirected-graph-path-queries](https://codezym.com/question/147-undirected-graph-path-queries)


Two indices have a path between them only if they belong to the same **group** of connected nodes. So the real job is to find these groups quickly.

The key idea is to look at the values in sorted order. In sorted order, every group is one continuous block, and a new block starts wherever two neighboring values are more than `diff` apart.

So we sort the indices by value once, give every index a group number, and then each query becomes a single check: do `u` and `v` have the same group number?


## Brute Force: Build the Whole Graph

The most direct way is to do exactly what the problem says.

Check every pair `(i, j)` and add an edge if `|arr[i] - arr[j]| <= diff`. Then, for each query, run a BFS from `u` and see if it reaches `v`.

```java
// check every pair and add an edge: O(N^2)
for (int i = 0; i < n; i++) {
    for (int j = i + 1; j < n; j++) {
        if (Math.abs(arr.get(i) - arr.get(j)) <= diff) {
            graph.get(i).add(j);
            graph.get(j).add(i);
        }
    }
}

// one full BFS for every query
for (String query : queries) {
    // parse u and v from "u,v"
    answers.add(bfsReaches(graph, u, v));
}
```

This works, but it has two big problems:

1. **Too many pairs.** With `N = 10^5`, there are about `5 * 10^9` pairs to check. If `diff` is large, almost every pair is also an edge.
2. **Repeated work.** Every query runs a new BFS over the same graph. With `10^5` queries, we search the same graph again and again.


## Improvement 1: Find the Groups Only Once

The graph never changes between queries, so we do not need a new search for every query.

Connected nodes form groups. If two nodes are in the same group, there is a path between them. If not, there is no path.

So we label every node with a group number once, using one BFS from each node that has no label yet. After that, each query is just:

```java
answers.add(groupOf[u] == groupOf[v]);
```

This fixes problem 2. But we still check all pairs to build the edges. We need a faster way to find the groups.


## Improvement 2: Sort the Values

Place all values on a number line, from smallest to largest. Now look at each pair of **neighbors** on this line.

**Small gap (at most `diff`):** the two neighbors have an edge. So a run of small gaps forms one long chain, and every node in that chain is connected.

**Big gap (more than `diff`):** this works like a wall. Values on the left of the wall are only smaller or equal, and values on the right are only bigger or equal. So any pair across the wall is at least as far apart as the wall itself, which is more than `diff`. No edge can cross it.

This means the groups are simply the blocks between the walls. We never need to look at any pair other than sorted neighbors.


## Final Solution

### How It Works

1. Make a list of indices `0, 1, ..., N - 1` and sort it by value.
2. Walk this sorted list, starting with group `0`.
3. If the gap between the current value and the previous value is more than `diff`, start a new group.
4. Save the group number of each index in `groupOf`.
5. For each query `"u,v"`, the answer is `groupOf[u] == groupOf[v]`.


### Why Sort Indices and Not Values?

Queries use the **original** indices. If we sorted `arr` directly, we would lose track of which value came from which index.

The `order` list solves this. It holds indices, sorted by their values. So we visit values from smallest to largest, and we still know the original index of each value.


### Why an Array for `groupOf`?

Indices go from `0` to `N - 1`. So a plain array works like a map from index to group number, and each lookup takes `O(1)` time.


### Dry Run

Take `arr = [3, 1, 6, 2]` and `diff = 1`.

| Sorted position | Index | Value | Gap from previous | Group |
|---|---|---|---|---|
| 1st | 1 | 1 | none | 0 |
| 2nd | 3 | 2 | 1 | 0 |
| 3rd | 0 | 3 | 1 | 0 |
| 4th | 2 | 6 | 3, more than 1 | 1 |

So `groupOf = [0, 0, 1, 0]`.

- `"1,3"`: both are in group 0, so `true`
- `"0,2"`: group 0 and group 1, so `false`
- `"0,1"`: both are in group 0, so `true`


### Small Details

- **Duplicates:** two equal values have a gap of `0`, which is never more than `diff`. So they always land in the same group.
- **Same node:** for a query like `"2,2"`, both sides have the same group, so the answer is `true`.
- **Large values:** the biggest possible gap is `10^9 - (-10^9) = 2 * 10^9`, which still fits in a Java `int`.


### Complexity

- **Time:** `O(N log N + Q)`. Sorting takes `O(N log N)`, the walk takes `O(N)`, and each query takes `O(1)`.
- **Space:** `O(N)` for the `order` list and the `groupOf` array.


### Code

```java
import java.util.*;

public class UndirectedGraphPathQueries {

    public UndirectedGraphPathQueries() {
    }

    /**
     * Gives every index a group id once, then answers each query
     * by comparing the group ids of u and v.
     */
    public List<Boolean> pathQueries(List<Integer> arr, int diff, List<String> queries) {
        int[] groupOf = buildGroups(arr, diff);

        List<Boolean> answers = new ArrayList<>();
        for (String query : queries) {
            String[] parts = query.split(",");
            int u = Integer.parseInt(parts[0].trim());
            int v = Integer.parseInt(parts[1].trim());
            answers.add(groupOf[u] == groupOf[v]);
        }
        return answers;
    }

    /**
     * Walks the indices from smallest value to largest value.
     * A new group starts when the gap between two neighbors is more than diff.
     * Returns groupOf, where groupOf[i] is the group id of original index i.
     */
    int[] buildGroups(List<Integer> arr, int diff) {
        int n = arr.size();

        // sort the indices (not the values), so original positions are never lost
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort((a, b) -> Integer.compare(arr.get(a), arr.get(b)));

        int[] groupOf = new int[n];
        int group = 0;
        groupOf[order.get(0)] = group;

        for (int k = 1; k < n; k++) {
            int prev = order.get(k - 1);
            int curr = order.get(k);

            // big gap: no value on the left can reach any value on the right
            if (arr.get(curr) - arr.get(prev) > diff) {
                group++;
            }
            groupOf[curr] = group;
        }
        return groupOf;
    }
}
```