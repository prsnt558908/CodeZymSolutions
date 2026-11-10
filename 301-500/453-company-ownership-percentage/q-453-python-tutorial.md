# Company Ownership Percentage in Python

#### Problem Statement
[https://codezym.com/question/453-company-ownership-percentage](https://codezym.com/question/453-company-ownership-percentage)


Think of every company as a node and every ownership as an arrow from the owner to the company it owns. There are no cycles, so this is a directed acyclic graph (DAG).


The answer is easy to describe: find every path from `ownerCompany` to `targetCompany`, multiply the percentages on each path, and add the results.


Walking every path one by one works, but the number of paths can explode. The fix is to **merge all paths that reach the same company into a single number**, and pass that number forward only once.


To merge safely, we visit companies in **topological order**: a company is visited only after all of its owners. A queue plus one counter per company gives us this order, so every relationship is handled exactly once.

---

## Approach 1: Brute Force (Try Every Path)

Start at `ownerCompany` holding 100%. Follow each arrow and multiply by its percentage. Whenever we reach `targetCompany`, add what we are holding to the answer.


In Example 1, there are three paths from `atlas` to `delta`:

```
atlas -> delta             100% x 10%        = 10%
atlas -> beacon -> delta   100% x 40% x 50%  = 20%
atlas -> cobalt -> delta   100% x 25% x 20%  =  5%
                                       total = 35%
```


Here `holdings` maps every company to the companies it owns directly, along with the percentage.

```python
# Follows every path that starts at 'company'.
# 'carried' is the percentage of 'company' that we own through the current path.
def walkAllPaths(self, holdings, company, carried, targetCompany):
    if company == targetCompany:
        return carried  # this path reached the target
    total = 0.0
    for owned, percent in holdings[company].items():
        total += self.walkAllPaths(holdings, owned, carried * percent / 100.0, targetCompany)
    return total

# answer = self.walkAllPaths(holdings, ownerCompany, 100.0, targetCompany)
```

### Why Is It Slow?

Different paths often pass through the same company, and each of them walks everything below that company again.


Look at this small "diamond":

```
      +--> b1 --+
      |         v
      a1        a2
      |         ^
      +--> c1 --+
```


There are 2 paths from `a1` to `a2`. Now chain 50 such diamonds (`a1` to `a2`, `a2` to `a3`, and so on). That is only 200 relationships, but there are 2^50 paths from `a1` to `a51`, which is more than 10^15. Brute force would never finish.


The waste is easy to spot: both paths arrive at `a2` and then repeat the exact same walk below it. It is much smarter to first add up everything that arrives at `a2`, and then continue from `a2` only once.

---

## Approach 2: Optimal (Merge Paths in Topological Order)

### The Idea

Let `share[c]` be the percentage of company `c` that `ownerCompany` owns, counting all paths.

- `share[ownerCompany] = 100`, because a company owns 100% of itself.
- If `x` owns `p%` of `y`, then `x` passes `share[x] * p / 100` to `y`.
- `share[y]` is the sum of everything its owners pass to it.


The answer is simply `share[targetCompany]`.

### When Is a Company Ready?

A company must not pass its share forward too early. `share[x]` is final only after **all** owners of `x` have passed their part to it.


So we need an order where every company comes after all of its owners. This is a **topological order**, and Kahn's algorithm builds it with a queue:

1. Count the direct owners of every company. Call this `pending_owners`.
2. Put every company with 0 owners in the queue.
3. Take a company from the queue and pass its share to every company it owns. Each of those now has one less pending owner. When a count reaches 0, that company is ready, so add it to the queue.
4. When the queue is empty, read `share[targetCompany]`.


Since there are no cycles, every company reaches 0 pending owners at some point and gets processed.


We start with **all** companies that have no owners, not only `ownerCompany`. Companies that `ownerCompany` cannot reach just have a share of 0 and pass on 0. We still process them, because the companies they own are waiting for them.


Edge cases need no special code:

- If `ownerCompany` equals `targetCompany`, the answer is the starting 100.
- If no path exists, nothing ever reaches `targetCompany`, so its share stays 0.

### Why Not Recursion With a Cache?

You could also use recursion with a cache (memoization), where each company remembers how much of the target it owns.


But recursion goes as deep as the longest path. Python's default recursion limit is 1000, so a chain of about 1,000 companies already raises a `RecursionError`, and the input can have 100,000 relationships. The queue does the same merging in a plain loop, so it is safe for any input.

### Walkthrough (Example 1)

Starting counts: `atlas = 0`, `beacon = 1`, `cobalt = 1`, `delta = 3`. Only `atlas` starts in the queue.

| Step | Taken from queue | Its share | What it passes on |
|------|------------------|-----------|-------------------|
| 1 | atlas | 100% | beacon +40%, cobalt +25%, delta +10% |
| 2 | beacon | 40% | delta +20% (50% of 40%) |
| 3 | cobalt | 25% | delta +5% (20% of 25%) |
| 4 | delta | 35% | nothing, it owns no company |


`delta` joins the queue only after all 3 of its owners are done, so its share `10 + 20 + 5 = 35` is final. The answer is `35.0`.

### Data Structures Used

- **`holdings`** (`dict` of `dict`): for each company, the companies it owns directly and the percentage. It lets us follow the arrows going out of a company quickly. A dict of dicts keeps each arrow together with its percentage, without needing an extra class.
- **`pending_owners`** (`dict`): how many owners of a company are not processed yet. It tells us exactly when a company's share is final.
- **`share`** (`dict`): the running percentage of each company owned by `ownerCompany`.
- **`ready`** (`deque`): companies whose share is final and can be passed on. A `deque` removes from the front in `O(1)`, while `list.pop(0)` would shift every element each time.

### Code

```python
from collections import deque
from typing import List


class CompanyOwnershipAnalyzer:

    def __init__(self):
        pass

    def getOwnershipPercentage(self, ownerships: List[str], ownerCompany: str, targetCompany: str) -> float:
        """Returns the total percentage of targetCompany owned by ownerCompany,
        adding up every direct and indirect ownership path."""
        # company -> {company it directly owns: percentage}
        holdings = {}
        # company -> number of its direct owners that are not processed yet
        pending_owners = {}

        for relationship in ownerships:
            owner, owned, percent = relationship.split(",")

            # register both companies, even if one owns nothing or has no owner
            holdings.setdefault(owner, {})
            holdings.setdefault(owned, {})
            pending_owners.setdefault(owner, 0)
            pending_owners.setdefault(owned, 0)

            holdings[owner][owned] = float(percent)
            pending_owners[owned] += 1

        # company -> percentage of it owned by ownerCompany, summed over all paths
        share = {company: 0.0 for company in holdings}
        share[ownerCompany] = 100.0  # a company owns 100% of itself

        # companies whose share is final and ready to be passed on
        ready = deque()
        for company, count in pending_owners.items():
            if count == 0:
                ready.append(company)

        while ready:
            company = ready.popleft()

            for owned, percent in holdings[company].items():
                # pass our share down this arrow: share * percent / 100
                share[owned] += share[company] * percent / 100.0

                # one more owner of 'owned' is finished
                pending_owners[owned] -= 1
                if pending_owners[owned] == 0:
                    ready.append(owned)

        return share.get(targetCompany, 0.0)
```

### Complexity

Let `n` be the number of relationships. There are at most `2n` companies.

- **Time:** `O(n)`. Each relationship is read once and passed along once.
- **Space:** `O(n)` for the dictionaries and the queue.