# Minimum Characters to Remove from Ends to Make a Palindrome in Java

#### Problem Statement
[https://codezym.com/question/123-remove-end-characters-make-palindrome](https://codezym.com/question/123-remove-end-characters-make-palindrome)


When we cut some characters from the left end and some from the right end, what is left is always one contiguous piece of the original string. So this problem is really asking: **what is the longest palindrome that is a contiguous substring of `s`?** Once we know its length, the answer is simply `n - longestPalindromeLength`.


We will build up to the best solution in three steps. First, a brute force that checks every substring. Then a faster "expand around the center" approach. Finally, **Manacher's algorithm**, which finds the longest palindromic substring in linear time by reusing work it has already done.

---

## Understanding the Problem

You are given a string `s`. You can only cut characters from its two ends.


Whatever remains must be a palindrome. Find the minimum number of characters you need to cut.


**Example:** `s = "cabbad"`

- Cut `'c'` from the left and `'d'` from the right.
- What remains is `"abba"`, which is a palindrome.
- Answer is `2`.


**Key observation:** removing a prefix and a suffix is the same as keeping one contiguous substring.


So to remove the fewest characters, we keep the **longest palindromic substring**.

```
answer = n - (length of longest palindromic substring)
```


A single character is always a palindrome. So the longest length is at least `1`, and the answer is never more than `n - 1`.

---

## Solution 1: Brute Force (Check Every Substring)

### Idea

The most direct way is to try every substring `s[left..right]` and check if it is a palindrome.


We keep track of the longest one we find.

### How do we check a palindrome?

Use two pointers. One starts at `left`, the other at `right`.


Compare the two characters and move both pointers inward. If any pair does not match, it is not a palindrome.

### Code

```java
public class MinimumCharactersToRemoveFromEnds {

    public MinimumCharactersToRemoveFromEnds() {
    }

    public int minimumCharactersToRemove(String s) {
        int n = s.length();
        int longest = 1; // a single character is always a palindrome

        // Try every substring s[left..right]
        for (int left = 0; left < n; left++) {
            for (int right = left; right < n; right++) {
                if (isPalindrome(s, left, right)) {
                    longest = Math.max(longest, right - left + 1);
                }
            }
        }

        // Remove everything outside the longest palindrome
        return n - longest;
    }

    /** Two pointers move inward and compare characters. */
    boolean isPalindrome(String s, int left, int right) {
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }
}
```

### Complexity

- There are about `n²` substrings, and checking each one takes up to `n` steps.
- **Time:** `O(n³)`
- **Space:** `O(1)`

### Problem with this approach

For `n = 100,000`, `n³` is around `10^15` steps. This is far too slow.


The waste is that we check each substring from scratch, even though many substrings share the same middle part.

---

## Solution 2: Expand Around the Center

### Idea

Every palindrome is a mirror image around its center.


So instead of picking a start and an end, we pick a **center** and grow outward as long as the characters on both sides match.


There are two kinds of centers:

- **Odd length** palindromes like `"aba"` have one character as the center.
- **Even length** palindromes like `"abba"` have their center between two characters.


We try every possible center of both kinds.

### Why is this better?

We never check a substring from scratch. Each center grows one step at a time, and the moment the two sides stop matching, we stop.

### Code

```java
public class MinimumCharactersToRemoveFromEnds {

    public MinimumCharactersToRemoveFromEnds() {
    }

    public int minimumCharactersToRemove(String s) {
        int n = s.length();
        int longest = 1;

        for (int center = 0; center < n; center++) {
            // Odd length palindrome: center is one character, like "aba"
            int oddLength = expand(s, center, center);

            // Even length palindrome: center is between two characters, like "abba"
            int evenLength = expand(s, center, center + 1);

            longest = Math.max(longest, Math.max(oddLength, evenLength));
        }

        return n - longest;
    }

    /**
     * Grows outward while both sides match.
     * Returns the length of the palindrome found.
     */
    int expand(String s, int left, int right) {
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        // left and right are now one step past the palindrome on each side
        return right - left - 1;
    }
}
```

### Complexity

- **Time:** `O(n²)` in the worst case.
- **Space:** `O(1)`

### Problem with this approach

Think about `s = "aaaa...a"` with `100,000` characters.


Every center can grow almost to the ends of the string. That adds up to billions of character comparisons, which is still too slow.


The waste here: a center that sits inside a big palindrome we already found starts growing from zero. But the mirror image on the other side of that big palindrome already tells us a lot about it.

---

## Solution 3: Manacher's Algorithm (Optimal)

### Idea in simple words

Manacher's algorithm is "expand around the center" plus one clever trick: **reuse what we already know.**


Imagine we have already found a big palindrome. Its left half is a mirror image of its right half.


Now say we are at a position `i` in its right half. The matching position `mirror` in the left half looks exactly the same around it, at least inside the big palindrome.


We already know how far the palindrome around `mirror` stretches. That gives `i` a free head start. We only need to expand beyond the part we already know.

### Step 1: Handle odd and even lengths together

Odd and even palindromes normally need two separate cases. To avoid that, we put a `#` between every pair of characters and also at both ends.

```
s = "abba"
t = "#a#b#b#a#"
```


Now every palindrome in `t` has odd length, and its center is always a single character of `t`. The center of `"abba"` is now the middle `#`.

### Step 2: The radius array

For each position `i` in `t`, `radius[i]` stores how many characters the palindrome centered at `i` stretches on **each side**.

```
t      = #  a  #  b  #  b  #  a  #
index  = 0  1  2  3  4  5  6  7  8
radius = 0  1  0  1  4  1  0  1  0
```


A handy fact: `radius[i]` is exactly the length of the matching palindrome in the original string `s`.


At index `4`, the radius is `4`. The palindrome there is `"#a#b#b#a#"`, which is `"abba"` in `s`, and its length is `4`.


So the longest palindrome in `s` is simply the **largest value in `radius`**.

### Step 3: Use the mirror

We remember the palindrome that reaches **farthest to the right** so far, using two values:

- `center`: where that palindrome is centered
- `right`: the last index it covers, which is `center + radius[center]`


When we reach a new position `i`:

1. If `i < right`, then `i` is inside that palindrome. Its mirror is `mirror = 2 * center - i`.
2. Start with `radius[i] = min(radius[mirror], right - i)`.
   - `radius[mirror]` is what the mirror tells us.
   - `right - i` is the limit, because we know nothing about characters beyond `right` yet.
3. Then try to expand further, just like in Solution 2.
4. If the palindrome at `i` now reaches past `right`, it becomes the new farthest one. Update `center` and `right`.

### Why is this O(n)?

Each time an expansion step succeeds, the palindrome at `i` reaches a new character beyond `right`, and then `right` moves there.


`right` only moves forward and can never pass the end of `t`. So the total number of successful expansion steps over the whole loop is at most the length of `t`, which is `2n + 1`.


Each position also has at most one failed comparison. So the total work is linear.

### Data structures used

- **`char[] t`**: the string with `#` separators. It lets one simple loop handle both odd and even palindromes.
- **`int[] radius`**: stores the answer for every center. This is what makes the mirror trick possible, because we can look up `radius[mirror]` instantly instead of recomputing it.

### Code

```java
public class MinimumCharactersToRemoveFromEnds {

    public MinimumCharactersToRemoveFromEnds() {
    }

    public int minimumCharactersToRemove(String s) {
        int n = s.length();
        char[] t = addSeparators(s);
        int m = t.length;

        // radius[i] = how many characters the palindrome centered at i
        // stretches on each side. It is also the length of that
        // palindrome in the original string s.
        int[] radius = new int[m];

        // The palindrome that reaches farthest to the right so far
        int center = 0;
        int right = 0;

        int longest = 1;

        for (int i = 0; i < m; i++) {
            // Free head start: copy from the mirror, but never cross 'right'
            if (i < right) {
                int mirror = 2 * center - i;
                radius[i] = Math.min(radius[mirror], right - i);
            }

            // Try to grow beyond what we already know
            while (i - radius[i] - 1 >= 0
                    && i + radius[i] + 1 < m
                    && t[i - radius[i] - 1] == t[i + radius[i] + 1]) {
                radius[i]++;
            }

            // This palindrome reaches farther right, so remember it
            if (i + radius[i] > right) {
                center = i;
                right = i + radius[i];
            }

            longest = Math.max(longest, radius[i]);
        }

        return n - longest;
    }

    /**
     * Puts '#' between characters and at both ends.
     * "abba" becomes "#a#b#b#a#", so every palindrome has odd length.
     */
    char[] addSeparators(String s) {
        StringBuilder sb = new StringBuilder("#");
        for (char c : s.toCharArray()) {
            sb.append(c).append('#');
        }
        return sb.toString().toCharArray();
    }
}
```

### Complexity

- **Time:** `O(n)`
- **Space:** `O(n)` for `t` and `radius`

---

## Quick Dry Run

`s = "aabcb"`, so `t = "#a#a#b#c#b#"`.

```
t      = #  a  #  a  #  b  #  c  #  b  #
index  = 0  1  2  3  4  5  6  7  8  9  10
radius = 0  1  2  1  0  1  0  3  0  1  0
```


- At index `2`, radius is `2`, which is `"aa"`.
- At index `7`, radius is `3`, which is `"bcb"`.


Notice index `9`. It is inside the palindrome centered at `7`, and its mirror is index `5`. Since `radius[5] = 1`, index `9` starts with radius `1` for free.


The largest radius is `3`. So the answer is `5 - 3 = 2`.

---

## Summary

| Approach | Time | Space |
|---|---|---|
| Brute Force | `O(n³)` | `O(1)` |
| Expand Around Center | `O(n²)` | `O(1)` |
| Manacher's Algorithm | `O(n)` | `O(n)` |


The whole problem comes down to one idea: **keep the longest palindromic substring and cut away everything else.**


Brute force and expand around center are easy to write, but only Manacher's algorithm is fast enough for strings of length `100,000`.