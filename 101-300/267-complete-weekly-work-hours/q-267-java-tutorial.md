# Complete Weekly Work Hours in Java

#### Problem Statement
[https://codezym.com/question/267-complete-weekly-work-hours](https://codezym.com/question/267-complete-weekly-work-hours)


## Core Idea

Every `#` is a blank that can hold one digit from 0 to 9. We fill the blanks one at a time, from left to right, and keep track of how many hours are still needed.

This is backtracking: put a digit in a blank, move on to the next blank, and when you come back, try the next digit.

Two simple ideas make it efficient. At each blank, we only try digits that can still reach the exact total. And since we always try smaller digits first, the answers come out already sorted.


## Understanding the Problem

Take Example 1: `workHours = "#120#30"` and `requiredHours = 10`.

The fixed digits `1, 2, 0, 3, 0` add up to `6`. So the two blanks must add up to `10 - 6 = 4`.

The pairs (0, 4), (1, 3), (2, 2), (3, 1) and (4, 0) give the five answers, from `"0120430"` to `"4120030"`.

The fixed digits never change. Only the blanks do.


## Approach 1: Brute Force

The simplest idea: try every digit in every blank, and check the total at the end.

We first save the positions of all `#` in a list called `blanks`. Then a recursive function puts each digit from 0 to 9 into blank number `k` and moves on to blank `k + 1`.

When all blanks are filled, we add up the seven digits and keep the string if the total is `requiredHours`. At the end, we sort the answers.

```java
void tryAll(char[] days, List<Integer> blanks, int k, int requiredHours, List<String> result) {
    // All blanks are filled: check the total
    if (k == blanks.size()) {
        int total = 0;
        for (char c : days) total += c - '0';
        if (total == requiredHours) result.add(new String(days));
        return;
    }
    // Try every digit in blank k
    for (int d = 0; d <= 9; d++) {
        days[blanks.get(k)] = (char) ('0' + d);
        tryAll(days, blanks, k + 1, requiredHours, result);
    }
}

// After the search
Collections.sort(result);
```

### Problems With Brute Force

- **Too many strings:** with 7 blanks, it builds and checks `10^7`, that is 10 million, strings.
- **No early stop:** it keeps going even when a path is hopeless. For `"#######"` with `requiredHours = 0`, it checks all 10 million strings to find just one answer, `"0000000"`.
- **Extra sorting:** the answers have to be sorted at the end.


## Approach 2: Backtracking With Smart Digit Limits

We keep the same left to right search and fix each problem, one by one.

### Improvement 1: Track the Hours Still Needed

Instead of adding up all seven digits at the end, we start with the hours the blanks must cover, just like we did by hand above:

```
remaining = requiredHours - fixedHours
```

Each time we put digit `d` in a blank, `remaining` goes down by `d`. When every blank is filled, `remaining` must be `0`.

### Improvement 2: Only Try Digits That Can Still Work

Say we are at a blank. We still need `remaining` hours, and `blanksAfter` blanks come after this one. The digit `d` we put here must be:

- **Not too big:** `d` cannot be more than `remaining`, or we go over the total.
- **Not too small:** each later blank adds at most `9` hours. So `d` must be at least `remaining - 9 * blanksAfter`, or the later blanks can never catch up.

Together with the normal 0 to 9 limit, this gives a small range of useful digits:

```java
int low = Math.max(0, remaining - 9 * blanksAfter);
int high = Math.min(9, remaining);
```

We only try digits from `low` to `high`. If `low > high`, no digit fits and the path stops right there. This cutting of hopeless paths is often called **pruning**.

Now **every digit we try leads to at least one valid answer**, so no time is wasted on dead ends. For `"#######"` with `requiredHours = 0`, the range at every blank is just `0`, so we reach the only answer in 7 steps instead of 10 million.

Impossible inputs are handled for free too. In Example 3, the fixed digits already give 18 hours, so `remaining = 10 - 18 = -8`. Then `high` is negative, the range is empty and we return an empty list.

### Improvement 3: No Sorting Needed

For digit strings of the same length, lexicographic order is just number order. So `"0120430"` comes before `"1120330"`.

All answers share the same fixed digits, so the first place where two answers differ is always a blank. We fill blanks from left to right and try smaller digits first, so the answer with the smaller digit at that blank is always built first.

That means answers are added to the list in sorted order. No sorting is needed.

### Dry Run on Example 1

We have `remaining = 4` and blanks at index `0` and `4`.

- **First blank:** `blanksAfter = 1`, so `low = max(0, 4 - 9) = 0` and `high = min(9, 4) = 4`.
- **Second blank:** `blanksAfter = 0`, so `low` and `high` both equal `remaining`. Exactly one digit fits.

| First blank | Hours still needed | Second blank | Answer |
|:---:|:---:|:---:|:---:|
| 0 | 4 | 4 | 0120430 |
| 1 | 3 | 3 | 1120330 |
| 2 | 2 | 2 | 2120230 |
| 3 | 1 | 1 | 3120130 |
| 4 | 0 | 0 | 4120030 |

Brute force builds 100 strings for this input. We build only 5, all of them are answers, and they come out sorted.

### Why These Data Structures?

**`char[] days`** is a copy of `workHours` that we can edit in place. Each new digit simply overwrites the blank. We create a `String` only when an answer is ready.

There is no need to put the `#` back. By the time we reach the end, every blank holds the digit chosen on the current path.

**`List<Integer> blanks`** holds the positions of all `#`. The search jumps straight from one blank to the next and skips the fixed days.

It also tells us how many blanks come after blank `k`: `blanks.size() - k - 1`.


## Complexity

Let `b` be the number of blanks and `R` the number of valid answers.

| Approach | Time | Extra Space |
|---|---|---|
| Brute Force | `O(10^b × 7)` plus sorting | `O(7)` |
| Backtracking | `O(7 × R)` | `O(7)` |

In the backtracking solution, every digit we try is on the path to some answer, and each path is at most 7 digits long. So the search takes at most `7 × R` steps, plus 7 steps to build each answer string.

We must return all `R` strings anyway, so we cannot do better. And `R` can be big: with all 7 days blank and `requiredHours = 31`, there are 512,365 answers.

Extra space does not count the output list.


## Final Java Code

```java
import java.util.*;

public class CompleteWeeklyWorkHours {

    public CompleteWeeklyWorkHours() {
    }

    public List<String> completeWorkHours(String workHours, int requiredHours) {
        List<String> result = new ArrayList<>();
        char[] days = workHours.toCharArray();

        // Save the positions of '#' and add up the hours that are already fixed
        List<Integer> blanks = new ArrayList<>();
        int fixedHours = 0;
        for (int i = 0; i < days.length; i++) {
            if (days[i] == '#') {
                blanks.add(i);
            } else {
                fixedHours += days[i] - '0';
            }
        }

        // The blanks together must make up the rest of the hours
        fill(days, blanks, 0, requiredHours - fixedHours, result);
        return result;
    }

    /**
     * Fills blank number k, then moves on to blank k + 1.
     * remaining = hours that the blanks from k onwards still need to add up to.
     * Smaller digits are tried first, so answers are added in sorted order.
     */
    void fill(char[] days, List<Integer> blanks, int k, int remaining, List<String> result) {
        // All blanks are filled
        if (k == blanks.size()) {
            // remaining can be non-zero here only when there were no blanks at all
            if (remaining == 0) {
                result.add(new String(days));
            }
            return;
        }

        int blanksAfter = blanks.size() - k - 1;

        // Not too small: each later blank can add at most 9 hours
        int low = Math.max(0, remaining - 9 * blanksAfter);
        // Not too big: we must never go over the remaining hours
        int high = Math.min(9, remaining);

        for (int d = low; d <= high; d++) {
            // No need to undo: at the end, every blank holds the digit chosen on this path
            days[blanks.get(k)] = (char) ('0' + d);
            fill(days, blanks, k + 1, remaining - d, result);
        }
    }
}
```