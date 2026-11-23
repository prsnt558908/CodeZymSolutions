# Maximum People in Trip Group in Java

#### Problem Statement
[https://codezym.com/question/239-max-people-in-trip-group](https://codezym.com/question/239-max-people-in-trip-group)


## Core Idea

Every person cares about only one thing: **the size of their group**, not who is in it.


If a group has `g` people, each member sees `g - 1` other people. So person `i` is happy when:

```
minPeople[i] + 1  <=  g  <=  maxPeople[i] + 1
```


Since `n` is at most 15, we can afford to try a lot. We first build a brute force that tries every arrangement. Then we make it fast with **bitmask DP**, so that each set of people is solved only once.


Finally, we use the "only sizes matter" idea to get an even faster **greedy** solution.


## 1. Brute Force: Try Every Arrangement

### A set of people as a number

We store a set of people as a **bitmask**: an `int` where bit `i` is `1` if person `i` is in the set. For example, `0b1011` is the set `{0, 1, 3}`.


Three tricks we will use:

- `remaining & -remaining` keeps only the lowest `1` bit. This is the first undecided person.
- `remaining ^ group` removes the people of `group` from `remaining`.
- `sub = (sub - 1) & rest` jumps to the next smaller subset of `rest`. For `rest = 0b101` it visits `0b101`, `0b100`, `0b001`, `0b000`. We stop after `0`.


### The recursion

Look at the first undecided person. They have two choices:

1. **Stay home.**
2. **Join a group** with some of the other undecided people. We try every such group and keep it only if it is valid.


Every arrangement puts this person somewhere: at home or in exactly one group. So deciding their fate first covers every arrangement, and never tries the same groups in a different order.

```java
// Returns the most people from `remaining` who can go on the trip
int solve(int remaining) {
    if (remaining == 0) {
        return 0;
    }
    int first = remaining & -remaining; // first undecided person
    int rest = remaining ^ first;       // everyone else

    // Choice 1: first person stays home
    int best = solve(rest);

    // Choice 2: first person goes with some subset of the rest
    for (int sub = rest; ; sub = (sub - 1) & rest) {
        int group = sub | first;
        if (isValid(group)) {
            best = Math.max(best, Integer.bitCount(group) + solve(remaining ^ group));
        }
        if (sub == 0) {
            break;
        }
    }
    return best;
}

// Checks every person in the group, again and again for the same group
boolean isValid(int group) {
    int others = Integer.bitCount(group) - 1;
    for (int i = 0; i < n; i++) {
        boolean inGroup = (group & (1 << i)) != 0;
        if (inGroup && (others < minPeople.get(i) || others > maxPeople.get(i))) {
            return false;
        }
    }
    return true;
}
```


### Why is it slow?

- **The same sets are solved again and again.** Groups `{0, 1}` then `{2, 3}` leave the same people as groups `{0, 2}` then `{1, 3}`. Both paths solve the rest from scratch.
- **The same groups are checked again and again.** `isValid` loops over all people every time it is called.


In the worst case, there are more than **10 billion** ways to pick who goes and split 15 people into groups. Far too slow.


## 2. Better: Bitmask DP

Two small fixes make the brute force fast:

1. **Check every group once.** Before solving, go over all `2^n` masks and save the result in `isValidGroup[mask]`.
2. **Solve every set once.** Save the answer of `solve(remaining)` in `memo[remaining]`. If we reach the same set again, we return the saved answer.


**Why plain arrays?** Every set of people is already a number from `0` to `2^n - 1`. So an array indexed by the mask works like a map, but it is simpler and faster. For 15 people, each array has only 32,768 entries.


`solve` stays almost the same. It checks `memo` first, saves its answer at the end, and looks up `isValidGroup` instead of calling `isValid`.


### Code

```java
import java.util.*;

public class TripGroupPlanner {

    boolean[] isValidGroup; // isValidGroup[mask] is true if the people in mask can form one group
    int[] groupSize;        // groupSize[mask] is the number of people in mask
    int[] memo;             // memo[mask] is the answer for the people in mask, -1 if not solved yet

    public TripGroupPlanner() {
    }

    public int maximumPeopleOnTrip(List<Integer> minPeople, List<Integer> maxPeople) {
        int n = minPeople.size();
        int totalMasks = 1 << n;

        isValidGroup = new boolean[totalMasks];
        groupSize = new int[totalMasks];
        memo = new int[totalMasks];
        Arrays.fill(memo, -1);

        // Check every possible group only once
        for (int mask = 1; mask < totalMasks; mask++) {
            groupSize[mask] = Integer.bitCount(mask);
            int others = groupSize[mask] - 1; // each member sees this many other people
            isValidGroup[mask] = true;
            for (int i = 0; i < n; i++) {
                boolean inGroup = (mask & (1 << i)) != 0;
                if (inGroup && (others < minPeople.get(i) || others > maxPeople.get(i))) {
                    isValidGroup[mask] = false;
                    break;
                }
            }
        }

        return solve(totalMasks - 1);
    }

    // Returns the most people from `remaining` who can go on the trip
    int solve(int remaining) {
        if (remaining == 0) {
            return 0;
        }
        if (memo[remaining] != -1) {
            return memo[remaining];
        }

        int first = remaining & -remaining; // first undecided person
        int rest = remaining ^ first;       // everyone else

        // Choice 1: first person stays home
        int best = solve(rest);

        // Choice 2: first person goes with some subset of the rest
        for (int sub = rest; ; sub = (sub - 1) & rest) {
            int group = sub | first;
            if (isValidGroup[group]) {
                best = Math.max(best, groupSize[group] + solve(remaining ^ group));
            }
            if (sub == 0) {
                break;
            }
        }

        memo[remaining] = best;
        return best;
    }
}
```


### Complexity

- **Time:** `O(3^n)`, around 7 million steps for `n = 15`.
- **Space:** `O(2^n)` for the arrays.


Why `3^n`? Each person is in one of 3 states: outside `remaining`, inside `remaining` but not in the group, or in the group. So there are at most `3^n` (set, group) pairs to try.


Every group must contain the first person, which cuts this to about `3^n / 2`. Checking all groups once adds only `O(2^n * n)`.


## 3. Best: Only Group Sizes Matter

Approach 2 still picks real people for every group. But remember: each person only checks the **size** of their group.


So let's first choose the sizes of the groups, like `[2, 3]` or `[1, 1, 3]`, and then check if we can fill them with people. For `n = 15`, only **684** lists of sizes have a total of at most 15. That is tiny.


For each person we store two numbers:

- `smallestSize[i] = minPeople[i] + 1`
- `largestSize[i] = maxPeople[i] + 1`


### Filling a list of sizes greedily

Fill the groups from the **smallest size to the largest**. For a group of size `g`, pick `g` free people with `smallestSize <= g <= largestSize`.


If more people qualify than needed, pick the ones with the **smallest `largestSize`** first.


Why? Groups only get bigger from here. A person with a small `largestSize` may never fit again if we skip them now. A more flexible person can still join a bigger group later. So putting the less flexible person in first never makes things worse.


**Example:** `minPeople = [1, 1, 1, 1, 1]`, `maxPeople = [1, 2, 2, 2, 2]`.


Person 0 accepts only size `2`. Persons 1 to 4 accept size `2` or `3`. Let's try the sizes `[2, 3]`:

- **Size 2:** everyone qualifies. Person 0 is the least flexible, so we pick persons 0 and 1.
- **Size 3:** persons 2, 3 and 4 qualify. All 5 people go on the trip.


If we had picked persons 1 and 2 for the pair instead, the group of 3 would need person 0, who only accepts 2. We would wrongly decide that `[2, 3]` is impossible.


### Building the size lists

`addGroups(fromSize, peopleOnTrip)` adds one group at a time. Each new group has size `>= fromSize`, the size of the previous group. So every list is built exactly once, already sorted from small to large.


We fill each group right when we add it, and undo it when we backtrack. Every list we manage to fill is a valid arrangement, so we update `best` at every step.


If a group cannot be filled, every list that continues from it is impossible too. So we skip that size and try the next one.


Two important data structures:

- `order`: people sorted by `largestSize`. Walking through it picks the least flexible people first, so we never sort inside the recursion.
- `used`: marks people who are already in a group. It is easy to set and undo while backtracking.


### Code

```java
import java.util.*;

public class TripGroupPlanner {

    int n;
    int[] smallestSize;  // smallest group size person i accepts (counting themselves)
    int[] largestSize;   // largest group size person i accepts (counting themselves)
    List<Integer> order; // people sorted by largestSize, least flexible first
    boolean[] used;      // used[i] is true if person i is already in a group
    int best;

    public TripGroupPlanner() {
    }

    public int maximumPeopleOnTrip(List<Integer> minPeople, List<Integer> maxPeople) {
        n = minPeople.size();
        smallestSize = new int[n];
        largestSize = new int[n];
        order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            smallestSize[i] = minPeople.get(i) + 1;
            largestSize[i] = maxPeople.get(i) + 1;
            order.add(i);
        }
        order.sort((a, b) -> largestSize[a] - largestSize[b]);

        used = new boolean[n];
        best = 0;
        addGroups(1, 0);
        return best;
    }

    // Adds groups of size >= fromSize, so sizes are always added in increasing order.
    // peopleOnTrip is the number of people already placed in groups.
    void addGroups(int fromSize, int peopleOnTrip) {
        best = Math.max(best, peopleOnTrip);

        for (int size = fromSize; peopleOnTrip + size <= n; size++) {
            List<Integer> group = pickGroup(size);
            if (group == null) {
                continue; // not enough free people accept this size
            }
            for (int person : group) {
                used[person] = true;
            }
            addGroups(size, peopleOnTrip + size);
            for (int person : group) {
                used[person] = false; // undo, so the next size can be tried
            }
        }
    }

    // Picks `size` free people who accept this size, least flexible first.
    // Returns null if there are not enough such people.
    List<Integer> pickGroup(int size) {
        List<Integer> group = new ArrayList<>();
        for (int person : order) {
            if (group.size() == size) {
                break;
            }
            if (!used[person] && smallestSize[person] <= size && size <= largestSize[person]) {
                group.add(person);
            }
        }
        return group.size() == size ? group : null;
    }
}
```


### Complexity

- **Time:** at most 684 size lists for `n = 15`. For each list we try up to `n` next sizes, and filling one group takes `O(n)`. That is about 150 thousand simple steps.
- **Space:** `O(n)`, plus a recursion depth of at most `n`.