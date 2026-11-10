# Longest Substring With At Most N Distinct Characters in Java

#### Problem Statement
[https://codezym.com/question/197-longest-substring-n-distinct-characters](https://codezym.com/question/197-longest-substring-n-distinct-characters)


## Core Idea

We need the longest continuous part of `s` that has at most `n` distinct (different) characters.


The main idea is a **sliding window**. The window is the part of the string between two pointers, `left` and `right`. We move `right` forward to make the window bigger. If the window ends up with more than `n` distinct characters, we move `left` forward to make it smaller, until it is valid again.


To count the distinct characters inside the window, we use a **map** from each character to the number of times it appears in the window. The size of the map is the number of distinct characters.


Both pointers only move forward. Each character is added to the window once and removed at most once, so the whole solution runs in linear time.


We will start with a simple brute force, see where it wastes work, and then improve it into the sliding window.


---

## Approach 1: Brute Force

Try every starting index `i`. From `i`, extend the substring one character at a time and keep its characters in a **set**.


As soon as the set has more than `n` characters, stop. Adding more characters can never bring that number down.

```java
int maxLength = 0;
for (int i = 0; i < s.length(); i++) {
    Set<Character> seen = new HashSet<>();
    for (int j = i; j < s.length(); j++) {
        seen.add(s.charAt(j));
        if (seen.size() > n) {
            break; // too many distinct characters, a longer substring will not help
        }
        maxLength = Math.max(maxLength, j - i + 1);
    }
}
return maxLength;
```

**Time:** O(L²), where `L` is the length of `s`.

**Space:** O(k), where `k` is the number of distinct characters in `s`.


### Why is this slow?

Take `s = "aaaa...a"` with 100,000 characters and `n = 1`. The inner loop never breaks, so it runs till the end of the string for every `i`. That is about 5 billion steps.


The waste is easy to see: every new start `i` throws away the set and scans the same characters again.


### The key observation

If `s[i..j]` is valid, then `s[i+1..j]` is also valid. Removing a character can never add a new distinct character.


So when the start moves forward, the end does not need to move back. We can keep the end where it is and continue from there. This is exactly what a sliding window does.


---

## Approach 2: Sliding Window with a HashMap (Optimal)

We keep a window `s[left..right]` that always has at most `n` distinct characters. For every `right` from `0` to the end of the string, we do three steps:

1. **Grow:** add `s[right]` to the window by increasing its count in the map.
2. **Shrink:** while the map has more than `n` keys, remove `s[left]` from the window. Decrease its count, delete the key if the count becomes `0`, and move `left` forward.
3. **Record:** the window is valid now, so update the answer with its length, `right - left + 1`.


### Why a map and not a set?

A set only tells us *whether* a character is in the window. It does not tell us *how many* copies are inside.


When `left` moves past a character, another copy of it may still be in the window. For example, if the window is `"ece"` and we drop the first `'e'`, the window `"ce"` still has an `'e'`.


The map stores a count for each character, and we delete a key only when its count reaches `0`. So `countMap.size()` is always the exact number of distinct characters in the window.


### Dry run

`s = "ecebaaa"`, `n = 2`

| right | added | what happens | window | length | maxLength |
|:---:|:---:|:---|:---:|:---:|:---:|
| 0 | `e` | valid | `e` | 1 | 1 |
| 1 | `c` | valid | `ec` | 2 | 2 |
| 2 | `e` | valid | `ece` | 3 | 3 |
| 3 | `b` | 3 distinct, drop `e` then `c` | `eb` | 2 | 3 |
| 4 | `a` | 3 distinct, drop `e` | `ba` | 2 | 3 |
| 5 | `a` | valid | `baa` | 3 | 3 |
| 6 | `a` | valid | `baaa` | 4 | 4 |


The answer is `4`, from the window `"baaa"`.


Look at `right = 3`. After dropping the first `e`, the window `"ceb"` still has 3 distinct characters, so we drop `c` too. This is why the shrink step is a `while` loop and not an `if`.


### Edge cases

- **`n = 0`:** no non-empty substring can be valid, so we return `0` right away.
- **Empty string:** the loop never runs, so the answer stays `0`.
- **`n` is at least the number of distinct characters in `s`:** the window never shrinks, so the answer is `s.length()`.


### Complexity

- **Time: O(L).** The `while` loop sits inside the `for` loop, but `left` only moves forward. Over the whole run, each character is added once and removed at most once.
- **Space: O(k).** The map has one entry per distinct character in the window. Since `s` only has ASCII characters, this is never more than 128 entries.


### Code

```java
import java.util.HashMap;
import java.util.Map;

public class LongestSubstringWithAtMostNDistinctCharacters {

    public LongestSubstringWithAtMostNDistinctCharacters() {
    }

    /**
     * Returns the length of the longest substring of s
     * that contains at most n distinct characters.
     */
    public int lengthOfLongestSubstringNDistinct(String s, int n) {
        // No character is allowed, so no non-empty substring can be valid
        if (n <= 0) {
            return 0;
        }

        // character -> how many times it appears inside the current window
        Map<Character, Integer> countMap = new HashMap<>();
        int left = 0;
        int maxLength = 0;

        for (int right = 0; right < s.length(); right++) {
            // 1. Grow: add s[right] to the window
            char rightChar = s.charAt(right);
            countMap.put(rightChar, countMap.getOrDefault(rightChar, 0) + 1);

            // 2. Shrink: drop characters from the left until the window
            //    has at most n distinct characters again
            while (countMap.size() > n) {
                char leftChar = s.charAt(left);
                int count = countMap.get(leftChar) - 1;
                if (count == 0) {
                    countMap.remove(leftChar); // no copy of leftChar is left in the window
                } else {
                    countMap.put(leftChar, count);
                }
                left++;
            }

            // 3. Record: s[left..right] is valid, keep the longest length
            maxLength = Math.max(maxLength, right - left + 1);
        }

        return maxLength;
    }
}
```