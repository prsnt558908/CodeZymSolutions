# Subset Sum Equal to K in Python

#### Problem Statement
[https://codezym.com/question/305-subset-sum-equal-to-k](https://codezym.com/question/305-subset-sum-equal-to-k)

---

## Core Idea

For every number in the list, we have only two choices: **take it** or **skip it**.

With `n` numbers, that gives `2 x 2 x ... x 2 = 2^n` different subsets.

Here `n` is at most `20`, so there are at most `2^20 = 1,048,576` subsets. That is small enough to simply check every one of them.

The neat trick is to use a **binary number (bit mask)** to describe a subset. Each bit works like an on/off switch for one element. Bit `1` means "take it" and bit `0` means "skip it".

So we just count from `0` to `2^n - 1`, build the sum for each mask, and stop as soon as some sum equals `k`.

---

## Understanding the Problem

We get a list `nums` and a target `k`. We must tell if we can pick some elements (each at most once) so that their sum is exactly `k`.


A few things to keep in mind:

- Picked elements do not need to be next to each other.
- Numbers can be **negative**. So we cannot stop early just because a sum became too big.
- The **empty subset** is allowed and its sum is `0`. So if `k = 0`, the answer is always `True`.

**Example:** `nums = [3, 8, 11, 14], k = 19`

Picking `8` and `11` gives `19`, so the answer is `True`.

---

## Solution 1: Simple Recursion (Pick or Skip)

The most natural way to try all subsets is recursion.


We walk through the list one element at a time. At each element we try two paths:

1. Take the element and add it to the running sum.
2. Skip the element and keep the running sum as it is.


When we reach the end of the list, one full subset has been decided. We check if its sum equals `k`.

### Code

```python
class SubsetSumEqualToK:
    def __init__(self):
        pass

    def subsetSum(self, nums, k):
        return self.check(nums, 0, 0, k)

    def check(self, nums, index, current_sum, k):
        """
        Decides for nums[index] whether to take it or skip it.
        current_sum is the sum of the numbers picked so far.
        """
        # All numbers decided, so one complete subset is ready
        if index == len(nums):
            return current_sum == k

        # Choice 1: take nums[index]
        if self.check(nums, index + 1, current_sum + nums[index], k):
            return True

        # Choice 2: skip nums[index]
        return self.check(nums, index + 1, current_sum, k)
```

### Complexity

- **Time:** `O(2^n)`, because we visit every subset once.
- **Space:** `O(n)`, because the recursion goes `n` calls deep and each call sits on the call stack.

### What is the problem with this?

This solution is correct, but it does not match what the problem asks for.


The problem wants us to **represent each subset with a bit mask** and use only **O(1) extra space**.


Recursion keeps up to `n` function calls waiting on the stack, so it uses `O(n)` extra memory. Also, the subsets are never written down as masks.

Let us fix both points with bit masking.

---

## Solution 2: Bit Masking (Optimal)

### What is a bit mask?

A bit mask is just a normal integer. We look at its binary form and treat each bit as a switch.

- Bit `i` is `1` → `nums[i]` is in the subset.
- Bit `i` is `0` → `nums[i]` is not in the subset.


Bits are counted from the **right side**, starting at index `0`.

### Example

`nums = [4, 7, 13], k = 10`. Here `n = 3`, so we have `2^3 = 8` masks.

| mask | binary | subset | sum |
|------|--------|--------|-----|
| 0 | 000 | [ ] | 0 |
| 1 | 001 | [4] | 4 |
| 2 | 010 | [7] | 7 |
| 3 | 011 | [4, 7] | 11 |
| 4 | 100 | [13] | 13 |
| 5 | 101 | [4, 13] | 17 |
| 6 | 110 | [7, 13] | 20 |
| 7 | 111 | [4, 7, 13] | 24 |

No sum equals `10`, so the answer is `False`.


Notice how counting `0, 1, 2, ... 7` gives us **every subset exactly once**. No subset is missed and none is repeated.

### How do we get all masks?

`1 << n` means "shift `1` to the left by `n` places". It is the same as `2^n`.

So a simple loop `for mask in range(1 << n)` visits every subset.

### How do we check if bit `i` is on?

We use `(mask >> i) & 1`.

- `mask >> i` moves bit `i` to the rightmost position.
- `& 1` keeps only that rightmost bit.


If the result is `1`, then `nums[i]` is part of this subset.

**Example:** `mask = 5` (binary `101`) and `i = 2`.

`5 >> 2` gives `1` (binary `001`). Then `1 & 1` gives `1`. So `nums[2]` is included.

### Small details

- **Empty subset:** mask `0` has no bits on, so its sum is `0`. This handles `k = 0` on its own, no special check needed.
- **Early exit:** as soon as one mask gives sum `k`, we return `True`. There is no need to check the rest.
- **Negative numbers:** we never skip a mask based on the sum, so negatives work without any extra handling.

### Code

```python
class SubsetSumEqualToK:
    def __init__(self):
        pass

    def subsetSum(self, nums, k):
        """
        Returns True if any subset of nums adds up to k.
        Every number from 0 to 2^n - 1 is a mask that stands for one subset.
        If bit i of the mask is 1, nums[i] is part of that subset.
        """
        n = len(nums)
        total_subsets = 1 << n  # 2^n subsets in total

        for mask in range(total_subsets):
            subset_sum = 0

            for i in range(n):
                # Is bit i switched on in this mask?
                if (mask >> i) & 1:
                    subset_sum += nums[i]

            if subset_sum == k:
                return True

        return False
```

### Complexity

- **Time:** `O(n * 2^n)`. There are `2^n` masks, and for each one we check `n` bits.
- **Space:** `O(1)`. We only use a few variables like `mask`, `subset_sum` and `i`.

---

## Key Takeaways

- Each element has two choices, so there are `2^n` subsets.
- An integer from `0` to `2^n - 1` can describe any subset through its bits.
- `(mask >> i) & 1` tells us if element `i` is picked.
- Bit masking avoids recursion and needs only constant extra memory.
- This works well only when `n` is small (around `20` or less), because `2^n` grows very fast.