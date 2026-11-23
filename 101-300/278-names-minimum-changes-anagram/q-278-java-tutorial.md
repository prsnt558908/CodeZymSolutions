# Names Requiring Minimum Changes to Become an Anagram in Java

#### Problem Statement
[https://codezym.com/question/278-names-minimum-changes-anagram](https://codezym.com/question/278-names-minimum-changes-anagram)


## Core Idea

Two strings are anagrams when they contain the same characters, the same number of times. Positions do not matter, because we can rearrange characters freely.


So for every candidate, we only need to answer one question: **how many characters of `name` are missing from it?** Each missing character costs exactly one change.


We count the characters of `name` once. Then for each candidate we find how many characters are missing, and we keep the candidates with the smallest number, in their original order.


## Why Each Missing Character Costs One Change

Take `name = "aabc"` and the candidate `"aabb"`.


The candidate already has both `a` and one `b`, so those can stay. Its second `b` is extra, and `c` is missing.


Replace the extra `b` with `c` and we get `"aabc"`. That is **1 missing character** and **1 change**.


This always holds:

- One change adds only one new character. So we need at least one change per missing character.
- Both strings have the same length. So there are exactly as many extra characters as missing ones, and each extra one can be replaced by a missing one.


So the number of changes is the total number of missing characters. This is the "sum of deficits" from the problem statement.


## Approach 1: Brute Force (Match Characters One by One)

The most direct idea is to match characters by hand.


For each character of `name`, search the candidate for a copy that is not used yet.

- If we find one, we mark it as used. This character can stay.
- If we do not find one, the character is missing. We need one change.

```java
int countChanges(String name, String candidate) {
    boolean[] used = new boolean[candidate.length()];
    int changes = 0;

    for (char c : name.toCharArray()) {
        boolean found = false;
        for (int j = 0; j < candidate.length(); j++) {
            if (!used[j] && candidate.charAt(j) == c) {
                used[j] = true;   // this copy is now taken
                found = true;
                break;
            }
        }
        if (!found) {
            changes++;            // c is missing, one change needed
        }
    }
    return changes;
}
```

We run this for every candidate and keep the ones with the smallest result.


### Why is this slow?

For every character of `name`, we may scan the whole candidate. If the length is `L`, that is `O(L²)` work for a single candidate.


`L` can be `100,000`, which means up to `10^10` steps for one candidate. That is far too slow.


The real waste is that we keep searching for **where** a character is. But we only care about **how many times** it appears.


## Approach 2: Count Characters (Optimal)

Since only counts matter, we stop searching and start counting.


### Step 1: Count the characters of `name` once

We build a count table `nameCount`, where `nameCount[c]` is how many times character `c` appears in `name`.


**Why an `int[128]` array?** All characters are English letters, and their ASCII codes are below 128. So the character itself can be the index, like `nameCount['a']`. It works like a small map from character to count, but it is simpler and faster than a `HashMap`.


`name` is the same for every candidate, so we count it only once.


### Step 2: Find what each candidate is missing

For each candidate, we copy `nameCount` into a new array called `need`. Think of `need` as a shopping list: how many of each character we still need.


Then we walk through the candidate:

- If the character is still on the list (`need[c] > 0`), we keep it and reduce `need[c]` by 1.
- Otherwise it is an extra character, so we skip it.


Whatever is left in `need` is missing. The sum of `need` is the number of changes.


We copy the array because every candidate must start with the full list. The copy has only 128 slots, so it is cheap.


### Step 3: Keep the names with the fewest changes

We walk through `nameList` once and track two things:

- `minChanges`: the smallest number of changes seen so far.
- `result`: every name that needs exactly `minChanges` changes.


For each candidate:

- If it needs **fewer** changes than `minChanges`, it is the new best. We clear `result` and update `minChanges`.
- If it needs **exactly** `minChanges` changes, we add it to `result`.


We go from left to right and add every match, so the input order and duplicate names are kept automatically.


### Dry Run

`name = "aabc"`, `nameList = ["aabb", "abcc", "dddd", "aabb"]`


`nameCount`: `a = 2, b = 1, c = 1`

| Candidate | Missing | Changes | minChanges | result |
|-----------|---------|---------|------------|--------|
| `aabb` | `c` | 1 | 1 | `[aabb]` |
| `abcc` | `a` | 1 | 1 | `[aabb, abcc]` |
| `dddd` | `a, a, b, c` | 4 | 1 | `[aabb, abcc]` |
| `aabb` | `c` | 1 | 1 | `[aabb, abcc, aabb]` |


Final answer: `["aabb", "abcc", "aabb"]`


### Code

```java
import java.util.*;

public class NamesRequiringMinimumChanges {
    public NamesRequiringMinimumChanges() {
    }

    public List<String> findNamesWithMinimumChanges(String name, List<String> nameList) {
        // count the characters of name only once
        int[] nameCount = countChars(name);

        List<String> result = new ArrayList<>();
        int minChanges = Integer.MAX_VALUE;

        for (String candidate : nameList) {
            int changes = countChanges(nameCount, candidate);

            if (changes < minChanges) {
                // new best found, older names are no longer the answer
                minChanges = changes;
                result.clear();
            }
            if (changes == minChanges) {
                result.add(candidate);
            }
        }
        return result;
    }

    // Returns how many times each character appears in s.
    // Letters have codes below 128, so the character itself is the index.
    int[] countChars(String s) {
        int[] count = new int[128];
        for (int i = 0; i < s.length(); i++) {
            count[s.charAt(i)]++;
        }
        return count;
    }

    // Returns the minimum number of replacements needed
    // to turn candidate into an anagram of name.
    int countChanges(int[] nameCount, String candidate) {
        // need[c] = how many more copies of c we still need
        int[] need = nameCount.clone();

        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (need[c] > 0) {
                need[c]--; // this character is useful, keep it
            }
        }

        // every character that is still missing costs one change
        int changes = 0;
        for (int count : need) {
            changes += count;
        }
        return changes;
    }
}
```


### Complexity

Let `L` be the length of `name` and `N` be the number of names in `nameList`.

- **Time:** `O(N × L)`. Each candidate is scanned once. Copying and summing `need` adds only 128 steps per candidate. `N × L` is the total number of characters in `nameList`, which is at most `1,000,000`.
- **Space:** `O(1)` extra. The count arrays always have 128 slots. The answer list is not counted.