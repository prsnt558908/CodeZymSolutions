# Design Dating App Gumble in Java

#### Problem Statement

[https://codezym.com/question/84-design-dating-app-gumble](https://codezym.com/question/84-design-dating-app-gumble)

Store each user in a map, keep reactions and matches in maps and sets, and scan users to find the best recommendation. There is only one fixed ranking policy, so simple helper methods are a better fit than a Strategy interface. No formal design pattern is needed here.


## Start with sorting, then avoid the sort

After handling super-accepts, we could collect eligible candidates, compute their ranking values, and sort them. The comparator or ranking key below represents the rules explained next.

```java
candidates.sort(rankingComparator);
return candidates.isEmpty() ? "" : candidates.get(0).userId;
```

We only need one result. Keep the best candidate seen so far and replace it whenever a better one appears. After the scan, it outranks every eligible user.

This avoids the extra list and sorting. Compute shared interests once per candidate.


## Store profile data and history together

- `Gumble` stores profiles by user ID and owns the allowed-interest set.
- `UserProfile` keeps profile fields, preferences, and activity state together.
- The reaction map stores `true` for acceptance and `false` for decline. A missing target means no reaction yet.
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

`getBucket` chooses the group. `isBetter` checks the tie-breakers in order.


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
- If a pin arrives after an earlier reaction, another reaction dismisses it but returns `false` and preserves the old decision. This resolves the overlap between ignoring repeated decisions and pinning until the receiver reacts.


## Complexity

Let `N` be users, `I` the maximum interests per user, and `M` one user's matches. Assume average constant-time hash lookups and ignore string lengths.

- Profile updates take `O(I)` time. Reactions, boosts, and super-accepts take average `O(1)` time.
- A normal feed query takes `O(N × (I + 1))` time and `O(1)` extra space. With active pins, finding the smallest sender takes `O(S)` time for `S` pins.
- Listing matches takes `O(M log M)` time. Reports take `O(N log N)` time and `O(N)` extra space.

Storage grows with users, interests, reactions, and matches. Reaction history can reach `O(N²)`. This is a simple in-memory baseline, with linear scans for ordinary feed queries.


## Complete Java code

Use this implementation in `Gumble.java`.

```java
import java.util.*;

class UserProfile {
    String userId;
    String name;
    int age;
    String gender;
    Set<String> interests = new HashSet<>();
    int minAge;
    int maxAge;
    String genderPreference;

    // These fields survive profile updates.
    Map<String, Boolean> reactions = new HashMap<>();
    Set<String> matches = new HashSet<>();
    Set<String> incomingSuperAccepts = new HashSet<>();
    boolean boosted;
    boolean superAcceptUsed;

    UserProfile(String userId) {
        this.userId = userId;
    }
}

public class Gumble {
    Map<String, UserProfile> users = new HashMap<>();
    Set<String> allowedInterests = new HashSet<>();

    public Gumble(List<String> allowedInterests) {
        if (allowedInterests != null) {
            for (String interest : allowedInterests) {
                if (interest != null && interest.matches("[a-z-]+")) {
                    this.allowedInterests.add(interest);
                }
            }
        }
    }

    boolean isValidGender(String gender) {
        return "MALE".equals(gender)
                || "FEMALE".equals(gender)
                || "OTHER".equals(gender);
    }

    public boolean addOrUpdateUser(
            String userId, String name, int age, String gender,
            List<String> interests, int minAge, int maxAge,
            String genderPreference) {

        // Validate everything before changing an existing user.
        if (userId == null || userId.isEmpty()
                || age < 18 || age > 100
                || minAge < 18 || maxAge > 100 || minAge > maxAge
                || !isValidGender(gender)
                || !(isValidGender(genderPreference)
                        || "ANY".equals(genderPreference))) {
            return false;
        }

        Set<String> filteredInterests = new HashSet<>();
        if (interests != null) {
            for (String interest : interests) {
                // Membership also guarantees the format was validated.
                if (allowedInterests.contains(interest)) {
                    filteredInterests.add(interest);
                }
            }
        }

        UserProfile user = users.get(userId);
        if (user == null) {
            user = new UserProfile(userId);
            users.put(userId, user);
        }

        // Replace profile fields, while keeping history on the same object.
        user.name = name;
        user.age = age;
        user.gender = gender;
        user.interests = filteredInterests;
        user.minAge = minAge;
        user.maxAge = maxAge;
        user.genderPreference = genderPreference;
        return true;
    }

    boolean isPreferred(UserProfile requester, UserProfile candidate) {
        return candidate.age >= requester.minAge
                && candidate.age <= requester.maxAge
                && ("ANY".equals(requester.genderPreference)
                        || requester.genderPreference.equals(candidate.gender));
    }

    int getBucket(UserProfile requester, UserProfile candidate) {
        boolean preferred = isPreferred(requester, candidate);
        boolean accepted = Boolean.TRUE.equals(
                candidate.reactions.get(requester.userId));

        if (preferred) {
            return accepted ? 0 : 1;
        }
        return accepted ? 2 : -1;
    }

    int countMutualInterests(UserProfile first, UserProfile second) {
        Set<String> smaller = first.interests;
        Set<String> larger = second.interests;
        if (smaller.size() > larger.size()) {
            smaller = second.interests;
            larger = first.interests;
        }

        int count = 0;
        for (String interest : smaller) {
            if (larger.contains(interest)) {
                count++;
            }
        }
        return count;
    }

    boolean isBetter(
            UserProfile candidate, int bucket, int mutual,
            UserProfile best, int bestBucket, int bestMutual) {

        if (best == null) {
            return true;
        }
        if (bucket != bestBucket) {
            return bucket < bestBucket;
        }
        if (candidate.boosted != best.boosted) {
            return candidate.boosted;
        }
        if (mutual != bestMutual) {
            return mutual > bestMutual;
        }
        if (candidate.matches.size() != best.matches.size()) {
            return candidate.matches.size() < best.matches.size();
        }
        return candidate.userId.compareTo(best.userId) < 0;
    }

    public String getBestProfile(String userId) {
        UserProfile requester = users.get(userId);
        if (requester == null) {
            return "";
        }

        // Active super-accepts override preferences and previous reactions.
        String pinned = "";
        for (String senderId : requester.incomingSuperAccepts) {
            if (pinned.isEmpty() || senderId.compareTo(pinned) < 0) {
                pinned = senderId;
            }
        }
        if (!pinned.isEmpty()) {
            return pinned;
        }

        UserProfile best = null;
        int bestBucket = 3;
        int bestMutual = 0;

        for (UserProfile candidate : users.values()) {
            if (candidate.userId.equals(userId)
                    || requester.reactions.containsKey(candidate.userId)) {
                continue;
            }

            int bucket = getBucket(requester, candidate);
            if (bucket == -1) {
                continue;
            }

            int mutual = countMutualInterests(requester, candidate);
            if (isBetter(candidate, bucket, mutual,
                    best, bestBucket, bestMutual)) {
                best = candidate;
                bestBucket = bucket;
                bestMutual = mutual;
            }
        }

        return best == null ? "" : best.userId;
    }

    public boolean acceptDeclineProfile(
            String userId, String targetUserId, boolean isAccepted) {
        UserProfile user = users.get(userId);
        UserProfile target = users.get(targetUserId);
        if (user == null || target == null || userId.equals(targetUserId)) {
            return false;
        }

        // Dismiss a newer pin, even if the original decision already exists.
        user.incomingSuperAccepts.remove(targetUserId);
        if (user.reactions.containsKey(targetUserId)) {
            return false;
        }

        user.reactions.put(targetUserId, isAccepted);
        if (isAccepted && Boolean.TRUE.equals(target.reactions.get(userId))) {
            // A match is stored on both sides, exactly once.
            user.matches.add(targetUserId);
            target.matches.add(userId);
        }
        return true;
    }

    public List<String> listMatchedProfiles(String userId) {
        UserProfile user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>(user.matches);
        Collections.sort(result);
        return result;
    }

    public boolean buyBoost(String userId) {
        UserProfile user = users.get(userId);
        if (user == null) {
            return false;
        }
        user.boosted = true;
        return true;
    }

    public boolean superAcceptProfile(String userId, String targetUserId) {
        UserProfile user = users.get(userId);
        UserProfile target = users.get(targetUserId);
        if (user == null || target == null || userId.equals(targetUserId)
                || user.superAcceptUsed) {
            return false;
        }

        // A super-accept is a feed pin, separate from an ordinary acceptance.
        user.superAcceptUsed = true;
        target.incomingSuperAccepts.add(userId);
        return true;
    }

    public List<String> showStats(int topN) {
        List<UserProfile> ordered = new ArrayList<>(users.values());
        ordered.sort((first, second) -> {
            int comparison = Integer.compare(
                    second.matches.size(), first.matches.size());
            if (comparison != 0) {
                return comparison;
            }
            return first.userId.compareTo(second.userId);
        });

        int matchedUsers = 0;
        int male = 0;
        int female = 0;
        int other = 0;
        int[] ages = new int[4];

        // Recompute cohorts so profile changes are reflected immediately.
        for (UserProfile user : ordered) {
            if (!user.matches.isEmpty()) {
                matchedUsers++;
            }
            if ("MALE".equals(user.gender)) {
                male++;
            } else if ("FEMALE".equals(user.gender)) {
                female++;
            } else {
                other++;
            }

            if (user.age <= 25) {
                ages[0]++;
            } else if (user.age <= 35) {
                ages[1]++;
            } else if (user.age <= 50) {
                ages[2]++;
            } else {
                ages[3]++;
            }
        }

        List<String> topUsers = new ArrayList<>();
        int limit = Math.min(Math.max(topN, 0), ordered.size());
        for (int index = 0; index < limit; index++) {
            UserProfile user = ordered.get(index);
            topUsers.add(user.userId + "-" + user.matches.size());
        }

        List<String> result = new ArrayList<>();
        result.add("totalUsers=" + users.size());
        result.add("matchedUsers=" + matchedUsers);
        result.add("topNByMatches=" + String.join(",", topUsers));
        result.add("genderCohort=MALE:" + male
                + ",FEMALE:" + female + ",OTHER:" + other);
        result.add("ageCohort=18-25:" + ages[0]
                + ",26-35:" + ages[1] + ",36-50:" + ages[2]
                + ",51-100:" + ages[3]);
        return result;
    }
}
```
