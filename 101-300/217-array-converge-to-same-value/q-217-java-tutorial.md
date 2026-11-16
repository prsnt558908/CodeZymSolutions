# Check If Array Can Converge To Same Value in Java

#### Problem Statement
[https://codezym.com/question/217-array-converge-to-same-value](https://codezym.com/question/217-array-converge-to-same-value)


Picture the digits `0` to `9` placed on a circle, where `9` is followed by `0`. Each element may move at most one step up or down on this circle. So every element can only ever become 3 different digits.


Instead of trying different moves for each element, we flip the question: **pick the final digit first, then check if every element can reach it.** There are only 10 possible final digits, so this check is cheap.


We start with a simple brute force that tests the 10 digits one by one. Then we improve it to a single pass where every element "votes" for the 3 digits it can become. The answer is the smallest digit that gets a vote from everyone.

---

## Understanding the Problem

An element `x` can become one of these 3 digits:

- `x`, it stays the same.
- `(x + 1) % 10`, one step up, so `9` becomes `0`.
- `(x + 9) % 10`, one step down, so `0` becomes `9`.


Why `(x + 9) % 10` and not `(x - 1) % 10`? In Java, `-1 % 10` is `-1`, not `9`. On a circle of 10, adding `9` lands on the same spot as going one step down, and it never goes negative.


So the real question is: **which digit can every element reach?**


Take Example 2, `nums = [8, 9, 0]`:

| Element | Can become |
|:---:|:---:|
| `8` | `7`, `8`, `9` |
| `9` | `8`, `9`, `0` |
| `0` | `9`, `0`, `1` |

Only `9` appears in every row, so the answer is `9`.


One small catch: the answer does not have to be in the list. For `nums = [5]`, the digits `4`, `5` and `6` all work, and the smallest is `4`.

---

## Approach 1: Try Every Target Digit (Brute Force)

A first thought is to try all 3 choices for every element. But that is `3^n` combinations, which is impossible for 100,000 elements.


Here is the trick: the final digit can only be one of 10 values. So we pick a target digit, then check if every element can reach it.


We try targets from `0` to `9` in order. The first target that works is automatically the smallest one.

```java
public int findConvergedValue(List<Integer> nums) {
    // try targets from 0 to 9, so the first one that works is the smallest
    for (int target = 0; target < 10; target++) {
        boolean everyoneCanReach = true;

        for (int x : nums) {
            if (!canBecome(x, target)) {
                everyoneCanReach = false;
                break;
            }
        }

        if (everyoneCanReach) {
            return target;
        }
    }
    return -1;
}

// x can become target by staying the same, or moving one step up or down
boolean canBecome(int x, int target) {
    return target == x
            || target == (x + 1) % 10
            || target == (x + 9) % 10;
}
```

### Complexity

- **Time:** `O(10 × n)`, at most one scan of the list per target digit.
- **Space:** `O(1)`.

### What Can Be Better?

This is fast enough for the limits, but it repeats work.


It starts a fresh scan of the list for every target digit. The same element can get checked many times against different targets, even though the 3 digits it can reach never change.


It would be nicer to visit each element **only once** and note down all 3 of its digits right away.

---

## Approach 2: Let Every Element Vote (One Pass)

Look at the Example 2 table again. We want the digit that appears in **every** row. So we just count how many rows each digit appears in.


Think of it as a vote. Each element gives one vote to each of the 3 digits it can become. A digit is a valid answer only if it gets a vote from every element, which means its vote count equals the list size.


Can one element vote twice for the same digit? No. `x - 1`, `x` and `x + 1` are always 3 different digits on a circle of 10. So a count equal to `n` really means all `n` elements can reach that digit.


For Example 2, the final counts are:

| Digit | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|---|
| Votes | 2 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 2 | **3** |

The list size is `3`, and only digit `9` has `3` votes. So the answer is `9`.


To get the smallest answer, we check digits from `0` to `9` and return the first one with a full count.

### Why an `int[]` of Size 10?

Digits are only `0` to `9`, so each digit can be its own index in the array. `votes[d]` holds the count for digit `d`.


There is no need for a map, and the memory stays the same no matter how long the list is.

### Code

```java
import java.util.*;

public class ArrayConvergence {

    public ArrayConvergence() {
    }

    /**
     * Returns the smallest digit that every element can become,
     * or -1 if no such digit exists.
     */
    public int findConvergedValue(List<Integer> nums) {
        // votes[d] = how many elements can become digit d
        int[] votes = new int[10];

        // every element votes for the 3 digits it can become
        for (int x : nums) {
            votes[x]++;            // stay the same
            votes[(x + 1) % 10]++; // one step up, 9 becomes 0
            votes[(x + 9) % 10]++; // one step down, 0 becomes 9
        }

        // scan 0 to 9, so the first full count is the smallest answer
        for (int digit = 0; digit < 10; digit++) {
            if (votes[digit] == nums.size()) {
                return digit;
            }
        }
        return -1;
    }
}
```

### Complexity

- **Time:** `O(n)`, one pass over the list plus a fixed check of 10 digits.
- **Space:** `O(1)`, just 10 counters.


Both approaches are linear in the end, since 10 is a constant. The real gain is that each element is read only once, with no nested loop and no helper method.

---

## Key Takeaways

- When the answer can only be one of a few values, **fix the answer first and check it**, instead of trying every combination of moves.
- When values are small, like `0` to `9`, a plain array indexed by the value is the simplest counter.
- On a circle of size `m`, step down with `(x + m - 1) % m` to avoid negative numbers.