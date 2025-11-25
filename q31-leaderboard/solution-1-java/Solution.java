import java.util.*;

/**
 * FantasyLeaderboard
 *
 * In-memory leaderboard where:
 *  - Each user has exactly one team (one or more players).
 *  - A player can be on multiple users' teams.
 *  - Player scores change via deltas (can be negative).
 *  - User score = sum of scores of players in that user's team.
 *  - Top-K users ordered by (score desc, userId asc).
 *
 * All operations are O(log N) for ranking updates plus O(team-size) for
 * the specific user(s) touched. Player score updates propagate only to
 * users that include that player.
 */
public class FantasyLeaderboard {

    /** players on each user's team */
    private final Map<String, Set<String>> userToPlayers = new HashMap<>();
    /** reverse index: which users include a given player */
    private final Map<String, Set<String>> playerToUsers = new HashMap<>();
    /** cumulative score for each player */
    private final Map<String, Integer> playerScore = new HashMap<>();
    /** current score per user */
    private final Map<String, Integer> userScore = new HashMap<>();

    /** ranking entries for quick reordering on score change */
    private static final class UserEntry {
        final String userId;
        int score; // mutated only after removing from ranking
        UserEntry(String userId, int score) { this.userId = userId; this.score = score; }
    }

    /** lookup to the live entry so we can remove/reinsert on updates */
    private final Map<String, UserEntry> userToEntry = new HashMap<>();

    /** TreeSet ordered by (score desc, userId asc) */
    private final TreeSet<UserEntry> ranking = new TreeSet<>(
        (a, b) -> {
            if (a == b) return 0;
            if (a.score != b.score) {
                return Integer.compare(b.score, a.score); // higher score first
            }
            return a.userId.compareTo(b.userId); // tie-break by userId asc
        }
    );

    public FantasyLeaderboard() {
        // no-op
    }

    /**
     * Registers a new user with a fixed team. Initial user score equals
     * the sum of CURRENT player scores (players may already have points).
     */
    public void addUser(String userId, List<String> playerIds) {
        // Defensive copy + normalize to a Set
        Set<String> team = new HashSet<>(playerIds);
        userToPlayers.put(userId, team);

        // Reverse index from each player to this user
        for (String pid : team) {
            playerToUsers.computeIfAbsent(pid, k -> new HashSet<>()).add(userId);
        }

        // Compute initial user score from already-accrued player scores
        int sum = 0;
        for (String pid : team) {
            sum += playerScore.getOrDefault(pid, 0);
        }
        userScore.put(userId, sum);

        // Add to ranking
        UserEntry entry = new UserEntry(userId, sum);
        userToEntry.put(userId, entry);
        ranking.add(entry);
    }

    /**
     * Adds a delta to the player's cumulative score and propagates that
     * delta to every user that has this player on their team, keeping the
     * ranking consistent.
     */
    public void addScore(String playerId, int scoreDelta) {
        // Update the player's cumulative score
        int newPlayerScore = playerScore.getOrDefault(playerId, 0) + scoreDelta;
        playerScore.put(playerId, newPlayerScore);

        // Update all users who include this player
        Set<String> impactedUsers = playerToUsers.get(playerId);
        if (impactedUsers == null || impactedUsers.isEmpty()) {
            return; // no teams include this player yet
        }

        for (String uid : impactedUsers) {
            // Update user's numeric score
            int updated = userScore.getOrDefault(uid, 0) + scoreDelta;
            userScore.put(uid, updated);

            // Reorder ranking: remove -> mutate -> re-add
            UserEntry entry = userToEntry.get(uid);
            if (entry != null) {
                ranking.remove(entry);
                entry.score = updated;
                ranking.add(entry);
            }
        }
    }

    /**
     * Returns up to K users by (score desc, userId asc).
     * If k exceeds number of users, returns all users.
     */
    public List<String> getTopK(int k) {
        int limit = Math.min(k, ranking.size());
        List<String> result = new ArrayList<>(limit);
        Iterator<UserEntry> it = ranking.iterator();
        while (result.size() < limit && it.hasNext()) {
            result.add(it.next().userId);
        }
        return result;
    }
}
