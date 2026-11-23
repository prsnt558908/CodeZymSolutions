# Tennis Match Tournament Results in Java

#### Problem Statement
[https://codezym.com/question/234-tennis-match-tournament-results](https://codezym.com/question/234-tennis-match-tournament-results)

## Core Idea

This is a knockout tournament where the smaller rank always wins.

The first two methods are plain simulation. We play one round at a time and pair players from left to right. Each round removes about half the players, so playing all the rounds takes only about `2n` steps.

The third method asks for a bracket where better players are knocked out as late as possible. Trying every ordering is far too slow. Instead, we **build the bracket backwards**. We start from the champion and keep adding the players of the earlier rounds, until we reach the first round.

---

## Playing One Round

Both `tournamentWinner()` and `eliminationRounds()` need the same building block: play one round and return the winners.

Index `0` plays index `1`, index `2` plays index `3`, and so on. The smaller rank wins.

If the number of players is odd, the last player advances without playing. This is called a **bye**.

```java
List<Integer> playRound(List<Integer> players) {
    List<Integer> winners = new ArrayList<>();
    for (int i = 0; i + 1 < players.size(); i += 2) {
        winners.add(Math.min(players.get(i), players.get(i + 1)));
    }

    // odd number of players: the last one advances without playing
    if (players.size() % 2 == 1) {
        winners.add(players.get(players.size() - 1));
    }
    return winners;
}
```

We keep each round in a simple `List`, because the left to right order of the winners decides who plays whom in the next round.

---

## Tournament Winner

We could call `playRound()` again and again until one player is left. That works, but there is a shortcut.

The smallest rank wins **every** match it plays. And a bye never knocks anyone out.

So the smallest rank can never be eliminated. It is always the winner.

```java
public int tournamentWinner(List<Integer> ranks) {
    return Collections.min(ranks);
}
```

---

## Elimination Rounds

Here we need every round, so we really do simulate. After each round, we turn the winners into a string like `"4,2,1,3"` and save it.

Example: `ranks = [9, 4, 7, 2, 6, 1, 8, 3]`

| Round | Matches | Saved string |
|---|---|---|
| 1 | (9,4) (7,2) (6,1) (8,3) | `"4,2,1,3"` |
| 2 | (4,2) (1,3) | `"2,1"` |
| 3 | (2,1) | `"1"` |

If there is only one player, no match is played. We just return that rank as the only string, like `["7"]`.

The number of players halves every round, so the total work is `n + n/2 + n/4 + ...`, which is about `2n`. So the whole simulation is `O(n)`.

---

## Optimal Ordering

We need an ordering where, after every round, the `m` players left are exactly the `m` smallest ranks.

If many orderings work, we return the lexicographically smallest one. That means we compare lists from the first element, just like words in a dictionary.

### Brute Force

Try every ordering of the ranks. Play the tournament for each one, check that only the best ranks survive each round, and keep the smallest valid ordering.

```java
// sorted = ranks in increasing order
boolean isValid(List<Integer> order, List<Integer> sorted) {
    List<Integer> players = order;
    while (players.size() > 1) {
        players = playRound(players);

        // the survivors must be the smallest players.size() ranks
        Set<Integer> best = new HashSet<>(sorted.subList(0, players.size()));
        if (!best.containsAll(players)) {
            return false;
        }
    }
    return true;
}
```

The problem is the number of orderings: `n!`. Even `12` players give about 479 million orderings. With up to 100,000 players, this is hopeless.

So instead of searching for the answer, we will **build** it.

### Build the Bracket Backwards

First, notice that the size of every round is fixed. It does not depend on the ordering. Each round, `size` becomes `(size + 1) / 2`.

For `5` players the sizes are `5 → 3 → 2 → 1`. So after round 1 the `3` smallest ranks must be left. After round 2, the `2` smallest. After round 3, only the smallest.

Now look at one round with `size` players, where `m` of them advance. Let `sorted` be the ranks in increasing order:

- **Survivors** are `sorted[0]` to `sorted[m - 1]`. They must win this round.
- **Losers** are `sorted[m]` to `sorted[size - 1]`. They must lose this round.

Every survivor must win and every loser must lose. So each match must be exactly **one survivor vs one loser**. And when `size` is odd, the bye must go to a survivor.

Here is the nice part: every survivor is smaller than every loser. So any survivor beats any loser, and we are free to pair them however we like.

This is why we sort the ranks once at the start. The sorted list directly tells us the survivors and losers of every round.

### Getting the Smallest Order

To build a round, we start from the order of the **next** round and place one loser next to each survivor. We make two simple choices, always picking the smaller option:

1. **Survivor first inside a pair.** The survivor is the smaller one, so `(survivor, loser)` beats `(loser, survivor)`.
2. **Losers in increasing order.** The first pair gets the smallest loser, the second pair gets the next one, and so on.

```
next round = [a0, a1, a2]
losers     = [b0, b1]                 (in increasing order)
this round = [a0, b0, a1, b1, a2]     (a2 gets the bye)
```

The bye always goes to the **last** survivor. A player with a bye is last in this round and stays last in the next round.

What about the order of the survivors? That is not our choice here. The winner of pair `i` becomes player `i` of the next round, so the survivors must follow the next round's order.

So the smallest order for the next round also gives the smallest order for this round. That is exactly why we build backwards: first build the next round, then expand it.

This fits nicely into recursion. To build a round of `size` players, first build the round of `(size + 1) / 2` players. The smallest case is a round of `1` player, which is just the champion `sorted[0]`.

### Walkthrough

Input: `ranks = [5, 1, 4, 2, 3]`, so `sorted = [1, 2, 3, 4, 5]` and the sizes are `5 → 3 → 2 → 1`.

| Round size | Next round | Losers added | This round |
|---|---|---|---|
| 1 | | | `[1]` (champion) |
| 2 | `[1]` | `2` | `[1, 2]` |
| 3 | `[1, 2]` | `3` | `[1, 3, 2]` |
| 5 | `[1, 3, 2]` | `4, 5` | `[1, 4, 3, 5, 2]` |

In the rounds of size `3` and `5`, the last survivor `2` gets the bye.

The answer is `[1, 4, 3, 5, 2]`.

Let's play it forward to check. `(1,4) (3,5) 2` gives `[1, 3, 2]`. Then `(1,3) 2` gives `[1, 2]`. Then `(1,2)` gives `[1]`. The best ranks survive every round.

---

## Final Code

```java
import java.util.*;

public class TennisMatchTournament {

    public TennisMatchTournament() {
    }

    // The smallest rank wins every match it plays, so it always wins the tournament.
    public int tournamentWinner(List<Integer> ranks) {
        return Collections.min(ranks);
    }

    // Plays the tournament round by round and saves the winners of each round.
    public List<String> eliminationRounds(List<Integer> ranks) {
        List<String> rounds = new ArrayList<>();

        // only one player: no match is played
        if (ranks.size() == 1) {
            rounds.add(String.valueOf(ranks.get(0)));
            return rounds;
        }

        List<Integer> players = ranks;
        while (players.size() > 1) {
            players = playRound(players);
            rounds.add(joinWithCommas(players));
        }
        return rounds;
    }

    // Builds the bracket backwards, starting from the champion.
    public List<Integer> optimalOrdering(List<Integer> ranks) {
        // if a round has m winners, they must be sorted.get(0) to sorted.get(m - 1)
        List<Integer> sorted = new ArrayList<>(ranks);
        Collections.sort(sorted);
        return buildRound(sorted, ranks.size());
    }

    // Returns the smallest valid order for a round with `size` players.
    // First builds the next round, then places one loser next to each survivor.
    List<Integer> buildRound(List<Integer> sorted, int size) {
        List<Integer> order = new ArrayList<>();
        if (size == 1) {
            order.add(sorted.get(0));   // the champion
            return order;
        }

        List<Integer> nextRound = buildRound(sorted, (size + 1) / 2);
        int survivorCount = nextRound.size();

        // pair i = (i-th survivor, i-th smallest loser), survivor goes first
        for (int i = 0; i < size / 2; i++) {
            order.add(nextRound.get(i));
            order.add(sorted.get(survivorCount + i));
        }

        // odd number of players: the last survivor gets the bye
        if (size % 2 == 1) {
            order.add(nextRound.get(survivorCount - 1));
        }
        return order;
    }

    // Plays one round and returns the winners in left to right order.
    List<Integer> playRound(List<Integer> players) {
        List<Integer> winners = new ArrayList<>();
        for (int i = 0; i + 1 < players.size(); i += 2) {
            winners.add(Math.min(players.get(i), players.get(i + 1)));
        }

        // odd number of players: the last one advances without playing
        if (players.size() % 2 == 1) {
            winners.add(players.get(players.size() - 1));
        }
        return winners;
    }

    // Turns [4, 2, 1, 3] into "4,2,1,3".
    String joinWithCommas(List<Integer> players) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) {
                text.append(",");
            }
            text.append(players.get(i));
        }
        return text.toString();
    }
}
```

---

## Complexity

| Method | Time | Extra Space |
|---|---|---|
| `tournamentWinner()` | `O(n)` | `O(1)` |
| `eliminationRounds()` | `O(n)` | `O(n)` |
| `optimalOrdering()` | `O(n log n)`, because of sorting | `O(n)` |

Building the order itself is only `O(n)`, because all the round sizes add up to about `2n`.

The recursion is only about `log2(n)` levels deep, around `17` for 100,000 players.