# Find Most Active Users in Chat Logs in Java

#### Problem Statement
[https://codezym.com/question/160-most-active-users-chat-logs](https://codezym.com/question/160-most-active-users-chat-logs)


A user's activeness is just the number of different people they have chatted with. So for every user we keep a **set** of chat partners. A set ignores duplicates, so repeated chats are handled for free.


The real work is answering "who is on top right now?" quickly. We keep users in a **heap** (`PriorityQueue`) ordered by the ranking rule. When a user's activeness grows, we do not hunt for their old heap entry. We push a fresh entry, and old entries are thrown away later when they reach the top.


This keeps `registerEvent`, the most frequent call, very cheap.


Note: nothing in this problem expires, so `currentTime` never changes an answer. We accept it and ignore it.


## Solution 1: Brute Force (Sort on Every Query)

Keep a map from each user to the set of users they have chatted with. A user's activeness is the size of their set.


A chat goes both ways, so we add each user to the other's set.


For a query, put all users in a list, sort them by the ranking rule and return the first `k`.

```java
// user id -> set of distinct users they have chatted with
Map<String, Set<String>> partners = new HashMap<>();

public void registerEvent(String sender, String receiver, int currentTime) {
    // A set ignores duplicates, so repeated chats are not counted again
    partners.computeIfAbsent(sender, id -> new HashSet<>()).add(receiver);
    partners.computeIfAbsent(receiver, id -> new HashSet<>()).add(sender);
}

public List<String> getMostActiveUsers(int k, int currentTime) {
    List<String> users = new ArrayList<>(partners.keySet());

    // Sort ALL users on every call
    users.sort((a, b) -> {
        int activenessA = partners.get(a).size();
        int activenessB = partners.get(b).size();
        if (activenessA != activenessB) {
            return activenessB - activenessA; // higher activeness first
        }
        return a.compareTo(b); // smaller id first
    });
    return new ArrayList<>(users.subList(0, Math.min(k, users.size())));
}

public String getMostActiveUser(int currentTime) {
    List<String> top = getMostActiveUsers(1, currentTime);
    return top.isEmpty() ? "" : top.get(0);
}
```


### What is wrong with it?

- **Every query sorts everyone.** Each chat can bring two new users, so there can be up to 2 million users and up to 200,000 queries. Sorting all of them on every query is far too slow.
- **Most of that work is wasted.** One chat changes the activeness of only two users, yet we sort all of them again.


## Solution 2: Heap with Lazy Cleanup

Two facts make this problem much easier:

1. A new chat changes only **two** users, and only by **+1**.
2. Activeness **never goes down**.


### Keep the ranking in a heap

A heap always keeps its "smallest" item on top. We define smallest using the ranking rule: higher activeness first, then smaller id. So the top of the heap is the most active user.


Each heap item is a `RankEntry`: a user id plus the activeness that user had when the entry was pushed.


### Push new entries, skip old ones

In `registerEvent`, if the two users have chatted before, nothing changes. Chats are two-way, so checking one side is enough.


Otherwise, we add each user to the other's set. Both users now have activeness one higher, so we push a fresh `RankEntry` for each of them.


Their old entries are still inside the heap. We do not remove them, because removing an item from the middle of a heap is slow (`PriorityQueue.remove(object)` scans the whole heap). We just leave them there. They are now **outdated**.


An entry is outdated when its activeness no longer matches the user's real activeness, which is the size of their partner set. This trick is often called **lazy deletion**.


### Answering queries

**`getMostActiveUser`**: pop outdated entries from the top until the top is valid. Return its user, or `""` if the heap is empty.


**`getMostActiveUsers(k)`**: pop entries one by one. Drop the outdated ones for good. Collect the valid ones until we have `k` users or the heap runs out. Then push the valid entries back, since later calls still need them.


Each outdated entry is dropped only once in its whole life. So on average, this cleanup costs very little.


### Why a heap and not a sorted set?

A sorted set (`TreeSet`) can also keep the ranking: on each chat, remove both users' old entries and add new ones. It even reads the top `k` a bit faster.


But `registerEvent` can be called up to 1,000,000 times, far more than the queries. With the heap, each chat costs just two quick pushes, and the cleanup moves to the queries, which are much rarer.


### Why `RankEntry` stores a copy of the activeness

A heap expects an item's order to stay the same while the item sits inside it. If the heap held live users whose activeness keeps changing, its order would silently break.


A `RankEntry` never changes after it is pushed, so the heap order always stays correct. It also makes outdated entries easy to spot: just compare the stored activeness with the real one.


### Dry Run

Example 3. Heap entries are shown as `(user, activeness)` in ranking order.

| Call | What changes | Heap after the call | Returns |
|---|---|---|---|
| `registerEvent("alice", "bob", 1)` | alice: {bob}, bob: {alice} | (alice,1), (bob,1) | |
| `registerEvent("bob", "alice", 2)` | nothing, they already chatted | (alice,1), (bob,1) | |
| `registerEvent("bob", "charlie", 3)` | bob: {alice, charlie}, charlie: {bob} | (bob,2), (alice,1), (bob,1) outdated, (charlie,1) | |
| `getMostActiveUser(4)` | top (bob,2) is valid | no change | `"bob"` |
| `getMostActiveUsers(3, 5)` | pops (bob,2), (alice,1), drops outdated (bob,1), pops (charlie,1), then pushes the 3 valid entries back | (bob,2), (alice,1), (charlie,1) | `["bob", "alice", "charlie"]` |


### Code

```java
import java.util.*;

/**
 * One entry in the ranking heap: a user and the activeness they had
 * at the moment this entry was pushed. It never changes after that.
 */
class RankEntry {
    String userId;
    int activeness;

    RankEntry(String userId, int activeness) {
        this.userId = userId;
        this.activeness = activeness;
    }
}

public class ChatLogsProcessor {

    // user id -> set of distinct users they have chatted with.
    // Activeness of a user is simply the size of this set.
    Map<String, Set<String>> partners = new HashMap<>();

    // Ranking heap: higher activeness first, smaller id first on ties.
    // It can also hold outdated entries, they are skipped when we read.
    PriorityQueue<RankEntry> heap = new PriorityQueue<>((a, b) -> {
        if (a.activeness != b.activeness) {
            return b.activeness - a.activeness;
        }
        return a.userId.compareTo(b.userId);
    });

    public ChatLogsProcessor() {
    }

    public void registerEvent(String sender, String receiver, int currentTime) {
        Set<String> senderPartners = partners.computeIfAbsent(sender, id -> new HashSet<>());
        Set<String> receiverPartners = partners.computeIfAbsent(receiver, id -> new HashSet<>());

        // They have chatted before, so nothing changes
        if (senderPartners.contains(receiver)) {
            return;
        }
        senderPartners.add(receiver);
        receiverPartners.add(sender);

        // Push fresh entries. Older entries of these two users are now outdated.
        heap.add(new RankEntry(sender, senderPartners.size()));
        heap.add(new RankEntry(receiver, receiverPartners.size()));
    }

    public String getMostActiveUser(int currentTime) {
        // Throw away outdated entries until the top one is valid
        while (!heap.isEmpty() && isOutdated(heap.peek())) {
            heap.poll();
        }
        if (heap.isEmpty()) {
            return "";
        }
        return heap.peek().userId;
    }

    public List<String> getMostActiveUsers(int k, int currentTime) {
        List<String> result = new ArrayList<>();
        List<RankEntry> validEntries = new ArrayList<>();

        // Pop in ranking order until we have k valid users
        while (!heap.isEmpty() && result.size() < k) {
            RankEntry entry = heap.poll();
            if (isOutdated(entry)) {
                continue; // dropped for good, it is never needed again
            }
            result.add(entry.userId);
            validEntries.add(entry);
        }

        // Put the valid entries back, later calls still need them
        heap.addAll(validEntries);
        return result;
    }

    /**
     * An entry is outdated if the user's activeness has grown
     * since the entry was pushed.
     */
    boolean isOutdated(RankEntry entry) {
        return entry.activeness != partners.get(entry.userId).size();
    }
}
```


## Complexity

Let `P` be the number of different user pairs who have chatted. The partner sets hold `2P` names in total, and the heap never holds more than `2P` entries.

| Method | Brute Force | Heap with Lazy Cleanup |
|---|---|---|
| `registerEvent` | O(1) | O(log P) |
| `getMostActiveUser` | O(U log U) | O(log P) on average |
| `getMostActiveUsers` | O(U log U) | O(k log P) on average |

Here `U` is the number of users. Space is O(P) for both.


"On average" means the cost of dropping outdated entries is spread over the chats that created them, since each entry is dropped at most once.