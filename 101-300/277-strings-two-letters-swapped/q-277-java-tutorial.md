# Strings That Have Two Letters Swapped in Java

#### Problem Statement
[https://codezym.com/question/277-strings-two-letters-swapped](https://codezym.com/question/277-strings-two-letters-swapped)


We go through `nameList` one string at a time and ask a simple question: can one swap turn this string into `name`?


A swap changes only two positions. So a matching string must differ from `name` at exactly two positions, and the two letters there must be the same as in `name`, only swapped.


We first try a brute force idea that tests every possible swap. It is easy to follow but very slow. Then we improve it to check each string in a single pass.


## When Does One Swap Work?

Let us compare the candidate `"makret"` with `name = "market"`.

| index | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| name | m | a | r | k | e | t |
| candidate | m | a | **k** | **r** | e | t |


The strings differ only at positions 2 and 3.


The candidate has `k` at position 2, which is what `name` has at position 3. It has `r` at position 3, which is what `name` has at position 2. The letters are crossed, so swapping them gives `"market"`.


So a candidate is a match only when all three of these are true:

1. It has the same length as `name`.
2. It differs from `name` at exactly two positions, say `i` and `j`.
3. The letters are crossed: `candidate[i] == name[j]` and `candidate[j] == name[i]`.


Why exactly two differences?

- **0 differences:** the string is already equal to `name`. The rules say this is not a match.
- **1 difference:** the correct letter would have to come from another position. But every other position is already correct, so taking a letter from there breaks it.
- **3 or more differences:** one swap can fix at most two positions.


## Approach 1: Brute Force (Try Every Swap)

For each candidate, the main method calls a helper `isOneSwapAway(name, candidate)` and keeps the candidate if it returns `true`.


In the brute force version, the helper tries every pair of positions, swaps them, and checks if the result equals `name`.

```java
boolean isOneSwapAway(String name, String candidate) {
    if (name.length() != candidate.length()) return false;

    char[] chars = candidate.toCharArray();
    int n = chars.length;

    // try every pair of positions (i, j)
    for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
            // swapping two equal letters changes nothing, so it does not count
            if (chars[i] == chars[j]) continue;

            swap(chars, i, j);
            if (new String(chars).equals(name)) return true;
            swap(chars, i, j); // undo before trying the next pair
        }
    }
    return false;
}

void swap(char[] chars, int i, int j) {
    char temp = chars[i];
    chars[i] = chars[j];
    chars[j] = temp;
}
```


### Why is this slow?

A string of length `n` has about `n * n / 2` pairs of positions, and each comparison with `name` takes `n` steps. That is `O(n^3)` work for a single candidate.


With `n = 100,000` this is around `5 * 10^14` steps, which is far too slow.


Most of this work is wasted. We keep trying swaps at positions where the letters already match `name`.


## Approach 2: Single Pass Comparison (Optimal)

We don't need to try any swaps. The rules above already tell us what a match looks like, so we only need to find **where** the candidate and `name` differ.


The main method stays the same. Only `isOneSwapAway` changes:

1. If the lengths are different, return `false`.
2. Walk through both strings together. Whenever the letters at a position differ, add that position to a small list called `diff`.
3. If `diff` gets a third position, stop right away. One swap can't fix three positions.
4. At the end, return `true` only if `diff` has exactly two positions and their letters are crossed.


We use a list for `diff` because it is the simplest way to remember where the strings differ. It never holds more than three numbers, so it costs almost nothing.


The `result` list collects matches while we go through `nameList` from left to right. So the original order is kept, and a repeated match is added every time it appears.


### Walkthrough

For `name = "market"`:

| candidate | positions that differ | result |
|---|---|---|
| `makret` | 2, 3 (letters crossed) | match |
| `market` | none | not a match |
| `masket` | 2 only | not a match |
| `markte` | 4, 5 (letters crossed) | match |
| `planet` | 0, 1, 2 (stop at the third) | not a match |

Output: `["makret", "markte"]`


### Complexity

- **Time:** `O(T)`, where `T` is the total number of characters in `nameList`. Each candidate is scanned at most once, and only when it has the same length as `name`.
- **Space:** `O(1)` extra, not counting the output list. The `diff` list never holds more than three positions.


### Java Code

```java
import java.util.*;

public class StringsThatHaveTwoLettersSwapped {

    public StringsThatHaveTwoLettersSwapped() {

    }

    /**
     * Returns every string from nameList that becomes equal to name
     * after swapping exactly two of its characters.
     * Matches keep their original order, and duplicates are kept too.
     */
    public List<String> findNamesWithTwoLettersSwapped(String name, List<String> nameList) {
        List<String> result = new ArrayList<>();
        for (String candidate : nameList) {
            if (isOneSwapAway(name, candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Checks if one swap of two characters turns candidate into name.
     * Compares both strings position by position and stops early
     * as soon as a third difference is found.
     */
    boolean isOneSwapAway(String name, String candidate) {
        if (name.length() != candidate.length()) {
            return false;
        }

        // positions where name and candidate have different characters
        List<Integer> diff = new ArrayList<>();
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) != candidate.charAt(i)) {
                diff.add(i);
                // one swap can fix at most two positions
                if (diff.size() > 2) {
                    return false;
                }
            }
        }

        // 0 differences means already equal, 1 difference can't be fixed by a swap
        if (diff.size() != 2) {
            return false;
        }

        int first = diff.get(0);
        int second = diff.get(1);
        // the two different characters must be crossed over
        return candidate.charAt(first) == name.charAt(second)
                && candidate.charAt(second) == name.charAt(first);
    }
}
```