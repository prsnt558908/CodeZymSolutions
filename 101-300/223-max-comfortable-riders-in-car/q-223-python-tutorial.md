# Maximum Comfortable Riders in a Car in Python

#### Problem Statement
[https://codezym.com/question/223-max-comfortable-riders-in-car](https://codezym.com/question/223-max-comfortable-riders-in-car)


Each rider is okay with only a certain number of **other** riders in the car. The trick is to look at the **group size** instead of single riders.

If `x` riders sit in the car, every one of them sees the same number of others: `x - 1`. So a group of size `x` is possible when at least `x` riders have `x - 1` inside their range.

This turns the problem into a counting problem. For every value `k`, we need to know how many riders have `k` inside their range. We first count it the slow way, then use a **difference array** with a running sum to get every count in one pass. The final solution runs in `O(n)` time.

---

## Approach 1: Brute Force

Try every group size `x`, from `n` down to `1`.

For each `x`, scan all riders and count how many have `minOther <= x - 1 <= maxOther`.

If this count is at least `x`, return `x`. We go from the biggest size down, so the first size that works is the answer.

If no size works, return `0`.

**Why is "at least x" enough?** If more than `x` riders are happy with `x - 1` others, we just pick any `x` of them. Riders who are left out do not need to be happy.

```python
# ranges: parsed (minOther, maxOther) pairs, one per rider
for x in range(n, 0, -1):
    others = x - 1
    comfortable = 0

    for minOther, maxOther in ranges:
        if minOther <= others <= maxOther:
            comfortable += 1

    if comfortable >= x:
        return x

return 0
```

**Note:** we cannot binary search on `x`. A bigger group can work even when a smaller one does not. With three riders `"2,2"`, a group of `3` works, but a group of `2` does not.

### Problem with this approach

For each of the `n` group sizes, we scan all `n` riders. That is `O(n²)` time.

With `n` up to `200,000`, this is about `40 billion` checks. Way too slow.

---

## Approach 2: Difference Array (Optimal)

### Step 1: Flip the question

The brute force asks every rider again and again, once for each group size.

Instead, let each rider **vote once** for every "number of others" they are okay with. A rider with `"1,3"` votes for `1`, `2` and `3`.

After all votes, `count[k]` is the number of riders okay with `k` others. That is exactly the number the brute force was finding.

But if we add votes one value at a time, a rider with a wide range like `"0,199999"` still costs `200,000` steps. In the worst case, this is still `O(n²)`.

### Step 2: Mark only the two ends

A rider's range is one continuous block of values. So we only mark where the block **starts** and where it **stops**:

- `diff[minOther] += 1` : votes start here
- `diff[maxOther + 1] -= 1` : votes stop after `maxOther`

Then we walk from left to right and keep a running sum. The running sum at index `k` is exactly `count[k]`.

For a single rider `"1,3"`:

| k | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| `diff[k]` | 0 | +1 | 0 | 0 | -1 |
| running sum | 0 | 1 | 1 | 1 | 0 |

The `+1` at index `1` turns the vote on, and the `-1` at index `4` turns it off. So the rider is counted only for `1`, `2` and `3`.

Now every rider costs `O(1)` work, no matter how wide the range is.

**Why a plain list?** The indexes we need are small whole numbers from `0` to `n`. A list of size `n + 1` stores every mark directly at its index. It is simpler and faster than a dictionary. The extra slot is for `maxOther + 1`, which can be `n`.

### Step 3: Handle ranges that are too big

A car holds at most `n` riders, so a rider can never see more than `n - 1` others.

Example 3 has the range `"3,3"` with only `3` riders. Nobody can see `3` other riders there.

So we cut `maxOther` down to `n - 1`. If `minOther` is now bigger than `maxOther`, this rider can never be happy, so we skip them. This also keeps `maxOther + 1` inside the list.

### Step 4: Pick the best group size

Walk `others` from `0` to `n - 1` and keep the running sum `count`. The group size here is `others + 1`.

If `count >= others + 1`, this group size works. Keep the largest one.

### Dry Run (Example 1)

Input: `["0,1", "1,3", "1,2", "2,4", "3,4"]`, so `n = 5`.

| others (k) | group size (k + 1) | `diff[k]` | count (running sum) | count >= size? |
|---|---|---|---|---|
| 0 | 1 | +1 | 1 | yes |
| 1 | 2 | +2 | 3 | yes |
| 2 | 3 | 0 | 3 | yes |
| 3 | 4 | 0 | 3 | no |
| 4 | 5 | -1 | 2 | no |

`diff[2]` is `0` because the `-1` from rider `"0,1"` (its range ends at `1`) cancels the `+1` from rider `"2,4"` (its range starts at `2`).

The largest working size is `3`, so the answer is `3`.

### Python Code

```python
from typing import List


class MaximumComfortableRiders:

    def __init__(self):
        pass

    def maximumRiders(self, comfortableRanges: List[str]) -> int:
        """
        Returns the biggest group size x such that at least x riders
        are comfortable with exactly x - 1 other riders.
        """
        n = len(comfortableRanges)

        # diff[k]: +1 where a comfort range starts, -1 just after it ends.
        # Size n + 1 because maxOther + 1 can be n.
        diff = [0] * (n + 1)

        for comfortRange in comfortableRanges:
            parts = comfortRange.split(",")
            minOther = int(parts[0])
            maxOther = int(parts[1])

            # Nobody can see more than n - 1 other riders.
            maxOther = min(maxOther, n - 1)
            if minOther > maxOther:
                continue  # this rider can never be comfortable

            diff[minOther] += 1
            diff[maxOther + 1] -= 1

        best = 0
        count = 0  # riders comfortable with exactly 'others' other riders

        for others in range(n):
            count += diff[others]

            # A group of (others + 1) riders works if enough riders are comfortable.
            if count >= others + 1:
                best = others + 1

        return best
```

### Complexity

- **Time:** `O(n)`. One pass to mark the ranges and one pass for the running sum.
- **Space:** `O(n)` for the `diff` list.