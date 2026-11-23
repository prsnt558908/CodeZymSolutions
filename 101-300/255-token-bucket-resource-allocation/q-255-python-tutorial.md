# Token Bucket Resource Allocation in Python

#### Problem Statement
[https://codezym.com/question/255-token-bucket-resource-allocation](https://codezym.com/question/255-token-bucket-resource-allocation)

This problem is about a shared bucket of tokens, where every granted request comes with a fixed 1 hour timer.

The whole solution rests on one simple observation. Every grant lives for exactly the same 1 hour, and time only moves forward. So the grant created first is always the grant that expires first. This means a plain queue is enough to clean up expired grants, because we only ever need to look at its front.

For early release, we keep a map from each user to their own queue of active grants, so we can find a user's tokens instantly. Released grants are only marked with a flag, and the cleanup simply skips them when they reach the front of the main queue later.

---

## Problem in Short

- The bucket starts with `capacity` free tokens.
- `requestTokens()` succeeds only if enough tokens are free. A user can hold many grants at the same time.
- A grant made at time `t` expires when `currentTimeMs >= t + 3,600,000`. So we store `expiryTimeMs = t + 3,600,000` and treat a grant as expired when `expiryTimeMs <= currentTimeMs`.
- `releaseTokens()` returns all active tokens of one user before they expire.
- Before every method call, all expired grants must return their tokens to the bucket.

---

## Approach 1: Brute Force (Scan Every Grant)

The simplest idea is to keep every active grant in one list.

Each grant is a small `TokenGrant` object that remembers `userId`, `tokens` and `expiryTimeMs`.

Before every method call, we scan the whole list. Expired grants give their tokens back and are removed.

For `releaseTokens()`, we scan the list again and remove every grant that belongs to that user.

```python
def __init__(self, capacity: int):
    self.oneHourMs = 3_600_000
    self.availableTokens = capacity
    self.activeGrants = []  # every active grant, in one list

def removeExpiredGrants(self, currentTimeMs: int) -> None:
    stillActive = []
    for grant in self.activeGrants:
        if grant.expiryTimeMs <= currentTimeMs:
            self.availableTokens += grant.tokens
        else:
            stillActive.append(grant)
    self.activeGrants = stillActive

def releaseTokens(self, userId: str, currentTimeMs: int) -> str:
    self.removeExpiredGrants(currentTimeMs)
    releasedTokens = 0
    stillActive = []
    for grant in self.activeGrants:
        if grant.userId == userId:
            releasedTokens += grant.tokens
        else:
            stillActive.append(grant)
    self.activeGrants = stillActive
    self.availableTokens += releasedTokens
    return "RELEASED" if releasedTokens > 0 else "NO_ACTIVE_TOKENS"
```

`requestTokens()` just checks `tokens <= self.availableTokens` and adds a new grant to the list.

### Problem with this approach

Every call scans all active grants, even when nothing has expired.

If there are `n` active grants, each call costs `O(n)`. With many calls and many grants, this gets slow very quickly.

We are wasting work in two places:

1. Checking grants that are nowhere close to expiring.
2. Checking grants of other users during release.

Let us fix them one by one.

---

## Approach 2: Queue + Map (Optimal)

### Fix 1: The oldest grant expires first

Every grant lives for exactly 1 hour, and `currentTimeMs` never goes backwards.

So if grant A was created before grant B, then A also expires before B (or at the same time).

In other words, grants expire in the same order in which they were created. A queue keeps exactly this order for free.

During cleanup, we look only at the front of the queue:

- If the front grant has expired, return its tokens, remove it and check the next one.
- If the front grant has not expired, stop. Every grant behind it is newer, so none of them has expired either.

You might think of a min heap (`heapq`) sorted by expiry time. It works, but it is not needed here. Since every grant lives the same 1 hour, the plain queue is already sorted by expiry time.

### Fix 2: Release without scanning everyone

We keep a dictionary from `userId` to a queue of that user's active grants.

On release, we pick that user's queue straight from the dictionary and return all its tokens. Other users are never touched.

But there is a catch. The same grants are also sitting inside the main `expiryQueue`, and removing items from the middle of a queue is slow.

So we do not remove them. We only mark each one with `released = True`.

Later, when a released grant reaches the front of `expiryQueue`, cleanup sees the flag and skips it. Its tokens were already returned once, so they must not be returned again.

### Keeping each user's queue clean

When a grant expires normally, we also remove it from its user's queue.

A user's queue is also in creation order. Every older grant of this user has already left that queue, either by expiring earlier or by being released. So the expiring grant is always at the front of its user's queue, and one `popleft()` removes it.

If a user's queue becomes empty, we remove that user from the dictionary. So any user found in the dictionary always holds some active tokens.

### Why these classes and data structures

- **`TokenGrant`**: one object per granted request. Both queues hold the same object, so setting `released = True` once is seen everywhere.
- **`expiryQueue`** (`deque` of `TokenGrant`): every grant in creation order, which is also expiry order. Cleanup only checks the front. We use a `deque` and not a list, because `popleft()` is `O(1)` while `list.pop(0)` is `O(n)`.
- **`userGrants`** (`dict` from `userId` to a `deque` of grants): gives one user's active grants directly, so release never scans other users.
- **`availableTokens`**: a running count of free tokens, so `getAvailableTokens()` is instant once cleanup is done.

### Dry run

`capacity = 6`. In the table, `sam:2` means a grant of 2 tokens held by sam.

| Call | Output | Available | expiryQueue (front first) |
|---|---|---|---|
| `requestTokens("sam", 2, 10)` | `GRANTED` | 4 | sam:2 |
| `requestTokens("sam", 3, 20)` | `GRANTED` | 1 | sam:2, sam:3 |
| `releaseTokens("sam", 30)` | `RELEASED` | 6 | sam:2 (released), sam:3 (released) |
| `getAvailableTokens(3600020)` | `6` | 6 | empty |

In the last call, both grants expire. Both are marked as released, so cleanup skips them.

Without the flag, we would add the same 5 tokens a second time and report 11, which is more than the capacity.

### Complexity

- `requestTokens()`: `O(1)` on average.
- `getAvailableTokens()`: `O(1)` on average.
- `releaseTokens()`: `O(k)`, where `k` is the number of active grants of that user.
- Space: `O(g)`, where `g` is the number of grants created in the last 1 hour.

A single cleanup can remove many grants at once. But each grant is removed only once in its whole life, so on average cleanup costs `O(1)` per call.

### Python Code

```python
from collections import deque


class TokenGrant:
    """One granted request: who holds the tokens, how many, and when they expire."""

    def __init__(self, userId: str, tokens: int, expiryTimeMs: int):
        self.userId = userId
        self.tokens = tokens
        self.expiryTimeMs = expiryTimeMs
        # True once the user has manually released this grant
        self.released = False


class TokenBucketResourceAllocation:

    def __init__(self, capacity: int):
        self.oneHourMs = 3_600_000
        self.availableTokens = capacity
        # Every grant in the order it was created.
        # All grants live exactly 1 hour, so this is also the order in which they expire.
        self.expiryQueue = deque()
        # userId -> deque of that user's active grants, oldest first
        self.userGrants = {}

    def requestTokens(self, userId: str, tokens: int, currentTimeMs: int) -> str:
        self.removeExpiredGrants(currentTimeMs)
        if tokens > self.availableTokens:
            return "REJECTED"
        grant = TokenGrant(userId, tokens, currentTimeMs + self.oneHourMs)
        self.availableTokens -= tokens
        self.expiryQueue.append(grant)
        if userId not in self.userGrants:
            self.userGrants[userId] = deque()
        self.userGrants[userId].append(grant)
        return "GRANTED"

    def releaseTokens(self, userId: str, currentTimeMs: int) -> str:
        self.removeExpiredGrants(currentTimeMs)
        grants = self.userGrants.pop(userId, None)
        if not grants:
            return "NO_ACTIVE_TOKENS"
        for grant in grants:
            # Tokens go back right now. The grant is still inside expiryQueue,
            # so the flag tells removeExpiredGrants() to skip it later.
            grant.released = True
            self.availableTokens += grant.tokens
        return "RELEASED"

    def getAvailableTokens(self, currentTimeMs: int) -> int:
        self.removeExpiredGrants(currentTimeMs)
        return self.availableTokens

    def removeExpiredGrants(self, currentTimeMs: int) -> None:
        """Returns the tokens of every expired grant to the bucket.

        Only the front of the queue is checked, because the oldest grant expires first.
        """
        while self.expiryQueue and self.expiryQueue[0].expiryTimeMs <= currentTimeMs:
            grant = self.expiryQueue.popleft()
            if grant.released:
                continue  # tokens were already returned by releaseTokens()
            self.availableTokens += grant.tokens

            # This grant is also the oldest grant in its user's queue
            grants = self.userGrants[grant.userId]
            grants.popleft()
            if not grants:
                del self.userGrants[grant.userId]
```