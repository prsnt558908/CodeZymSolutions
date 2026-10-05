# Design a Leaderboard for Fantasy Teams in Python

#### Problem Statement
[https://codezym.com/question/31-design-leaderboard-fantasy-teams](https://codezym.com/question/31-design-leaderboard-fantasy-teams)

In this problem, one player's points change the score of many users at once. The simple way is to add up every team whenever someone asks for the leaderboard, but that repeats a lot of work. The better way is to turn the link around: every player remembers which users picked them, so when a player scores, we update only those users.

This "tell only the ones who care" idea is the **Observer** design pattern. The player is the one being watched, and the users who picked that player are the watchers. A light version of Observer, where each player keeps a simple list of its users, is the best fit here. A full textbook version with listener classes, or any other pattern, would only add code.

The other half of the solution is a **sorted list** that always keeps users in leaderboard order: higher score first, and for equal scores, userId in dictionary order. So `getTopK(k)` just reads the first k users.

We will start with a simple solution that calculates everything when asked, see why it is slow, and then improve it in two small steps.

## Quick Recap

- `addUser(userId, playerIds)`: adds a user with one team. The user's score starts as the sum of the current points of the team's players.
- `addScore(playerId, score)`: adds `score` (it can be negative) to a player. Every user who has this player gains or loses the same points.
- `getTopK(k)`: returns the top k userIds. Higher score comes first. For equal scores, the smaller userId (dictionary order) comes first. If k is more than the number of users, return everyone.

## Solution 1: Simple Approach (Calculate Everything When Asked)

### Idea

Keep just two dictionaries:

- `player_scores`: total points of every player so far.
- `teams`: the set of players in every user's team.

`addUser` and `addScore` only save data. All the real work happens in `getTopK`:

1. For every user, add up the points of the players in their team.
2. Sort all users: higher score first, then userId in dictionary order.
3. Return the first k users.

The sort key `(-score, userId)` does the whole ordering in one go. A bigger score gives a smaller negative number, so it comes first, and equal scores fall back to the userId.

It is short and easy to trust, which makes it a good starting point.

### Complexity

Let `U` be the number of users and `T` the team size.

- `addUser`: O(T)
- `addScore`: O(1)
- `getTopK`: O(U × T + U log U)

### Code

```python
from typing import List


class FantasyLeaderboard:
    def __init__(self):
        self.player_scores = {}  # playerId -> total points of that player so far
        self.teams = {}          # userId -> set of playerIds in that user's team

    def addUser(self, userId: str, playerIds: List[str]) -> None:
        # a team is a set of players, so a repeated player id counts only once
        self.teams[userId] = set(playerIds)

    def addScore(self, playerId: str, score: int) -> None:
        # just remember the player's new total, users are not touched here
        self.player_scores[playerId] = self.player_scores.get(playerId, 0) + score

    def getTopK(self, k: int) -> List[str]:
        # Step 1: calculate every user's score from scratch
        user_scores = {}
        for user_id, team in self.teams.items():
            user_scores[user_id] = sum(self.player_scores.get(p, 0) for p in team)

        # Step 2: sort all users, higher score first, ties by userId in dictionary order
        user_ids = sorted(self.teams, key=lambda u: (-user_scores[u], u))

        # Step 3: return the first k users (or everyone, if k is bigger)
        return user_ids[:k]
```

## Solution 2: Optimized Approach (Observer + Sorted List)

### Why Solution 1 Is Slow

A live leaderboard is read all the time, but Solution 1 rebuilds it from zero on every `getTopK` call.

- It adds up **every** team again, even if only one player scored since the last call.
- It sorts **every** user again, even when we only need the top 1.

We fix these two problems one at a time.

### Fix 1: Update Only the Users Who Picked the Player

When `p2` scores 10 points, only the users who have `p2` in their team change. Everyone else stays the same.

So every player keeps a list of the users who picked them. We call these users the player's **fans**.

- In `addUser`, the new user is added to the fans list of each of their players.
- In `addScore`, we go through that player's fans and add the points to each fan's score.

Now every user's score is always up to date, and we never add up a full team again.

One detail: a player can earn points before anyone picks them. So each player also keeps their own total, and a new user starts with the current totals of their players.

### Fix 2: Keep Users Sorted All the Time

Scores are now always fresh, but sorting everyone on each `getTopK` is still slow.

Python has no built-in sorted set, so we build one from two simple parts: a plain list that we always keep sorted, and the `bisect` module, which does binary search on a sorted list.

Each item in the list is a pair `(-score, userId)`, the same key Solution 1 sorted with. The first k pairs in the list are the answer.

When a user's score changes, we do three steps in this exact order:

1. Find the old pair with `bisect_left` and remove it.
2. Change the user's score.
3. Put the new pair back with `insort`, which inserts it at the right spot so the list stays sorted.

Why remove first? We find the old pair using the old score. If we changed the score first, we would search for a pair that is not in the list and end up removing the wrong entry.

### Example Walkthrough

Here is the example from the problem, step by step.

| Call | Users updated | `ranking` after the call |
|---|---|---|
| `addUser("uA", ["p1", "p2"])` | uA joins with 0 | `[(0, 'uA')]` |
| `addUser("uB", ["p2"])` | uB joins with 0 | `[(0, 'uA'), (0, 'uB')]` |
| `addScore("p2", 10)` | fans of p2: uA, uB | `[(-10, 'uA'), (-10, 'uB')]` |
| `addScore("p1", 3)` | fans of p1: uA | `[(-13, 'uA'), (-10, 'uB')]` |
| `addScore("p2", -5)` | fans of p2: uA, uB | `[(-8, 'uA'), (-5, 'uB')]` |

Ties like `(-10, 'uA')` and `(-10, 'uB')` are ordered by userId, so uA comes first. At any moment, `getTopK(k)` just reads the first k pairs of the list.

### Which Design Pattern Fits?

**Observer (used, in a light form).** A player is the one being watched, and the users who picked that player are the watchers. When the player scores, only its watchers get updated. That is exactly the Observer idea, and it is what saves us from adding up every team again.

We keep it light. Each `Player` holds a plain list of users and the leaderboard loops over it.

Why not a textbook Observer, where every `User` gets a listener method and updates itself when notified? Because a user's score is also stored inside its pair in the sorted `ranking` list. A user that changes only its own score would leave an old, wrong pair in the ranking (see Fix 2). Only the leaderboard owns the ranking, so the leaderboard must run the remove, change, add back steps.

Also, there is only one kind of watcher, so a separate listener class would add code without adding value.

**Strategy (looks useful, but not needed).** It is tempting to make the ranking rule a pluggable strategy. But this problem has exactly one fixed rule: score high to low, then userId. The `(-score, userId)` pair covers it.

A family of Strategy classes would be extra code with nothing to switch between. If the problem later asked for different ranking modes, that would be the time to add Strategy.

### Classes and Data Structures

- **`User`**: holds the userId and the current score. We need the current score to build, and later find, the user's pair in the ranking.
- **`Player`**: holds the player's total points and its fans list. The total lets a user who joins late start with the right score. The fans list is what lets `addScore` touch only the affected users.
- **`players` (dict of playerId to `Player`)**: finds any player by id in O(1).
- **`ranking` (sorted list of `(-score, userId)` pairs)**: always holds every user in leaderboard order. userIds are unique, so no two pairs are ever equal, and binary search always lands on the exact pair we want to remove.

### Complexity

Let `U` be the number of users, `T` the team size, and `F` the number of fans of the player who scored.

- `addUser`: O(T + U)
- `addScore`: O(F × U)
- `getTopK`: O(k), we just slice the front of the sorted list

Binary search finds the right spot in O(log U). The O(U) part comes from putting an item into, or taking one out of, the middle of a list, because every item after that spot shifts by one.

This shift is a single fast memory move, so in practice it is much quicker than adding up every team and sorting all users on every query, which is what Solution 1 does.

### Code

```python
from bisect import bisect_left, insort
from typing import List


class User:
    """One user of the app. Every user owns exactly one team,
    so the user's score is the same as the team's score."""

    def __init__(self, user_id: str):
        self.id = user_id
        self.score = 0


class Player:
    """A real-world player. It keeps its total points and the list of
    users who picked it (its fans). When the player scores, only these
    fans need to be updated."""

    def __init__(self, player_id: str):
        self.id = player_id
        self.score = 0
        self.fans = []  # users whose team has this player


class FantasyLeaderboard:
    def __init__(self):
        self.players = {}  # playerId -> Player

        # every user as a (-score, userId) pair, always kept in sorted order.
        # -score puts higher scores first, userId breaks ties in dictionary order.
        self.ranking = []

    def addUser(self, userId: str, playerIds: List[str]) -> None:
        user = User(userId)

        # a team is a set of players, so a repeated player id counts only once
        for player_id in set(playerIds):
            player = self.get_or_create_player(player_id)
            user.score += player.score  # points the player has already earned
            player.fans.append(user)    # user will now hear about future points
        insort(self.ranking, (-user.score, user.id))

    def addScore(self, playerId: str, score: int) -> None:
        player = self.get_or_create_player(playerId)
        player.score += score

        # update only the users who picked this player
        for user in player.fans:
            # find and remove the old pair BEFORE changing the score
            index = bisect_left(self.ranking, (-user.score, user.id))
            self.ranking.pop(index)
            user.score += score
            insort(self.ranking, (-user.score, user.id))  # add back at its new position

    def getTopK(self, k: int) -> List[str]:
        # ranking is already sorted, so the first k pairs are the answer
        return [user_id for _, user_id in self.ranking[:k]]

    def get_or_create_player(self, player_id: str) -> Player:
        # a player can get points before anyone picks them, so create on first use
        if player_id not in self.players:
            self.players[player_id] = Player(player_id)
        return self.players[player_id]
```