# Implement Basic Printf Formatter in Java

#### Problem Statement
[https://codezym.com/question/247-basic-printf-formatter](https://codezym.com/question/247-basic-printf-formatter)

We read the template once from left to right and build the answer in a `StringBuilder`. Normal characters are copied as they are. When we meet `%s`, `%d` or `%f`, we take the next value, format it, and add it to the answer.


The only thing that changes from one placeholder to another is **how the value is formatted**. That makes the **Strategy pattern** a good fit: every placeholder letter gets its own small formatter class, and a `Map` finds the right formatter from the letter.


To support a new placeholder like `%x` later, we add one class and one map entry. The scanning loop does not change.


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

```java
public String format(String template, List<String> values) {
    String result = template;
    for (String value : values) {
        int pos = result.indexOf('%');         // first placeholder still left
        char letter = result.charAt(pos + 1);  // 's', 'd' or 'f'
        String text = formatValue(letter, value);
        // build a new string with this placeholder replaced
        result = result.substring(0, pos) + text + result.substring(pos + 2);
    }
    return result;
}
```

`formatValue` applies the rule for `s`, `d` or `f`. We will write it in the next approach.


This works for the examples, but it has two problems.


**Problem 1: wrong output.** `indexOf` searches from the start every time, so it can find a `%` inside a value we already inserted. Take template `"%s and %d"` with values `["50%d off", "7"]`:

- After step 1: `"50%d off and %d"`
- Step 2 finds the `%d` inside `"50%d off"` and gives `"507 off and %d"`

The correct answer is `"50%d off and 7"`.


**Problem 2: slow.** Every replacement builds a brand new string and copies all of its characters. With 10,000 placeholders and a result of up to 1,000,000 characters, this can mean billions of character copies.


Both problems have the same cause: we keep going back over text we have already handled.

---

## Approach 2: One Pass With a StringBuilder

Instead of editing the template again and again, we read it **only once** and write the answer into a new `StringBuilder`:

- Normal character: copy it.
- `%` followed by `s`, `d` or `f`: format the next value, append it, and jump 2 characters ahead.


We only ever read the template, never the text we wrote. So a `%` inside a value can never be mistaken for a placeholder.


**Why a `StringBuilder`?** Java strings cannot be changed, so `result + text` creates a full new copy every time. A `StringBuilder` grows in place, so each append only costs the length of the text we add.

```java
public String format(String template, List<String> values) {
    StringBuilder result = new StringBuilder();
    int valueIndex = 0;
    int i = 0;
    while (i < template.length()) {
        char ch = template.charAt(i);
        // '%' followed by s, d or f starts a placeholder
        if (ch == '%' && i + 1 < template.length()
                && "sdf".indexOf(template.charAt(i + 1)) >= 0) {
            result.append(formatValue(template.charAt(i + 1), values.get(valueIndex)));
            valueIndex++;
            i += 2; // skip both '%' and the letter
        } else {
            result.append(ch);
            i++;
        }
    }
    return result.toString();
}

String formatValue(char letter, String value) {
    if (letter == 's') {
        return value;
    } else if (letter == 'd') {
        return String.valueOf(Integer.parseInt(value));
    } else {
        double rounded = Math.round(Double.parseDouble(value) * 1_000_000) / 1_000_000.0;
        return String.format(Locale.US, "%.6f", rounded);
    }
}
```

This is correct and runs in linear time.


**Problem:** all formatting rules live in one `if / else` chain. Every new placeholder (`%x` for hex, `%c` for a character, and so on) means editing this method again. The scanning code and the formatting rules are also mixed together in one class.

---

## Approach 3: Strategy Pattern (Final Solution)

Look at what changes from one placeholder to another: only the **formatting rule**. The scanning stays the same. This is exactly what the Strategy pattern is for: each rule goes into its own class, behind one common interface.

- `ValueFormatter` is the interface. It has one method, `format(value)`.
- `StringFormatter`, `IntegerFormatter` and `FloatFormatter` each hold one rule.
- A `Map<Character, ValueFormatter>` connects each letter to its formatter.


**Why a `Map`?** When we see `%f`, `formatters.get('f')` returns the right formatter in `O(1)`, without any `if / else`. The map also knows which letters are placeholders, so `containsKey` replaces the old `"sdf"` check.


The loop never needs to know which rule it is using. Adding `%x` now takes one new class and one line in the constructor. Each rule can also be tested on its own.

### Why Not Other Patterns?

- **Chain of Responsibility:** each handler would check "is this letter mine?" and pass it on if not. Here the letter already tells us the right handler, so a map lookup is simpler and faster.
- **Interpreter:** it treats the template as a small language and builds a class for each part of its syntax. That pays off for complex formats like `%-10.3f`. Our placeholders are only two characters long, so it would only add extra classes.

### Formatting %f

```java
double x = Double.parseDouble(value);
double rounded = Math.round(x * 1_000_000) / 1_000_000.0;
return String.format(Locale.US, "%.6f", rounded);
```

- `Math.round` rounds to the nearest whole number. The largest possible value, 10^15, fits easily in a `long`.
- We divide by `1_000_000.0`, not `1_000_000`. Dividing a `long` by an `int` is integer division and would drop the decimals.
- `%.6f` always prints 6 digits after the point, so `42.5` becomes `42.500000`.
- `Locale.US` keeps the separator as `.`. On a machine with a German locale, `%.6f` alone would print `42,500000`.

### Dry Run

Template `"Price: %f and item: %s"` with values `["42.5", "book"]`:

| Template part | Action | Result so far |
|:---|:---|:---|
| `"Price: "` | copy each character | `"Price: "` |
| `"%f"` | `FloatFormatter` turns `"42.5"` into `"42.500000"` | `"Price: 42.500000"` |
| `" and item: "` | copy each character | `"Price: 42.500000 and item: "` |
| `"%s"` | `StringFormatter` returns `"book"` | `"Price: 42.500000 and item: book"` |

### Java Code

```java
import java.util.*;

/**
 * Strategy: one small rule for turning a raw value into text.
 * Each placeholder letter (s, d, f) gets its own implementation.
 */
interface ValueFormatter {
    String format(String value);
}

// %s : the value is used exactly as given
class StringFormatter implements ValueFormatter {
    @Override
    public String format(String value) {
        return value;
    }
}

// %d : parse as an integer, so "+7" or "007" is printed as "7"
class IntegerFormatter implements ValueFormatter {
    @Override
    public String format(String value) {
        return String.valueOf(Integer.parseInt(value));
    }
}

// %f : round to 6 decimal places and always print 6 digits after '.'
class FloatFormatter implements ValueFormatter {
    @Override
    public String format(String value) {
        double x = Double.parseDouble(value);
        // divide by 1_000_000.0 (not 1_000_000) so it is not integer division
        double rounded = Math.round(x * 1_000_000) / 1_000_000.0;
        // Locale.US keeps the decimal separator as '.' on every machine
        return String.format(Locale.US, "%.6f", rounded);
    }
}

public class BasicPrintfFormatter {

    // placeholder letter -> the formatter that handles it
    Map<Character, ValueFormatter> formatters = new HashMap<>();

    public BasicPrintfFormatter() {
        formatters.put('s', new StringFormatter());
        formatters.put('d', new IntegerFormatter());
        formatters.put('f', new FloatFormatter());
    }

    /**
     * Reads the template once from left to right.
     * Normal characters are copied as they are.
     * Each placeholder is replaced by the next value, formatted by its strategy.
     * Time: O(T + V), T = template length, V = total length of values.
     */
    public String format(String template, List<String> values) {
        StringBuilder result = new StringBuilder();
        int valueIndex = 0;
        int i = 0;

        while (i < template.length()) {
            char ch = template.charAt(i);

            // a placeholder is '%' followed by a letter we have a formatter for
            if (ch == '%' && i + 1 < template.length()
                    && formatters.containsKey(template.charAt(i + 1))) {
                ValueFormatter formatter = formatters.get(template.charAt(i + 1));
                result.append(formatter.format(values.get(valueIndex)));
                valueIndex++;
                i += 2; // skip both '%' and the letter
            } else {
                result.append(ch); // normal character, copy it as it is
                i++;
            }
        }
        return result.toString();
    }
}
```

### Complexity

Let `T` be the template length and `V` the total length of all values.

- **Time: `O(T + V)`.** Each template character is read once, and each value is parsed and copied once.
- **Space: `O(T + V)`** for the result. The map holds only 3 formatters.