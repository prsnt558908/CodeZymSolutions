# Minimum Meals to Eat All Food Items in Java

#### Problem Statement
[https://codezym.com/question/449-minimum-meals-to-eat-all-food-items](https://codezym.com/question/449-minimum-meals-to-eat-all-food-items)

This looks like a scheduling problem where we could simply eat the "most important" items first. But simple picking rules can give wrong answers. So whenever more than `maxItemsPerMeal` items are available, we have to try every possible group of items for that meal.

Trying every group directly is far too slow. The key observation is that there are at most 15 items.

The whole situation at any moment is just "which items are already eaten", and that fits in a single 15-bit number. There are only `2^15 = 32,768` such situations.

So we solve each situation once, save its answer, and reuse it whenever we reach it again. This is recursion with memoization (top-down dynamic programming) over bitmasks.

---

## How a Meal Works

Before each meal, an item is **available** if it is not eaten yet and all of its required items were eaten in earlier meals.

- If at most `maxItemsPerMeal` items are available, we eat all of them.
- Otherwise we eat exactly `maxItemsPerMeal` of them.

So every meal eats `mealSize = min(available count, maxItemsPerMeal)` items. The only real decision is **which** items to pick when too many are available.

---

## Why Greedy Picking Fails

A natural idea is to first eat the items that the most other items are waiting for. Look at this input:

```
foodItemCount = 6
eatingRules = ["1,2", "1,5", "3,6", "4,6", "6,5"]
maxItemsPerMeal = 2
```

At the start, items 1, 3 and 4 are available. Two items need item 1 (items 2 and 5), while only one item needs 3 or 4 (item 6). So greedy eats item 1 in the first meal.

| Plan | Meal 1 | Meal 2 | Meal 3 | Meal 4 |
|---|---|---|---|---|
| Greedy | 1, 3 | 2, 4 | 6 | 5 |
| Best | 3, 4 | 1, 6 | 2, 5 | |

Item 6 needs both 3 and 4, and item 5 needs item 6. Greedy splits 3 and 4 across two meals, which pushes 6 and 5 one meal later. So greedy needs 4 meals while the best plan needs only 3.

Smarter rules like "longest chain first" also fail on some bigger inputs. So we really need to try every group.

---

## Solution 1: Brute Force, Try Every Meal Plan

We simulate the meals with recursion. `solve(eaten)` returns the fewest meals needed to finish, given the set of items already eaten.

It finds the available items, tries every group of `mealSize` of them, and keeps the best result.

```java
// required.get(item) = set of items that must be eaten before 'item'
// Returns the fewest meals needed to eat every item not in 'eaten'.
int solve(Set<Integer> eaten) {
    if (eaten.size() == foodItemCount) {
        return 0;
    }

    // items not eaten yet whose required items are all eaten
    List<Integer> available = new ArrayList<>();
    for (int item = 1; item <= foodItemCount; item++) {
        if (!eaten.contains(item) && eaten.containsAll(required.get(item))) {
            available.add(item);
        }
    }

    // eat everything if it fits, otherwise exactly maxItemsPerMeal items
    int mealSize = Math.min(available.size(), maxItemsPerMeal);

    // allGroups() returns every way to pick mealSize items from the list
    int best = Integer.MAX_VALUE;
    for (List<Integer> group : allGroups(available, mealSize)) {
        Set<Integer> next = new HashSet<>(eaten);
        next.addAll(group);
        best = Math.min(best, 1 + solve(next));
    }
    return best;
}
```

When everything fits in one meal, `mealSize` equals the number of available items. So there is exactly one group: all of them.

### What is wrong with it?

We reach the same set of eaten items through many different meal orders. For example, eating item 1 and then item 2 leaves us in exactly the same situation as eating item 2 and then item 1.

Brute force solves that situation again from scratch every time.

With 15 items, no rules and 1 item per meal, brute force walks through `15!` (about 1.3 trillion) orders. But there are only `2^15 = 32,768` different sets of eaten items.

Copying a `HashSet` at every step also adds extra cost.

---

## Solution 2: Memoization With Bitmasks

We fix the brute force in three small steps.

### Step 1: Save the answer for each eaten set

The fewest meals still needed depends only on **which** items are eaten, not on the order we ate them.

So we compute it once per set, save it, and return the saved value the next time we reach that set.

### Step 2: Store the eaten set as one number

A set of at most 15 items fits in the bits of one `int`. Bit `i` is `1` when item `i + 1` is eaten.

For example, with 4 items, the set {1, 3} is binary `0101`, which is the number 5.

This makes every operation cheap:

- Adding a group of items to the set is just `eaten | group`.
- "Everything eaten" is `(1 << n) - 1`, the number with `n` ones.
- The memo can be a plain `int[]` of size `2^n`. The mask itself is the index, so we need no map and no hashing.

### Step 3: Store the required items as a number too

`requiredMask[i]` has a `1` bit for every item that must be eaten before item `i`. A rule `"a,b"` turns on bit `a - 1` in `requiredMask[b - 1]`.

Item `i` is ready when all of its required bits are also on in `eaten`:

```java
(requiredMask[i] & eaten) == requiredMask[i]
```

This single check replaces the slower `containsAll` call.

### Building the groups

`buildGroups()` is the usual backtracking for combinations. It picks available items one by one and stores every finished group as a mask.

It stops early when too few items are left to fill the group.

### Complexity

- **Time:** O(3^n). For a state and a group being built, each item is in one of three places: already eaten, picked for this meal, or neither. So the total work is at most `3^n` steps (about 14 million for n = 15), and in practice far fewer.
- **Space:** O(2^n) for the memo array.

### Java Code

```java
import java.util.*;

public class FoodTastingPlanner {

    int foodItemCount;
    int maxItemsPerMeal;

    // mask with every item eaten, for example binary 1111111 when there are 7 items
    int allEaten;

    // requiredMask[i] has a 1 bit for every item that must be eaten before item i
    int[] requiredMask;

    // memo[eaten] = fewest meals needed to finish from state 'eaten', -1 means not solved yet
    int[] memo;

    public FoodTastingPlanner() {
    }

    public int minimumMeals(int foodItemCount, List<String> eatingRules, int maxItemsPerMeal) {
        this.foodItemCount = foodItemCount;
        this.maxItemsPerMeal = maxItemsPerMeal;
        allEaten = (1 << foodItemCount) - 1;

        // item labels 1..n become bit positions 0..n-1
        requiredMask = new int[foodItemCount];
        for (String rule : eatingRules) {
            String[] parts = rule.split(",");
            int before = Integer.parseInt(parts[0]) - 1;
            int later = Integer.parseInt(parts[1]) - 1;
            requiredMask[later] |= 1 << before;
        }

        memo = new int[1 << foodItemCount];
        Arrays.fill(memo, -1);
        return solve(0);
    }

    // Returns the fewest meals needed to eat every item that is not in 'eaten'.
    int solve(int eaten) {
        if (eaten == allEaten) {
            return 0;
        }
        if (memo[eaten] != -1) {
            return memo[eaten];
        }

        // items not eaten yet whose required items are all eaten
        List<Integer> available = new ArrayList<>();
        for (int item = 0; item < foodItemCount; item++) {
            boolean notEaten = (eaten & (1 << item)) == 0;
            boolean ready = (requiredMask[item] & eaten) == requiredMask[item];
            if (notEaten && ready) {
                available.add(item);
            }
        }

        // eat everything if it fits, otherwise exactly maxItemsPerMeal items
        int mealSize = Math.min(available.size(), maxItemsPerMeal);
        List<Integer> groups = new ArrayList<>();
        buildGroups(available, 0, mealSize, 0, groups);

        // try every group and keep the best result
        int best = Integer.MAX_VALUE;
        for (int group : groups) {
            best = Math.min(best, 1 + solve(eaten | group));
        }

        memo[eaten] = best;
        return best;
    }

    // Adds to 'groups' every way to pick 'left' more items from available[start...].
    // 'group' is the mask of the items picked so far.
    void buildGroups(List<Integer> available, int start, int left, int group, List<Integer> groups) {
        if (left == 0) {
            groups.add(group);
            return;
        }
        // stop when too few items remain to finish the group
        for (int i = start; i + left <= available.size(); i++) {
            int item = available.get(i);
            buildGroups(available, i + 1, left - 1, group | (1 << item), groups);
        }
    }
}
```