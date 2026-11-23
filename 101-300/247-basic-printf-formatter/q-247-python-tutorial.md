# Implement Basic Printf Formatter in Python

#### Problem Statement
[https://codezym.com/question/247-basic-printf-formatter](https://codezym.com/question/247-basic-printf-formatter)

We read the template once from left to right and collect the pieces of the answer in a list. Normal characters are copied as they are. When we meet `%s`, `%d` or `%f`, we take the next value, format it, and add it to the answer.


The only thing that changes from one placeholder to another is **how the value is formatted**. That makes the **Strategy pattern** a good fit: every placeholder letter gets its own small formatter class, and a dictionary finds the right formatter from the letter.


To support a new placeholder like `%x` later, we add one class and one dictionary entry. The scanning loop does not change.


We will start with a simple "replace one by one" version, see why it is slow and can even give wrong output, and then fix it in two steps.

---

## Understanding the Problem

Each placeholder uses the next value from `values`:

- `%s`: use the value exactly as given.
- `%d`: parse it as an integer and print that number. So `"+7"` and `"007"` both become `7`.
- `%f`: parse it as a decimal number, round it to 6 decimal places, and print exactly 6 digits after the `.`.


For `%f`, the statement gives the exact rule: `rounded = round(x * 1,000,000) / 1,000,000`.

| Value | x * 1,000,000 | After rounding | Printed |
|:---|---:|---:|:---|
| `"42.5"` | 42500000 | 42500000 | `42.500000` |
| `"3.14159265"` | 3141592.65 | 3141593 | `3.141593` |
| `"-2"` | -2000000 | -2000000 | `-2.000000` |

---

## Approach 1: Replace Placeholders One by One

The most direct idea is to find the first placeholder, replace it with the formatted value, and repeat this for every value.

```python
def format(self, template: str, values: List[str]) -> str:
    result = template
    for value in values:
        pos = result.index('%')        # first placeholder still left
        letter = result[pos + 1]       # 's', 'd' or 'f'
        text = self.formatValue(letter, value)
        # build a new string with this placeholder replaced
        result = result[:pos] + text + result[pos + 2:]
    return result
```

`formatValue` applies the rule for `s`, `d` or `f`. We will write it in the next approach.


This works for the examples, but it has two problems.


**Problem 1: wrong output.** `index` searches from the start every time, so it can find a `%` inside a value we already inserted. Take template `"%s and %d"` with values `["50%d off", "7"]`:

- After step 1: `"50%d off and %d"`
- Step 2 finds the `%d` inside `"50%d off"` and gives `"507 off and %d"`

The correct answer is `"50%d off and 7"`.


**Problem 2: slow.** Every replacement builds a brand new string and copies all of its characters. With 10,000 placeholders and a result of up to 1,000,000 characters, this can mean billions of character copies.


Both problems have the same cause: we keep going back over text we have already handled.

---

## Approach 2: One Pass With a List

Instead of editing the template again and again, we read it **only once** and collect the answer in a list of pieces:

- Normal character: copy it.
- `%` followed by `s`, `d` or `f`: format the next value, add it, and jump 2 characters ahead.


We only ever read the template, never the text we wrote. So a `%` inside a value can never be mistaken for a placeholder.


**Why a list and `join`?** Python strings cannot be changed, so `result += text` may copy the whole string every time. Adding to a list is cheap, and `"".join(parts)` builds the final string just once.

```python
def format(self, template: str, values: List[str]) -> str:
    parts = []
    value_index = 0
    i = 0
    n = len(template)
    while i < n:
        ch = template[i]
        # '%' followed by s, d or f starts a placeholder
        if ch == '%' and i + 1 < n and template[i + 1] in "sdf":
            parts.append(self.formatValue(template[i + 1], values[value_index]))
            value_index += 1
            i += 2  # skip both '%' and the letter
        else:
            parts.append(ch)
            i += 1
    return "".join(parts)

def formatValue(self, letter: str, value: str) -> str:
    if letter == 's':
        return value
    elif letter == 'd':
        return str(int(value))
    else:
        rounded = math.floor(float(value) * 1_000_000 + 0.5) / 1_000_000
        return f"{rounded:.6f}"
```

This is correct and runs in linear time.


**Problem:** all formatting rules live in one `if / elif` chain. Every new placeholder (`%x` for hex, `%c` for a character, and so on) means editing this method again. The scanning code and the formatting rules are also mixed together in one class.

---

## Approach 3: Strategy Pattern (Final Solution)

Look at what changes from one placeholder to another: only the **formatting rule**. The scanning stays the same. This is exactly what the Strategy pattern is for: each rule goes into its own class, behind one common base class.

- `ValueFormatter` is the base class. It has one method, `format(value)`.
- `StringFormatter`, `IntegerFormatter` and `FloatFormatter` each hold one rule.
- The dictionary `self.formatters` connects each letter to its formatter.


**Why a dictionary?** When we see `%f`, `self.formatters['f']` returns the right formatter in `O(1)`, without any `if / elif`. The dictionary also knows which letters are placeholders, so `in self.formatters` replaces the old `in "sdf"` check.


The loop never needs to know which rule it is using. Adding `%x` now takes one new class and one line in `__init__`. Each rule can also be tested on its own.

### Why Not Other Patterns?

- **Chain of Responsibility:** each handler would check "is this letter mine?" and pass it on if not. Here the letter already tells us the right handler, so a dictionary lookup is simpler and faster.
- **Interpreter:** it treats the template as a small language and builds a class for each part of its syntax. That pays off for complex formats like `%-10.3f`. Our placeholders are only two characters long, so it would only add extra classes.

### Formatting %f

```python
x = float(value)
rounded = math.floor(x * 1_000_000 + 0.5) / 1_000_000
return f"{rounded:.6f}"
```

- We do not use Python's `round()` here. It sends an exact half to the nearest **even** number, so `round(2.5)` is `2`.
- Exact halves really happen. For `"0.0000025"`, `x * 1_000_000` is exactly `2.5`.
- `math.floor(v + 0.5)` always rounds a half up. It turns `2.5` into `3`, so we print `0.000003`.
- `:.6f` always prints 6 digits after the point, so `42.5` becomes `42.500000`. It also always uses `.` as the separator.

### Dry Run

Template `"Price: %f and item: %s"` with values `["42.5", "book"]`:

| Template part | Action | Result so far |
|:---|:---|:---|
| `"Price: "` | copy each character | `"Price: "` |
| `"%f"` | `FloatFormatter` turns `"42.5"` into `"42.500000"` | `"Price: 42.500000"` |
| `" and item: "` | copy each character | `"Price: 42.500000 and item: "` |
| `"%s"` | `StringFormatter` returns `"book"` | `"Price: 42.500000 and item: book"` |

### Python Code

```python
import math
from typing import List


class ValueFormatter:
    """Strategy: one small rule for turning a raw value into text."""

    def format(self, value: str) -> str:
        raise NotImplementedError


class StringFormatter(ValueFormatter):
    # %s : the value is used exactly as given
    def format(self, value: str) -> str:
        return value


class IntegerFormatter(ValueFormatter):
    # %d : parse as an integer, so "+7" or "007" is printed as "7"
    def format(self, value: str) -> str:
        return str(int(value))


class FloatFormatter(ValueFormatter):
    # %f : round to 6 decimal places and always print 6 digits after '.'
    def format(self, value: str) -> str:
        x = float(value)
        # floor(v + 0.5) always rounds a half up. Python's round() would
        # send an exact half to the even number instead (round(2.5) is 2).
        rounded = math.floor(x * 1_000_000 + 0.5) / 1_000_000
        return f"{rounded:.6f}"


class BasicPrintfFormatter:

    def __init__(self):
        # placeholder letter -> the formatter that handles it
        self.formatters = {
            's': StringFormatter(),
            'd': IntegerFormatter(),
            'f': FloatFormatter(),
        }

    def format(self, template: str, values: List[str]) -> str:
        """
        Reads the template once from left to right.
        Normal characters are copied as they are.
        Each placeholder is replaced by the next value, formatted by its strategy.
        Time: O(T + V), T = template length, V = total length of values.
        """
        parts = []  # pieces of the answer, joined once at the end
        value_index = 0
        i = 0
        n = len(template)

        while i < n:
            ch = template[i]

            # a placeholder is '%' followed by a letter we have a formatter for
            if ch == '%' and i + 1 < n and template[i + 1] in self.formatters:
                formatter = self.formatters[template[i + 1]]
                parts.append(formatter.format(values[value_index]))
                value_index += 1
                i += 2  # skip both '%' and the letter
            else:
                parts.append(ch)  # normal character, copy it as it is
                i += 1

        return "".join(parts)
```

### Complexity

Let `T` be the template length and `V` the total length of all values.

- **Time: `O(T + V)`.** Each template character is read once, and each value is parsed and copied once.
- **Space: `O(T + V)`** for the result. The dictionary holds only 3 formatters.