# Chess Players Rank in Java

#### Problem Statement
[https://codezym.com/question/415-chess-players-rank](https://codezym.com/question/415-chess-players-rank)


Think of every game result `"u,v"` as an arrow from the winner `u` to the loser `v`. If you can walk from player `A` to player `B` by following arrows, then `A` is surely stronger than `B`.


A player's rank is fixed only when every other player is either surely stronger or surely weaker than them.


So for each player, we count both groups. One BFS (breadth first search) from a player finds everyone it is surely stronger than. Running it from every player gives us both counts for everyone, and from those counts we get the exact ranks.

---

## Understanding the Problem

Here is example 2. Each line goes down from the winner to the loser.

```
 0   1
  \ /
   2
  / \
 3   4
  \ /
   5
```

Look at player `2`.

- `0` and `1` beat `2`, so they are surely stronger.
- `2` beat `3` and `4`, and both of them beat `5`. So `3`, `4` and `5` are surely weaker.


That covers `2 + 3 = 5` players, which is everyone else. So player `2` sits right below its two stronger players, at rank `3`.


Now look at player `0`.

- `0` is surely stronger than `2`, `3`, `4` and `5`.
- But `0` and `1` never played, and no chain of games connects them. Either of them can be on top.


So `0` can be rank `1` or rank `2`. Its rank is not known.


This gives us a simple rule. For any player, let:

- `stronger` = number of players surely stronger than it
- `weaker` = number of players surely weaker than it


**The rank is known when `stronger + weaker == n - 1`, and only then. The rank is `stronger + 1`.**


Why `stronger + 1`? All stronger players must sit above this player, all weaker players must sit below it, and nobody is left over. So exactly `stronger` players are above it.

---

## Solution 1: Check Every Pair (Brute Force)

### Idea

For each player `p`, go through every other player `q` and ask:

- Is there a chain of wins from `q` to `p`? Then `q` is stronger.
- Is there a chain of wins from `p` to `q`? Then `q` is weaker.


Here `canBeat(a, b)` runs a BFS from `a`, following the arrows, and returns `true` if it reaches `b`.

```java
int[] ranks = new int[n];
for (int player = 0; player < n; player++) {
    int stronger = 0;
    int weaker = 0;
    for (int other = 0; other < n; other++) {
        if (other == player) {
            continue;
        }
        if (canBeat(other, player)) {
            stronger++;
        } else if (canBeat(player, other)) {
            weaker++;
        }
    }
    if (stronger + weaker == n - 1) {
        ranks[player] = stronger + 1;
    }
}
```

### Why It Is Wasteful

For each of the `n` players we check `n - 1` others, and each check may run two searches. That is about `2 · n²` searches, and one search can touch all `n` players and all `m` results. So the time is `O(n² · (n + m))`.


Most of this work is repeated. One search from `p` already walks through **every** player that `p` is surely stronger than. Yet we start a brand new search for every single `q`.

---

## Solution 2: One BFS per Player

### Idea

Run just one BFS from each player `p`. Every player it reaches is surely weaker than `p`.


Now read the same fact the other way: `p` is surely stronger than each player it reaches. So when the BFS from `p` reaches a player `q`, we update two counters at once:

- `weakerCount[p]++`
- `strongerCount[q]++`


Once BFS has run from all `n` players, both counters are complete. We never need a second search with the arrows flipped.

### Important Data Structures

- `List<List<Integer>> beats`: `beats.get(u)` lists every player that `u` defeated directly. BFS keeps asking "who did this player beat?", and this list answers it in one step.

- `boolean[] visited`: a player can be reached by more than one chain. In example 2, BFS from `0` reaches `5` through `3` and again through `4`. Without `visited`, we would count `5` twice.

- `Queue<Integer> queue`: holds the players whose wins we still need to follow. `ArrayDeque` is a simple and fast queue.

- `int[] strongerCount` and `int[] weakerCount`: the two numbers our rule needs for every player.

### Steps

1. Build `beats` from `gameResults`.
2. Run a BFS from every player and update both counters.
3. If `strongerCount[p] + weakerCount[p] == n - 1`, player `p` has rank `strongerCount[p] + 1`.


Both public methods share one helper, `findKnownRanks()`. It returns an array where `ranks[p]` is the rank of player `p`, or `0` if that rank is not known.

- `countPlayersWithKnownRank()` counts the non-zero entries.
- `getKnownPlayerRanks()` builds the `"playerId,rank"` strings. It checks players from `0` to `n - 1`, so the list is already sorted by player id.


Even `n = 1` works. The only player has `0 + 0 == 0`, so it gets rank `1`.

### Code

```java
import java.util.*;

public class ChessRankAnalyzer {

    public ChessRankAnalyzer() {
    }

    /**
     * Returns how many players have an exact, known rank.
     */
    public int countPlayersWithKnownRank(int n, List<String> gameResults) {
        int[] ranks = findKnownRanks(n, gameResults);
        int count = 0;
        for (int rank : ranks) {
            if (rank > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns "playerId,rank" for every player whose rank is known,
     * in ascending order of playerId.
     */
    public List<String> getKnownPlayerRanks(int n, List<String> gameResults) {
        int[] ranks = findKnownRanks(n, gameResults);
        List<String> result = new ArrayList<>();
        // Players are checked from 0 to n - 1, so the list is already sorted by playerId
        for (int player = 0; player < n; player++) {
            if (ranks[player] > 0) {
                result.add(player + "," + ranks[player]);
            }
        }
        return result;
    }

    /**
     * Returns an array where ranks[p] is the exact rank of player p,
     * or 0 if the rank of p cannot be known.
     */
    int[] findKnownRanks(int n, List<String> gameResults) {
        // beats.get(u) holds every player that u defeated directly
        List<List<Integer>> beats = new ArrayList<>();
        for (int player = 0; player < n; player++) {
            beats.add(new ArrayList<>());
        }
        for (String result : gameResults) {
            String[] parts = result.split(",");
            int winner = Integer.parseInt(parts[0].trim());
            int loser = Integer.parseInt(parts[1].trim());
            beats.get(winner).add(loser);
        }

        int[] strongerCount = new int[n]; // players surely stronger than p
        int[] weakerCount = new int[n];   // players surely weaker than p

        for (int player = 0; player < n; player++) {
            // BFS: every player we reach from 'player' is surely weaker than 'player'
            boolean[] visited = new boolean[n];
            Queue<Integer> queue = new ArrayDeque<>();
            visited[player] = true;
            queue.add(player);

            while (!queue.isEmpty()) {
                int current = queue.poll();
                for (int loser : beats.get(current)) {
                    if (!visited[loser]) {
                        visited[loser] = true;
                        queue.add(loser);
                        weakerCount[player]++;  // loser is below player
                        strongerCount[loser]++; // player is above loser
                    }
                }
            }
        }

        int[] ranks = new int[n];
        for (int player = 0; player < n; player++) {
            // Rank is fixed only when every other player is surely above or surely below
            if (strongerCount[player] + weakerCount[player] == n - 1) {
                ranks[player] = strongerCount[player] + 1;
            }
        }
        return ranks;
    }
}
```

### Complexity

- Building `beats` takes `O(n + m)`, where `m` is the number of game results.
- Each BFS takes `O(n + m)`, and we run `n` of them. Total time: `O(n · (n + m))`.
- Space: `O(n + m)` for the graph and the arrays.


For `n = 100` and at most `4950` results, that is only about half a million steps.

---

## Key Takeaways

- Treat each result as an arrow from winner to loser. A chain of arrows means "surely stronger".
- A rank is known when `stronger + weaker == n - 1`, and then it is `stronger + 1`.
- One BFS from a player finds everyone weaker than it. Updating `strongerCount` in the same BFS saves a second search.