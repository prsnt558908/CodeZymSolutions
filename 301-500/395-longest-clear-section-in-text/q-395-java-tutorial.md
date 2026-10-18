# Longest Clear Section in Garbled Text in Java

#### Problem Statement

[https://codezym.com/question/395-longest-clear-section-in-text](https://codezym.com/question/395-longest-clear-section-in-text)

Keep a consecutive section with no repeated characters. Scan the text once and use a map to remember each character's last position. When a character repeats inside the current section, move the start just past its previous occurrence. This sliding window avoids checking the same sections again.

## Start with a simple approach

Try every starting position. From each start, add characters to a set until a character repeats. Keep the largest length found.

This can take **O(n²)** time because we scan overlapping sections repeatedly. We can improve it by remembering where characters last appeared.

## Use a sliding window

The window is the section from `left` to `right`, including both ends.

Use a map called `lastSeen` to store each character's most recent index. We only need its latest position to decide whether it repeats inside the window.

For each character at `right`:

1. Look up its previous index.
2. If that index is at least `left`, move `left` to `previous + 1`.
3. Save `right` as the character's latest index.
4. Update the answer using `right - left + 1`.

Never move `left` backwards. An older occurrence outside the current window does not create a repeat inside it.

Spaces and special characters count like any other character. Uppercase and lowercase characters remain different.

## Walk through an example

Consider `"abba"`:

- Reading `"ab"` gives a clear section of length `2`.
- The second `b` repeats the `b` at index `1`. Move `left` to index `2`. The current section is now `"b"`.
- The final `a` previously appeared at index `0`, outside the window. Keep `left` at `2`. The current section becomes `"ba"`.

The answer is **2**. Moving `left` backwards on the final `a` would incorrectly include both copies of `b`.

For `"chaptermark"`, the section `"chapterm"` gives the answer **8**.

## Why this works

Before adding a character, the window has no repeats. Only the new character can break that rule.

If it already appears inside the window, moving `left` just past its previous position removes the repeat. Starting any earlier would still include both copies. The resulting window is therefore the longest clear section ending at `right`.

Checking the largest window over all ending positions gives the answer. For empty text, the loop does not run and the result stays `0`.

## Time and space

Let `n` be the number of characters and `k` the number of distinct characters in the input.

**Time: O(n)** on average, using constant-time hash map operations.

**Extra space: O(k)** for the map.

## Java code

`LongestClearSection` keeps the supplied constructor and method signature. All working data is local to the method, so calls are independent.

The map uses integer character codes as keys. `offset` reads the string, while `right` counts characters. Using `codePointAt` also handles characters that Java stores in two `char` values.

```java
import java.util.HashMap;
import java.util.Map;

public class LongestClearSection {

    public LongestClearSection() {
    }

    public int longestClearSection(String garbledText) {
        // Character code -> most recent character index.
        Map<Integer, Integer> lastSeen = new HashMap<>();
        int left = 0;
        int right = 0;
        int bestLength = 0;

        for (int offset = 0; offset < garbledText.length(); ) {
            int current = garbledText.codePointAt(offset);
            Integer previous = lastSeen.get(current);

            // Only a repeat inside the current window moves its start.
            if (previous != null && previous >= left) {
                left = previous + 1;
            }

            lastSeen.put(current, right);
            bestLength = Math.max(bestLength, right - left + 1);

            // Advance by one complete character.
            offset += Character.charCount(current);
            right++;
        }

        return bestLength;
    }
}
```
