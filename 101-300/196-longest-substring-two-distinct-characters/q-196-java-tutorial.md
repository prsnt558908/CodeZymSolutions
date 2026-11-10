# Longest Substring With At Most Two Distinct Characters in Java

#### Problem Statement
[https://codezym.com/question/196-longest-substring-two-distinct-characters](https://codezym.com/question/196-longest-substring-two-distinct-characters)

The core idea is to use a **sliding window**. A window is just a part of the string between two pointers, `left` and `right`. We call a window **valid** when it has at most two distinct characters.

We move `right` forward one step at a time to grow the window. If a third distinct character comes in, we move `left` forward to shrink the window until it is valid again. A small map counts every character inside the window, so we always know how many distinct characters it has.

The biggest valid window we see along the way is the answer, and we find it in a single pass over the string.

## Brute Force: Try Every Starting Point

Pick every index as a starting point. From there, keep adding characters to the right and store the distinct ones in a `Set`.

The moment the set gets a third character, every longer substring from this start is also invalid. So we stop and move to the next start.

```java
int best = 0;
for (int start = 0; start < s.length(); start++) {
    Set<Character> distinct = new HashSet<>();
    for (int end = start; end < s.length(); end++) {
        distinct.add(s.charAt(end));
        if (distinct.size() > 2) break; // third character found, stop here
        best = Math.max(best, end - start + 1);
    }
}
return best;
```

### Why is it slow?

This takes `O(n²)` time in the worst case. Think of a string like `"aaaa...a"`. The inner loop never stops early, so we do about `n * n / 2` steps. For `n = 100,000` that is around 5 billion steps, which is far too slow.

The real problem is repeated work. When `start` moves one step to the right, we forget everything we learned and scan the same characters again.

## Optimized Solution: Sliding Window

### Key Observation

Suppose the window from `left` to `right` has more than two distinct characters. Then every longer window that starts at `left` also has more than two, because adding characters never removes a distinct one. So `left` cannot be the start of any valid substring from now on, and it is safe to move it forward.

This means we never start over. When a new character on the right breaks the rule, we drop characters from the left until the window is valid again. Both `left` and `right` only move forward, never back.

### Why a HashMap and not a Set?

When a character leaves from the left, we need to know if that was its **last copy** in the window. A set cannot tell us that. It only knows if a character is present, not how many times it appears.

So we use a `HashMap<Character, Integer>` that stores how many times each character appears in the window.

- When a count drops to `0`, we remove that character from the map.
- This keeps the size of the map equal to the number of distinct characters in the window.

### Algorithm

For every index `right`, from the start of the string to the end:

1. **Expand:** add `s.charAt(right)` to the map by increasing its count.
2. **Shrink:** while the map has more than 2 keys, decrease the count of `s.charAt(left)`, remove it if its count becomes `0`, and move `left` one step forward.
3. **Update:** the window from `left` to `right` is valid now. Compare its length, `right - left + 1`, with the best answer so far.

### Dry Run

Let us run it on `s = "abaccc"`.

| right | char | window after shrinking | counts in map | length | best |
|---|---|---|---|---|---|
| 0 | `a` | `"a"` | a:1 | 1 | 1 |
| 1 | `b` | `"ab"` | a:1, b:1 | 2 | 2 |
| 2 | `a` | `"aba"` | a:2, b:1 | 3 | 3 |
| 3 | `c` | `"ac"` | a:1, c:1 | 2 | 3 |
| 4 | `c` | `"acc"` | a:1, c:2 | 3 | 3 |
| 5 | `c` | `"accc"` | a:1, c:3 | 4 | 4 |

At `right = 3`, the window `"abac"` has three distinct characters, so we start shrinking.

We remove `a` first. Its count drops from 2 to 1, so it stays in the map. Then we remove `b`. Its count drops to 0, so it leaves the map and only `a` and `c` are left.

This is why we shrink with a `while` loop and not a single `if`. Sometimes more than one character must leave before the window is valid again.

The final answer is **4**, from the substring `"accc"`.

### Edge Cases

- **Empty or `null` string:** we return `0` right away.
- **One or two distinct characters only**, like `"aaaa"` or `"abbba"`: the window never shrinks, so the answer is the full length.
- **Spaces, digits and symbols** are treated like any other character. Also, `'a'` and `'A'` are two different characters.

### Complexity Analysis

| Approach | Time | Space |
|---|---|---|
| Brute Force | `O(n²)` | `O(1)` |
| Sliding Window | `O(n)` | `O(1)` |

The sliding window has a `while` loop inside a `for` loop, but it is still `O(n)`. The `left` pointer never moves back, so over the whole run it moves at most `n` times. Together with `right`, that is about `2n` steps.

Space is `O(1)` because the map never holds more than 3 characters at once.

### Java Code

```java
import java.util.*;

public class LongestSubstringWithAtMostTwoDistinctCharacters {

    public LongestSubstringWithAtMostTwoDistinctCharacters() {
    }

    /**
     * Returns the length of the longest substring of s
     * that has at most two distinct characters.
     */
    public int lengthOfLongestSubstringTwoDistinct(String s) {
        if (s == null || s.isEmpty()) return 0;

        // character -> how many times it appears in the current window
        Map<Character, Integer> count = new HashMap<>();

        int left = 0; // start of the current window
        int best = 0; // longest valid window seen so far

        for (int right = 0; right < s.length(); right++) {
            // 1. Expand: add the character at 'right' to the window
            char incoming = s.charAt(right);
            count.put(incoming, count.getOrDefault(incoming, 0) + 1);

            // 2. Shrink: remove characters from the left
            //    until the window has at most 2 distinct characters
            while (count.size() > 2) {
                char outgoing = s.charAt(left);
                int newCount = count.get(outgoing) - 1;
                if (newCount == 0) {
                    count.remove(outgoing); // its last copy left the window
                } else {
                    count.put(outgoing, newCount);
                }
                left++;
            }

            // 3. Update: the window [left, right] is valid now
            best = Math.max(best, right - left + 1);
        }

        return best;
    }
}
```