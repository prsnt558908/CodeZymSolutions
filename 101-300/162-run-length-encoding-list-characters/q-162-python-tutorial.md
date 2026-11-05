# Run Length Encoding for List of Characters in Python

#### Problem Statement
[https://codezym.com/question/162-run-length-encoding-list-characters](https://codezym.com/question/162-run-length-encoding-list-characters)

Think in consecutive groups. For encoding, count equal characters until the character changes. For lookup, skip complete groups until we reach the requested position. Both methods scan from left to right, and lookup never needs to build the original list.


## Understand the two formats

`getEncodedList(characters)` returns a list with entries such as `"A,2"`. Only adjacent equal characters belong to the same group.

```text
Input:  ["A", "A", "B", "B", "B", "C", "C", "A"]
Output: ["A,2", "B,3", "C,2", "A,1"]
```

The final `"A"` starts a new group. A map of total character frequencies would lose this information.


`get(compressedString, i)` reads a different format, such as `"A:2|B:3|C:2|A:1"`. Indexes start at zero. Negative indexes and indexes beyond the decoded list return `""`.


Each lookup uses its own input string. It does not depend on any earlier encoding call, so `RunLengthEncoding` needs no stored fields.


## 1. A simple lookup: expand the string

We could read each group and add its character to an `expanded` list once for every occurrence.

```python
for copy in range(count):
    expanded.append(character)
```

Then we could return the element at index `i` after checking that the index is valid.


This works for small counts, but a short input such as `"A:1000000000"` would create a billion entries. If the decoded length is `R`, the expanded list alone needs `O(R)` space and `O(R)` work to build.


## 2. Encode with one current group

Keep `current`, the character in the active group, and `count`, its length. The `encoded` list stores completed groups in their original order.

- When the next character equals `current`, increase `count`.
- Otherwise, save the previous group if it exists, then start a new group.
- After the loop, save the final group.

We start with `count = 0`, so an empty input naturally returns an empty list. Each character is counted once, and each group is saved once.


## 3. Find an index by skipping groups

Let `remaining` start at `i`. It represents the desired index within the part of the decoded list we have not skipped yet.


For a group containing `count` copies:

- If `remaining < count`, the answer is this group's character.
- Otherwise, subtract `count` and move to the next group.

Subtracting a skipped group's length keeps `remaining` relative to the next group. If all groups are skipped, the index is out of range.


For `get("A:2|B:3|C:2|A:1", 6)`:

- Skip two `A` values. `remaining` becomes `4`.
- Skip three `B` values. `remaining` becomes `1`.
- The `C` group has two values. Since `1 < 2`, return `"C"`.


### Read large counts safely

The count has no stated upper bound. The code reads one digit at a time and returns as soon as `count > remaining`. Later digits cannot make the count smaller.


This also avoids converting an extremely long count into one integer. We read directly from the string, so there is no need to create extra lists with `split()`.


For example, with `remaining = 5`, reading the first two digits of a count starting with `12` is enough to identify the answer.


## Complexity

Let `n` be the input list length, `k` the number of encoded groups, and `m` the compressed string length.


`getEncodedList` takes `O(n)` time and produces `O(k)` output entries. It uses `O(1)` extra working space beyond the output.


`get` takes `O(m)` time in the worst case and `O(1)` extra space. It can stop early when it finds the answer. Its work does not depend on the decoded length.


## Complete Python solution

```python
class RunLengthEncoding:
    def __init__(self):
        pass

    def getEncodedList(self, characters):
        encoded = []
        current = ""
        count = 0

        for character in characters:
            if character == current:
                count += 1
            else:
                # Save the previous run before starting a new one.
                if count > 0:
                    encoded.append(f"{current},{count}")
                current = character
                count = 1

        # The last run has no following character to close it.
        if count > 0:
            encoded.append(f"{current},{count}")
        return encoded

    def get(self, compressedString, i):
        if i < 0:
            return ""

        position = 0
        remaining = i

        while position < len(compressedString):
            character = compressedString[position]
            position += 2  # Skip the character and its colon.
            count = 0

            while (position < len(compressedString)
                   and compressedString[position] != "|"):
                count = count * 10 + int(compressedString[position])

                # Further digits cannot make the count smaller.
                if count > remaining:
                    return character
                position += 1

            # This entire run lies before the requested index.
            remaining -= count
            position += 1  # Skip the separator, if present.

        return ""
```
