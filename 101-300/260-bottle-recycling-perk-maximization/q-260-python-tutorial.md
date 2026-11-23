# Bottle Recycling Perk Maximization in Python

#### Problem Statement
[https://codezym.com/question/260-bottle-recycling-perk-maximization](https://codezym.com/question/260-bottle-recycling-perk-maximization)

The key idea is simple. If we want to buy `k` perks, we must keep `k` bottles for them, and the best thing to do with every other bottle is to recycle it. So checking "can we buy `k` perks?" takes just one line of math.

We start by trying every `k` one by one. Then we speed it up with binary search. Finally, we turn the check into a direct formula that gives the answer in `O(1)` time.

---

## Understanding the Problem

- Recycling 1 bottle gives `recycleVal` dollars.
- Buying 1 perk costs 1 bottle and `perkVal` dollars.
- Each bottle can be used only once: either recycled or spent on a perk.

We start with `numBottles` bottles and `dollars` money. We want to buy as many perks as possible.

---

## Key Observation

Say we want to buy `k` perks.

- `k` bottles must be kept for the perks.
- The other `numBottles - k` bottles have no other use, so we recycle all of them. Extra money never hurts.

So for any `k`:

```
money we have = dollars + (numBottles - k) * recycleVal
money we need = k * perkVal
```

`k` perks are possible when `money we need <= money we have`. Also, `k` can never be more than `numBottles`.

The order of actions does not matter. We can always recycle first and then buy the perks.

Here is the check for every `k` in Example 1 (`numBottles = 5, dollars = 4, recycleVal = 2, perkVal = 3`):

| Perks `k` | Bottles recycled | Money we have | Money we need | Possible? |
|:---:|:---:|:---:|:---:|:---:|
| 0 | 5 | `4 + 10 = 14` | `0` | Yes |
| 1 | 4 | `4 + 8 = 12` | `3` | Yes |
| 2 | 3 | `4 + 6 = 10` | `6` | Yes |
| 3 | 2 | `4 + 4 = 8` | `9` | No |
| 4 | 1 | `4 + 2 = 6` | `12` | No |
| 5 | 0 | `4 + 0 = 4` | `15` | No |

The largest `k` that works is `2`, so the answer is `2`.

---

## Approach 1: Brute Force

Put the check in a small helper method, then try every `k` from `0` to `numBottles`.

```python
def canBuy(self, k: int, numBottles: int, dollars: int, recycleVal: int, perkVal: int) -> bool:
    """Can we buy k perks? Keep k bottles and recycle all the others."""
    money = dollars + (numBottles - k) * recycleVal
    needed = k * perkVal
    return needed <= money
```

```python
# Try every k and remember the largest one that works.
best = 0
for k in range(numBottles + 1):
    if self.canBuy(k, numBottles, dollars, recycleVal, perkVal):
        best = k
return best
```

**Big numbers:** `numBottles` and `recycleVal` can both be `10^9`, so `money` can reach about `10^18`. Python integers grow as big as needed, so there is no overflow to worry about.

This works, but `numBottles` can be `10^9`. Checking one billion values of `k` is too slow.

---

## Approach 2: Binary Search on the Answer

Look at the "Possible?" column in the table again: `Yes, Yes, Yes, No, No, No`.

It always has this shape. When `k` goes up by 1:

- we need `perkVal` more dollars, and
- we recycle one bottle less, so we have `recycleVal` fewer dollars.

Money we need never goes down, and money we have never goes up. So once some `k` fails, every bigger `k` fails too.

When answers look like "all Yes, then all No", we can binary search for the last Yes. Each step checks the middle `k` and throws away half of the range.

### Code

```python
class BottleRecyclingPerkMaximization:

    def __init__(self):
        pass

    def maximumPerks(self, numBottles: int, dollars: int, recycleVal: int, perkVal: int) -> int:
        low = 0
        high = numBottles  # we can never buy more perks than bottles
        answer = 0         # 0 perks is always possible

        # Find the largest k for which canBuy(k) is True.
        while low <= high:
            mid = (low + high) // 2
            if self.canBuy(mid, numBottles, dollars, recycleVal, perkVal):
                answer = mid    # mid works, try more perks
                low = mid + 1
            else:
                high = mid - 1  # mid fails, try fewer perks
        return answer

    def canBuy(self, k: int, numBottles: int, dollars: int, recycleVal: int, perkVal: int) -> bool:
        """
        Can we buy k perks?
        Keep k bottles for the perks and recycle all the others.
        """
        money = dollars + (numBottles - k) * recycleVal
        needed = k * perkVal
        return needed <= money
```

**Time:** `O(log numBottles)`. For `10^9` bottles, that is only about 30 checks.

**Space:** `O(1)`.

---

## Approach 3: Direct Formula (Optimal)

We can skip the search and solve the check for `k` directly:

```
k * perkVal <= dollars + (numBottles - k) * recycleVal
k * perkVal <= dollars + numBottles * recycleVal - k * recycleVal
k * perkVal + k * recycleVal <= dollars + numBottles * recycleVal
k * (perkVal + recycleVal) <= dollars + numBottles * recycleVal
k <= (dollars + numBottles * recycleVal) / (perkVal + recycleVal)
```

**In simple words:** imagine we first recycle every bottle. That gives the most money we can ever have: `dollars + numBottles * recycleVal`. Call it `totalWealth`.

To buy one perk, we take back one bottle, giving up its `recycleVal` dollars, and then pay `perkVal` dollars. So each perk really costs `perkVal + recycleVal`. Call it `costPerPerk`.

So the answer is `totalWealth // costPerPerk`. The `//` is integer division, so the result is rounded down to a whole number.

Two small details:

- **Cap at `numBottles`.** Every perk needs its own bottle. For `numBottles = 2, dollars = 100, recycleVal = 1, perkVal = 1`, the formula gives `102 // 2 = 51`, but we can only buy `2` perks.
- **Both values are `0`.** If `perkVal` and `recycleVal` are both `0`, then `costPerPerk` is `0` and we cannot divide by it. Perks are free in this case, so the answer is simply `numBottles`.

Dry run on the given examples:

| Example | `totalWealth` | `costPerPerk` | `totalWealth // costPerPerk` | Answer |
|:---:|:---:|:---:|:---:|:---:|
| 1 | `4 + 5 * 2 = 14` | `3 + 2 = 5` | `2` | `min(2, 5) = 2` |
| 2 | `1 + 4 * 5 = 21` | `6 + 5 = 11` | `1` | `min(1, 4) = 1` |
| 3 | `0 + 6 * 1 = 6` | `0 + 1 = 1` | `6` | `min(6, 6) = 6` |
| 4 | `10 + 4 * 0 = 10` | `3 + 0 = 3` | `3` | `min(3, 4) = 3` |

### Code

```python
class BottleRecyclingPerkMaximization:

    def __init__(self):
        pass

    def maximumPerks(self, numBottles: int, dollars: int, recycleVal: int, perkVal: int) -> int:
        """
        Returns the maximum number of perks that can be bought.
        Each perk really costs (perkVal + recycleVal), because the bottle
        used for the perk could have been recycled instead.
        """
        costPerPerk = perkVal + recycleVal

        # Both values are 0, so perks are free. One perk per bottle.
        if costPerPerk == 0:
            return numBottles

        # Money we would have if we recycled every bottle.
        totalWealth = dollars + numBottles * recycleVal

        perks = totalWealth // costPerPerk

        # Every perk needs its own bottle.
        return min(perks, numBottles)
```

**Time:** `O(1)`.

**Space:** `O(1)`.

---

## Complexity Summary

| Approach | Time | Space |
|:---|:---:|:---:|
| Brute Force | `O(numBottles)` | `O(1)` |
| Binary Search | `O(log numBottles)` | `O(1)` |
| Direct Formula | `O(1)` | `O(1)` |