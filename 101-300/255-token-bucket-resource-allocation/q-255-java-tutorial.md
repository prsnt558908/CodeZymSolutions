# Token Bucket Resource Allocation in Java

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

```java
List<TokenGrant> activeGrants = new ArrayList<>();

void removeExpiredGrants(long currentTimeMs) {
    for (TokenGrant grant : activeGrants) {
        if (grant.expiryTimeMs <= currentTimeMs) {
            availableTokens += grant.tokens;
        }
    }
    activeGrants.removeIf(grant -> grant.expiryTimeMs <= currentTimeMs);
}

public String releaseTokens(String userId, long currentTimeMs) {
    removeExpiredGrants(currentTimeMs);
    int releasedTokens = 0;
    for (TokenGrant grant : activeGrants) {
        if (grant.userId.equals(userId)) {
            releasedTokens += grant.tokens;
        }
    }
    activeGrants.removeIf(grant -> grant.userId.equals(userId));
    availableTokens += releasedTokens;
    return releasedTokens > 0 ? "RELEASED" : "NO_ACTIVE_TOKENS";
}
```

`requestTokens()` just checks `tokens <= availableTokens` and adds a new grant to the list.

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

You might think of a `PriorityQueue` sorted by expiry time. It works, but it is not needed here. Since every grant lives the same 1 hour, the plain queue is already sorted by expiry time.

### Fix 2: Release without scanning everyone

We keep a map from `userId` to a queue of that user's active grants.

On release, we pick that user's queue straight from the map and return all its tokens. Other users are never touched.

But there is a catch. The same grants are also sitting inside the main `expiryQueue`, and removing items from the middle of a queue is slow.

So we do not remove them. We only mark each one with `released = true`.

Later, when a released grant reaches the front of `expiryQueue`, cleanup sees the flag and skips it. Its tokens were already returned once, so they must not be returned again.

### Keeping each user's queue clean

When a grant expires normally, we also remove it from its user's queue.

A user's queue is also in creation order. Every older grant of this user has already left that queue, either by expiring earlier or by being released. So the expiring grant is always at the front of its user's queue, and one `poll()` removes it.

If a user's queue becomes empty, we remove that user from the map. So any user found in the map always holds some active tokens.

### Why these classes and data structures

- **`TokenGrant`**: one object per granted request. Both queues hold the same object, so setting `released = true` once is seen everywhere.
- **`expiryQueue`** (`Queue<TokenGrant>`): every grant in creation order, which is also expiry order. Cleanup only checks the front.
- **`userGrants`** (`Map<String, Queue<TokenGrant>>`): gives one user's active grants directly, so release never scans other users.
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

### Java Code

```java
import java.util.*;

/**
 * One granted request: who holds the tokens, how many, and when they expire.
 */
class TokenGrant {
    String userId;
    int tokens;
    long expiryTimeMs;
    // true once the user has manually released this grant
    boolean released;

    TokenGrant(String userId, int tokens, long expiryTimeMs) {
        this.userId = userId;
        this.tokens = tokens;
        this.expiryTimeMs = expiryTimeMs;
        this.released = false;
    }
}

public class TokenBucketResourceAllocation {

    long oneHourMs = 3_600_000L;

    int availableTokens;

    // Every grant in the order it was created.
    // All grants live exactly 1 hour, so this is also the order in which they expire.
    Queue<TokenGrant> expiryQueue = new ArrayDeque<>();

    // userId -> that user's active grants, oldest first
    Map<String, Queue<TokenGrant>> userGrants = new HashMap<>();

    public TokenBucketResourceAllocation(int capacity) {
        this.availableTokens = capacity;
    }

    public String requestTokens(String userId, int tokens, long currentTimeMs) {
        removeExpiredGrants(currentTimeMs);
        if (tokens > availableTokens) {
            return "REJECTED";
        }
        TokenGrant grant = new TokenGrant(userId, tokens, currentTimeMs + oneHourMs);
        availableTokens -= tokens;
        expiryQueue.add(grant);
        userGrants.computeIfAbsent(userId, id -> new ArrayDeque<>()).add(grant);
        return "GRANTED";
    }

    public String releaseTokens(String userId, long currentTimeMs) {
        removeExpiredGrants(currentTimeMs);
        Queue<TokenGrant> grants = userGrants.remove(userId);
        if (grants == null || grants.isEmpty()) {
            return "NO_ACTIVE_TOKENS";
        }
        for (TokenGrant grant : grants) {
            // Tokens go back right now. The grant is still inside expiryQueue,
            // so the flag tells removeExpiredGrants() to skip it later.
            grant.released = true;
            availableTokens += grant.tokens;
        }
        return "RELEASED";
    }

    public int getAvailableTokens(long currentTimeMs) {
        removeExpiredGrants(currentTimeMs);
        return availableTokens;
    }

    /**
     * Returns the tokens of every expired grant to the bucket.
     * Only the front of the queue is checked, because the oldest grant expires first.
     */
    void removeExpiredGrants(long currentTimeMs) {
        while (!expiryQueue.isEmpty() && expiryQueue.peek().expiryTimeMs <= currentTimeMs) {
            TokenGrant grant = expiryQueue.poll();
            if (grant.released) {
                continue; // tokens were already returned by releaseTokens()
            }
            availableTokens += grant.tokens;

            // This grant is also the oldest grant in its user's queue
            Queue<TokenGrant> grants = userGrants.get(grant.userId);
            grants.poll();
            if (grants.isEmpty()) {
                userGrants.remove(grant.userId);
            }
        }
    }
}
```