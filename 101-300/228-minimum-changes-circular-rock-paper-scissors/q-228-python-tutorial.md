# Minimum Changes Circular Rock Paper Scissors in Python

#### Problem Statement
[https://codezym.com/question/228-minimum-changes-circular-rock-paper-scissors](https://codezym.com/question/228-minimum-changes-circular-rock-paper-scissors)


## Core Idea

Engineers sit around a round table, and a tie happens when two neighbors pick the same option. The key idea is that ties only happen inside **blocks**: groups of neighbors with the same choice, like `RRR` or `PP`. A block of length `L` needs exactly `L // 2` changes (rounded down), and each block can be fixed on its own.


The circle adds one twist: the first and last engineers are also neighbors. We handle it by starting our count at a seat where the choice changes. If no such seat exists, everyone picked the same option and the answer is `(n + 1) // 2`.


We will build up to this step by step: a brute force idea first, then a Dynamic Programming solution, and finally the simple block counting solution.


## Brute Force: Try Every Arrangement

Every engineer can end up with `R`, `P` or `S`. So we can build every possible final arrangement, skip the ones with a tie, and count how many seats differ from the original choices.

```python
# Build every final arrangement, one seat at a time.
def tryAll(self, choices, arrangement, seat):
    if seat == len(choices):
        if self.hasNoTies(arrangement):
            self.best = min(self.best, self.countDifferences(choices, arrangement))
        return
    for option in "RPS":
        arrangement[seat] = option
        self.tryAll(choices, arrangement, seat + 1)
```


This is correct, but there are `3^n` arrangements. For just 30 engineers that is already more than 200 trillion, and `n` can be 200,000.


The waste is easy to spot. When we decide the option for one seat, only its neighbors matter. Seats far away do not matter at all.


## Solution 1: Dynamic Programming

### Idea

Walk around the table seat by seat. When we pick the final option for seat `i`, we only need to know the final option of seat `i - 1`.


So we keep a list `cost` of just 3 numbers:

- `cost[c]` = the fewest changes so far if the current engineer ends with option `c`.


Why only 3 numbers? The previous engineer can end in only 3 ways (`R`, `P` or `S`), and that is all the history we need.


To move to the next engineer `i`:

```
next_cost[c] = (0 if choices[i] == c else 1) + min(cost of the other two options)
```


Taking the minimum over the **other two** options makes sure two neighbors never end with the same option. With options stored as `"RPS"`, the other two options of index `c` are `(c + 1) % 3` and `(c + 2) % 3`.


### Handling the Circle

The last engineer also sits next to engineer 0. A simple left to right walk does not see this pair.


So we **fix** the final option of engineer 0 and run the walk 3 times, once for each option. In each run:

- Engineer 0 may only end with the fixed option. The other two options start with `not_allowed = n + 1`, a cost bigger than any real answer, so they never win.
- The last engineer may only end with one of the other two options.


The answer is the smallest result of the 3 runs.


### Code

```python
class MinimumChangesCircularRockPaperScissors:

    def __init__(self):
        # Index 0 = Rock, 1 = Paper, 2 = Scissors
        self.options = "RPS"

    def minChanges(self, choices: str) -> int:
        """
        Returns the minimum number of engineers who must change their choice
        so that no two neighbors around the circular table have the same choice.
        """
        # Engineer 0 and the last engineer are neighbors.
        # So we fix the final option of engineer 0 and try all 3 options.
        return min(self.countChanges(choices, first) for first in range(3))

    def countChanges(self, choices: str, first: int) -> int:
        """
        Minimum changes when engineer 0 must end with options[first].
        cost[c] = fewest changes so far if the current engineer ends with options[c].
        """
        n = len(choices)
        not_allowed = n + 1  # bigger than any real answer

        # Engineer 0 can only end with options[first].
        cost = [not_allowed, not_allowed, not_allowed]
        cost[first] = self.changeCost(choices[0], first)

        for i in range(1, n):
            next_cost = [0, 0, 0]
            for c in range(3):
                # The previous engineer must end with one of the other two options.
                best_previous = min(cost[(c + 1) % 3], cost[(c + 2) % 3])
                next_cost[c] = best_previous + self.changeCost(choices[i], c)
            cost = next_cost

        # The last engineer sits next to engineer 0,
        # so it must end with one of the other two options.
        return min(cost[(first + 1) % 3], cost[(first + 2) % 3])

    def changeCost(self, original: str, c: int) -> int:
        """Returns 0 if the engineer already has options[c], otherwise 1."""
        return 0 if original == self.options[c] else 1
```


### Complexity

- **Time:** `O(n)`. Each engineer is processed in 3 runs, with 3 options per run.
- **Space:** `O(1)`. We only keep 3 numbers at a time.


This is fast enough. But it hides a much simpler pattern, so let's look for it.


## Solution 2: Count Blocks of Same Choices (Optimal)

### Step 1: Solve a Straight Row

Forget the circle for a moment. Look at one block of `L` same choices, like `RRRRR`.


**`L // 2` changes are enough.** Change the 2nd, 4th, 6th ... engineer of the block. A changed engineer's two neighbors are not changed, so they block at most 2 options. The third option is always free.


**Fewer changes are not possible.** Pair up the block: (1st, 2nd), (3rd, 4th) and so on. That gives `L // 2` pairs. Each pair is a tie, and no engineer is in two pairs, so each pair needs its own change.


A change in one block never fixes a tie in another block. So for a straight row, the answer is just the sum of `L // 2` over all blocks.

```
RRRPPS  ->  RRR | PP | S  ->  1 + 1 + 0 = 2
```


### Step 2: Bring Back the Circle

The circle causes two problems.


**Problem 1: a block can wrap around the end.** In `RPR`, a straight row count sees blocks `R`, `P`, `R` and returns 0. But the last `R` sits next to the first `R`, so the real answer is 1.


**Fix:** find a seat `start` whose choice differs from the seat on its left. Then walk once around the table beginning at `start`. The walk now begins and ends at a block edge, so no block is split in two, and the circle behaves exactly like a straight row.

```
choices : R P S R R     (last R and first R are neighbors)
start   : seat 1, because P differs from R
walk    : P S R R R     (seats 1, 2, 3, 4, 0)
blocks  : P | S | RRR   ->  0 + 0 + 1 = 1
```


In code, seat `(start + step) % n` gives this walk order without building a new string.


**Problem 2: everyone picked the same option.** Then no seat differs from its left neighbor, so there is no `start`. The whole table is one closed loop of ties, like `PPPPPP`.


**Fix:** count it directly. There are `n` ties (one for every pair of neighbors), and one change fixes at most 2 ties. So we need at least `n / 2` changes, rounded up, which is `(n + 1) // 2`. Changing every second engineer, plus the last one when `n` is odd, reaches exactly that.

```
PPPPPP  ->  PRPRPR  ->  3 changes
RRRRR   ->  RPRPS   ->  3 changes
```


### Code

```python
class MinimumChangesCircularRockPaperScissors:

    def __init__(self):
        pass

    def minChanges(self, choices: str) -> int:
        """
        Returns the minimum number of engineers who must change their choice
        so that no two neighbors around the circular table have the same choice.
        """
        n = len(choices)

        # Find a seat whose choice differs from the seat on its left.
        start = -1
        for i in range(1, n):
            if choices[i] != choices[i - 1]:
                start = i
                break

        # No such seat: everyone picked the same option.
        # The whole table is one closed loop of ties.
        if start == -1:
            return (n + 1) // 2

        # Walk once around the table, beginning at 'start'.
        # The walk begins and ends at a block edge, so no block is split in two.
        changes = 0
        block_length = 1
        for step in range(1, n):
            current = choices[(start + step) % n]
            previous = choices[(start + step - 1) % n]

            if current == previous:
                block_length += 1              # same block continues
            else:
                changes += block_length // 2   # a block just ended
                block_length = 1               # a new block starts here
        changes += block_length // 2           # the last block

        return changes
```


### Complexity

- **Time:** `O(n)`. One pass to find `start`, one pass to count blocks.
- **Space:** `O(1)`. Just a few counters.