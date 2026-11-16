# Design GPU Credit Calculator in Python

#### Problem Statement
[https://codezym.com/question/89-design-gpu-credit-calculator](https://codezym.com/question/89-design-gpu-credit-calculator)


## Core Idea

We need to add credit grants, spend credits, and ask for the balance at any timestamp, in the past or in the future.


The key fact is that `addCredit` and `useCredit` always arrive in time order. So when a spend happens, every earlier event is already known, and no new event can ever land before it. We can decide right away which grants this spend uses, and that decision never changes.


Next, instead of rebuilding every grant for each query, we look at the balance as a line over time. The line only jumps at a few moments: when a grant starts, when credits are spent, and when a grant expires.


If we store every jump, the balance at timestamp `T` is just the sum of all jumps at or before `T`.


A min heap gives us the grant to spend from next, and a segment tree adds up the jumps quickly.


We will get there in three steps: replay the whole ledger, then store the balance changes, then keep those changes in a segment tree.


## Rules to Keep in Mind

- A grant added at `timestamp` with `expiration` is usable while `timestamp <= t < timestamp + expiration`. We call `timestamp + expiration` its **expiry time**: the first moment it can no longer be used.
- So `expiration = 0` gives a grant that is never usable.
- A spend can only use grants that are active at the spend time. It takes from the earliest expiry time first, and from the smaller `creditId` on a tie.
- A failed spend is not recorded, so it changes nothing.
- `getBalance(T)` applies only the adds and spends at times `<= T`, and counts only the grants that are active at `T`.


## Solution 1: Brute Force, Replay the Ledger

The statement already hints at this approach: keep a ledger of every successful call, and replay it from the start whenever we need a balance.


Two small classes hold the data:

- `LedgerEvent`: one successful call, with `isAdd`, `time`, `creditId`, `amount` and `expiryTime`
- `CreditGrant`: one grant, with `creditId`, `remaining` credits, `startTime` and `expiryTime`. Its `isActive(t)` checks `startTime <= t < expiryTime`.

`self.ledger` is a list of every successful call, in the order they happened. `addCredit` just rejects a duplicate id and adds an ADD event to it.

```python
def getBalance(self, timestamp: int) -> int:
    return sum(grant.remaining for grant in self.replay(timestamp)
               if grant.isActive(timestamp))

def useCredit(self, timestamp: int, amount: int) -> bool:
    # Every event in the ledger is at or before 'timestamp',
    # so getBalance(timestamp) is the credit we can spend right now.
    if self.getBalance(timestamp) < amount:
        return False
    self.ledger.append(LedgerEvent(False, timestamp, None, amount, 0))
    return True

def replay(self, time: int) -> list:
    """Rebuilds every grant by replaying the ledger from the start, up to 'time'."""
    grants = []
    for event in self.ledger:
        if event.time > time:
            break  # the ledger is sorted by time
        if event.isAdd:
            grants.append(CreditGrant(event.creditId, event.amount, event.time, event.expiryTime))
            continue
        # USE event: sort the active grants and take credits in that order
        active = [grant for grant in grants if grant.isActive(event.time)]
        active.sort(key=lambda grant: (grant.expiryTime, grant.creditId))
        need = event.amount
        for grant in active:
            take = min(need, grant.remaining)
            grant.remaining -= take
            need -= take
    return grants
```

**Why it is slow:** every call replays the ledger from the very first event, and every replayed spend sorts the active grants again. With `n` events and `g` grants, a single call can cost O(n × g log g).


Notice the waste: no matter how many times we replay a spend, it always takes credits from the same grants.


## Solution 2: Record Every Balance Change

### Decide each spend only once

As we saw, no event can ever land before a spend. So a replay would always split a spend over the same grants. We split it once, right away, and only remember how it changes the balance.


### Pick grants with a min heap

To spend, we always want the active grant with the earliest expiry time, and the smaller `creditId` on a tie. A min heap (`heapq`) of tuples `(expiryTime, creditId, grant)` keeps exactly that grant on top.


Tuples are compared item by item, and every `creditId` is unique. So two tuples never tie on the first two items, and Python never has to compare two grant objects.


Spend times never go back. So once a grant is expired for one spend, it is expired for every later spend too, and we can remove it from the heap for good. A grant whose credits run out is removed as well.


We also keep `queueTotal`: the credits left in all grants of the heap. After the expired grants are removed, every grant in the heap is active. So `queueTotal` tells us at once whether there is enough credit to spend.


### The balance as a timeline of changes

Picture the balance as a line over time. It moves only at these moments:

- a grant starts: the line goes up by its amount
- credits are spent: the line goes down by the amount spent
- a grant expires: the line goes down by whatever is still left in it


We store these jumps in a dict: `timestamp -> total change at that timestamp`. The balance at timestamp `T` is the sum of all changes at timestamps `<= T`.


The expiry jump is the tricky one, because we do not know yet how much will be left. So when a grant is added, we record `-amount` at its expiry time, as if the whole grant will vanish.


Each time we take `take` credits from a grant, those credits are already gone and cannot vanish again. So we also add `+take` at its expiry time, which makes that drop smaller.


Here is Example 1:

| Call | Changes recorded |
|---|---|
| `addCredit("b", 5, 10, 5)` | `+5` at 10, `-5` at 15 |
| `addCredit("a", 7, 11, 4)` | `+7` at 11, `-7` at 15 |
| `addCredit("c", 10, 12, 10)` | `+10` at 12, `-10` at 22 |
| `useCredit(14, 21)`, 7 from "a" | `-7` at 14, `+7` at 15 |
| same spend, 5 from "b" | `-5` at 14, `+5` at 15 |
| same spend, 9 from "c" | `-9` at 14, `+9` at 22 |


The dict ends up as `10: +5`, `11: +7`, `12: +10`, `14: -21`, `15: 0`, `22: -1`.


So `getBalance(14) = 5 + 7 + 10 - 21 = 1`, `getBalance(21)` is also `1`, and `getBalance(22) = 1 - 1 = 0`. These match the expected output.


### Code

```python
import heapq


class CreditGrant:
    """One credit grant added by addCredit()."""

    def __init__(self, creditId: str, amount: int, expiryTime: int):
        self.creditId = creditId
        self.remaining = amount        # credits of this grant that are not spent yet
        self.expiryTime = expiryTime   # first timestamp at which this grant is expired


class GPUCreditCalculator:

    def __init__(self):
        # ids of all grants ever added, used to reject duplicates
        self.creditIds = set()

        # grants we can still spend from, stored as (expiryTime, creditId, grant).
        # heapq keeps the smallest tuple on top:
        # earliest expiry first, then smaller creditId.
        self.spendQueue = []

        # total credits left in all grants of spendQueue
        self.queueTotal = 0

        # timestamp -> how much the balance goes up (+) or down (-) at that timestamp
        self.balanceChanges = {}

    def addCredit(self, creditId: str, amount: int, timestamp: int, expiration: int) -> bool:
        if creditId in self.creditIds:
            return False
        self.creditIds.add(creditId)

        expiryTime = timestamp + expiration
        grant = CreditGrant(creditId, amount, expiryTime)
        heapq.heappush(self.spendQueue, (expiryTime, creditId, grant))
        self.queueTotal += amount

        # balance goes up when the grant starts and comes down when it expires
        self.addChange(timestamp, amount)
        self.addChange(expiryTime, -amount)
        return True

    def useCredit(self, timestamp: int, amount: int) -> bool:
        # Spends never go back in time, so an expired grant
        # can never be used again. Remove it for good.
        # spendQueue[0] is the top tuple, so spendQueue[0][0] is its expiryTime
        while self.spendQueue and self.spendQueue[0][0] <= timestamp:
            expiredGrant = heapq.heappop(self.spendQueue)[2]
            self.queueTotal -= expiredGrant.remaining
        if self.queueTotal < amount:
            return False

        while amount > 0:
            grant = self.spendQueue[0][2]  # the grant on top of the heap
            take = min(amount, grant.remaining)
            grant.remaining -= take
            self.queueTotal -= take
            amount -= take

            # balance comes down right now ...
            self.addChange(timestamp, -take)
            # ... and these credits are no longer there to vanish at expiry
            self.addChange(grant.expiryTime, take)

            if grant.remaining == 0:
                heapq.heappop(self.spendQueue)
        return True

    def getBalance(self, timestamp: int) -> int:
        # balance at a timestamp = sum of all changes at or before it
        balance = 0
        for time, change in self.balanceChanges.items():
            if time <= timestamp:
                balance += change
        return balance

    def addChange(self, timestamp: int, change: int) -> None:
        """Adds 'change' to the balance change already stored at 'timestamp'."""
        self.balanceChanges[timestamp] = self.balanceChanges.get(timestamp, 0) + change
```

**Why `getBalance` is still slow:** it visits every timestamp stored in the dict. Each `addCredit` can add two new timestamps, so with `100,000` calls and many queries, this adds up to billions of steps.


## Solution 3: Segment Tree Over Time (Optimal)

The timeline needs only two operations:

- add a change at one timestamp
- get the sum of all changes at timestamps `<= T`

A segment tree does both by visiting just one node per level.


### What the tree stores

Every timestamp is a leaf. Every node above the leaves stores the sum of the changes of all the leaves below it. Here is a tiny tree for timestamps `0` to `7`:

```
                      [0-7]
                 /            \
           [0-3]                [4-7]
          /     \              /     \
      [0-1]     [2-3]      [4-5]     [6-7]
      /   \     /   \      /   \     /   \
     0     1   2     3    4     5   6     7    <- timestamp
```


### Adding a change

Add it to the leaf, then to every node on the path up to the root. For example, a change at timestamp `5` updates `5`, `[4-5]`, `[4-7]` and `[0-7]`.


### Sum of changes up to T

Start with the leaf of `T` and walk up to the root. Whenever the current node is a right child, its left sibling covers only smaller timestamps, so we add that sibling as a whole.


For `T = 5`:

- start with leaf `5` itself
- leaf `5` is a right child, so add leaf `4`
- its parent `[4-5]` is a left child, so add nothing
- the next parent `[4-7]` is a right child, so add `[0-3]`

The result is `[0-3] + 4 + 5`, which covers exactly the timestamps `0` to `5`.


### Storing the tree in a dict

We number the nodes like this:

- the root is node `1`
- the children of node `i` are `2 * i` and `2 * i + 1`
- the parent of node `i` is `i // 2`
- the leaf of timestamp `t` is node `LEAVES + t`

A right child always has an odd number, and its left sibling is the number just before it.


The largest expiry time is `10^9 + 10^9 = 2 × 10^9`. `LEAVES = 2^31` is the smallest power of two with room for all these timestamps, and a list for this tree would need over 4 billion slots.


But one change touches only the 32 nodes on its path. So we keep only the touched nodes in a dict: node number -> sum. A node missing from the dict has sum `0`.


**Why not a sorted list of timestamps:** binary search finds one timestamp quickly, but adding up all changes before `T` still means walking over them one by one, just like Solution 2.


Keeping running totals does not fix it, because one new change in the middle would change every total after it.


### Code

```python
import heapq


class CreditGrant:
    """One credit grant added by addCredit()."""

    def __init__(self, creditId: str, amount: int, expiryTime: int):
        self.creditId = creditId
        self.remaining = amount        # credits of this grant that are not spent yet
        self.expiryTime = expiryTime   # first timestamp at which this grant is expired


class GPUCreditCalculator:

    def __init__(self):
        # ids of all grants ever added, used to reject duplicates
        self.creditIds = set()

        # grants we can still spend from, stored as (expiryTime, creditId, grant).
        # heapq keeps the smallest tuple on top:
        # earliest expiry first, then smaller creditId.
        self.spendQueue = []

        # total credits left in all grants of spendQueue
        self.queueTotal = 0

        # Segment tree over timestamps 0 .. 2^31 - 1, one leaf per timestamp.
        # Node 1 is the root. Children of node i are 2 * i and 2 * i + 1.
        # The leaf of timestamp t is node LEAVES + t.
        self.LEAVES = 1 << 31

        # node -> sum of balance changes of all timestamps under that node.
        # Only nodes that were touched are stored. A missing node means 0.
        self.tree = {}

    def addCredit(self, creditId: str, amount: int, timestamp: int, expiration: int) -> bool:
        if creditId in self.creditIds:
            return False
        self.creditIds.add(creditId)

        expiryTime = timestamp + expiration
        grant = CreditGrant(creditId, amount, expiryTime)
        heapq.heappush(self.spendQueue, (expiryTime, creditId, grant))
        self.queueTotal += amount

        # balance goes up when the grant starts and comes down when it expires
        self.addChange(timestamp, amount)
        self.addChange(expiryTime, -amount)
        return True

    def useCredit(self, timestamp: int, amount: int) -> bool:
        # Spends never go back in time, so an expired grant
        # can never be used again. Remove it for good.
        # spendQueue[0] is the top tuple, so spendQueue[0][0] is its expiryTime
        while self.spendQueue and self.spendQueue[0][0] <= timestamp:
            expiredGrant = heapq.heappop(self.spendQueue)[2]
            self.queueTotal -= expiredGrant.remaining
        if self.queueTotal < amount:
            return False

        while amount > 0:
            grant = self.spendQueue[0][2]  # the grant on top of the heap
            take = min(amount, grant.remaining)
            grant.remaining -= take
            self.queueTotal -= take
            amount -= take

            # balance comes down right now ...
            self.addChange(timestamp, -take)
            # ... and these credits are no longer there to vanish at expiry
            self.addChange(grant.expiryTime, take)

            if grant.remaining == 0:
                heapq.heappop(self.spendQueue)
        return True

    def getBalance(self, timestamp: int) -> int:
        # balance at a timestamp = sum of all changes at or before it
        return self.sumUpTo(timestamp)

    def addChange(self, timestamp: int, change: int) -> None:
        """Adds 'change' to the leaf of 'timestamp' and to every node above it."""
        node = self.LEAVES + timestamp
        while node >= 1:
            self.tree[node] = self.tree.get(node, 0) + change
            node //= 2

    def sumUpTo(self, timestamp: int) -> int:
        """Sum of all balance changes at timestamps 0 .. timestamp."""
        node = self.LEAVES + timestamp
        total = self.tree.get(node, 0)
        while node > 1:
            # A right child (odd node) has its left sibling just before it.
            # Every timestamp under that sibling is smaller, so add it whole.
            if node % 2 == 1:
                total += self.tree.get(node - 1, 0)
            node //= 2
        return total
```


## Key Data Structures

| Data structure | Why we need it |
|---|---|
| `CreditGrant` | One grant: its id, the credits left in it and its expiry time. |
| `creditIds` (set) | Rejects a duplicate `creditId` in O(1). |
| `spendQueue` (heap) | Keeps the next grant to spend from on top: earliest expiry time, then smaller `creditId`. |
| `queueTotal` | Credits left in the heap. Once expired grants are removed, it tells in O(1) if a spend can succeed. |
| `balanceChanges` (dict) | Solution 2 only: timestamp -> total balance change at that timestamp. |
| `tree` (dict) | Segment tree of balance changes over time. Gives the balance at any timestamp by visiting at most 32 nodes. |


## Complexity

Let `n` be the number of calls, `g` the number of grants and `K` the number of different timestamps in the change dict.

| | Brute Force | Balance Changes | Segment Tree |
|---|---|---|---|
| `addCredit` | O(1) | O(log g) | O(log g + 32) |
| `useCredit` | O(n × g log g) | O(log g) per grant touched | O(log g + 32) per grant touched |
| `getBalance` | O(n × g log g) | O(K) | O(32) |
| Extra space | O(n) | O(n) | O(32 × n) |


A spend touches a grant when it takes credits from it or removes it from the heap. Every grant leaves the heap only once, so across all calls this extra work stays small.


Every tree update or sum visits one node per level, and the tree has 32 levels.