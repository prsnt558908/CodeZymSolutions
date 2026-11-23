# Count Special Subarrays With Odd Divisors in Python

#### Problem Statement
[https://codezym.com/question/262-count-special-subarrays](https://codezym.com/question/262-count-special-subarrays)


The trick is to think in primes, not divisors. A number has an odd number of divisors only when it is a perfect square, and a product is a perfect square only when every prime in it appears an even number of times.


So we keep only the primes that appear an odd number of times in each number, and count subarrays with the classic **prefix + hash map** technique. To compare prefixes in O(1), each prime gets a random 64-bit number, and a whole set of primes becomes the **XOR** of their numbers.


---

## Key Observations

### 1. Odd number of divisors means perfect square

Divisors come in pairs. If `d` divides `x`, then `x / d` also divides `x`.


A divisor is left without a partner only when `d = x / d`, which means `x = d * d`. So the number of divisors is odd exactly when `x` is a perfect square.


### 2. Perfect square means every prime has an even power

`36 = 2² × 3²` is a perfect square.

`12 = 2² × 3` is not, because 3 has an odd power.


### 3. Only "odd or even" matters

When we multiply numbers, the powers of each prime add up. We only care whether each total is odd or even.


So we can replace every number with its **odd primes**: the primes that divide it an odd number of times.

| Number | Prime factors | Odd primes |
|---|---|---|
| 2 | 2 | {2} |
| 8 | 2³ | {2} |
| 3 | 3 | {3} |
| 12 | 2² × 3 | {3} |
| 18 | 2 × 3² | {2} |
| 4 | 2² | {} |


An easy way to check a subarray: walk through it and **toggle** each odd prime in a set. Add it if it is missing, remove it if it is already there.


The product is a perfect square exactly when the set ends up **empty**.

* `[2, 8]`: toggle 2, toggle 2, the set is empty. Special (product 16).
* `[8, 3]`: the set becomes {2, 3}. Not special.


---

## Approach 1: Brute Force

Try every subarray. For each start index, extend the end one step at a time and keep toggling primes.

```python
n = len(nums)
answer = 0
for start in range(n):
    oddPrimes = set()
    for end in range(start, n):
        for p in self.getOddPrimes(nums[end]):
            # toggle: remove if present, otherwise add
            if p in oddPrimes:
                oddPrimes.remove(p)
            else:
                oddPrimes.add(p)
        if not oddPrimes:
            answer += 1
```

`getOddPrimes(value)` returns the odd primes of a number. We will see how it works in the final solution.


**Problem:** there are about n² / 2 subarrays. For n = 100,000 that is about 5 billion. Far too slow.


---

## Approach 2: Prefix Sets

Let `prefix[i]` be the toggled set after the first `i` numbers. `prefix[0]` is the empty set.


Going from `prefix[i]` to `prefix[j]` toggles exactly the odd primes of the subarray `nums[i..j-1]`. If the set did not change, every prime was toggled an even number of times.


So the subarray `nums[i..j-1]` is special **exactly when `prefix[i]` equals `prefix[j]`**.


Let us try it on `[2, 8, 3, 12]`:

| i | Number added | prefix[i] |
|---|---|---|
| 0 | none | {} |
| 1 | 2 | {2} |
| 2 | 8 | {} |
| 3 | 3 | {3} |
| 4 | 12 | {} |


The empty set appears at positions 0, 2 and 4. That gives 3 equal pairs:

* (0, 2) is `[2, 8]`
* (2, 4) is `[3, 12]`
* (0, 4) is `[2, 8, 3, 12]`

The answer is 3.


Now we only need to **count pairs of equal prefix sets**. We walk from left to right and keep a dictionary from set to "how many times we have seen it". Each new prefix pairs up with every earlier equal prefix.


A normal `set` cannot be a dictionary key, so we store a `frozenset` copy of it.

```python
prefixCount = {frozenset(): 1}     # the empty prefix
current = set()

answer = 0
for value in nums:
    for p in self.getOddPrimes(value):
        if p in current:
            current.remove(p)
        else:
            current.add(p)
    key = frozenset(current)       # store a copy
    count = prefixCount.get(key, 0)
    answer += count
    prefixCount[key] = count + 1
```


**Problem:** a prefix set can hold many primes. Hashing it and copying it into the dictionary takes as many steps as the set has primes.


If every number is a different prime, the set grows by one at every step. The total work becomes O(n²) again, and memory blows up as well.


---

## Approach 3: XOR Fingerprints (Optimal)

We never need to look inside a prefix set. We only need to know whether two sets are **equal**.


So we replace every set with a single integer, a **fingerprint** of the set.


### Step 1: Give every prime a random 64-bit number

The first time we see a prime, we pick a random 64-bit integer for it with `random.getrandbits(64)` and remember it in a dictionary. The same prime always gets the same number.


### Step 2: Fingerprint of a set = XOR of its primes' numbers

XOR fits this problem perfectly because `a ^ a = 0`.


XOR-ing the same prime twice cancels it out, just like an even power cancels out. So toggling a prime in the set becomes **one XOR** on the fingerprint.

```python
prefixHash ^= self.getPrimeHash(p)   # toggle prime p
```

No copying, no growing sets.


**Why random numbers and not the primes themselves?**

Plain primes collide easily. `2 ^ 7 = 5`, so the set {2, 7} would look exactly like {5}. Then `[2, 7, 5]` would wrongly look special, even though `2 × 7 × 5 = 70` is not a square.


With random 64-bit numbers, two different sets get the same fingerprint with a chance of about 1 in 2⁶⁴. Even for the largest input, the chance of any wrong count is below one in a billion, so we can safely ignore it.


### Step 3: One fingerprint per number

A number's fingerprint is the XOR of the random numbers of its odd primes. For `6 = 2 × 3` it is `hash(2) ^ hash(3)`. For a perfect square like `4` it is `0`.


We keep these in a cache dictionary, so a value that repeats is broken into primes only once.


### Step 4: Count equal prefix fingerprints

This is exactly Approach 2, but the dictionary key is now an integer instead of a set.


On `[2, 8, 3, 12]` the prefix fingerprints are `0, hash(2), 0, hash(3), 0`. The value `0` shows up three times, which gives the same 3 pairs as before.


### Finding the odd primes of a number

Values go up to 10⁹, and √(10⁹) is about 31,623. So we build all primes up to 31,623 once, using the **Sieve of Eratosthenes**. There are only 3,401 of them.


For a value `x`, we take each prime `p`, divide `x` by `p` as long as it divides, and count the divisions. If the count is odd, `p` is an odd prime of `x`.


We stop as soon as `p * p > x`. If what is left is greater than 1, it is a prime itself. A composite number always has a prime factor no bigger than its square root, and all those small factors were already removed.


### Important data structures

* `primes` (list): all primes up to 31,623. We only divide by primes, which is about 9 times fewer divisions than trying every number up to 31,623.
* `primeHash` (dictionary from prime to random number): gives each prime one fixed random number. A dictionary is needed so a prime gets the same number every time we see it.
* `valueHash` (dictionary from value to fingerprint): cache of each value's fingerprint, so repeated values are factored only once.
* `prefixCount` (dictionary from fingerprint to count): how many prefixes had each fingerprint so far. Each new prefix finds all its equal partners in O(1).


### Complexity

* **Sieve:** about 31,623 steps, done once.
* **Factoring:** at most about 3,400 divisions for each distinct value. In practice much less, because we stop at √x.
* **Main loop:** O(n) with O(1) dictionary operations.
* **Total time:** O(n × P) in the worst case, where P ≈ 3,400 is the number of small primes.
* **Space:** O(n) for the dictionaries.


### Code

```python
import random
from typing import List


class CountSpecialSubarrays:

    def __init__(self):
        # all primes up to 31623, enough to break any value up to 10^9 into primes
        # (31623 * 31623 > 10^9)
        self.primes = self.buildPrimes(31623)

        # prime -> its random 64-bit fingerprint
        self.primeHash = {}

        # value -> XOR of the fingerprints of its odd primes (cache for repeated values)
        self.valueHash = {}

    def countSpecialSubarrays(self, nums: List[int]) -> int:
        # fingerprint of a prefix -> how many prefixes so far had this fingerprint
        prefixCount = {}

        prefixHash = 0              # the empty prefix has no odd primes
        prefixCount[prefixHash] = 1

        answer = 0
        for value in nums:
            # toggle all odd primes of this value in one XOR
            prefixHash ^= self.getValueHash(value)

            # every earlier prefix with the same fingerprint closes one special subarray
            count = prefixCount.get(prefixHash, 0)
            answer += count
            prefixCount[prefixHash] = count + 1
        return answer

    # XOR of the fingerprints of all odd primes of value
    def getValueHash(self, value: int) -> int:
        if value in self.valueHash:
            return self.valueHash[value]
        result = 0
        for p in self.getOddPrimes(value):
            result ^= self.getPrimeHash(p)
        self.valueHash[value] = result
        return result

    # a prime gets a random 64-bit fingerprint the first time we see it
    def getPrimeHash(self, prime: int) -> int:
        if prime not in self.primeHash:
            self.primeHash[prime] = random.getrandbits(64)
        return self.primeHash[prime]

    # primes that divide value an odd number of times
    # example: 72 = 2^3 * 3^2 gives [2]
    def getOddPrimes(self, value: int) -> List[int]:
        oddPrimes = []
        x = value
        for p in self.primes:
            if p * p > x:
                break               # no prime factor of x is left below sqrt(x)
            if x % p != 0:
                continue            # p does not divide x, skip it
            count = 0
            while x % p == 0:
                x //= p
                count += 1
            if count % 2 == 1:
                oddPrimes.append(p)
        if x > 1:
            oddPrimes.append(x)     # what is left is a single prime
        return oddPrimes

    # Sieve of Eratosthenes: returns all primes up to limit
    def buildPrimes(self, limit: int) -> List[int]:
        isComposite = [False] * (limit + 1)
        primes = []
        for i in range(2, limit + 1):
            if isComposite[i]:
                continue
            primes.append(i)
            for j in range(i * i, limit + 1, i):
                isComposite[j] = True
        return primes
```