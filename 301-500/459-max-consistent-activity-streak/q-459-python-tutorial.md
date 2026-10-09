# Maximum Consistent Activity Streak in Python

#### Problem Statement
[https://codezym.com/question/459-max-consistent-activity-streak](https://codezym.com/question/459-max-consistent-activity-streak)

## Core Idea

A streak is consistent when the value moves by the same amount every day. So instead of looking at the values, we look at the **gap** (difference) between every pair of neighboring days. A consistent streak is simply a stretch where the gap never changes.

For Part 1, one pass is enough: count how many times in a row the same gap repeats.

For Part 2, replacing one day only affects the streaks that touch that day. The new value can continue the streak on its left, lead into the streak on its right, or sit exactly in the middle of its two neighbors and join both streaks into one.

If we know the longest streak that **ends** at every day and the longest streak that **starts** at every day, each of these three choices can be checked in O(1). Two plain lists give us exactly that, so the whole solution runs in O(n) time.

## Thinking in Gaps

Take `activity = [4, 9, 14, 31, 24, 29]` and write the gap between every pair of neighbors:

```
activity:   4     9     14    31    24    29
gap:           5     5     17    -7     5
```

A streak is a run of equal gaps, and a run of `k` equal gaps covers `k + 1` days. The longest run here is `5, 5`, which covers the 3 days `[4, 9, 14]`.

Two neighboring days have only one gap, so they always form a streak. This means the answer is never less than `2`.

## Part 1: Longest Streak Without Changes

Walk through the list once and keep `current`, the length of the streak that ends today.

- If today's gap equals yesterday's gap, the streak grows by one day.
- Otherwise a fresh streak of 2 days (yesterday and today) begins.

The answer is the largest `current` we ever see. This takes O(n) time, and Part 2 reuses this method.

## Part 2: Longest Streak With At Most One Change

### Which replacement values are worth trying?

The new value can be any integer, so we cannot try them all. Luckily, only a few values can ever help.

Suppose we replace day `i` and it becomes part of a longer streak. Day `i` is either the last day, the first day or a middle day of that streak:

1. **Last day:** it must continue the gap of the streak on its left. Value: `a[i-1] + (a[i-1] - a[i-2])`.
2. **First day:** it must lead into the gap of the streak on its right. Value: `a[i+1] - (a[i+2] - a[i+1])`.
3. **Middle day:** the gaps on both sides must be equal, so it must be the exact middle of its neighbors. Value: `(a[i-1] + a[i+1]) // 2`. This works only when `a[i+1] - a[i-1]` is even, otherwise the middle is a fraction.

Streaks of just 2 days always exist without any change, so they need no special value.

### Approach 1: Brute Force

For every day, try each candidate value and rerun the Part 1 scan on the changed list. We start from the Part 1 answer, since changing nothing is also allowed.

```python
# Reuses longestConsistentStreak() from the final code below
def longestConsistentStreakWithOneChange(self, activity):
    a = list(activity)                      # a copy, so the input list stays unchanged
    n = len(a)
    best = self.longestConsistentStreak(a)  # leave every value unchanged

    for i in range(n):
        # The only replacement values worth trying for day i
        candidates = []
        if i >= 2:
            candidates.append(a[i - 1] + (a[i - 1] - a[i - 2]))  # continue the left streak
        if i + 2 < n:
            candidates.append(a[i + 1] - (a[i + 2] - a[i + 1]))  # lead into the right streak
        if 0 < i < n - 1 and (a[i + 1] - a[i - 1]) % 2 == 0:
            candidates.append((a[i - 1] + a[i + 1]) // 2)        # exact middle of both neighbors

        original = a[i]
        for value in candidates:
            a[i] = value
            best = max(best, self.longestConsistentStreak(a))  # full rescan of the list: O(n)
        a[i] = original  # undo the change before trying the next day
    return best
```

This is correct but slow. There are up to `3n` candidate changes and each one rescans the whole list, so it takes O(n²) time. For 100,000 days that is about 30 billion steps.

### Approach 2: Streaks From Both Sides (Optimal)

The brute force wastes time. A change at day `i` only matters around day `i`, yet we rescan everything. Any streak through day `i` is built from three pieces:

```
[ streak ending at day i-1 ]  day i  [ streak starting at day i+1 ]
```

So we precompute two lists, once:

- `ends_at[i]`: length of the longest streak (no change) that **ends** at day `i`. Filled left to right, exactly like the Part 1 scan.
- `starts_at[i]`: length of the longest streak (no change) that **starts** at day `i`. Filled the same way, right to left.

Now each candidate from the list above takes O(1) to check:

| Day `i` becomes | Length of the new streak |
|---|---|
| Last day of the left streak | `ends_at[i-1] + 1` |
| First day of the right streak | `1 + starts_at[i+1]` |
| Middle day that joins both | `left + 1 + right` |

Each option is used only when the neighbors it needs exist. For example, day `0` has nothing on its left.

For the "joins both" option, the common gap is `diff = (a[i+1] - a[i-1]) // 2`:

- The streak ending at `i-1` moves by one fixed gap, `a[i-1] - a[i-2]`. If that gap equals `diff`, the whole streak fits, so `left = ends_at[i-1]`. Otherwise only day `i-1` fits, so `left = 1`.
- The right side works the same way, using `a[i+2] - a[i+1]` and `starts_at[i+1]`.

As before, we start from the Part 1 answer to cover the "no change" case.

#### Walkthrough

For `activity = [4, 9, 14, 31, 24, 29]`:

| Day | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| activity | 4 | 9 | 14 | 31 | 24 | 29 |
| ends_at | 1 | 2 | 3 | 2 | 2 | 2 |
| starts_at | 3 | 2 | 2 | 2 | 2 | 1 |

Replace day 3 (`31`). Its neighbors are `14` and `24`, which are `10` apart. That is even, so `diff = 5` and the new value is `19`.

- Left: `14 - 9 = 5` matches, so `left = ends_at[2] = 3`.
- Right: `29 - 24 = 5` matches, so `right = starts_at[4] = 2`.

The joined streak has `3 + 1 + 2 = 6` days, the whole list.

Now take `activity = [2, 5, 9, 12]`. To join both sides at day 1 or day 2, the neighbors are `7` apart, and the middle would be a fraction. So no join is possible, and the best we can do is stretch a 2 day streak to 3 days.

## Why These Data Structures?

- **`ends_at` and `starts_at` lists:** they remember the streak on the left and on the right of every day. This turns each replacement check from a full O(n) rescan into an O(1) lookup.
- **No copy of `activity` in the optimal solution:** it only reads the values, so the input list stays unchanged, as the rules require. The brute force does change values while testing, which is why it works on a copy.

## Complexity

- **Part 1:** O(n) time, O(1) extra space.
- **Part 2:** O(n) time, O(n) extra space for the two lists.

## Final Code

```python
class MaximumConsistentActivityStreak:

    def __init__(self):
        pass

    # ===================== Part 1: Without Changes =====================

    def longestConsistentStreak(self, activity):
        a = activity  # short alias, the list is only read, never changed
        best = 2      # any two neighboring days form a streak
        current = 2   # length of the streak that ends at day i
        for i in range(2, len(a)):
            if a[i] - a[i - 1] == a[i - 1] - a[i - 2]:
                current += 1  # same gap as before: the streak grows
            else:
                current = 2   # gap changed: a new streak starts at day i - 1
            best = max(best, current)
        return best

    # ===================== Part 2: At Most One Change =====================

    def longestConsistentStreakWithOneChange(self, activity):
        a = activity  # short alias, the list is only read, never changed
        n = len(a)

        # ends_at[i] = length of the longest streak (no change) that ends at day i
        ends_at = [1] * n
        ends_at[1] = 2
        for i in range(2, n):
            if a[i] - a[i - 1] == a[i - 1] - a[i - 2]:
                ends_at[i] = ends_at[i - 1] + 1
            else:
                ends_at[i] = 2

        # starts_at[i] = length of the longest streak (no change) that starts at day i
        starts_at = [1] * n
        starts_at[n - 2] = 2
        for i in range(n - 3, -1, -1):
            if a[i + 1] - a[i] == a[i + 2] - a[i + 1]:
                starts_at[i] = starts_at[i + 1] + 1
            else:
                starts_at[i] = 2

        best = self.longestConsistentStreak(a)  # leave every value unchanged

        # Try replacing each day i
        for i in range(n):
            # Option 1: day i continues the streak that ends at day i - 1
            if i > 0:
                best = max(best, ends_at[i - 1] + 1)
            # Option 2: day i leads into the streak that starts at day i + 1
            if i < n - 1:
                best = max(best, 1 + starts_at[i + 1])
            # Option 3: day i becomes the middle value that joins both streaks
            if 0 < i < n - 1:
                best = max(best, self.join_both_sides(a, ends_at, starts_at, i))
        return best

    def join_both_sides(self, a, ends_at, starts_at, i):
        """
        Replaces day i with the exact middle of its two neighbors and returns the
        length of the joined streak. Returns 0 if the middle is not a whole number.
        """
        gap = a[i + 1] - a[i - 1]
        if gap % 2 != 0:
            return 0
        diff = gap // 2  # common gap of the joined streak

        # The streak on the left fits fully only if it moves by the same gap
        left = 1
        if i >= 2 and a[i - 1] - a[i - 2] == diff:
            left = ends_at[i - 1]

        # Same check for the streak on the right
        right = 1
        if i + 2 < len(a) and a[i + 2] - a[i + 1] == diff:
            right = starts_at[i + 1]

        return left + 1 + right
```