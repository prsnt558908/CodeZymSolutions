# Minimum Cost to Build a Skilled Team in Python

#### Problem Statement
[https://codezym.com/question/283-min-cost-to-build-skilled-team](https://codezym.com/question/283-min-cost-to-build-skilled-team)


## Core Idea

The trick is to stop counting Java and Python separately.


A `"11"` candidate adds 1 Java skill and 1 Python skill to the team. A `"10"` (Java-only) candidate and a `"01"` (Python-only) candidate hired together do the exact same job. Let's call each of these a **unit**.


Every k-skilled team contains at least k units, and extra people only add cost. So the answer for k is simply the sum of the **k cheapest units**.


This is a simple greedy solution: sort the costs, pair up cheap Java-only and Python-only candidates, put all units into one sorted list and keep a running total.


## Solution 1: Try Every Count of "11" Candidates (Slow)

Two simple facts first.


A `"00"` candidate adds nothing, so we never hire them.


Inside each skill group, cheaper is always better. If we need some Java-only candidates, we take the cheapest ones. The same goes for Python-only and `"11"` candidates.


So for a fixed k, the only real choice is **how many `"11"` candidates to hire**. Call it `b`. Then we still need `k - b` Java-only and `k - b` Python-only candidates.


We sort each group and build running sums, where `java_sum[i]` is the total cost of the `i` cheapest Java-only candidates. Then we try every `b`:

```python
# both_sum[i], java_sum[i], python_sum[i] = cost of the i cheapest candidates in that group
for k in range(1, n + 1):
    best = -1
    for b in range(min(k, len(both)) + 1):
        need = k - b  # Java-only and Python-only candidates still needed
        if need > len(java_only) or need > len(python_only):
            continue
        cost = both_sum[b] + java_sum[need] + python_sum[need]
        if best == -1 or cost < best:
            best = cost
    result.append(best)
```

This gives the right answer, but it is slow.


**Problem:** for every k we try up to k values of `b`. In the worst case that is about n² / 2 steps, which means billions of steps for n = 200,000.


## Solution 2: Pick the k Cheapest Units (Optimal)

Look at `java_sum[need] + python_sum[need]` in the code above.


Pair the cheapest Java-only candidate with the cheapest Python-only candidate, the second cheapest with the second cheapest, and so on. Now `java_sum[need] + python_sum[need]` is just the cost of the first `need` pairs.


Because both groups are sorted, the pair costs come out sorted too. So every option in Solution 1 is: the `b` cheapest `"11"` units plus the `k - b` cheapest pair units.


Trying every `b` is the same as picking the **k cheapest units from both lists together**.


Why? If a chosen unit costs more than a unit we skipped, swapping them makes the team cheaper. So the best choice is always the k cheapest units.


### Steps

1. Group candidates into Java-only, Python-only and `"11"`. Skip `"00"`.
2. Sort Java-only and Python-only, then pair them up in order. Candidates left without a partner can never complete a unit, so they are ignored.
3. Put every `"11"` cost and every pair cost into one list `units` and sort it.
4. Walk through `units` with a running total. The total after k units is the answer for k.
5. If k is bigger than the number of units, the answer is `-1`.


### Walkthrough (Example 1)

`hiringCost = [5, 2, 8, 4, 7]`, `skills = ["10", "01", "11", "01", "10"]`

- Java-only: `[5, 7]`
- Python-only: `[2, 4]`
- `"11"`: `[8]`
- Pairs: `5 + 2 = 7` and `7 + 4 = 11`
- Sorted units: `[7, 8, 11]`


| k | Unit added | Running total |
|---|------------|---------------|
| 1 | pair `5 + 2 = 7` | 7 |
| 2 | `"11"` candidate `8` | 15 |
| 3 | pair `7 + 4 = 11` | 26 |
| 4 | no units left | -1 |
| 5 | no units left | -1 |


Look at k = 2. The `"11"` candidate (8) wins over the next pair (7 + 4 = 11), even though 7 and 4 are each cheaper than 8. A `"11"` candidate must be compared with a whole pair, never with one person.


### Why These Data Structures

We only need plain Python lists.


Sorting a list puts the cheapest costs first, which is exactly what the greedy choice needs. The running total then gives each answer in O(1).


`zip` walks the two sorted lists side by side and stops at the shorter one. So it builds the pairs and drops candidates without a partner in one step.


### Complexity

- **Time:** O(n log n) for sorting. Everything else is a single pass.
- **Space:** O(n) for the lists.


### Python Code

```python
from typing import List


class MinimumCostSkilledTeam:

    def __init__(self):
        pass

    def findMinimumHiringCosts(self, hiringCost: List[int], skills: List[str]) -> List[int]:
        """Returns the minimum cost of a k-skilled team for every k from 1 to n.
        Uses -1 when no k-skilled team can be formed."""
        n = len(hiringCost)

        java_only = []
        python_only = []
        # A unit adds 1 Java skill and 1 Python skill to the team
        units = []

        # Group candidates by skill. "00" candidates never help, so we skip them.
        for cost, skill in zip(hiringCost, skills):
            if skill == "10":
                java_only.append(cost)
            elif skill == "01":
                python_only.append(cost)
            elif skill == "11":
                units.append(cost)  # a "11" candidate is a unit alone

        # Pair the i-th cheapest Java-only with the i-th cheapest Python-only.
        # zip stops at the shorter list, so candidates without a partner are left out.
        java_only.sort()
        python_only.sort()
        for java_cost, python_cost in zip(java_only, python_only):
            units.append(java_cost + python_cost)

        # The cheapest k-skilled team is made of the k cheapest units
        units.sort()

        result = []
        total = 0
        for k in range(1, n + 1):
            if k <= len(units):
                total += units[k - 1]
                result.append(total)
            else:
                result.append(-1)  # not enough units for k
        return result
```