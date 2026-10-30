# Longest Repeated Activity Pattern in Java

#### Problem Statement
[https://codezym.com/question/419-longest-repeated-activity-pattern](https://codezym.com/question/419-longest-repeated-activity-pattern)


We need the longest continuous piece of the activity log that appears at least twice. The two copies are allowed to overlap.

The whole solution stands on one simple fact: if some pattern of length `L` repeats, then some pattern of every shorter length repeats too. So instead of trying every length one by one, we can **binary search** on the length.

To check one length quickly, we slide a window over the log and turn each window into a number, its **fingerprint**. A trick called **rolling hash** updates this number in O(1) every time the window moves one step. A map then groups windows with the same fingerprint, so repeated windows are easy to spot.

Together, this gives an O(n log n) solution. We will reach it in three steps: brute force, then binary search, then rolling hash.


## Approach 1: Brute Force

Try every length, from the longest possible (`n - 1`) down to `1`. For each length, try every start from left to right, and check if the same pattern appears again later in the log.

The first pattern we find is the answer:

- It is the longest, because longer lengths are tried first.
- Its first occurrence starts earliest, because starts are tried from left to right.

```java
public String findLongestRepeatedPattern(String activityLog) {
    int n = activityLog.length();

    // Try every length, from the longest possible down to 1
    for (int length = n - 1; length >= 1; length--) {
        // Try every start, from left to right
        for (int start = 0; start + length <= n; start++) {
            String pattern = activityLog.substring(start, start + length);
            // Does the same pattern appear again after this start?
            if (activityLog.indexOf(pattern, start + 1) != -1) {
                return pattern;
            }
        }
    }
    return "";
}
```

**Why is it slow?** There are about n² pairs of (length, start), and each `indexOf` call can scan the whole log. That is roughly O(n³) work, far too slow for a log of 30,000 letters.


## Approach 2: Binary Search on the Length

If `"abcd"` appears twice, then `"abc"` also appears twice, because it sits inside both copies. So once a length fails, every longer length fails too:

```
length:    1     2     3    ...   best   best + 1   ...
repeats?   yes   yes   yes  ...   yes    no         no
```

The answers switch from "yes" to "no" only once. Binary search finds the last "yes" in O(log n) checks. For 30,000 letters, that is about 15 checks instead of 30,000.

To check one length, we add every window of that length to a `HashSet`. If a window is already in the set, a repeat exists.

```java
/** Returns true if some pattern of this length appears at least twice. */
boolean hasRepeat(String log, int length) {
    Set<String> seen = new HashSet<>();
    for (int start = 0; start + length <= log.length(); start++) {
        String pattern = log.substring(start, start + length);
        if (seen.contains(pattern)) {
            return true;
        }
        seen.add(pattern);
    }
    return false;
}
```

```java
int low = 1;
int high = activityLog.length() - 1;
int bestLength = 0;

while (low <= high) {
    int mid = (low + high) / 2;
    if (hasRepeat(activityLog, mid)) {
        bestLength = mid;  // mid works, so try a longer length
        low = mid + 1;
    } else {
        high = mid - 1;    // mid fails, so try a shorter length
    }
}
```

After the loop, `bestLength` is the length of the answer. Picking the right pattern of that length is covered in the final solution.

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

Letters work the same way, using `31` in place of `10`. The weight of the leftmost letter, `100` in the example, becomes `31^(L - 1)`. In the code, it is called `leftWeight`.

These numbers grow huge, so after every step we keep only the remainder after dividing by a big prime, `1,000,000,007`. This remainder is the window's **fingerprint**.

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

- **`long[] hashes`:** the fingerprint of every window of the current length, filled in one O(n) pass.
- **`Map<Long, List<Integer>>`:** groups window starts by fingerprint. Equal windows always land in the same group, so we look for a twin only inside one group, never across the whole log.
- **`regionMatches`:** compares two windows letter by letter without creating new strings.

### Complexity

- **Time:** O(n log n) on average. The binary search makes about log n checks, and each check is O(n).
- **Space:** O(n) for the fingerprints and the map.


## Final Code

```java
import java.util.*;

public class MachineActivityAnalyzer {

    // Numbers used to build the fingerprint (rolling hash) of a window
    long BASE = 31;
    long MOD = 1_000_000_007L;

    public MachineActivityAnalyzer() {

    }

    public String findLongestRepeatedPattern(String activityLog) {
        int low = 1;
        int high = activityLog.length() - 1;
        int bestLength = 0;
        int bestStart = -1;

        // Binary search on the length of the pattern
        while (low <= high) {
            int mid = (low + high) / 2;
            int start = earliestRepeatStart(activityLog, mid);
            if (start != -1) {
                // A pattern of length mid repeats, so try a longer length
                bestLength = mid;
                bestStart = start;
                low = mid + 1;
            } else {
                // Nothing of length mid repeats, so nothing longer can repeat
                high = mid - 1;
            }
        }

        if (bestLength == 0) {
            return "";
        }
        return activityLog.substring(bestStart, bestStart + bestLength);
    }

    /**
     * Returns the start index of the earliest pattern of the given length
     * that appears at least twice, or -1 if no pattern of this length repeats.
     */
    int earliestRepeatStart(String log, int length) {
        long[] hashes = windowHashes(log, length);

        // fingerprint -> start indices of all windows with that fingerprint
        Map<Long, List<Integer>> groups = new HashMap<>();
        for (int start = 0; start < hashes.length; start++) {
            List<Integer> group = groups.get(hashes[start]);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(hashes[start], group);
            }
            group.add(start);
        }

        // Visit windows from left to right. The first one with a twin wins.
        for (int start = 0; start < hashes.length; start++) {
            for (int other : groups.get(hashes[start])) {
                // Equal fingerprints almost always mean equal letters, but we confirm it
                if (other != start && log.regionMatches(start, log, other, length)) {
                    return start;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the fingerprint of every window of the given length.
     * hashes[i] belongs to the window that starts at index i.
     */
    long[] windowHashes(String log, int length) {
        int count = log.length() - length + 1;
        long[] hashes = new long[count];

        // Weight of the leftmost letter: BASE^(length - 1) % MOD
        long leftWeight = 1;
        for (int k = 1; k < length; k++) {
            leftWeight = leftWeight * BASE % MOD;
        }

        // Fingerprint of the first window
        long hash = 0;
        for (int k = 0; k < length; k++) {
            hash = (hash * BASE + value(log.charAt(k))) % MOD;
        }
        hashes[0] = hash;

        // Slide the window one step at a time
        for (int start = 1; start < count; start++) {
            // Remove the letter that leaves on the left (+ MOD keeps the value positive)
            hash = (hash - value(log.charAt(start - 1)) * leftWeight % MOD + MOD) % MOD;
            // Shift everything one place and add the letter that enters on the right
            hash = (hash * BASE + value(log.charAt(start + length - 1))) % MOD;
            hashes[start] = hash;
        }
        return hashes;
    }

    /** Turns 'a'..'z' into 1..26 */
    int value(char c) {
        return c - 'a' + 1;
    }
}
```