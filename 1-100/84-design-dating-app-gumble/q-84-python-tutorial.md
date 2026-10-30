# Design Dating App Gumble in Python

#### Problem Statement

[https://codezym.com/question/84-design-dating-app-gumble](https://codezym.com/question/84-design-dating-app-gumble)

Store each user in a map, keep reactions and matches in maps and sets, and scan users to find the best recommendation. There is only one fixed ranking policy, so simple helper methods are a better fit than a Strategy interface. No formal design pattern is needed here.


## Start with sorting, then avoid the sort

After handling super-accepts, we could collect eligible candidates, compute their ranking values, and sort them. The comparator or ranking key below represents the rules explained next.

```python
candidates.sort(key=ranking_key)
return candidates[0].user_id if candidates else ""
```

We only need one result. Keep the best candidate seen so far and replace it whenever a better one appears. After the scan, it outranks every eligible user.

This avoids the extra list and sorting. Compute shared interests once per candidate.


## Store profile data and history together

- `Gumble` stores profiles by user ID and owns the allowed-interest set.
- `UserProfile` keeps profile fields, preferences, and activity state together.
- The reaction map stores `True` for acceptance and `False` for decline. A missing target means no reaction yet.
- Sets store matches and incoming super-accepts without duplicates.

Validate updates first. Replace profile fields and interests on the existing object, preserving reactions, matches, boosts, and super-accept state.

The constructor filters allowed interests using `[a-z-]+`. Membership in this set then guarantees a user's interest is valid too.


## Apply the ranking rules in order

Active super-accepts come first. Return the smallest sender ID, even if preferences fail or the requester reacted earlier. Feed queries do not consume pins or record reactions.

Otherwise, exclude the requester and previously reacted-to users. Check candidate age and gender against the requester's preferences. The candidate's own preferences do not matter here.

Use these groups, from highest to lowest priority.

1. Preferred candidates who already accepted the requester.
2. Preferred candidates who have not accepted the requester.
3. Unpreferred candidates who already accepted the requester.

Others are ineligible. Within a group, prefer boosted users, then more shared interests, then fewer matches, then smaller ID. Boost cannot outrank a higher group.

Use `(group, -boosted, -shared_interests, match_count, user_id)` as the ranking tuple. Smaller wins. Negation puts higher values first.

The `UserProfile` dataclass uses `default_factory` for separate mutable collections per user. Public method names match the supplied interface.


## Record reactions and matches

A first reaction records the decision and removes that target's incoming pin. Mutual ordinary acceptance adds both users to each other's match sets. Reactions do not require a previous feed query or a preference match.

`listMatchedProfiles` sorts a copy of the match set. `buyBoost` sets a permanent flag and returns success for an existing user, even if already boosted.

`showStats` counts users with matches and sorts all users by decreasing match count, then ID. Zero-match users are included. Cohorts use current profile fields and the statement's output format.


## A small walkthrough

Suppose preferred candidates `u6` and `u7` have not accepted `u4` and share one interest each with `u4`. With two matches for `u6` and none for `u7`, `u7` wins.

Boosting `u6` makes it win. A super-accept from `u2` then ranks `u2` above both, even outside `u4`'s preferences.


## Clarify the bonus rules

The API has no strict/lenient flags. Follow the explicit eligibility rules in `getBestProfile`.

The examples leave two super-accept interactions unresolved. This solution assumes the following.

- Super-accept only pins the sender. Ordinary acceptance and matching happen through `acceptDeclineProfile`.
- If a pin arrives after an earlier reaction, another reaction dismisses it but returns `False` and preserves the old decision. This resolves the overlap between ignoring repeated decisions and pinning until the receiver reacts.


## Complexity

Let `N` be users, `I` the maximum interests per user, and `M` one user's matches. Assume average constant-time hash lookups and ignore string lengths.

- Profile updates take `O(I)` time. Reactions, boosts, and super-accepts take average `O(1)` time.
- A normal feed query takes `O(N × (I + 1))` time and `O(1)` extra space. With active pins, finding the smallest sender takes `O(S)` time for `S` pins.
- Listing matches takes `O(M log M)` time. Reports take `O(N log N)` time and `O(N)` extra space.

Storage grows with users, interests, reactions, and matches. Reaction history can reach `O(N²)`. This is a simple in-memory baseline, with linear scans for ordinary feed queries.


## Complete Python code

```python
from dataclasses import dataclass, field
import re


@dataclass
class UserProfile:
    user_id: str
    name: str = ""
    age: int = 18
    gender: str = "OTHER"
    interests: set[str] = field(default_factory=set)
    min_age: int = 18
    max_age: int = 100
    gender_preference: str = "ANY"

    # These fields survive profile updates.
    reactions: dict[str, bool] = field(default_factory=dict)
    matches: set[str] = field(default_factory=set)
    incoming_super_accepts: set[str] = field(default_factory=set)
    boosted: bool = False
    super_accept_used: bool = False


class Gumble:
    def __init__(self, allowedInterests):
        self.users: dict[str, UserProfile] = {}
        self.allowed_interests = {
            interest
            for interest in (allowedInterests or [])
            if isinstance(interest, str) and re.fullmatch(r"[a-z-]+", interest)
        }

    def _valid_gender(self, gender):
        return gender in ("MALE", "FEMALE", "OTHER")

    def addOrUpdateUser(
        self, userId, name, age, gender, interests,
        minAge, maxAge, genderPreference
    ):
        # Validate everything before changing an existing user.
        if (
            not userId
            or not 18 <= age <= 100
            or not 18 <= minAge <= maxAge <= 100
            or not self._valid_gender(gender)
            or not (
                self._valid_gender(genderPreference)
                or genderPreference == "ANY"
            )
        ):
            return False

        # Membership also guarantees the format was validated.
        filtered_interests = {
            interest
            for interest in (interests or [])
            if interest in self.allowed_interests
        }

        user = self.users.get(userId)
        if user is None:
            user = UserProfile(userId)
            self.users[userId] = user

        # Replace profile fields, while keeping history on the same object.
        user.name = name
        user.age = age
        user.gender = gender
        user.interests = filtered_interests
        user.min_age = minAge
        user.max_age = maxAge
        user.gender_preference = genderPreference
        return True

    def _preferred(self, requester, candidate):
        return (
            requester.min_age <= candidate.age <= requester.max_age
            and (
                requester.gender_preference == "ANY"
                or requester.gender_preference == candidate.gender
            )
        )

    def _bucket(self, requester, candidate):
        preferred = self._preferred(requester, candidate)
        accepted = candidate.reactions.get(requester.user_id, False)

        if preferred:
            return 0 if accepted else 1
        return 2 if accepted else -1

    def _mutual_interests(self, first, second):
        smaller, larger = first.interests, second.interests
        if len(smaller) > len(larger):
            smaller, larger = larger, smaller

        return sum(1 for interest in smaller if interest in larger)

    def getBestProfile(self, userId):
        requester = self.users.get(userId)
        if requester is None:
            return ""

        # Active super-accepts override preferences and previous reactions.
        if requester.incoming_super_accepts:
            return min(requester.incoming_super_accepts)

        best_id = ""
        best_rank = None

        for candidate in self.users.values():
            if (
                candidate.user_id == userId
                or candidate.user_id in requester.reactions
            ):
                continue

            bucket = self._bucket(requester, candidate)
            if bucket == -1:
                continue

            mutual = self._mutual_interests(requester, candidate)
            # Smaller tuples rank first. Negate values that should be higher.
            rank = (
                bucket,
                -int(candidate.boosted),
                -mutual,
                len(candidate.matches),
                candidate.user_id,
            )
            if best_rank is None or rank < best_rank:
                best_id = candidate.user_id
                best_rank = rank

        return best_id

    def acceptDeclineProfile(self, userId, targetUserId, isAccepted):
        user = self.users.get(userId)
        target = self.users.get(targetUserId)
        if user is None or target is None or userId == targetUserId:
            return False

        # Dismiss a newer pin, even if the original decision already exists.
        user.incoming_super_accepts.discard(targetUserId)
        if targetUserId in user.reactions:
            return False

        user.reactions[targetUserId] = isAccepted
        if isAccepted and target.reactions.get(userId, False):
            # A match is stored on both sides, exactly once.
            user.matches.add(targetUserId)
            target.matches.add(userId)
        return True

    def listMatchedProfiles(self, userId):
        user = self.users.get(userId)
        if user is None:
            return []
        return sorted(user.matches)

    def buyBoost(self, userId):
        user = self.users.get(userId)
        if user is None:
            return False
        user.boosted = True
        return True

    def superAcceptProfile(self, userId, targetUserId):
        user = self.users.get(userId)
        target = self.users.get(targetUserId)
        if (
            user is None
            or target is None
            or userId == targetUserId
            or user.super_accept_used
        ):
            return False

        # A super-accept is a feed pin, separate from an ordinary acceptance.
        user.super_accept_used = True
        target.incoming_super_accepts.add(userId)
        return True

    def showStats(self, topN):
        ordered = sorted(
            self.users.values(),
            key=lambda user: (-len(user.matches), user.user_id),
        )
        matched_users = 0
        genders = {"MALE": 0, "FEMALE": 0, "OTHER": 0}
        ages = [0, 0, 0, 0]

        # Recompute cohorts so profile changes are reflected immediately.
        for user in ordered:
            if user.matches:
                matched_users += 1
            genders[user.gender] += 1

            if user.age <= 25:
                ages[0] += 1
            elif user.age <= 35:
                ages[1] += 1
            elif user.age <= 50:
                ages[2] += 1
            else:
                ages[3] += 1

        limit = min(max(topN, 0), len(ordered))
        top_users = ",".join(
            f"{user.user_id}-{len(user.matches)}"
            for user in ordered[:limit]
        )
        return [
            f"totalUsers={len(self.users)}",
            f"matchedUsers={matched_users}",
            f"topNByMatches={top_users}",
            f"genderCohort=MALE:{genders['MALE']},"
            f"FEMALE:{genders['FEMALE']},OTHER:{genders['OTHER']}",
            f"ageCohort=18-25:{ages[0]},26-35:{ages[1]},"
            f"36-50:{ages[2]},51-100:{ages[3]}",
        ]
```
