# Design a Leaderboard for Fantasy Teams in Java

#### Problem Statement
[https://codezym.com/question/31-design-leaderboard-fantasy-teams](https://codezym.com/question/31-design-leaderboard-fantasy-teams)

In this problem, one player's points change the score of many users at once. The simple way is to add up every team whenever someone asks for the leaderboard, but that repeats a lot of work. The better way is to turn the link around: every player remembers which users picked them, so when a player scores, we update only those users.

This "tell only the ones who care" idea is the **Observer** design pattern. The player is the one being watched, and the users who picked that player are the watchers. A light version of Observer, where each player keeps a simple list of its users, is the best fit here. A full textbook version with interfaces, or any other pattern, would only add code.

The other half of the solution is a **sorted set** that always keeps users in leaderboard order: higher score first, and for equal scores, userId in dictionary order. So `getTopK(k)` just reads the first k users.

We will start with a simple solution that calculates everything when asked, see why it is slow, and then improve it in two small steps.

## Quick Recap

- `addUser(userId, playerIds)`: adds a user with one team. The user's score starts as the sum of the current points of the team's players.
- `addScore(playerId, score)`: adds `score` (it can be negative) to a player. Every user who has this player gains or loses the same points.
- `getTopK(k)`: returns the top k userIds. Higher score comes first. For equal scores, the smaller userId (dictionary order) comes first. If k is more than the number of users, return everyone.

## Solution 1: Simple Approach (Calculate Everything When Asked)

### Idea

Keep just two maps:

- `playerScores`: total points of every player so far.
- `teams`: the set of players in every user's team.

`addUser` and `addScore` only save data. All the real work happens in `getTopK`:

1. For every user, add up the points of the players in their team.
2. Sort all users: higher score first, then userId in dictionary order.
3. Return the first k users.

It is short and easy to trust, which makes it a good starting point.

### Complexity

Let `U` be the number of users and `T` the team size.

- `addUser`: O(T)
- `addScore`: O(1)
- `getTopK`: O(U × T + U log U)

### Code

```java
import java.util.*;

public class FantasyLeaderboard {

    // playerId -> total points of that player so far
    Map<String, Long> playerScores = new HashMap<>();

    // userId -> players in that user's team
    Map<String, Set<String>> teams = new HashMap<>();

    public FantasyLeaderboard() {
    }

    public void addUser(String userId, List<String> playerIds) {
        // a team is a set of players, so a repeated player id counts only once
        teams.put(userId, new HashSet<>(playerIds));
    }

    public void addScore(String playerId, int score) {
        // just remember the player's new total, users are not touched here
        playerScores.put(playerId, playerScores.getOrDefault(playerId, 0L) + score);
    }

    public List<String> getTopK(int k) {
        // Step 1: calculate every user's score from scratch
        Map<String, Long> userScores = new HashMap<>();
        for (String userId : teams.keySet()) {
            long total = 0;
            for (String playerId : teams.get(userId)) {
                total += playerScores.getOrDefault(playerId, 0L);
            }
            userScores.put(userId, total);
        }

        // Step 2: sort all users, higher score first, ties by userId in dictionary order
        List<String> userIds = new ArrayList<>(teams.keySet());
        userIds.sort((a, b) -> {
            long scoreA = userScores.get(a);
            long scoreB = userScores.get(b);
            if (scoreA != scoreB) {
                return Long.compare(scoreB, scoreA);
            }
            return a.compareTo(b);
        });

        // Step 3: return the first k users (or everyone, if k is bigger)
        return new ArrayList<>(userIds.subList(0, Math.min(k, userIds.size())));
    }
}
```

## Solution 2: Optimized Approach (Observer + Sorted Set)

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

So we keep all users in a **sorted set** (`TreeSet`) that follows the leaderboard order. The first k users in the set are the answer.

When a user's score changes, we do three steps in this exact order:

1. Remove the user from the sorted set.
2. Change the user's score.
3. Add the user back. It lands at its new position.

Why remove first? A `TreeSet` finds an element by comparing its score with the others. If we change the score while the user is still inside, the set looks in the wrong place and may not find the user to remove. The leaderboard would then be broken.

### Example Walkthrough

Here is the example from the problem, step by step.

| Call | Users updated | Ranking after the call |
|---|---|---|
| `addUser("uA", ["p1", "p2"])` | uA joins with 0 | uA (0) |
| `addUser("uB", ["p2"])` | uB joins with 0 | uA (0), uB (0) |
| `addScore("p2", 10)` | fans of p2: uA, uB | uA (10), uB (10) |
| `addScore("p1", 3)` | fans of p1: uA | uA (13), uB (10) |
| `addScore("p2", -5)` | fans of p2: uA, uB | uA (8), uB (5) |

Ties like uA (10) and uB (10) are ordered by userId, so uA comes first. At any moment, `getTopK(k)` just reads the first k names of the ranking.

### Which Design Pattern Fits?

**Observer (used, in a light form).** A player is the one being watched, and the users who picked that player are the watchers. When the player scores, only its watchers get updated. That is exactly the Observer idea, and it is what saves us from adding up every team again.

We keep it light. Each `Player` holds a plain `List<User>` and the leaderboard loops over it.

Why not a textbook Observer, where a listener interface lets every `User` update itself when notified? Because a user's score also decides its position inside the sorted set. A user that changes its own score would leave the sorted set out of order (see Fix 2). Only the leaderboard owns the sorted set, so the leaderboard must run the remove, change, add back steps.

Also, there is only one kind of watcher, so an interface would add code without adding value.

**Strategy (looks useful, but not needed).** It is tempting to make the ranking rule a pluggable strategy. But this problem has exactly one fixed rule: score high to low, then userId. A single comparator covers it.

A Strategy interface plus classes would be extra code with nothing to switch between. If the problem later asked for different ranking modes, that would be the time to add Strategy.

### Classes and Data Structures

- **`User`**: holds the userId and the current score. It is the item stored in the sorted set, so it carries both values the leaderboard order needs.
- **`Player`**: holds the player's total points and its fans list. The total lets a user who joins late start with the right score. The fans list is what lets `addScore` touch only the affected users.
- **`players` (`Map<String, Player>`)**: finds any player by id in O(1).
- **`ranking` (`TreeSet<User>`)**: always holds every user in leaderboard order. The comparator breaks ties by userId, and userIds are unique, so two different users never look equal to the set and nobody gets dropped.

Scores are `long` instead of `int`, so large totals stay safe from overflow.

### Complexity

Let `U` be the number of users, `T` the team size, and `F` the number of fans of the player who scored.

- `addUser`: O(T + log U)
- `addScore`: O(F × log U), only the affected users are touched
- `getTopK`: O(log U + k), we just read from the front of the sorted set

### Code

```java
import java.util.*;

/**
 * One user of the app. Every user owns exactly one team,
 * so the user's score is the same as the team's score.
 */
class User {
    String id;
    long score = 0;

    User(String id) {
        this.id = id;
    }
}

/**
 * A real-world player. It keeps its total points and the list of
 * users who picked it (its fans). When the player scores, only these
 * fans need to be updated.
 */
class Player {
    String id;
    long score = 0;
    List<User> fans = new ArrayList<>();

    Player(String id) {
        this.id = id;
    }
}

public class FantasyLeaderboard {

    // playerId -> Player
    Map<String, Player> players = new HashMap<>();

    // every user, always sorted by: higher score first,
    // ties broken by userId in dictionary order
    TreeSet<User> ranking = new TreeSet<>((a, b) -> {
        if (a.score != b.score) {
            return Long.compare(b.score, a.score);
        }
        return a.id.compareTo(b.id);
    });

    public FantasyLeaderboard() {
    }

    public void addUser(String userId, List<String> playerIds) {
        User user = new User(userId);

        // a team is a set of players, so a repeated player id counts only once
        for (String playerId : new HashSet<>(playerIds)) {
            Player player = getOrCreatePlayer(playerId);
            user.score += player.score; // points the player has already earned
            player.fans.add(user);      // user will now hear about future points
        }
        ranking.add(user);
    }

    public void addScore(String playerId, int score) {
        Player player = getOrCreatePlayer(playerId);
        player.score += score;

        // update only the users who picked this player
        for (User user : player.fans) {
            ranking.remove(user); // remove BEFORE changing the score
            user.score += score;
            ranking.add(user);    // add back, it lands at its new position
        }
    }

    public List<String> getTopK(int k) {
        // ranking is already sorted, so the first k users are the answer
        List<String> result = new ArrayList<>();
        for (User user : ranking) {
            if (result.size() == k) {
                break;
            }
            result.add(user.id);
        }
        return result;
    }

    // a player can get points before anyone picks them, so create on first use
    Player getOrCreatePlayer(String playerId) {
        Player player = players.get(playerId);
        if (player == null) {
            player = new Player(playerId);
            players.put(playerId, player);
        }
        return player;
    }
}
```