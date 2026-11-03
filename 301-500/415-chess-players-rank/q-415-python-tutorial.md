# Chess Players Rank in Python

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


Here `self.canBeat(a, b)` runs a BFS from `a`, following the arrows, and returns `True` if it reaches `b`.

```python
ranks = [0] * n
for player in range(n):
    stronger = 0
    weaker = 0
    for other in range(n):
        if other == player:
            continue
        if self.canBeat(other, player):
            stronger += 1
        elif self.canBeat(player, other):
            weaker += 1
    if stronger + weaker == n - 1:
        ranks[player] = stronger + 1
```

### Why It Is Wasteful

For each of the `n` players we check `n - 1` others, and each check may run two searches. That is about `2 · n²` searches, and one search can touch all `n` players and all `m` results. So the time is `O(n² · (n + m))`.


Most of this work is repeated. One search from `p` already walks through **every** player that `p` is surely stronger than. Yet we start a brand new search for every single `q`.

---

## Solution 2: One BFS per Player

### Idea

Run just one BFS from each player `p`. Every player it reaches is surely weaker than `p`.


Now read the same fact the other way: `p` is surely stronger than each player it reaches. So when the BFS from `p` reaches a player `q`, we update two counters at once:

- `weaker_count[p] += 1`
- `stronger_count[q] += 1`


Once BFS has run from all `n` players, both counters are complete. We never need a second search with the arrows flipped.

### Important Data Structures

- `beats` (a list of lists): `beats[u]` lists every player that `u` defeated directly. BFS keeps asking "who did this player beat?", and this list answers it in one step.

- `visited` (a list of booleans): a player can be reached by more than one chain. In example 2, BFS from `0` reaches `5` through `3` and again through `4`. Without `visited`, we would count `5` twice.

- `queue` (a `deque`): holds the players whose wins we still need to follow. A `deque` removes items from the front in `O(1)`, which a plain `list` cannot do.

- `stronger_count` and `weaker_count`: the two numbers our rule needs for every player.

### Steps

1. Build `beats` from `gameResults`.
2. Run a BFS from every player and update both counters.
3. If `stronger_count[p] + weaker_count[p] == n - 1`, player `p` has rank `stronger_count[p] + 1`.


Both required methods share one helper, `findKnownRanks()`. It returns a list where `ranks[p]` is the rank of player `p`, or `0` if that rank is not known.

- `countPlayersWithKnownRank()` counts the non-zero entries.
- `getKnownPlayerRanks()` builds the `"playerId,rank"` strings. It checks players from `0` to `n - 1`, so the list is already sorted by player id.


Even `n = 1` works. The only player has `0 + 0 == 0`, so it gets rank `1`.

### Code

```python
from collections import deque
from typing import List


class ChessRankAnalyzer:

    def __init__(self):
        pass

    def countPlayersWithKnownRank(self, n: int, gameResults: List[str]) -> int:
        """Returns how many players have an exact, known rank."""
        ranks = self.findKnownRanks(n, gameResults)
        count = 0
        for rank in ranks:
            if rank > 0:
                count += 1
        return count

    def getKnownPlayerRanks(self, n: int, gameResults: List[str]) -> List[str]:
        """Returns "playerId,rank" for every player whose rank is known,
        in ascending order of playerId."""
        ranks = self.findKnownRanks(n, gameResults)
        result = []
        # Players are checked from 0 to n - 1, so the list is already sorted by playerId
        for player in range(n):
            if ranks[player] > 0:
                result.append(f"{player},{ranks[player]}")
        return result

    def findKnownRanks(self, n: int, gameResults: List[str]) -> List[int]:
        """Returns a list where ranks[p] is the exact rank of player p,
        or 0 if the rank of p cannot be known."""
        # beats[u] holds every player that u defeated directly
        beats = [[] for _ in range(n)]
        for result in gameResults:
            winner, loser = result.split(",")
            beats[int(winner)].append(int(loser))

        stronger_count = [0] * n  # players surely stronger than p
        weaker_count = [0] * n  # players surely weaker than p

        for player in range(n):
            # BFS: every player we reach from 'player' is surely weaker than 'player'
            visited = [False] * n
            visited[player] = True
            queue = deque([player])

            while queue:
                current = queue.popleft()
                for loser in beats[current]:
                    if not visited[loser]:
                        visited[loser] = True
                        queue.append(loser)
                        weaker_count[player] += 1  # loser is below player
                        stronger_count[loser] += 1  # player is above loser

        ranks = [0] * n
        for player in range(n):
            # Rank is fixed only when every other player is surely above or surely below
            if stronger_count[player] + weaker_count[player] == n - 1:
                ranks[player] = stronger_count[player] + 1
        return ranks
```

### Complexity

- Building `beats` takes `O(n + m)`, where `m` is the number of game results.
- Each BFS takes `O(n + m)`, and we run `n` of them. Total time: `O(n · (n + m))`.
- Space: `O(n + m)` for the graph and the lists.


For `n = 100` and at most `4950` results, that is only about half a million steps.

---

## Key Takeaways

- Treat each result as an arrow from winner to loser. A chain of arrows means "surely stronger".
- A rank is known when `stronger + weaker == n - 1`, and then it is `stronger + 1`.
- One BFS from a player finds everyone weaker than it. Updating `stronger_count` in the same BFS saves a second search.