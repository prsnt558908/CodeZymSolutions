# Maximum Consistent Activity Streak in Java

#### Problem Statement
[https://codezym.com/question/459-max-consistent-activity-streak](https://codezym.com/question/459-max-consistent-activity-streak)

## Core Idea

A streak is consistent when the value moves by the same amount every day. So instead of looking at the values, we look at the **gap** (difference) between every pair of neighboring days. A consistent streak is simply a stretch where the gap never changes.

For Part 1, one pass is enough: count how many times in a row the same gap repeats.

For Part 2, replacing one day only affects the streaks that touch that day. The new value can continue the streak on its left, lead into the streak on its right, or sit exactly in the middle of its two neighbors and join both streaks into one.

If we know the longest streak that **ends** at every day and the longest streak that **starts** at every day, each of these three choices can be checked in O(1). Two plain arrays give us exactly that, so the whole solution runs in O(n) time.

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

The answer is the largest `current` we ever see. This takes O(n) time. In the final code this scan lives in `longestRun()`, so Part 2 can reuse it.

**Why `long`?** Values range from -1,000,000,000 to 1,000,000,000, so a gap can reach 2,000,000,000. That is very close to the `int` limit (about 2.1 billion). To stay safe, we copy the values into a `long[]` once and do all the math there.

## Part 2: Longest Streak With At Most One Change

### Which replacement values are worth trying?

The new value can be any integer, so we cannot try them all. Luckily, only a few values can ever help.

Suppose we replace day `i` and it becomes part of a longer streak. Day `i` is either the last day, the first day or a middle day of that streak:

1. **Last day:** it must continue the gap of the streak on its left. Value: `a[i-1] + (a[i-1] - a[i-2])`.
2. **First day:** it must lead into the gap of the streak on its right. Value: `a[i+1] - (a[i+2] - a[i+1])`.
3. **Middle day:** the gaps on both sides must be equal, so it must be the exact middle of its neighbors. Value: `(a[i-1] + a[i+1]) / 2`. This works only when `a[i+1] - a[i-1]` is even, otherwise the middle is a fraction.

Streaks of just 2 days always exist without any change, so they need no special value.

### Approach 1: Brute Force

For every day, try each candidate value and rerun the Part 1 scan on the changed list. We start from the Part 1 answer, since changing nothing is also allowed.

```java
// Reuses longestRun() and toLongArray() from the final code below
public int longestConsistentStreakWithOneChange(List<Integer> activity) {
    long[] a = toLongArray(activity); // a copy, so the input list stays unchanged
    int n = a.length;
    int best = longestRun(a);         // leave every value unchanged

    for (int i = 0; i < n; i++) {
        // The only replacement values worth trying for day i
        List<Long> candidates = new ArrayList<>();
        if (i >= 2) {
            candidates.add(a[i - 1] + (a[i - 1] - a[i - 2])); // continue the left streak
        }
        if (i + 2 < n) {
            candidates.add(a[i + 1] - (a[i + 2] - a[i + 1])); // lead into the right streak
        }
        if (i > 0 && i < n - 1 && (a[i + 1] - a[i - 1]) % 2 == 0) {
            candidates.add((a[i - 1] + a[i + 1]) / 2);       // exact middle of both neighbors
        }

        long original = a[i];
        for (long value : candidates) {
            a[i] = value;
            best = Math.max(best, longestRun(a)); // full rescan of the list: O(n)
        }
        a[i] = original; // undo the change before trying the next day
    }
    return best;
}
```

This is correct but slow. There are up to `3n` candidate changes and each one rescans the whole list, so it takes O(n²) time. For 100,000 days that is about 30 billion steps.

### Approach 2: Streaks From Both Sides (Optimal)

The brute force wastes time. A change at day `i` only matters around day `i`, yet we rescan everything. Any streak through day `i` is built from three pieces:

```
[ streak ending at day i-1 ]  day i  [ streak starting at day i+1 ]
```

So we precompute two arrays, once:

- `endsAt[i]`: length of the longest streak (no change) that **ends** at day `i`. Filled left to right, exactly like the Part 1 scan.
- `startsAt[i]`: length of the longest streak (no change) that **starts** at day `i`. Filled the same way, right to left.

Now each candidate from the list above takes O(1) to check:

| Day `i` becomes | Length of the new streak |
|---|---|
| Last day of the left streak | `endsAt[i-1] + 1` |
| First day of the right streak | `1 + startsAt[i+1]` |
| Middle day that joins both | `left + 1 + right` |

Each option is used only when the neighbors it needs exist. For example, day `0` has nothing on its left.

For the "joins both" option, the common gap is `diff = (a[i+1] - a[i-1]) / 2`:

- The streak ending at `i-1` moves by one fixed gap, `a[i-1] - a[i-2]`. If that gap equals `diff`, the whole streak fits, so `left = endsAt[i-1]`. Otherwise only day `i-1` fits, so `left = 1`.
- The right side works the same way, using `a[i+2] - a[i+1]` and `startsAt[i+1]`.

As before, we start from the Part 1 answer to cover the "no change" case.

**Watch out:** in Java, `-7 % 2` is `-1`, not `1`. So to detect an odd gap we test `gap % 2 != 0`. Testing `gap % 2 == 1` would miss negative odd gaps.

#### Walkthrough

For `activity = [4, 9, 14, 31, 24, 29]`:

| Day | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| activity | 4 | 9 | 14 | 31 | 24 | 29 |
| endsAt | 1 | 2 | 3 | 2 | 2 | 2 |
| startsAt | 3 | 2 | 2 | 2 | 2 | 1 |

Replace day 3 (`31`). Its neighbors are `14` and `24`, which are `10` apart. That is even, so `diff = 5` and the new value is `19`.

- Left: `14 - 9 = 5` matches, so `left = endsAt[2] = 3`.
- Right: `29 - 24 = 5` matches, so `right = startsAt[4] = 2`.

The joined streak has `3 + 1 + 2 = 6` days, the whole list.

Now take `activity = [2, 5, 9, 12]`. To join both sides at day 1 or day 2, the neighbors are `7` apart, and the middle would be a fraction. So no join is possible, and the best we can do is stretch a 2 day streak to 3 days.

## Why These Data Structures?

- **`long[] a`:** a copy of the input. Gaps cannot overflow in `long`, indexing is simple, and the original list stays unchanged, as the rules require.
- **`int[] endsAt` and `int[] startsAt`:** they remember the streak on the left and on the right of every day. This turns each replacement check from a full O(n) rescan into an O(1) lookup.

## Complexity

- **Part 1:** O(n) time, O(n) space for the `long` copy.
- **Part 2:** O(n) time, O(n) space for the copy and the two arrays.

## Final Code

```java
import java.util.*;

public class MaximumConsistentActivityStreak {

    public MaximumConsistentActivityStreak() {
    }

    // ===================== Part 1: Without Changes =====================

    public int longestConsistentStreak(List<Integer> activity) {
        return longestRun(toLongArray(activity));
    }

    /** Length of the longest streak in a, found by comparing neighboring gaps. */
    int longestRun(long[] a) {
        int best = 2;    // any two neighboring days form a streak
        int current = 2; // length of the streak that ends at day i
        for (int i = 2; i < a.length; i++) {
            if (a[i] - a[i - 1] == a[i - 1] - a[i - 2]) {
                current++;   // same gap as before: the streak grows
            } else {
                current = 2; // gap changed: a new streak starts at day i - 1
            }
            best = Math.max(best, current);
        }
        return best;
    }

    // ===================== Part 2: At Most One Change =====================

    public int longestConsistentStreakWithOneChange(List<Integer> activity) {
        long[] a = toLongArray(activity);
        int n = a.length;

        // endsAt[i] = length of the longest streak (no change) that ends at day i
        int[] endsAt = new int[n];
        endsAt[0] = 1;
        endsAt[1] = 2;
        for (int i = 2; i < n; i++) {
            if (a[i] - a[i - 1] == a[i - 1] - a[i - 2]) {
                endsAt[i] = endsAt[i - 1] + 1;
            } else {
                endsAt[i] = 2;
            }
        }

        // startsAt[i] = length of the longest streak (no change) that starts at day i
        int[] startsAt = new int[n];
        startsAt[n - 1] = 1;
        startsAt[n - 2] = 2;
        for (int i = n - 3; i >= 0; i--) {
            if (a[i + 1] - a[i] == a[i + 2] - a[i + 1]) {
                startsAt[i] = startsAt[i + 1] + 1;
            } else {
                startsAt[i] = 2;
            }
        }

        int best = longestRun(a); // leave every value unchanged

        // Try replacing each day i
        for (int i = 0; i < n; i++) {
            // Option 1: day i continues the streak that ends at day i - 1
            if (i > 0) {
                best = Math.max(best, endsAt[i - 1] + 1);
            }
            // Option 2: day i leads into the streak that starts at day i + 1
            if (i < n - 1) {
                best = Math.max(best, 1 + startsAt[i + 1]);
            }
            // Option 3: day i becomes the middle value that joins both streaks
            if (i > 0 && i < n - 1) {
                best = Math.max(best, joinBothSides(a, endsAt, startsAt, i));
            }
        }
        return best;
    }

    /**
     * Replaces day i with the exact middle of its two neighbors and returns the
     * length of the joined streak. Returns 0 if the middle is not a whole number.
     */
    int joinBothSides(long[] a, int[] endsAt, int[] startsAt, int i) {
        long gap = a[i + 1] - a[i - 1];
        if (gap % 2 != 0) {
            return 0; // "!= 0" (not "== 1") so negative odd gaps are caught too
        }
        long diff = gap / 2; // common gap of the joined streak

        // The streak on the left fits fully only if it moves by the same gap
        int left = 1;
        if (i >= 2 && a[i - 1] - a[i - 2] == diff) {
            left = endsAt[i - 1];
        }

        // Same check for the streak on the right
        int right = 1;
        if (i + 2 < a.length && a[i + 2] - a[i + 1] == diff) {
            right = startsAt[i + 1];
        }

        return left + 1 + right;
    }

    /** Copies the list into a long array, so gaps can never overflow. */
    long[] toLongArray(List<Integer> activity) {
        long[] a = new long[activity.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = activity.get(i);
        }
        return a;
    }
}
```