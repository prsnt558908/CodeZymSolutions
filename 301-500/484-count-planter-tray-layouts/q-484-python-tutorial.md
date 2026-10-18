# Count Planter Tray Layouts in Python

#### Problem Statement
[https://codezym.com/question/484-count-planter-tray-layouts](https://codezym.com/question/484-count-planter-tray-layouts)

---

We fill the display from left to right, one column at a time. The leftmost empty column can be filled in only three ways: one vertical tray (uses 1 column), one square tray (uses 2 columns), or two L-shaped trays that lock together into a `2 × 3` block (uses 3 columns, and there are 2 mirror versions of it). So the answer for `n` columns is built from the answers for `n - 1`, `n - 2` and `n - 3` columns. This is a simple dynamic programming problem, much like counting the ways to climb stairs. We start with plain recursion, then save answers in a list, and finally keep only the last three answers.


![Example showing valid planter tray layouts](https://d3jug84e56mcss.cloudfront.net/question_images/0/q-484-img-1-sm-50-80.jpg)


---

## Understanding the Problem

The display has `2` rows and `n` columns. We have three tray types:

- **Vertical tray:** covers both spaces of one column.
- **Square tray:** covers a full `2 × 2` block.
- **L-shaped tray:** covers 3 spaces of a `2 × 2` block. All 4 rotations are allowed.


There is **no horizontal tray**. Keep this in mind. It is the reason this problem stays simple.


We must cover every space exactly once and count all different layouts.

---

## Key Observation: Look at the Leftmost Column

Let `ways(n)` be the number of layouts for `n` columns.


Look only at the leftmost column. Some tray must cover it. Let's see every option.


### Case 1: A vertical tray

```
V . . .
V . . .
```

One column is used. The rest is a smaller display with `n - 1` columns.

So this case gives `ways(n - 1)` layouts.


### Case 2: A square tray

```
S S . .
S S . .
```

Two columns are used. This case gives `ways(n - 2)` layouts.


### Case 3: An L-shaped tray

An L-shaped tray lives inside 2 columns. Two smaller questions come up here.


**Does the L cover both spaces of column 1?**

Yes, it must. If the L covered only one space of column 1, it would take both spaces of column 2. The other space of column 1 would then be trapped. A vertical tray needs the full column, a square tray needs column 2, and another L also needs space in column 2. Nothing fits.


So the L covers all of column 1 and one space of column 2. This leaves one **hole** in column 2.


**Who can fill that hole?**

A vertical tray cannot, it needs the full column. A square tray cannot either. Only a second L-shaped tray can. It covers the hole plus both spaces of column 3.


So L-shaped trays always come in pairs and form a `2 × 3` block. There are exactly 2 such blocks:

```
A B B .        A A B .
A A B .        A B B .
```

Three columns are used, in 2 different ways. This case gives `2 × ways(n - 3)` layouts.


> In similar problems that allow a horizontal tray, L-shaped trays can be far apart with horizontal trays between them. Here that is impossible, so the pair always closes right away.


### The Formula

Adding the three cases:

```
ways(n) = ways(n - 1) + ways(n - 2) + 2 × ways(n - 3)
```

**Base values:**

- `ways(0) = 1`: an empty display is already full. There is one way: place nothing.
- `ways` of a negative number is `0`: a tray that does not fit gives no layout.


### Quick Check

| n | ways(n-1) | ways(n-2) | 2 × ways(n-3) | ways(n) |
|---|-----------|-----------|---------------|---------|
| 1 | 1 | 0 | 0 | 1 |
| 2 | 1 | 1 | 0 | 2 |
| 3 | 2 | 1 | 2 | 5 |
| 4 | 5 | 2 | 2 | 9 |
| 5 | 9 | 5 | 4 | **18** |
| 6 | 18 | 9 | 10 | **37** |

Both examples match: `18` for 5 columns and `37` for 6 columns.

---

## Approach 1: Plain Recursion (Too Slow)

The formula can be written directly as a recursive function.

```python
def ways(n):
    if n < 0:
        return 0  # tray did not fit
    if n == 0:
        return 1  # display is full
    return (ways(n - 1) + ways(n - 2) + 2 * ways(n - 3)) % 1_000_000_007
```

This is correct, but it has two big problems.


**Problem 1: Repeated work.** `ways(n - 3)` is reached from many different paths, and each time it is computed again from scratch. The number of calls grows exponentially.


**Problem 2: Deep recursion.** `n` can be `1,000,000`. Python stops at a recursion depth of about `1000` and raises `RecursionError`.


Saving results in a dictionary (memoization) fixes Problem 1, but the recursion is still too deep. So we flip the direction and build answers from small to big.

---

## Approach 2: Bottom-Up DP with a List

Each answer depends only on smaller answers. So we compute `ways(1)`, `ways(2)`, `ways(3)` ... in order and store them in a list.


**Why a list?** It gives instant access to `ways[i - 1]`, `ways[i - 2]` and `ways[i - 3]` while we move forward. No recursion means no depth problem.


**Why take the modulo at every step?** Python integers never overflow, but the numbers grow huge very fast. Big numbers make every addition slow. Keeping each value below `1,000,000,007` keeps the loop fast.


### Code

```python
class PlanterTrayLayouts:
    def __init__(self):
        self.mod = 1_000_000_007

    def countLayouts(self, displayLength):
        """
        Counts layouts using a DP list.
        ways[i] = number of layouts for a display with i columns.
        """
        ways = [0] * (displayLength + 1)

        # An empty display is already full. There is exactly one way: place nothing.
        ways[0] = 1

        for i in range(1, displayLength + 1):
            # Case 1: first column is one vertical tray
            count = ways[i - 1]

            # Case 2: first two columns are one square tray
            if i >= 2:
                count += ways[i - 2]

            # Case 3: first three columns are two L-shaped trays locked together.
            # There are 2 mirror versions of this block.
            if i >= 3:
                count += 2 * ways[i - 3]

            ways[i] = count % self.mod

        return ways[displayLength]
```

### Complexity

- **Time:** `O(n)`, one loop over the columns.
- **Space:** `O(n)`, for the list.

---

## Approach 3: Keep Only the Last Three Answers (Best)

Look at the loop again. To compute `ways[i]` we only need the last three values. Everything older is never read again.


So we replace the list with three variables and slide them forward after each step:

```
before step:  three_before   two_before   one_before
after step:   two_before     one_before   current
```

At the start, `one_before = ways(0) = 1`. The other two are `0`, because negative column counts give no layouts.


### Code

```python
class PlanterTrayLayouts:
    def __init__(self):
        self.mod = 1_000_000_007

    def countLayouts(self, displayLength):
        """
        Counts layouts keeping only the last three answers.
        """
        three_before = 0  # ways(i - 3)
        two_before = 0    # ways(i - 2)
        one_before = 1    # ways(i - 1), starts as ways(0) = 1

        for _ in range(displayLength):
            # vertical tray + square tray + two mirror L-shaped blocks
            current = (one_before + two_before + 2 * three_before) % self.mod

            # slide the window one column to the right
            three_before = two_before
            two_before = one_before
            one_before = current

        return one_before
```

### Complexity

- **Time:** `O(n)`, one loop over the columns.
- **Space:** `O(1)`, just three variables.


With `n` up to `1,000,000`, this simple loop finishes in well under a second.

---

## Key Takeaways

- Always ask: **"How can the first column be filled?"** Each answer becomes one term in the formula.
- L-shaped trays can only appear in pairs here, forming a `2 × 3` block with 2 mirror versions.
- Recursion shows the idea. Bottom-up DP makes it fast and safe.
- When each step needs only the last few answers, keep just those in variables.