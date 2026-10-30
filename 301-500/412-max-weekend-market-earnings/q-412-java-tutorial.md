# Maximum Weekend Market Earnings in Java

#### Problem Statement
[https://codezym.com/question/412-max-weekend-market-earnings](https://codezym.com/question/412-max-weekend-market-earnings)


The villages and roads form a tree, and every market is either open or closed. The main trick is to look at the loss one road at a time: if both ends of a road are open, the total goes down by `2 * customerLoss`.

A tree has no loops. So we root it at village 0 and solve it from the leaves up to the root. For every village we keep only two numbers: the best earnings of its subtree when that village is **open**, and when it is **closed**. A parent builds its own two numbers from the numbers of its children.

This idea is called dynamic programming on a tree (tree DP). It solves the whole problem in `O(n)` time.


## Key Observation: The Loss Belongs to the Road

An open market loses `customerLoss` for every open neighbor. Now look at one road between villages `A` and `B`, when both are open:

- `A` loses `customerLoss` because of `B`.
- `B` loses `customerLoss` because of `A`.

So every road with **both ends open** reduces the total by `2 * customerLoss`. We will call this value `pairLoss`.

```
total = (sum of earnings of open markets) - pairLoss * (number of roads with both ends open)
```

Check with Example 2: both markets are open and one road joins them. Total is `12 + 11 - 2 * 2 = 19`. Correct.

This view is easier to work with, because now each road can be checked on its own.


## Approach 1: Brute Force

Try every possible open/closed choice. With `n` villages there are `2^n` choices.

We store one choice in a bitmask. Bit `v` is `1` when village `v` is open.

```java
// roadList holds every road as {first, second}
long best = 0; // keeping all markets closed gives 0
for (int mask = 0; mask < (1 << villageCount); mask++) {
    long total = 0;
    for (int v = 0; v < villageCount; v++) {
        if ((mask & (1 << v)) != 0) {
            total += marketEarnings.get(v); // village v is open
        }
    }
    for (int[] road : roadList) {
        boolean bothOpen = (mask & (1 << road[0])) != 0 && (mask & (1 << road[1])) != 0;
        if (bothOpen) {
            total -= 2L * customerLoss;
        }
    }
    best = Math.max(best, total);
}
return best;
```

**Problem:** this takes `O(2^n * n)` time. It works for 15 villages, but with 200,000 villages it would never finish.

**What we waste:** a village is only affected by its direct neighbors. So the best choice inside one part of the tree does not change when we flip a village far away. Brute force still tries all those combinations together.


## Approach 2: Tree DP (Optimal)

### The Idea

Root the tree at village 0. Now every village except the root has exactly one parent, and some children. The **subtree** of a village is the village itself plus everything below it.

The subtree of village `v` is joined to the rest of the tree by only one road: the road to its parent. So the parent does not care about the choices deep inside this subtree. It only needs to know one thing: is `v` open or closed?

So for each village we store two numbers:

- `closedBest[v]`: best total inside the subtree of `v` when `v` is closed.
- `openBest[v]`: best total inside the subtree of `v` when `v` is open.


### Combining Children

If `v` is **closed**, each child is free to be open or closed, so it takes its better option.

If `v` is **open**, an open child also costs `pairLoss`, because the road between them now has both ends open.

```
closedBest[v] = sum over children of  max(closedBest[child], openBest[child])

openBest[v]   = marketEarnings[v]
                + sum over children of  max(closedBest[child], openBest[child] - pairLoss)
```

A leaf has no children, so its `closedBest` is `0` and its `openBest` is its own earning.

The answer is `max(closedBest[0], openBest[0])`.

`closedBest` is never negative, because keeping the whole subtree closed (total `0`) is always one of its options. So when no market is worth opening, we correctly return `0`.


### Dry Run: Example 1

Roads `0 - 1 - 2`, earnings `[9, 14, 8]`, `customerLoss = 4`, so `pairLoss = 8`. Village 0 is the root.

| Village | closedBest | openBest |
|---|---|---|
| 2 (leaf) | 0 | 8 |
| 1 | max(0, 8) = 8 | 14 + max(0, 8 - 8) = 14 |
| 0 | max(8, 14) = 14 | 9 + max(8, 14 - 8) = 17 |

Answer is `max(14, 17) = 17`. We open villages 0 and 2.


### First Version: Recursive DFS

The most natural way to write this is a DFS that solves the children first:

```java
// returns {closedBest, openBest} for the subtree of village v
long[] solve(int v, int parent) {
    long closed = 0;
    long open = marketEarnings.get(v);
    for (int child : neighbors.get(v)) {
        if (child == parent) {
            continue;
        }
        long[] childBest = solve(child, v);
        long childClosed = childBest[0];
        long childOpen = childBest[1];
        closed += Math.max(childClosed, childOpen);
        open += Math.max(childClosed, childOpen - pairLoss);
    }
    return new long[]{closed, open};
}
```

**Problem:** `villageCount` can be 200,000 and the tree can be one long line. Then the recursion goes 200,000 calls deep, which can crash with a `StackOverflowError`.


### Final Version: No Recursion

All we really need is one rule: **solve every child before its parent**.

1. Run BFS from village 0. BFS visits a parent before its children. We save this visit order, and also remember the parent of each village.
2. Walk the saved order **backwards**. Now every child comes before its parent, so when we reach a village, all its children are already solved.

The DP formulas stay exactly the same. Only the visiting order changes.


### Data Structures Used

- `List<List<Integer>> neighbors`: the adjacency list. For every village it holds the villages joined to it by a road. We can list the neighbors of any village quickly, and the memory used is only `O(n)`.
- `int[] parent`: the parent of each village after rooting the tree at 0. Inside a neighbor list, it tells us which neighbor is the parent and which ones are children.
- `List<Integer> order`: villages in BFS order. Read backwards, it gives children before parents.
- `long[] closedBest` and `long[] openBest`: the two DP values of each village. They are `long` because a total can reach `200,000 * 10^9 = 2 * 10^14`, which does not fit in an `int`.


## Final Java Code

```java
import java.util.*;

public class VillageMarketPlanner {

    public VillageMarketPlanner() {

    }

    /**
     * Returns the best total earnings of open markets.
     * For every village we compute two answers for its subtree:
     * one when the village is closed and one when it is open.
     */
    public long maximumWeekendEarnings(int villageCount, List<String> roads, List<Integer> marketEarnings, int customerLoss) {
        // neighbors.get(v) holds every village that has a road to v
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < villageCount; i++) {
            neighbors.add(new ArrayList<>());
        }
        for (String road : roads) {
            String[] parts = road.split(",");
            int first = Integer.parseInt(parts[0].trim());
            int second = Integer.parseInt(parts[1].trim());
            neighbors.get(first).add(second);
            neighbors.get(second).add(first);
        }

        // BFS from village 0 gives a parent for every village
        // and an order where each parent comes before its children
        int[] parent = new int[villageCount];
        parent[0] = -1;
        List<Integer> order = new ArrayList<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int village = queue.poll();
            order.add(village);
            for (int next : neighbors.get(village)) {
                if (next != parent[village]) {
                    parent[next] = village;
                    queue.add(next);
                }
            }
        }

        // two open neighbors cost the total 2 * customerLoss together
        long pairLoss = 2L * customerLoss;

        // best total of a village's subtree when that village is closed / open
        long[] closedBest = new long[villageCount];
        long[] openBest = new long[villageCount];

        // walk the order backwards, so children are solved before their parent
        for (int i = order.size() - 1; i >= 0; i--) {
            int village = order.get(i);
            long closed = 0;
            long open = marketEarnings.get(village);
            for (int child : neighbors.get(village)) {
                if (child == parent[village]) {
                    continue;
                }
                // village closed: the child picks its better option freely
                closed += Math.max(closedBest[child], openBest[child]);
                // village open: an open child also costs pairLoss
                open += Math.max(closedBest[child], openBest[child] - pairLoss);
            }
            closedBest[village] = closed;
            openBest[village] = open;
        }

        // closedBest[0] is never negative, so keeping all markets closed is covered
        return Math.max(closedBest[0], openBest[0]);
    }
}
```


## Complexity

- **Time:** `O(n)`. Building the adjacency list, the BFS and the backward walk each touch every village and road a constant number of times.
- **Space:** `O(n)` for the adjacency list, the BFS order and the DP arrays.