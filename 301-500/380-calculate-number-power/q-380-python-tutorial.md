# Calculate Power Without Built-In Functions in Python

#### Problem Statement
[https://codezym.com/question/380-calculate-number-power](https://codezym.com/question/380-calculate-number-power)

---

We need to find `base` raised to `exponent` without using `**`, `pow()` or `math.pow()`.


The simplest way is to multiply `base` by itself again and again. But the exponent can be as big as 2 billion, so that is far too slow.


The core trick is **fast power** (also called repeated squaring). Instead of multiplying by `base` each time, we keep **squaring** it: `base`, `base²`, `base⁴`, `base⁸` and so on. Each squaring doubles the power, so we reach huge powers in very few steps.


With this trick, even the biggest exponent needs only about 32 loop rounds instead of 2 billion.

---

## Understanding the Problem

The rules are simple:

- `base^3` means `base * base * base`
- `base^-2` means `1 / (base^2)`
- `base^0` is always `1.0`


A few things make this tricky:

- `exponent` can be anywhere from `-2,147,483,648` to `2,147,483,647`
- `base` can be negative, like `-2.0`
- We cannot use any built-in power function

---

## Solution 1: Brute Force (Simple Loop)

### Idea

Start with `result = 1.0`. Multiply it by `base`, exactly `abs(exponent)` times.


If the exponent was negative, return `1 / result` at the end.


Python integers have no size limit, so `abs(-2147483648)` is safe and gives `2147483648`.


### Code

```python
class PowerCalculator:
    """Brute force: multiply base by itself, exponent times.

    Correct, but far too slow for very large exponents.
    """

    def __init__(self):
        pass

    def calculatePower(self, base, exponent):
        n = abs(exponent)

        result = 1.0
        for _ in range(n):
            result = result * base

        # Negative exponent: answer is the reciprocal of the positive power
        if exponent < 0:
            return 1.0 / result
        return result
```


### Problem with this solution

For `exponent = 2,147,483,647`, the loop runs over **2 billion times**.


In Python, that would take many minutes. We need a way to skip most of these multiplications.


**Complexity:** Time `O(n)`, Space `O(1)`, where `n = abs(exponent)`.

---

## Solution 2: Fast Power (Repeated Squaring)

### Key observation

Look at `2^10`:

```
2^10 = (2 * 2)^5 = 4^5
```

One multiplication (`2 * 2`) cut the exponent from `10` down to `5`.


Now `5` is odd, so we take one `4` aside and halve the rest:

```
4^5 = 4 * 4^4 = 4 * (4 * 4)^2 = 4 * 16^2
```


Every step **halves** the exponent. Halving 2 billion again and again reaches `0` in about 32 steps.


### Seeing it in binary

Every number is a sum of powers of 2. For example:

```
13 = 8 + 4 + 1        (binary 1101)
```

So:

```
3^13 = 3^8 * 3^4 * 3^1
```


We can build `3^1`, `3^2`, `3^4`, `3^8` by squaring again and again.


Then we multiply into the answer **only** the powers where the binary digit of the exponent is `1`.


### The loop in plain words

While `n > 0`:

1. If `n` is odd (last binary digit is `1`), multiply `result` by `current_power`.
2. Square `current_power`.
3. Halve `n` using `n // 2` (this moves to the next binary digit).


### Dry run: `base = 2`, `exponent = 10`

Start with `n = 10`, `result = 1`, `current_power = 2`.

| Round | n at start | n odd? | result after   | current_power after |
|-------|------------|--------|----------------|---------------------|
| 1     | 10         | No     | 1              | 4                   |
| 2     | 5          | Yes    | 1 × 4 = 4      | 16                  |
| 3     | 2          | No     | 4              | 256                 |
| 4     | 1          | Yes    | 4 × 256 = 1024 | 65536               |

Now `n` is `0`, so we stop. The answer is `1024`, which is correct.


### Negative exponent

We first find the positive power, then return `1 / result`.


Example: `4^-2` → `4^2 = 16` → `1 / 16 = 0.0625`.


### Exponent 0 and negative base

If `exponent` is `0`, the loop never runs and `result` stays `1.0`. No special case is needed.


A negative base like `-2.0` also works on its own, because normal multiplication already handles the sign.


### Why these variables

- **`n = abs(exponent)`**: the size of the exponent. We only need its size here and check the sign separately at the end.
- **`current_power`**: holds `base^1`, `base^2`, `base^4`, `base^8` and so on. This is what lets us jump ahead instead of multiplying by `base` one step at a time.
- **`float(base)`**: if `base` comes in as an int like `2`, squaring it would build giant Python integers with millions of digits. Turning it into a float first keeps every step fast.
- **`result`**: collects only the powers we actually need.


### Code

```python
class PowerCalculator:
    """Calculates base raised to exponent without using a built-in power function.

    Uses fast power (repeated squaring), so even the biggest exponent
    needs only about 32 loop rounds.
    """

    def __init__(self):
        pass

    def calculatePower(self, base, exponent):
        # Python integers never overflow, so abs() is safe even for -2147483648
        n = abs(exponent)

        result = 1.0
        # Holds base^1, then base^2, base^4, base^8 ... as the loop runs.
        # float() keeps the math in floats, so numbers never grow into huge ints.
        current_power = float(base)

        while n > 0:
            # Current binary digit of n is 1, so this power is part of the answer
            if n % 2 == 1:
                result = result * current_power
            # Square it to get the next power: base^k becomes base^(2k)
            current_power = current_power * current_power
            # Move to the next binary digit of n
            n = n // 2

        # Negative exponent: answer is the reciprocal of the positive power
        if exponent < 0:
            return 1.0 / result
        return result
```


### Complexity

- **Time:** `O(log n)`. `n` is halved every round, so there are at most 32 rounds.
- **Space:** `O(1)`. We only use a few variables.

---

## Brute Force vs Fast Power

| Approach     | Loop rounds for exponent = 2,147,483,647 | Time       |
|--------------|------------------------------------------|------------|
| Brute Force  | 2,147,483,647                            | `O(n)`     |
| Fast Power   | 31                                       | `O(log n)` |

---

## Key Takeaways

- Squaring doubles the power, so we reach large powers in very few steps.
- The binary digits of the exponent tell us which squared powers to multiply into the answer.
- For a negative exponent, compute the positive power and return its reciprocal.
- Convert `base` to a float so that every multiplication stays quick.