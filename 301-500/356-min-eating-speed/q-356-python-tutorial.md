# Minimum Dog Biscuit Eating Speed in Python

#### Problem Statement
[https://codezym.com/question/356-min-eating-speed](https://codezym.com/question/356-min-eating-speed)


## Core Idea

Bruno picks one speed and sticks with it. If a speed is fast enough, every faster speed is also fast enough. If a speed is too slow, every slower speed is also too slow.

So if we write "can he finish?" for every speed from 1 upward, it looks like this:

```
No  No  No  No  Yes  Yes  Yes  Yes ...
```


We only need to find the **first Yes**.

We will first try every speed one by one (brute force). Then we will make it much faster using **binary search on the speed**.


## Understanding the Problem

In one hour, Bruno eats from only one pile. So a pile of `pile` biscuits at speed `speed` takes `pile / speed` hours, **rounded up**.

For example, a pile of 13 at speed 9 takes 13 / 9 = 1.44, rounded up to **2 hours**.


A speed is good if the total hours across all piles is `<= hours`.

To round up without using decimals, we use this common trick:

```python
hoursForPile = (pile + speed - 1) // speed
```

`//` is integer division in Python, so no floating point numbers are involved.


**Range of the answer:**

- The smallest possible speed is `1`.
- At speed = biggest pile, every pile takes exactly 1 hour. That is `len(biscuitPiles)` hours in total, which always fits because `hours >= len(biscuitPiles)`.

So the answer always lies between `1` and the biggest pile.


## Solution 1: Brute Force

Try speed `1`, then `2`, then `3`, and so on. The first speed that finishes in time is the answer.

```python
from typing import List


class MinimumDogBiscuitEatingSpeed:
    def __init__(self):
        pass

    def minimumBiscuitEatingSpeed(self, biscuitPiles: List[int], hours: int) -> int:
        maxPile = max(biscuitPiles)

        # try every speed starting from 1, the first one that works is the answer
        for speed in range(1, maxPile + 1):
            if self.canFinish(biscuitPiles, hours, speed):
                return speed

        # never reached, speed = maxPile always works
        return maxPile

    def canFinish(self, biscuitPiles: List[int], hours: int, speed: int) -> bool:
        """Returns True if Bruno can eat all piles within hours at the given speed."""
        hoursNeeded = 0
        for pile in biscuitPiles:
            hoursNeeded += (pile + speed - 1) // speed
            if hoursNeeded > hours:
                return False
        return True
```


### What is wrong with this?

The biggest pile can be `1,000,000,000`. In the worst case we check a billion speeds, and each check walks through up to 10,000 piles.

That is way too slow.


The real waste is that we learn very little from each check. We test speeds one at a time, even though a single check can tell us about a huge group of speeds at once.


## Solution 2: Binary Search on Speed

Pick a speed `mid` from the middle of the range and check it.

- **Bruno can finish at `mid`:** the answer is `mid` or smaller. All speeds above `mid` can be thrown away.
- **Bruno cannot finish at `mid`:** `mid` and every speed below it are too slow. The answer is bigger than `mid`.


Either way, **half of the remaining speeds are gone** after one check. A range of 1 billion speeds shrinks to one speed in about 30 checks.


### Steps

1. Set `low = 1` and `high = max(biscuitPiles)`.
2. While `low < high`:
   - `mid = (low + high) // 2`
   - If Bruno can finish at `mid`, set `high = mid`.
   - Otherwise set `low = mid + 1`.
3. When `low == high`, that is the answer.


**Why `high = mid` and not `mid - 1`?** Because `mid` worked, it might be the answer itself. We must keep it inside the range.


### Dry Run

`biscuitPiles = [8, 13, 17]`, `hours = 5`

| low | high | mid | hours needed at mid | result |
|-----|------|-----|---------------------|--------|
| 1 | 17 | 9 | 1 + 2 + 2 = 5 | fits, `high = 9` |
| 1 | 9 | 5 | 2 + 3 + 4 = 9 | too slow, `low = 6` |
| 6 | 9 | 7 | 2 + 2 + 3 = 7 | too slow, `low = 8` |
| 8 | 9 | 8 | 1 + 2 + 3 = 6 | too slow, `low = 9` |
| 9 | 9 | - | - | answer is **9** |


### Small details that matter

- **Early exit:** we stop counting as soon as `hoursNeeded > hours`. No need to finish the loop once we know the answer is "too slow".
- **Big numbers:** total hours at speed 1 can reach 10,000 × 1,000,000,000. Python integers grow as needed, so this is safe without any special handling.


We do not need any extra data structure here. The given list and a few number variables are enough.


### Code

```python
from typing import List


class MinimumDogBiscuitEatingSpeed:
    def __init__(self):
        pass

    def minimumBiscuitEatingSpeed(self, biscuitPiles: List[int], hours: int) -> int:
        """Binary searches the smallest speed that lets Bruno finish in time.
        Answer always lies between 1 and the biggest pile."""
        low = 1
        high = max(biscuitPiles)

        while low < high:
            mid = (low + high) // 2
            if self.canFinish(biscuitPiles, hours, mid):
                # mid works, but a smaller speed might also work
                high = mid
            else:
                # mid is too slow, so every speed up to mid is too slow
                low = mid + 1
        return low

    def canFinish(self, biscuitPiles: List[int], hours: int, speed: int) -> bool:
        """Returns True if Bruno can eat all piles within hours at the given speed."""
        hoursNeeded = 0
        for pile in biscuitPiles:
            # hours for one pile = pile / speed, rounded up
            hoursNeeded += (pile + speed - 1) // speed
            if hoursNeeded > hours:
                return False
        return True
```


## Complexity

Let `n` be the number of piles and `M` be the biggest pile.

| Solution | Time | Extra Space |
|----------|------|-------------|
| Brute Force | O(n × M) | O(1) |
| Binary Search | O(n × log M) | O(1) |


With `M = 1,000,000,000`, `log M` is only about 30. So binary search does around 30 passes over the piles instead of a billion.