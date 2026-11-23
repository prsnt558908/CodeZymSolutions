# Merge Cars Final Arrangement in Python

#### Problem Statement
[https://codezym.com/question/237-merge-cars-final-arrangement](https://codezym.com/question/237-merge-cars-final-arrangement)


## Core Idea

Same-name cars that sit next to each other form a **block**. For example, `BMW BMW Audi Audi Audi Tata` has three blocks: `BMW×2`, `Audi×3` and `Tata×1`.


Every block shrinks on its own, and each merge removes exactly `k - 1` cars from it. So the final size of a block depends only on its starting size.


This means we do not need to simulate the scans at all. One small formula gives the final size of each block, and a single left to right pass builds the answer in `O(n)` time.


## Approach 1: Brute Force (Follow the Rules)

Do exactly what the problem says. Scan from left to right. If the next `k` cars have the same name, replace them with one car and continue right after it. Repeat full scans until the list stops changing.

```python
current = list(cars)
changed = True

while changed:
    changed = False
    next_cars = []
    i = 0

    while i < len(current):
        if self.nextKAreSame(current, i, k):
            next_cars.append(current[i])  # k cars become one car
            i += k                        # continue right after the merged cars
            changed = True
        else:
            next_cars.append(current[i])
            i += 1
    current = next_cars

return current
```

Here `nextKAreSame()` compares the `k` cars starting at index `i`, one by one.


### Why is this slow?

1. **Checking `k` cars at every position.** For a block that is just shorter than `k`, each position compares up to `k` cars before it finds a different name. One scan can cost `O(n × k)`, which is far too slow when `n` and `k` can both be 100,000.

2. **Repeated scans.** Every scan rebuilds the whole list, even the blocks that are already finished.


## Improving the Brute Force

### Fix 1: Think in blocks, not single cars

Look at block sizes instead of single cars:

`A A A A B B B C` → `A×4, B×3, C×1`


Now "are the next `k` cars the same?" becomes "does this block have at least `k` cars?". That is one number check instead of `k` comparisons.


Blocks also never affect each other. A merge keeps the name and always leaves one car behind, so a block can shrink but never vanish. For example, in `A A B A A` the `B` always stays in the middle, so the two `A` blocks can never join.


So we can solve every block on its own. The only question left is: **if a block starts with `size` cars, how many are left at the end?**


### Fix 2: Skip the scans with a formula

Every merge turns `k` cars into `1`, so the block loses exactly `k - 1` cars. Merges continue while the block has at least `k` cars.

For example, with `size = 8` and `k = 4` the block goes `8 → 5 → 2`. Now `2 < 4`, so it stops.


There is a shortcut for this. Put one car aside. The other `size - 1` cars disappear `k - 1` at a time, one merge each time, until fewer than `k - 1` of them are left. That leftover is `(size - 1) % (k - 1)`. Add back the car we put aside:

```
final_size = 1 + (size - 1) % (k - 1)
```

For `size = 8` and `k = 4` this gives `1 + 7 % 3 = 2`, same as above.


Checking it on the examples:

| Block | size | k | final_size |
|---|---|---|---|
| BMW | 2 | 2 | 1 + 1 % 1 = **1** |
| Audi | 3 | 2 | 1 + 2 % 1 = **1** |
| Ford | 3 | 3 | 1 + 2 % 2 = **1** |
| Kia | 2 | 3 | 1 + 1 % 2 = **2** |

With `k = 2`, `(size - 1) % 1` is always `0`, so every block ends with one car. That is why Example 3 becomes `A, B, C`.


The order of merges does not matter either. Whichever `k` cars of a block are merged first, the block still loses `k - 1` cars per merge. So the left to right scan rule leads to the same answer as the formula.


## Approach 2: Optimal One Pass Solution

1. Use two pointers, `start` and `end`, to find each block. `end` moves forward until the name changes.
2. Compute the block's final size with the formula.
3. Add the block's name that many times to the result.
4. Move `start` to `end` and repeat.


**Why just a list?** We return cars in the same order as the input, and adding to the end of a `list` is fast.


**Why no stack?** A stack is the usual tool when `k` equal items are deleted completely, because the items on both sides can then meet and join. Here a block never disappears, so nothing ever joins, and a simple left to right pass is enough.


### Python Code

```python
from typing import List


class CarMerger:

    def __init__(self):
        pass

    def finalArrangement(self, cars: List[str], k: int) -> List[str]:
        """
        Returns the cars left after no more merges are possible.
        Same-name neighbors form a block, and each block shrinks on its own,
        so we compute the final size of every block directly.
        """
        result = []
        n = len(cars)
        start = 0

        while start < n:
            name = cars[start]

            # Move end forward until the name changes.
            end = start
            while end < n and cars[end] == name:
                end += 1
            block_size = end - start

            # Keep one car aside. Each merge removes k - 1 of the others.
            final_size = 1 + (block_size - 1) % (k - 1)

            # Add the name final_size times.
            result.extend([name] * final_size)

            # The next block starts where this one ended.
            start = end

        return result
```


### Complexity

- **Time:** `O(n)`. Each car is visited once, and the result gets at most `n` cars.
- **Space:** `O(n)` for the result list. Apart from that, only a few variables are used.