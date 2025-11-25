
from dataclasses import dataclass
from typing import List, Dict, Set, Tuple
import bisect


class FantasyLeaderboard:
    """
    FantasyLeaderboard

    In-memory leaderboard where:
      - Each user has exactly one team (one or more players).
      - A player can be on multiple users' teams.
      - Player scores change via deltas (can be negative).
      - User score = sum of scores of players in that user's team.
      - Top-K users ordered by (score desc, userId asc).

    All operations are O(log N) for ranking updates plus O(team-size) for
    the specific user(s) touched. Player score updates propagate only to
    users that include that player.
    """

    # /** players on each user's team */
    # private final Map<String, Set<String>> userToPlayers = new HashMap<>();
    # → Python:
    def __init__(self) -> None:
        # no-op
        self.user_to_players: Dict[str, Set[str]] = {}

        # /** reverse index: which users include a given player */
        # private final Map<String, Set<String>> playerToUsers = new HashMap<>();
        self.player_to_users: Dict[str, Set[str]] = {}

        # /** cumulative score for each player */
        # private final Map<String, Integer> playerScore = new HashMap<>();
        self.player_score: Dict[str, int] = {}

        # /** current score per user */
        # private final Map<String, Integer> userScore = new HashMap<>();
        self.user_score: Dict[str, int] = {}

        # /** ranking entries for quick reordering on score change */
        # private static final class UserEntry { ... }
        # lookup to the live entry so we can remove/reinsert on updates
        self.user_to_entry: Dict[str, "_UserEntry"] = {}

        # /** TreeSet ordered by (score desc, userId asc) */
        # In Python, maintain a parallel sorted list of keys and entries:
        # keys are tuples (-score, userId) so natural tuple order matches our desired order.
        self._rank_keys: List[Tuple[int, str]] = []  # list of (-score, userId)
        self._ranking: List["_UserEntry"] = []       # parallel list of entries

    @dataclass
    class _UserEntry:
        # final String userId;
        userId: str
        # int score; // mutated only after removing from ranking
        score: int

    # Helper: compute sort key for an entry: (-score, userId)
    def _key(self, entry: "_UserEntry") -> Tuple[int, str]:
        return (-entry.score, entry.userId)

    # Helper: insert an entry into the sorted ranking structures
    def _insert_entry(self, entry: "_UserEntry") -> None:
        key = self._key(entry)
        idx = bisect.bisect_left(self._rank_keys, key)
        self._rank_keys.insert(idx, key)
        self._ranking.insert(idx, entry)

    # Helper: remove an entry from the sorted ranking structures
    def _remove_entry(self, entry: "_UserEntry") -> None:
        key = self._key(entry)
        idx = bisect.bisect_left(self._rank_keys, key)
        # Safety scan in case of rare mismatches (should not happen as key is unique by userId)
        if idx < len(self._rank_keys) and self._rank_keys[idx] == key and self._ranking[idx].userId == entry.userId:
            self._rank_keys.pop(idx)
            self._ranking.pop(idx)
            return
        # Fallback linear search (degenerate, unlikely)
        for j in range(max(0, idx - 2), min(len(self._ranking), idx + 3)):
            if self._ranking[j].userId == entry.userId and self._rank_keys[j] == key:
                self._rank_keys.pop(j)
                self._ranking.pop(j)
                return
        # If not found, ignore silently; structure remains consistent.

    # /**
    #  * Registers a new user with a fixed team. Initial user score equals
    #  * the sum of CURRENT player scores (players may already have points).
    #  */
    def addUser(self, userId: str, playerIds: List[str]) -> None:
        # Defensive copy + normalize to a Set
        team: Set[str] = set(playerIds)
        self.user_to_players[userId] = team

        # Reverse index from each player to this user
        for pid in team:
            if pid not in self.player_to_users:
                self.player_to_users[pid] = set()
            self.player_to_users[pid].add(userId)

        # Compute initial user score from already-accrued player scores
        sum_score = 0
        for pid in team:
            sum_score += self.player_score.get(pid, 0)
        self.user_score[userId] = sum_score

        # Add to ranking
        entry = FantasyLeaderboard._UserEntry(userId=userId, score=sum_score)
        self.user_to_entry[userId] = entry
        self._insert_entry(entry)

    # /**
    #  * Adds a delta to the player's cumulative score and propagates that
    #  * delta to every user that has this player on their team, keeping the
    #  * ranking consistent.
    #  */
    def addScore(self, playerId: str, score: int) -> None:
        scoreDelta = score  # name aligned with Java comments

        # Update the player's cumulative score
        newPlayerScore = self.player_score.get(playerId, 0) + scoreDelta
        self.player_score[playerId] = newPlayerScore

        # Update all users who include this player
        impactedUsers = self.player_to_users.get(playerId)
        if not impactedUsers or len(impactedUsers) == 0:
            return  # no teams include this player yet

        for uid in impactedUsers:
            # Update user's numeric score
            updated = self.user_score.get(uid, 0) + scoreDelta
            self.user_score[uid] = updated

            # Reorder ranking: remove -> mutate -> re-add
            entry = self.user_to_entry.get(uid)
            if entry is not None:
                self._remove_entry(entry)
                entry.score = updated
                self._insert_entry(entry)

    # /**
    #  * Returns up to K users by (score desc, userId asc).
    #  * If k exceeds number of users, returns all users.
    #  */
    def getTopK(self, k: int) -> List[str]:
        # Be tolerant if k arrives as a string in some harness logs.
        try:
            k = int(k)
        except Exception:
            # If conversion fails, default to 0 to return empty (defensive)
            k = 0
        if k <= 0:
            return []
        limit = min(k, len(self._ranking))
        # ranking is already maintained sorted by (-score, userId)
        return [self._ranking[i].userId for i in range(limit)]


# --- Minimal usage example (matches HTML sample) ---
# lb = FantasyLeaderboard()
# lb.addUser("uA", ["p1", "p2"])
# lb.addUser("uB", ["p2"])
# assert lb.getTopK(2) == ["uA", "uB"]
# lb.addScore("p2", 10)
# assert lb.getTopK(2) == ["uA", "uB"]
# lb.addScore("p1", 3)
# assert lb.getTopK(1) == ["uA"]
# lb.addScore("p2", -5)
# assert lb.getTopK(5) == ["uA", "uB"]
