# Longest Repeated Activity Pattern in Python

#### Problem Statement
[https://codezym.com/question/419-longest-repeated-activity-pattern](https://codezym.com/question/419-longest-repeated-activity-pattern)


We need the longest continuous piece of the activity log that appears at least twice. The two copies are allowed to overlap.

The whole solution stands on one simple fact: if some pattern of length `L` repeats, then some pattern of every shorter length repeats too. So instead of trying every length one by one, we can **binary search** on the length.

To check one length quickly, we slide a window over the log and turn each window into a number, its **fingerprint**. A trick called **rolling hash** updates this number in O(1) every time the window moves one step. A dict then groups windows with the same fingerprint, so repeated windows are easy to spot.

Together, this gives an O(n log n) solution. We will reach it in three steps: brute force, then binary search, then rolling hash.


## Approach 1: Brute Force

Try every length, from the longest possible (`n - 1`) down to `1`. For each length, try every start from left to right, and check if the same pattern appears again later in the log.

The first pattern we find is the answer:

- It is the longest, because longer lengths are tried first.
- Its first occurrence starts earliest, because starts are tried from left to right.

```python
def findLongestRepeatedPattern(self, activityLog):
    n = len(activityLog)

    # Try every length, from the longest possible down to 1
    for length in range(n - 1, 0, -1):
        # Try every start, from left to right
        for start in range(n - length + 1):
            pattern = activityLog[start:start + length]
            # Does the same pattern appear again after this start?
            if activityLog.find(pattern, start + 1) != -1:
                return pattern
    return ""
```

**Why is it slow?** There are about n² pairs of (length, start), and each `find` call can scan the whole log. That is roughly O(n³) work, far too slow for a log of 30,000 letters.


## Approach 2: Binary Search on the Length

If `"abcd"` appears twice, then `"abc"` also appears twice, because it sits inside both copies. So once a length fails, every longer length fails too:

```
length:    1     2     3    ...   best   best + 1   ...
repeats?   yes   yes   yes  ...   yes    no         no
```

The answers switch from "yes" to "no" only once. Binary search finds the last "yes" in O(log n) checks. For 30,000 letters, that is about 15 checks instead of 30,000.

To check one length, we add every window of that length to a `set`. If a window is already in the set, a repeat exists.

```python
def has_repeat(self, log, length):
    """Returns True if some pattern of this length appears at least twice."""
    seen = set()
    for start in range(len(log) - length + 1):
        pattern = log[start:start + length]
        if pattern in seen:
            return True
        seen.add(pattern)
    return False
```

```python
low = 1
high = len(activityLog) - 1
best_length = 0

while low <= high:
    mid = (low + high) // 2
    if self.has_repeat(activityLog, mid):
        best_length = mid  # mid works, so try a longer length
        low = mid + 1
    else:
        high = mid - 1     # mid fails, so try a shorter length
```

After the loop, `best_length` is the length of the answer. Picking the right pattern of that length is covered in the final solution.

**What is still slow?** Each check builds up to `n` strings, and each string can be thousands of letters long. Creating and hashing them costs O(n × L) per check. For a 30,000 letter log, a single check can touch about 225 million letters and fill a lot of memory.

We need a way to compare windows without building strings.


## Approach 3: Binary Search + Rolling Hash

### Turning a window into a number

Think of a window as a number, where every letter is one digit: `'a' = 1`, `'b' = 2`, ..., `'z' = 26`. We use base `31` instead of base `10`, so that each letter fits in one digit.

The real trick is sliding. With normal digits, moving from window `"123"` to window `"234"` takes three small steps:

1. Remove the leftmost digit: `123 - 1 × 100 = 23`
2. Shift everything one place left: `23 × 10 = 230`
3. Add the new digit: `230 + 4 = 234`

This is O(1) work, no matter how long the window is.

Letters work the same way, using `31` in place of `10`. The weight of the leftmost letter, `100` in the example, becomes `31^(L - 1)`. In the code, it is called `left_weight`, and the built-in `pow(BASE, length - 1, MOD)` computes it directly.

These numbers grow huge and slow to work with, so after every step we keep only the remainder after dividing by a big prime, `1,000,000,007`. This remainder is the window's **fingerprint**.

### Equal fingerprints, equal letters?

Equal windows always get equal fingerprints. But since we keep only a remainder, two different windows can, very rarely, get the same fingerprint too.

So when two fingerprints match, we compare the actual letters to be sure. This keeps the answer always correct, and because such clashes are rare, the extra work is tiny.

### Picking the earliest pattern

When several patterns share the longest length, we must return the one whose first occurrence starts earliest.

For one length, we first group the window starts by fingerprint. Then we visit windows from left to right and stop at the first window that has a twin, another window with the same letters. No repeated pattern can start before it.

Stopping at the first *second copy* we meet would be a mistake.

In `"abxyxyab"`, the second `"xy"` (index 4) shows up before the second `"ab"` (index 6). But `"ab"` starts at index 0, earlier than `"xy"` at index 2. So the answer is `"ab"`.

### Walkthrough

For `activityLog = "abmnabpqmn"`, the binary search runs like this:

| low | high | mid | earliest repeated window | next step |
|---|---|---|---|---|
| 1 | 9 | 5 | none | high = 4 |
| 1 | 4 | 2 | `"ab"` at index 0 | best = 2, low = 3 |
| 3 | 4 | 3 | none | high = 2 |

Now `low > high`, so the search stops. The best length is `2` and it starts at index `0`, so the answer is `"ab"`. The other repeated pattern, `"mn"`, starts later at index `2`.

### Why these data structures?

- **`hashes` (a list):** the fingerprint of every window of the current length, filled in one O(n) pass.
- **`groups` (a dict of lists):** maps each fingerprint to the starts of the windows that have it. Equal windows always land in the same group, so we look for a twin only inside one group, never across the whole log.
- **Slice comparison:** `log[start:start + length] == log[other:other + length]` checks the actual letters. It runs only when two fingerprints match.

### Complexity

- **Time:** O(n log n) on average. The binary search makes about log n checks, and each check is O(n).
- **Space:** O(n) for the fingerprints and the dict.


## Final Code

```python
class MachineActivityAnalyzer:
    # Numbers used to build the fingerprint (rolling hash) of a window
    BASE = 31
    MOD = 1_000_000_007

    def __init__(self):
        pass

    def findLongestRepeatedPattern(self, activityLog):
        low = 1
        high = len(activityLog) - 1
        best_length = 0
        best_start = -1

        # Binary search on the length of the pattern
        while low <= high:
            mid = (low + high) // 2
            start = self.earliest_repeat_start(activityLog, mid)
            if start != -1:
                # A pattern of length mid repeats, so try a longer length
                best_length = mid
                best_start = start
                low = mid + 1
            else:
                # Nothing of length mid repeats, so nothing longer can repeat
                high = mid - 1

        if best_length == 0:
            return ""
        return activityLog[best_start:best_start + best_length]

    def earliest_repeat_start(self, log, length):
        """
        Returns the start index of the earliest pattern of the given length
        that appears at least twice, or -1 if no pattern of this length repeats.
        """
        hashes = self.window_hashes(log, length)

        # fingerprint -> start indices of all windows with that fingerprint
        groups = {}
        for start, fingerprint in enumerate(hashes):
            if fingerprint not in groups:
                groups[fingerprint] = []
            groups[fingerprint].append(start)

        # Visit windows from left to right. The first one with a twin wins.
        for start, fingerprint in enumerate(hashes):
            for other in groups[fingerprint]:
                # Equal fingerprints almost always mean equal letters, but we confirm it
                if other != start and log[start:start + length] == log[other:other + length]:
                    return start
        return -1

    def window_hashes(self, log, length):
        """
        Returns the fingerprint of every window of the given length.
        hashes[i] belongs to the window that starts at index i.
        """
        BASE, MOD = self.BASE, self.MOD
        values = [ord(c) - ord('a') + 1 for c in log]  # 'a'..'z' becomes 1..26

        # Weight of the leftmost letter: BASE^(length - 1) % MOD
        left_weight = pow(BASE, length - 1, MOD)

        # Fingerprint of the first window
        h = 0
        for k in range(length):
            h = (h * BASE + values[k]) % MOD
        hashes = [h]

        # Slide the window one step at a time
        for start in range(1, len(log) - length + 1):
            # Remove the letter that leaves on the left
            h = (h - values[start - 1] * left_weight) % MOD
            # Shift everything one place and add the letter that enters on the right
            h = (h * BASE + values[start + length - 1]) % MOD
            hashes.append(h)
        return hashes
```