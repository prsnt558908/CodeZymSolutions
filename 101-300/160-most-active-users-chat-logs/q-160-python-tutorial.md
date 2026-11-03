# Find Most Active Users in Chat Logs in Python

#### Problem Statement
[https://codezym.com/question/160-most-active-users-chat-logs](https://codezym.com/question/160-most-active-users-chat-logs)


A user's activeness is just the number of different people they have chatted with. So for every user we keep a **set** of chat partners. A set ignores duplicates, so repeated chats are handled for free.


The real work is answering "who is on top right now?" quickly. We keep users in a **heap** (`heapq`) ordered by the ranking rule. When a user's activeness grows, we do not hunt for their old heap entry. We push a fresh entry, and old entries are thrown away later when they reach the top.


This keeps `registerEvent`, the most frequent call, very cheap.


Note: nothing in this problem expires, so `currentTime` never changes an answer. We accept it and ignore it.


## Solution 1: Brute Force (Sort on Every Query)

Keep a dictionary from each user to the set of users they have chatted with. A user's activeness is the size of their set.


We use `defaultdict(set)`, which creates an empty set the first time we see a user.


A chat goes both ways, so we add each user to the other's set.


For a query, sort all users by the ranking rule and return the first `k`.

```python
class ChatLogsProcessor:
    def __init__(self):
        # user id -> set of distinct users they have chatted with
        self.partners = defaultdict(set)

    def registerEvent(self, sender: str, receiver: str,
                      currentTime: int) -> None:
        # A set ignores duplicates, so repeated chats are not counted again
        self.partners[sender].add(receiver)
        self.partners[receiver].add(sender)

    def getMostActiveUsers(self, k: int, currentTime: int) -> List[str]:
        # Sort ALL users on every call:
        # higher activeness first, then smaller id first
        users = sorted(self.partners,
                       key=lambda user: (-len(self.partners[user]), user))
        return users[:k]

    def getMostActiveUser(self, currentTime: int) -> str:
        top = self.getMostActiveUsers(1, currentTime)
        return top[0] if top else ""
```


### What is wrong with it?

- **Every query sorts everyone.** Each chat can bring two new users, so there can be up to 2 million users and up to 200,000 queries. Sorting all of them on every query is far too slow.
- **Most of that work is wasted.** One chat changes the activeness of only two users, yet we sort all of them again.


## Solution 2: Heap with Lazy Cleanup

Two facts make this problem much easier:

1. A new chat changes only **two** users, and only by **+1**.
2. Activeness **never goes down**.


### Keep the ranking in a heap

`heapq` always keeps the smallest item at `heap[0]`. Each heap item is a tuple `(-activeness, userId)`.


Tuples are compared item by item. The minus sign makes higher activeness smaller, so it comes first. On a tie, the smaller id comes first. That is exactly the ranking rule, so `heap[0]` is the most active user.


The activeness inside a tuple is the value that user had when the tuple was pushed.


### Push new entries, skip old ones

In `registerEvent`, if the two users have chatted before, nothing changes. Chats are two-way, so checking one side is enough.


Otherwise, we add each user to the other's set. Both users now have activeness one higher, so we push a fresh tuple for each of them.


Their old tuples are still inside the heap. We do not remove them, because removing an item from the middle of a heap is slow (`heapq` has no remove, so we would have to search the whole list and fix the heap again). We just leave them there. They are now **outdated**.


An entry is outdated when its activeness no longer matches the user's real activeness, which is the size of their partner set. This trick is often called **lazy deletion**.


### Answering queries

**`getMostActiveUser`**: pop outdated entries from the top until the top is valid. Return its user, or `""` if the heap is empty.


**`getMostActiveUsers(k)`**: pop entries one by one. Drop the outdated ones for good. Collect the valid ones until we have `k` users or the heap runs out. Then push the valid entries back, since later calls still need them.


Each outdated entry is dropped only once in its whole life. So on average, this cleanup costs very little.


### Why a heap and not a sorted set?

A sorted set can also keep the ranking: on each chat, remove both users' old entries and add new ones. It even reads the top `k` a bit faster. But Python's standard library has no sorted set, while `heapq` is built in.


It is also the better fit. `registerEvent` can be called up to 1,000,000 times, far more than the queries. With the heap, each chat costs just two quick pushes, and the cleanup moves to the queries, which are much rarer.


### Why each entry stores a copy of the activeness

A heap expects an item's order to stay the same while the item sits inside it. If the heap held live users whose activeness keeps changing, its order would silently break.


A tuple never changes after it is pushed, so the heap order always stays correct. It also makes outdated entries easy to spot: just compare the stored activeness with the real one.


### Dry Run

Example 3. Heap entries are shown as `(-activeness, user)` in ranking order.

| Call | What changes | Heap after the call | Returns |
|---|---|---|---|
| `registerEvent("alice", "bob", 1)` | alice: {bob}, bob: {alice} | (-1, alice), (-1, bob) | |
| `registerEvent("bob", "alice", 2)` | nothing, they already chatted | (-1, alice), (-1, bob) | |
| `registerEvent("bob", "charlie", 3)` | bob: {alice, charlie}, charlie: {bob} | (-2, bob), (-1, alice), (-1, bob) outdated, (-1, charlie) | |
| `getMostActiveUser(4)` | top (-2, bob) is valid | no change | `"bob"` |
| `getMostActiveUsers(3, 5)` | pops (-2, bob), (-1, alice), drops outdated (-1, bob), pops (-1, charlie), then pushes the 3 valid entries back | (-2, bob), (-1, alice), (-1, charlie) | `["bob", "alice", "charlie"]` |


### Code

```python
import heapq
from collections import defaultdict
from typing import List, Tuple


class ChatLogsProcessor:
    def __init__(self):
        # user id -> set of distinct users they have chatted with.
        # Activeness of a user is simply the size of this set.
        self.partners = defaultdict(set)

        # Ranking heap of (-activeness, userId) tuples.
        # The minus sign puts higher activeness on top, and on ties
        # the smaller id comes first. Outdated entries are skipped.
        self.heap = []

    def registerEvent(self, sender: str, receiver: str,
                      currentTime: int) -> None:
        senderPartners = self.partners[sender]
        receiverPartners = self.partners[receiver]

        # They have chatted before, so nothing changes
        if receiver in senderPartners:
            return
        senderPartners.add(receiver)
        receiverPartners.add(sender)

        # Push fresh entries, older entries of these users are now outdated
        heapq.heappush(self.heap, (-len(senderPartners), sender))
        heapq.heappush(self.heap, (-len(receiverPartners), receiver))

    def getMostActiveUser(self, currentTime: int) -> str:
        # Throw away outdated entries until the top one is valid
        while self.heap and self.isOutdated(self.heap[0]):
            heapq.heappop(self.heap)
        if not self.heap:
            return ""
        return self.heap[0][1]

    def getMostActiveUsers(self, k: int, currentTime: int) -> List[str]:
        result = []
        validEntries = []

        # Pop in ranking order until we have k valid users
        while self.heap and len(result) < k:
            entry = heapq.heappop(self.heap)
            if self.isOutdated(entry):
                continue  # dropped for good, it is never needed again
            result.append(entry[1])
            validEntries.append(entry)

        # Put the valid entries back, later calls still need them
        for entry in validEntries:
            heapq.heappush(self.heap, entry)
        return result

    def isOutdated(self, entry: Tuple[int, str]) -> bool:
        """True if the user's activeness grew after this entry was pushed."""
        negativeActiveness, userId = entry
        return -negativeActiveness != len(self.partners[userId])
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