# IPO Share Allocation With Bids in Python

#### Problem Statement
[https://codezym.com/question/264-ipo-share-allocation-with-bids](https://codezym.com/question/264-ipo-share-allocation-with-bids)

## Core Idea

This problem looks like a simulation: give out shares one at a time, round after round. But we are only asked **who gets zero shares**, not how many shares each bidder gets.

Every bidder asks for at least one share. So the first round inside each price group already decides who gets something and who gets nothing.

That turns the problem into simple counting. Group the bids by price, visit the groups from the highest price to the lowest, and in each group compare the shares left with the number of bidders.

We will start with the direct simulation, see why it is slow, and then replace it with counting.

---

## Solution 1: Brute Force (Simulate Every Share)

Follow the rules exactly as they are written.

1. Group the bids by price. Visit the groups from the highest price to the lowest.
2. Inside a group, sort the bidders by timestamp, then by user ID.
3. Run round-robin: in each round, every bidder who still needs shares gets one share.
4. Stop when the whole group is satisfied or no shares are left.
5. Every bidder who received 0 shares goes into the answer.

Here is the round-robin part for one price group.

```python
# 'group' is one price group, already sorted by timestamp and then user ID
received = [0] * len(group)

while shares_left > 0:
    gave_any = False

    # One round: every bidder who still needs shares gets one share
    for i in range(len(group)):
        if shares_left == 0:
            break
        if received[i] < group[i].requested_shares:
            received[i] += 1
            shares_left -= 1
            gave_any = True

    # Nobody needed a share in this round, so the group is fully satisfied
    if not gave_any:
        break

for i in range(len(group)):
    if received[i] == 0:
        unallotted.append(group[i].user_id)
```

### Why is this slow?

A round gives at most one share to each bidder. If one bidder asks for 1,000,000,000 shares, we may need a billion rounds, and every round walks over the whole group.

With up to 100,000 bids and 1,000,000,000 shares, this is far too slow.

But look at what we actually need: only the bidders who end with **0** shares. Almost none of these rounds can change that.

---

## Solution 2: Only the First Round Matters (Optimal)

We keep the grouping and the price order from Solution 1. Only the round-robin loop is replaced.

### Key observation

In the first round of a group, shares go out one per bidder, in order, until every bidder has one or the shares run out.

If the shares run out during this round, the bidders after that point get nothing. Every lower price group gets nothing too.

If they don't run out, every bidder already has a share. The later rounds only decide **how many** extra shares people get, and we don't need that.

We also need to know how many shares are left for the next group. Round-robin stops when the group is satisfied or the shares run out.

So a group uses `min(shares_left, group_demand)` shares, where `group_demand` is the total number of shares the group asked for.

### Three cases for each group

| Case | Who gets zero shares | Shares left after the group |
|---|---|---|
| `shares_left >= group_size` | Nobody | `max(0, shares_left - group_demand)` |
| `0 < shares_left < group_size` | Everyone after the first `shares_left` bidders | `0` |
| `shares_left == 0` | Everyone in the group | `0` |

The order inside a group (timestamp, then user ID) is needed only when there are not enough shares for everyone. That is the only time we sort a group.

In code, the last two cases share one branch. When `shares_left` is `0`, "everyone after the first 0 bidders" is simply everyone.

### Dry run: Example 2

`bids = ["4,2,30,5", "9,4,25,2", "2,3,25,1", "7,1,10,4"]`, `totalShares = 4`

| Price | Bidders | Group size | Group demand | Shares left before | Zero shares | Shares left after |
|---|---|---|---|---|---|---|
| 30 | 4 | 1 | 2 | 4 | nobody | 2 |
| 25 | 2, 9 | 2 | 7 | 2 | nobody | 0 |
| 10 | 7 | 1 | 1 | 0 | 7 | 0 |

The answer is `[7]`.

Example 4 shows the middle case. There are 2 shares for 3 bidders at price 50. After sorting, the order is `14, 5, 10`. Users 14 and 5 get one share each, and user 10 gets nothing.

### Why these data structures?

- **`Bid` class:** keeps the four numbers of a bid together, with clear names. `bid.timestamp` is much easier to read than `parts[3]`.
- **`dict` from price to a list of bids:** one pass over the input builds every allocation group.
- **Sorted list of prices:** the `dict` keeps prices in input order, not price order. So we sort its keys from high to low with `sorted(groups.keys(), reverse=True)`.

### Python Code

```python
from typing import List


class IpoShareAllocation:

    def __init__(self):
        pass

    def getUnallottedUsers(self, bids: List[str],
                           totalShares: int) -> List[int]:
        """Returns IDs of bidders who get zero shares, in increasing order."""
        # 1. Build one allocation group per bid price.
        groups = {}
        for text in bids:
            bid = Bid(text)
            groups.setdefault(bid.bid_price, []).append(bid)

        unallotted = []
        shares_left = totalShares

        # 2. Visit the groups from the highest price to the lowest.
        for price in sorted(groups.keys(), reverse=True):
            group = groups[price]

            if shares_left >= len(group):
                # The first round gives every bidder in this group
                # at least one share.
                group_demand = sum(bid.requested_shares for bid in group)
                # The group takes what it asked for, or every share left
                # if it asked for more.
                shares_left = max(0, shares_left - group_demand)
            else:
                # Not enough shares to give everyone one share.
                # Line the bidders up: earlier timestamp first,
                # then smaller user ID.
                group.sort(key=lambda bid: (bid.timestamp, bid.user_id))

                # The first 'shares_left' bidders get one share each.
                # The rest get nothing.
                for bid in group[shares_left:]:
                    unallotted.append(bid.user_id)
                shares_left = 0

        # 3. The answer must be in increasing order of user ID.
        unallotted.sort()
        return unallotted


class Bid:
    """One bid, parsed from "userId,requestedShares,bidPrice,timestamp"."""

    def __init__(self, text: str):
        parts = text.split(",")
        self.user_id = int(parts[0])
        self.requested_shares = int(parts[1])
        self.bid_price = int(parts[2])
        self.timestamp = int(parts[3])
```

### Complexity

- **Time:** `O(n log n)`, where `n` is the number of bids. We sort the prices, some of the groups and the answer. Everything else is a single pass.
- **Space:** `O(n)` for the bids, the groups and the answer.