# Binary Audit Log Palindrome Validation in Java

#### Problem Statement
[https://codezym.com/question/460-binary-audit-log-validation](https://codezym.com/question/460-binary-audit-log-validation)

The whole solution rests on two small observations.

First, every palindrome of length 5 or more hides a palindrome of length **5 or 6** in its middle. So we only need to make sure that no window of 5 or 6 characters is a palindrome.

Second, if we fill the log from left to right, a new character can only form such a window together with the **5 characters just before it**. So we never need to remember a whole log, only its last 5 characters.

There are at most `2^5 = 32` such endings. We keep a small set of endings that are still valid and update it for every character. If the set ever becomes empty, the answer is `IMPOSSIBLE`. Otherwise it is `POSSIBLE`. This takes `O(n)` time.

## Brute Force: Try Every Replacement

The most direct idea is to try both values for every `?`, build every complete log and test each one.

```
1??
├── 10?
│   ├── 100
│   └── 101
└── 11?
    ├── 110
    └── 111
```

To test a complete log, we look at every substring of length 5 or more and check if it reads the same both ways.

This has two big problems:

- **Too many logs:** every `?` doubles the number of logs. With `k` question marks we get `2^k` logs. For just 50 question marks that is about `10^15` logs, and a log can have 50,000 of them.
- **Slow testing:** a log of length `n` has about `n^2 / 2` substrings, and checking one can take `n` steps. That is `O(n^3)` work for every single log.

Let's fix them one at a time.

## Improvement 1: Only Windows of Length 5 and 6 Matter

Cut off the first and last character of a palindrome and look at what is left:

```
1001001      length 7, palindrome
 00100       length 5, still a palindrome

10011001     length 8, palindrome
 001100      length 6, still a palindrome
```

The two ends of a palindrome are a matching pair. Take them away and the middle part still reads the same both ways.

Keep cutting, and every odd length palindrome shrinks down to length 5, while every even length palindrome shrinks down to length 6. So:

> If no window of length 5 or 6 is a palindrome, then no substring of length 5 or more is a palindrome.

Now testing a complete log needs only two sliding windows, of size 5 and size 6. That is `O(n)` instead of `O(n^3)`.

We need **both** sizes. In `011110` neither window of size 5 (`01111` and `11110`) is a palindrome, but the full window of size 6 is.

We still try `2^k` logs though. That is the next fix.

## Improvement 2: Only the Last 5 Characters Matter

Let's build the log from left to right, one character at a time.

When we place a new character, every window that ends earlier was already checked. The only new windows we need to check are the two that **end at this character**:

- the last 5 characters: the new one and the 4 before it
- the last 6 characters: the new one and the 5 before it

Both use only the new character and the **5 characters before it**. Older characters are never looked at again.

So if two partly built logs end with the same 5 characters, their futures are exactly the same:

```
log A:  000 01011
log B:  110 01011
            -----
            same ending
```

Any character that is safe to add to log A is also safe to add to log B. So we only need to keep one of them.

This is the big saving. Instead of a list of full logs, we keep a **set of endings**: the last 5 characters of every valid log built so far.

An ending is at most 5 characters, each `0` or `1`, so there are at most `2^5 = 32` different endings. The set never grows past 32, no matter how many `?` the log has.

## Final Algorithm

1. Start with a set that holds only the empty ending `""`.
2. Read the log one character at a time. A `?` gives two choices, `0` and `1`. A known character gives one choice: itself.
3. For every ending in the set and every choice:
   - Join them: `text = ending + choice`. It has at most 6 characters.
   - If the last 5 characters of `text`, or all 6 of them, form a palindrome, skip this choice.
   - Otherwise, add the last 5 characters of `text` to the next set.
4. If the next set is empty, no valid log exists. Return `IMPOSSIBLE`.
5. If we reach the end of the log, return `POSSIBLE`.

## Walkthrough

Let's run `s = "10?01"`.

| Character read | Endings after reading it |
|---|---|
| start | `""` (empty) |
| `1` | `1` |
| `0` | `10` |
| `?` | `100`, `101` |
| `0` | `1000`, `1010` |
| `1` | none, because `10001` and `10101` are palindromes |

The set became empty, so the answer is `IMPOSSIBLE`.

## Why a HashSet?

Many different logs can end with the same 5 characters. A `HashSet` stores each ending only once, so such logs merge into one entry automatically.

This merging is exactly what stops the count from doubling at every `?`. It keeps the set at 32 endings or fewer, and adding to it is `O(1)`.

We fill a new set (`nextEndings`) for each character, so only the endings that could place the current character move forward.

## Complexity

- **Time:** `O(n)`. For each of the `n` characters we try at most 32 endings and 2 choices, and each check reads at most 6 characters.
- **Space:** `O(1)` extra. The set never holds more than 32 strings of at most 5 characters.

## Java Code

```java
import java.util.*;

public class BinaryAuditLogPalindromeValidation {

    public BinaryAuditLogPalindromeValidation() {
    }

    /**
     * Returns "POSSIBLE" if every '?' can be replaced with '0' or '1'
     * so that the log has no palindromic substring of length 5 or more.
     */
    public String checkPossibility(String s) {
        // All possible endings (last 5 characters) of valid logs built so far.
        // Before reading anything, the only ending is the empty string.
        Set<String> endings = new HashSet<>();
        endings.add("");

        for (int i = 0; i < s.length(); i++) {
            char event = s.charAt(i);
            // '?' can become '0' or '1'. A known event must stay as it is.
            String choices = (event == '?') ? "01" : String.valueOf(event);
            Set<String> nextEndings = new HashSet<>();

            for (String ending : endings) {
                for (char choice : choices.toCharArray()) {
                    String text = ending + choice; // at most 6 characters
                    if (createsLongPalindrome(text)) continue;

                    // Only the last 5 characters can matter for future checks.
                    nextEndings.add(lastChars(text, 5));
                }
            }

            // No valid way to place this event, so no valid log exists.
            if (nextEndings.isEmpty()) return "IMPOSSIBLE";
            endings = nextEndings;
        }
        return "POSSIBLE";
    }

    /**
     * text = old ending + the new character (at most 6 characters).
     * Checks the only two new windows that end at the new character:
     * the last 5 characters and the last 6 characters.
     */
    boolean createsLongPalindrome(String text) {
        if (text.length() >= 5 && isPalindrome(lastChars(text, 5))) return true;
        return text.length() == 6 && isPalindrome(text);
    }

    /** Returns the last 'count' characters of text, or all of it if it is shorter. */
    String lastChars(String text, int count) {
        return text.substring(Math.max(0, text.length() - count));
    }

    boolean isPalindrome(String text) {
        int left = 0, right = text.length() - 1;
        while (left < right) {
            if (text.charAt(left) != text.charAt(right)) return false;
            left++;
            right--;
        }
        return true;
    }
}
```