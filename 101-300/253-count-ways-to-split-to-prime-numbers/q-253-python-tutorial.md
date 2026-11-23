# Count Ways to Split String Into Prime Numbers in Python

#### Problem Statement
[https://codezym.com/question/253-count-ways-to-split-to-prime-numbers](https://codezym.com/question/253-count-ways-to-split-to-prime-numbers)


## Core Idea

Splitting the string is a series of small choices: where should the current part end?

Once we pick a first part that is prime, the rest of the string (a suffix) is a smaller copy of the same problem. So a simple recursion can count all the splits.

But that recursion solves the same suffix many times. So we use dynamic programming: count the splits of each suffix only once, store the count in a list, and build the answer from right to left.

To check if a part is prime, we try dividing it by numbers up to its square root.


## Building Block: Is This Part Prime?

Both approaches below keep asking one question: is this part a prime number?

If a number is not prime, we can write it as `a × b`, where both `a` and `b` are bigger than 1. They cannot both be bigger than the square root, or their product would be too big. So one of them is at most the square root, and we only need to try divisors up to there.

In code we use `math.isqrt(number)`. It gives the square root rounded down to a whole number, so there are no decimals to worry about.

For a 12-digit number the square root is below 1,000,000. We also skip even divisors after checking 2, so the loop runs at most about 500,000 times.

```python
def isPrime(self, number: int) -> bool:
    if number < 2:
        return False  # 0 and 1 are not prime
    if number == 2:
        return True
    if number % 2 == 0:
        return False  # every other even number is not prime

    # Try odd divisors up to the square root.
    for divisor in range(3, math.isqrt(number) + 1, 2):
        if number % divisor == 0:
            return False
    return True
```


## Approach 1: Brute Force (Try Every Split)

Start at index `0` and try every possible first part: `s[0:1]`, `s[0:2]`, `s[0:3]` and so on. If the part is prime, count the ways to split the rest of the string with the same function.

When `start` reaches the end of the string, every digit has been used. That is one complete split, so we return `1`.

A part can never start with `'0'`. A part like `"07"` has a leading zero, and `"0"` alone is not prime. So if the digit at `start` is `'0'`, there are no ways from here.

```python
def countPrimeSplits(self, s: str) -> int:
    return self.countFrom(s, 0)

# Number of ways to split s[start:] into prime parts.
def countFrom(self, s: str, start: int) -> int:
    # Every digit is used: one complete split found.
    if start == len(s):
        return 1
    # No part can start with '0'.
    if s[start] == '0':
        return 0
    total = 0
    for end in range(start, len(s)):
        number = int(s[start:end + 1])
        if self.isPrime(number):
            total += self.countFrom(s, end + 1)
    return total
```

### Why is it slow?

Take `s = "3175"`. The suffix `"5"` is solved 3 separate times: after `[3, 17]`, after `[31, 7]` and after `[317]`.

A string with `n` digits has 2<sup>n-1</sup> possible splits, and that number doubles with every extra digit. Many of these splits share the same suffix, so the same suffixes and the same prime checks are solved over and over.

With at most 12 digits this is still small, but much of the work is repeated. Let's remove it.


## Approach 2: Dynamic Programming (Count Each Suffix Once)

The key observation: **the number of ways to split a suffix does not depend on how we reached it.**

The suffix `"5"` has exactly 1 split, no matter which parts came before it. So we count each suffix once and reuse that count.

### The `ways` list

Let `ways[i]` be the number of ways to split the suffix that starts at index `i`.

For every `i`, we try each part `s[i:end + 1]`. If that part is prime, every split of the rest can follow it. So:

```
ways[i] = sum of ways[end + 1], for every end where s[i:end + 1] is prime
```

- **Base case:** `ways[n] = 1`. The suffix is empty, so all digits are used. This is one finished split.
- **Starts with `'0'`:** `ways[i]` stays `0`, for the same reason as in Approach 1.
- **Answer:** `ways[0]`.

`ways[i]` only needs values to its right. So we fill the list from right to left, and everything it needs is already computed.

**Why a list?** A suffix is fully described by its start index, a number from `0` to `n`. So a list of size `n + 1` is the simplest way to keep one count per suffix.

### Two small speed ups

1. **Build the number digit by digit.** As `end` moves right, the part grows by one digit: `number = number * 10 + digit`. So we never need to slice the string.
2. **Skip useless prime checks.** If `ways[end + 1]` is `0`, the rest cannot be split into primes, so this part adds nothing. We skip its prime check. This helps because big numbers are the slowest to check.

### Dry Run: s = "3175"

| i | Suffix | Prime parts found | ways[i] |
|---|--------|-------------------|---------|
| 4 | (empty) | none, base case | 1 |
| 3 | 5 | 5 (adds ways[4] = 1) | 1 |
| 2 | 75 | 7 (adds ways[3] = 1) | 1 |
| 1 | 175 | 17 (adds ways[3] = 1) | 1 |
| 0 | 3175 | 3 (adds ways[1] = 1)<br>31 (adds ways[2] = 1)<br>317 (adds ways[3] = 1) | 3 |

So the answer is `ways[0] = 3`. These are exactly `[3, 17, 5]`, `[31, 7, 5]` and `[317, 5]`.

### Python Code

```python
import math


class PrimeSplitCounter:

    def __init__(self):
        pass

    def countPrimeSplits(self, s: str) -> int:
        """
        Counts the ways to split s into parts where every part is prime.
        ways[i] = number of ways to split the suffix that starts at index i.
        """
        n = len(s)
        ways = [0] * (n + 1)

        # Empty suffix: all digits are used, so it is one complete split.
        ways[n] = 1

        # Fill from right to left, so ways[end + 1] is always ready.
        for start in range(n - 1, -1, -1):
            # No part can start with '0', so ways[start] stays 0.
            if s[start] == '0':
                continue

            number = 0
            for end in range(start, n):
                # Grow the current part by one digit.
                number = number * 10 + int(s[end])

                # Count it only if the rest can be split and this part is prime.
                if ways[end + 1] > 0 and self.isPrime(number):
                    ways[start] += ways[end + 1]

        return ways[0]

    def isPrime(self, number: int) -> bool:
        """
        A number that is not prime always has a divisor
        no bigger than its square root.
        """
        if number < 2:
            return False  # 0 and 1 are not prime
        if number == 2:
            return True
        if number % 2 == 0:
            return False  # every other even number is not prime

        # Try odd divisors up to the square root.
        for divisor in range(3, math.isqrt(number) + 1, 2):
            if number % divisor == 0:
                return False
        return True
```

### Complexity

- **Time:** `O(n² × √V)`, where `V` is the largest part value (below 10<sup>12</sup>). For 12 digits there are only 78 possible parts (start, end pairs), and each one is prime checked at most once.
- **Space:** `O(n)` for the `ways` list.