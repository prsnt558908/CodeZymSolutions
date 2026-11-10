# Company Ownership Percentage in Java

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

```java
// Follows every path that starts at 'company'.
// 'carried' is the percentage of 'company' that we own through the current path.
double walkAllPaths(Map<String, Map<String, Double>> holdings, String company,
                    double carried, String targetCompany) {
    if (company.equals(targetCompany)) {
        return carried; // this path reached the target
    }
    double total = 0.0;
    for (String owned : holdings.get(company).keySet()) {
        double percent = holdings.get(company).get(owned);
        total += walkAllPaths(holdings, owned, carried * percent / 100.0, targetCompany);
    }
    return total;
}

// answer = walkAllPaths(holdings, ownerCompany, 100.0, targetCompany)
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

1. Count the direct owners of every company. Call this `pendingOwners`.
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


But recursion goes as deep as the longest path. A chain of 100,000 companies means 100,000 nested calls, and that crashes with a `StackOverflowError`. The queue does the same merging in a plain loop, so it is safe for any input.

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

- **`holdings`** (`Map<String, Map<String, Double>>`): for each company, the companies it owns directly and the percentage. It lets us follow the arrows going out of a company quickly. A map of maps keeps each arrow together with its percentage, without needing an extra class.
- **`pendingOwners`** (`Map<String, Integer>`): how many owners of a company are not processed yet. It tells us exactly when a company's share is final.
- **`share`** (`Map<String, Double>`): the running percentage of each company owned by `ownerCompany`.
- **`ready`** (`Queue<String>`): companies whose share is final and can be passed on.

### Code

```java
import java.util.*;

public class CompanyOwnershipAnalyzer {

    public CompanyOwnershipAnalyzer() {
    }

    /**
     * Returns the total percentage of targetCompany owned by ownerCompany,
     * adding up every direct and indirect ownership path.
     */
    public double getOwnershipPercentage(List<String> ownerships, String ownerCompany, String targetCompany) {
        // company -> (company it directly owns -> percentage)
        Map<String, Map<String, Double>> holdings = new HashMap<>();
        // company -> number of its direct owners that are not processed yet
        Map<String, Integer> pendingOwners = new HashMap<>();

        for (String relationship : ownerships) {
            String[] parts = relationship.split(",");
            String owner = parts[0];
            String owned = parts[1];
            double percent = Double.parseDouble(parts[2]);

            // register both companies, even if one owns nothing or has no owner
            holdings.putIfAbsent(owner, new HashMap<>());
            holdings.putIfAbsent(owned, new HashMap<>());
            pendingOwners.putIfAbsent(owner, 0);
            pendingOwners.putIfAbsent(owned, 0);

            holdings.get(owner).put(owned, percent);
            pendingOwners.put(owned, pendingOwners.get(owned) + 1);
        }

        // company -> percentage of it owned by ownerCompany, summed over all paths
        Map<String, Double> share = new HashMap<>();
        for (String company : holdings.keySet()) {
            share.put(company, 0.0);
        }
        share.put(ownerCompany, 100.0); // a company owns 100% of itself

        // companies whose share is final and ready to be passed on
        Queue<String> ready = new ArrayDeque<>();
        for (String company : pendingOwners.keySet()) {
            if (pendingOwners.get(company) == 0) {
                ready.add(company);
            }
        }

        while (!ready.isEmpty()) {
            String company = ready.poll();

            for (String owned : holdings.get(company).keySet()) {
                // pass our share down this arrow: share * percent / 100
                double percent = holdings.get(company).get(owned);
                share.put(owned, share.get(owned) + share.get(company) * percent / 100.0);

                // one more owner of 'owned' is finished
                pendingOwners.put(owned, pendingOwners.get(owned) - 1);
                if (pendingOwners.get(owned) == 0) {
                    ready.add(owned);
                }
            }
        }

        return share.getOrDefault(targetCompany, 0.0);
    }
}
```

### Complexity

Let `n` be the number of relationships. There are at most `2n` companies.

- **Time:** `O(n)`. Each relationship is read once and passed along once.
- **Space:** `O(n)` for the maps and the queue.