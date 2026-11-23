# IPO Share Allocation With Bids in Java

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

```java
// 'group' is one price group, already sorted by timestamp and then userId
int[] received = new int[group.size()];

while (sharesLeft > 0) {
    boolean gaveAny = false;

    // One round: every bidder who still needs shares gets one share
    for (int i = 0; i < group.size() && sharesLeft > 0; i++) {
        if (received[i] < group.get(i).requestedShares) {
            received[i]++;
            sharesLeft--;
            gaveAny = true;
        }
    }

    // Nobody needed a share in this round, so the group is fully satisfied
    if (!gaveAny) {
        break;
    }
}

for (int i = 0; i < group.size(); i++) {
    if (received[i] == 0) {
        unallotted.add(group.get(i).userId);
    }
}
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

So a group uses `min(sharesLeft, groupDemand)` shares, where `groupDemand` is the total number of shares the group asked for.

### Three cases for each group

| Case | Who gets zero shares | Shares left after the group |
|---|---|---|
| `sharesLeft >= groupSize` | Nobody | `max(0, sharesLeft - groupDemand)` |
| `0 < sharesLeft < groupSize` | Everyone after the first `sharesLeft` bidders | `0` |
| `sharesLeft == 0` | Everyone in the group | `0` |

The order inside a group (timestamp, then user ID) is needed only when there are not enough shares for everyone. That is the only time we sort a group.

In code, the last two cases share one branch. When `sharesLeft` is `0`, "everyone after the first 0 bidders" is simply everyone.

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
- **`HashMap<Integer, List<Bid>>`:** maps each price to its bidders. One pass over the input builds every allocation group.
- **Sorted list of prices:** a `HashMap` has no order. So we copy its keys into a list and sort it from high to low.
- **`long` for `groupDemand`:** a group can ask for up to 100,000 × 1,000,000,000 shares, far more than an `int` can hold. `sharesLeft` is a `long` too, so the subtraction stays safe.

### Java Code

```java
import java.util.*;

public class IpoShareAllocation {

    public IpoShareAllocation() {
    }

    /**
     * Returns the user IDs of bidders who receive zero shares, in increasing order.
     */
    public List<Integer> getUnallottedUsers(List<String> bids, int totalShares) {
        // 1. Build one allocation group per bid price.
        Map<Integer, List<Bid>> groups = new HashMap<>();
        for (String text : bids) {
            Bid bid = new Bid(text);
            groups.computeIfAbsent(bid.bidPrice, key -> new ArrayList<>()).add(bid);
        }

        // 2. Visit the groups from the highest price to the lowest.
        List<Integer> prices = new ArrayList<>(groups.keySet());
        prices.sort(Collections.reverseOrder());

        List<Integer> unallotted = new ArrayList<>();
        long sharesLeft = totalShares;

        for (int price : prices) {
            List<Bid> group = groups.get(price);

            if (sharesLeft >= group.size()) {
                // The first round gives every bidder in this group at least one share.
                long groupDemand = 0;
                for (Bid bid : group) {
                    groupDemand += bid.requestedShares;
                }
                // The group takes what it asked for, or every share left if it asked for more.
                sharesLeft = Math.max(0, sharesLeft - groupDemand);
            } else {
                // Not enough shares to give everyone one share.
                // Line the bidders up: earlier timestamp first, then smaller userId.
                group.sort((a, b) -> {
                    if (a.timestamp != b.timestamp) {
                        return Integer.compare(a.timestamp, b.timestamp);
                    }
                    return Integer.compare(a.userId, b.userId);
                });

                // The first 'sharesLeft' bidders get one share each. The rest get nothing.
                for (int i = (int) sharesLeft; i < group.size(); i++) {
                    unallotted.add(group.get(i).userId);
                }
                sharesLeft = 0;
            }
        }

        // 3. The answer must be in increasing order of user ID.
        Collections.sort(unallotted);
        return unallotted;
    }
}

/**
 * One bid, parsed from "userId,requestedShares,bidPrice,timestamp".
 */
class Bid {
    int userId;
    int requestedShares;
    int bidPrice;
    int timestamp;

    Bid(String text) {
        String[] parts = text.split(",");
        userId = Integer.parseInt(parts[0].trim());
        requestedShares = Integer.parseInt(parts[1].trim());
        bidPrice = Integer.parseInt(parts[2].trim());
        timestamp = Integer.parseInt(parts[3].trim());
    }
}
```

### Complexity

- **Time:** `O(n log n)`, where `n` is the number of bids. We sort the prices, some of the groups and the answer. Everything else is a single pass.
- **Space:** `O(n)` for the bids, the groups and the answer.