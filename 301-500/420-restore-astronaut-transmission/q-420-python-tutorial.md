# Restore Astronaut Transmission in Python

#### Problem Statement

[https://codezym.com/question/420-restore-astronaut-transmission](https://codezym.com/question/420-restore-astronaut-transmission)


Read the transmission once and build the answer from left to right. Use two lists as stacks to remember where each repeated section starts and how many copies it needs. When its closing bracket arrives, the first copy is already in the answer. We only need to append the remaining copies.


## 1. Start with a simple replacement

Find the first `]` and its matching `[`. This gives an innermost section, so it contains only letters. Read the count immediately before `[`, expand that section, and replace the whole instruction.

For example, `a2[b3[c]]d` becomes `a2[bccc]d`, then `abcccbcccd`.

Once `opening`, `close`, `count_start`, and `count` are known, the replacement looks like this:

```python
section = text[opening + 1:close]
text = text[:count_start] + section * count + text[close + 1:]
```

This works, but every replacement searches the changing string again and copies text outside the section. We can avoid that repeated work.


## 2. Build one growing answer

Use a list of characters named `restored`. Appending to this list avoids repeatedly rebuilding an immutable string. Join the characters once at the end.

Two lists keep track of unfinished sections. `counts` stores their repetition counts. `starts` stores their starting positions in the output buffer. We add and remove entries at the end of each list, using them as stacks.

A stack fits because the last section opened must be the first section closed.

Process each input character as follows:

- **Digit:** Update `count = count * 10 + digit`. This reads counts such as `12` correctly.
- **`[`:** Save the count and the current output length. Reset the count to zero.
- **Letter:** Append it to the output.
- **`]`:** Remove the most recent count and start position. Save the current output length as `end`, then copy the characters from `start` up to, but excluding, `end` another `count - 1` times.

Keep `end` fixed while copying. The newly appended characters must not extend the section being copied.


## Walk through a nested section

Consider `a2[b3[c]]d`.

1. Reading `a`, then entering `2[`, saves the outer start position as `1`.
2. Reading `b`, then entering `3[`, saves the inner start position as `2`. After reading `c`, the buffer is `abc`.
3. The first `]` repeats the section `c` two more times. The buffer becomes `abccc`.
4. The next `]` repeats the section starting at position `1`, which is `bccc`, once more. The buffer becomes `abcccbccc`.
5. Append `d` to get `abcccbcccd`.


## Why this works

When a section closes, all its inner sections have already closed. Therefore, the characters between its saved start and the current buffer end form one fully restored copy of that section.

Appending that range `count - 1` times gives exactly the required number of copies. Text before the saved start stays unchanged. Since we only append characters, the start positions of outer sections remain valid.

A count of `1` needs no extra copies. Adjacent sections and plain letters are handled in their original order. All working data is local to the method, so separate calls do not affect each other.


## Time and space complexity

Let `n` be the input length, `L` the restored length, and `d` the maximum nesting depth.

**Time: `O(n + L)`.** We scan the input once. Every appended character stays in the final answer, so there are exactly `L` character appends in total. The final join also takes `O(L)` time. This is optimal because we must read the input and produce the entire output.

**Space: `O(L + d)`.** The output buffer uses `O(L)` space. The two stacks use `O(d)` space. The returned string also needs `O(L)` space.


## Python code

```python
class AstronautTransmissionRestorer:
    def __init__(self):
        pass

    def restoreTransmission(self, compressedTransmission: str) -> str:
        restored = []
        counts = []
        starts = []
        count = 0

        for symbol in compressedTransmission:
            if "0" <= symbol <= "9":
                count = count * 10 + int(symbol)
            elif symbol == "[":
                # Remember this section before reading its first copy.
                counts.append(count)
                starts.append(len(restored))
                count = 0
            elif symbol == "]":
                repeat_count = counts.pop()
                start = starts.pop()

                # Keep the source range fixed while the buffer grows.
                end = len(restored)

                # The first copy is already present.
                for _ in range(1, repeat_count):
                    for j in range(start, end):
                        restored.append(restored[j])
            else:
                restored.append(symbol)

        return "".join(restored)
```
