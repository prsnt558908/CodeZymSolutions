# Longest Clear Section in Garbled Text in Python

#### Problem Statement

[https://codezym.com/question/395-longest-clear-section-in-text](https://codezym.com/question/395-longest-clear-section-in-text)

Keep a consecutive section with no repeated characters. Scan the text once and use a dictionary to remember each character's last position. When a character repeats inside the current section, move the start just past its previous occurrence. This sliding window avoids checking the same sections again.

## Start with a simple approach

Try every starting position. From each start, add characters to a set until a character repeats. Keep the largest length found.

This can take **O(n²)** time because we scan overlapping sections repeatedly. We can improve it by remembering where characters last appeared.

## Use a sliding window

The window is the section from `left` to `right`, including both ends.

Use a dictionary called `lastSeen` to store each character's most recent index. We only need its latest position to decide whether it repeats inside the window.

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

**Time: O(n)** on average, using constant-time dictionary operations.

**Extra space: O(k)** for the dictionary.

## Python code

`LongestClearSection` keeps the supplied constructor and method signature. All working data is local to the method, so calls are independent.

```python
class LongestClearSection:
    def __init__(self):
        pass

    def longestClearSection(self, garbledText):
        # Character -> most recent index.
        lastSeen = {}
        left = 0
        bestLength = 0

        for right, current in enumerate(garbledText):
            previous = lastSeen.get(current, -1)

            # Only a repeat inside the current window moves its start.
            if previous >= left:
                left = previous + 1

            lastSeen[current] = right
            bestLength = max(bestLength, right - left + 1)

        return bestLength
```
