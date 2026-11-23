# Maximum People in Trip Group in Python

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

We store a set of people as a **bitmask**: an integer where bit `i` is `1` if person `i` is in the set. For example, `0b1011` is the set `{0, 1, 3}`.


Three tricks we will use:

- `remaining & -remaining` keeps only the lowest `1` bit. This is the first undecided person.
- `remaining ^ group` removes the people of `group` from `remaining`.
- `sub = (sub - 1) & rest` jumps to the next smaller subset of `rest`. For `rest = 0b101` it visits `0b101`, `0b100`, `0b001`, `0b000`. We stop after `0`.


### The recursion

Look at the first undecided person. They have two choices:

1. **Stay home.**
2. **Join a group** with some of the other undecided people. We try every such group and keep it only if it is valid.


Every arrangement puts this person somewhere: at home or in exactly one group. So deciding their fate first covers every arrangement, and never tries the same groups in a different order.

```python
def solve(self, remaining: int) -> int:
    """Returns the most people from `remaining` who can go on the trip."""
    if remaining == 0:
        return 0
    first = remaining & -remaining  # first undecided person
    rest = remaining ^ first        # everyone else

    # Choice 1: first person stays home
    best = self.solve(rest)

    # Choice 2: first person goes with some subset of the rest
    sub = rest
    while True:
        group = sub | first
        if self.isValid(group):
            best = max(best, bin(group).count("1") + self.solve(remaining ^ group))
        if sub == 0:
            break
        sub = (sub - 1) & rest
    return best

def isValid(self, group: int) -> bool:
    """Checks every person in the group, again and again for the same group."""
    others = bin(group).count("1") - 1
    for i in range(self.n):
        inGroup = (group & (1 << i)) != 0
        if inGroup and not (self.minPeople[i] <= others <= self.maxPeople[i]):
            return False
    return True
```


### Why is it slow?

- **The same sets are solved again and again.** Groups `{0, 1}` then `{2, 3}` leave the same people as groups `{0, 2}` then `{1, 3}`. Both paths solve the rest from scratch.
- **The same groups are checked again and again.** `isValid` loops over all people every time it is called.


In the worst case, there are more than **10 billion** ways to pick who goes and split 15 people into groups. Far too slow.


## 2. Better: Bitmask DP

Two small fixes make the brute force fast:

1. **Check every group once.** Before solving, go over all `2^n` masks and save the result in `isValidGroup[mask]`.
2. **Solve every set once.** Save the answer of `solve(remaining)` in `memo[remaining]`. If we reach the same set again, we return the saved answer.


**Why plain lists?** Every set of people is already a number from `0` to `2^n - 1`. So a list indexed by the mask works like a map, but it is simpler and faster. For 15 people, each list has only 32,768 entries.


`solve` stays almost the same. It checks `memo` first, saves its answer at the end, and looks up `isValidGroup` instead of calling `isValid`.


### Code

```python
from typing import List


class TripGroupPlanner:

    def __init__(self):
        pass

    def maximumPeopleOnTrip(self, minPeople: List[int], maxPeople: List[int]) -> int:
        n = len(minPeople)
        totalMasks = 1 << n

        # isValidGroup[mask]: can the people in mask form one group?
        self.isValidGroup = [False] * totalMasks
        # groupSize[mask]: number of people in mask
        self.groupSize = [0] * totalMasks
        # memo[mask]: answer for the people in mask, -1 if not solved yet
        self.memo = [-1] * totalMasks

        # Check every possible group only once
        for mask in range(1, totalMasks):
            self.groupSize[mask] = bin(mask).count("1")
            others = self.groupSize[mask] - 1  # each member sees this many other people
            self.isValidGroup[mask] = all(
                minPeople[i] <= others <= maxPeople[i]
                for i in range(n)
                if mask & (1 << i)
            )

        return self.solve(totalMasks - 1)

    def solve(self, remaining: int) -> int:
        """Returns the most people from `remaining` who can go on the trip."""
        if remaining == 0:
            return 0
        if self.memo[remaining] != -1:
            return self.memo[remaining]

        first = remaining & -remaining  # first undecided person
        rest = remaining ^ first        # everyone else

        # Choice 1: first person stays home
        best = self.solve(rest)

        # Choice 2: first person goes with some subset of the rest
        sub = rest
        while True:
            group = sub | first
            if self.isValidGroup[group]:
                best = max(best, self.groupSize[group] + self.solve(remaining ^ group))
            if sub == 0:
                break
            sub = (sub - 1) & rest

        self.memo[remaining] = best
        return best
```


### Complexity

- **Time:** `O(3^n)`, around 7 million steps for `n = 15`.
- **Space:** `O(2^n)` for the lists.


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

```python
from typing import List


class TripGroupPlanner:

    def __init__(self):
        pass

    def maximumPeopleOnTrip(self, minPeople: List[int], maxPeople: List[int]) -> int:
        self.n = len(minPeople)
        # Smallest and largest group size each person accepts (counting themselves)
        self.smallestSize = [m + 1 for m in minPeople]
        self.largestSize = [m + 1 for m in maxPeople]
        # People sorted by largestSize, least flexible first
        self.order = sorted(range(self.n), key=lambda i: self.largestSize[i])
        # used[i]: is person i already in a group?
        self.used = [False] * self.n
        self.best = 0

        self.addGroups(1, 0)
        return self.best

    def addGroups(self, fromSize: int, peopleOnTrip: int) -> None:
        """Adds groups of size >= fromSize, so sizes are always added in increasing order.
        peopleOnTrip is the number of people already placed in groups."""
        self.best = max(self.best, peopleOnTrip)

        for size in range(fromSize, self.n - peopleOnTrip + 1):
            group = self.pickGroup(size)
            if group is None:
                continue  # not enough free people accept this size
            for person in group:
                self.used[person] = True
            self.addGroups(size, peopleOnTrip + size)
            for person in group:
                self.used[person] = False  # undo, so the next size can be tried

    def pickGroup(self, size: int):
        """Picks `size` free people who accept this size, least flexible first.
        Returns None if there are not enough such people."""
        group = []
        for person in self.order:
            if len(group) == size:
                break
            fits = self.smallestSize[person] <= size <= self.largestSize[person]
            if fits and not self.used[person]:
                group.append(person)
        return group if len(group) == size else None
```


### Complexity

- **Time:** at most 684 size lists for `n = 15`. For each list we try up to `n` next sizes, and filling one group takes `O(n)`. That is about 150 thousand simple steps.
- **Space:** `O(n)`, plus a recursion depth of at most `n`.