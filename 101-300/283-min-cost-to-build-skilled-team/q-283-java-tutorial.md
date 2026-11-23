# Minimum Cost to Build a Skilled Team in Java

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


We sort each group and build running sums, where `javaSum[i]` is the total cost of the `i` cheapest Java-only candidates. Then we try every `b`:

```java
// bothSum[i], javaSum[i], pythonSum[i] = cost of the i cheapest candidates in that group
for (int k = 1; k <= n; k++) {
    long best = -1;
    for (int b = 0; b <= Math.min(k, both.size()); b++) {
        int need = k - b; // Java-only and Python-only candidates still needed
        if (need > javaOnly.size() || need > pythonOnly.size()) {
            continue;
        }
        long cost = bothSum[b] + javaSum[need] + pythonSum[need];
        if (best == -1 || cost < best) {
            best = cost;
        }
    }
    result.add(best);
}
```

This gives the right answer, but it is slow.


**Problem:** for every k we try up to k values of `b`. In the worst case that is about n² / 2 steps, which means billions of steps for n = 200,000.


## Solution 2: Pick the k Cheapest Units (Optimal)

Look at `javaSum[need] + pythonSum[need]` in the code above.


Pair the cheapest Java-only candidate with the cheapest Python-only candidate, the second cheapest with the second cheapest, and so on. Now `javaSum[need] + pythonSum[need]` is just the cost of the first `need` pairs.


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

We only need plain lists (`ArrayList`).


Sorting a list puts the cheapest costs first, which is exactly what the greedy choice needs. The running total then gives each answer in O(1).


A total can reach 200,000 × 10^9 = 2 × 10^14, which does not fit in `int`. So every cost and total is stored as `long`.


### Complexity

- **Time:** O(n log n) for sorting. Everything else is a single pass.
- **Space:** O(n) for the lists.


### Java Code

```java
import java.util.*;

public class MinimumCostSkilledTeam {

    public MinimumCostSkilledTeam() {
    }

    /**
     * Returns the minimum cost of a k-skilled team for every k from 1 to n.
     * Uses -1 when no k-skilled team can be formed.
     */
    public List<Long> findMinimumHiringCosts(List<Integer> hiringCost, List<String> skills) {
        int n = hiringCost.size();

        List<Long> javaOnly = new ArrayList<>();
        List<Long> pythonOnly = new ArrayList<>();
        // A unit adds 1 Java skill and 1 Python skill to the team
        List<Long> units = new ArrayList<>();

        // Group candidates by skill. "00" candidates never help, so we skip them.
        for (int i = 0; i < n; i++) {
            long cost = hiringCost.get(i);
            String skill = skills.get(i);

            if (skill.equals("10")) {
                javaOnly.add(cost);
            } else if (skill.equals("01")) {
                pythonOnly.add(cost);
            } else if (skill.equals("11")) {
                units.add(cost); // a "11" candidate is a unit alone
            }
        }

        // Pair the i-th cheapest Java-only with the i-th cheapest Python-only.
        // Candidates left without a partner can never complete a unit.
        Collections.sort(javaOnly);
        Collections.sort(pythonOnly);
        int pairCount = Math.min(javaOnly.size(), pythonOnly.size());
        for (int i = 0; i < pairCount; i++) {
            units.add(javaOnly.get(i) + pythonOnly.get(i));
        }

        // The cheapest k-skilled team is made of the k cheapest units
        Collections.sort(units);

        List<Long> result = new ArrayList<>();
        long total = 0;
        for (int k = 1; k <= n; k++) {
            if (k <= units.size()) {
                total += units.get(k - 1);
                result.add(total);
            } else {
                result.add(-1L); // not enough units for k
            }
        }
        return result;
    }
}
```