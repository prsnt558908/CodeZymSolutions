# Basic Calculator for Addition and Subtraction in Java

#### Problem Statement

[https://codezym.com/question/435-calculator-for-addition-subtraction](https://codezym.com/question/435-calculator-for-addition-subtraction)

Read the expression once and keep a running total. When a parenthesized group starts, save the outer total and its sign on a stack. When the group ends, combine its value with the saved total. `BasicCalculator` provides the required method, and `ExpressionEvaluator` handles reading and evaluating the expression.


## 1. Start with an expression without parentheses

For `20 - 6 + 3`, read each complete number and add it with the correct sign.

- After `+`, use `sign = 1`.
- After `-`, use `sign = -1`.

The main step is simply:

```java
total += sign * number;
```

This evaluates addition and subtraction from left to right. Read all consecutive digits together, so `20` becomes one number. Skip spaces.

However, `20 - (6 - 2) + 3` needs more information. The minus before the opening parenthesis applies to the whole group, while the operators inside the group follow their own order.


## 2. Save the outer calculation on a stack

A stack keeps the most recently saved item at the top. That matches parentheses because the last group opened is the first group closed.

Use a list as a stack. Each entry stores two values, the outer `total` and the `sign` before the opening parenthesis.

On `(`:

1. Save the current total and sign.
2. Reset `total` to `0` and `sign` to `1` to start the inner calculation.

On `)`:

1. Remove the most recent saved pair.
2. Combine it with the completed inner total:

```java
total = saved[0] + saved[1] * total;
```

For `20 - (6 - 2) + 3`, save `(20, -1)`, calculate the inner value `4`, then restore `20 + (-1) * 4 = 16`. Finally, add `3` to get `19`.

This avoids repeatedly finding groups and rebuilding strings, which can take `O(n²)` time with deep nesting. It also avoids recursive calls, so deeply nested input does not exhaust the call stack.


## 3. Handle negative values and repeated calls

Unary minus needs no separate branch. At the start, `total` is `0`. Reading `-8` sets the sign to `-1`, then adds `-8`.

For `-(8 - 3)`, the saved sign is `-1`. When the group closes, its value `5` becomes `-5`.

`BasicCalculator` stores no changing state. It creates an `ExpressionEvaluator` for each call. The evaluator keeps its position, total, sign, and stack inside `evaluate`, so previous calls cannot affect the next result.

Use `long` for number building and arithmetic inside the evaluator. Return an `int`, as required, because the problem guarantees that the final result fits.


## 4. Why it works and complexity

At each parenthesis level, `total` contains the values already processed at that level. Every new number is added with the correct sign. A completed group is combined in exactly the same way as one number.

The stack restores the correct outer calculation for every closing parenthesis. Therefore, after the scan finishes, the total is the value of the entire expression.

For an expression of length `n` and maximum nesting depth `d`:

- **Time:** `O(n)`. Each character is processed a constant number of times.
- **Extra space:** `O(d + 1)`, which is `O(n)` in the worst case. Only unfinished outer calculations are saved.


## 5. Complete code

```java
import java.util.ArrayList;
import java.util.List;

public class BasicCalculator {
    public BasicCalculator() {
    }

    /** Evaluates one valid expression with independent state. */
    public int calculate(String expression) {
        return new ExpressionEvaluator(expression).evaluate();
    }
}

class ExpressionEvaluator {
    String expression;

    ExpressionEvaluator(String expression) {
        this.expression = expression;
    }

    int evaluate() {
        // Each entry stores an outer total and its pending sign.
        List<long[]> stack = new ArrayList<>();
        long total = 0;
        int sign = 1;
        int index = 0;

        while (index < expression.length()) {
            char character = expression.charAt(index);

            if (character >= '0' && character <= '9') {
                long number = 0;

                // Read the complete number before adding it.
                while (index < expression.length()) {
                    char digit = expression.charAt(index);
                    if (digit < '0' || digit > '9') {
                        break;
                    }
                    number = number * 10 + (digit - '0');
                    index++;
                }

                total += sign * number;
                continue;
            }

            if (character == '+') {
                sign = 1;
            } else if (character == '-') {
                sign = -1;
            } else if (character == '(') {
                // Pause the outer calculation and start the inner one.
                stack.add(new long[] {total, sign});
                total = 0;
                sign = 1;
            } else if (character == ')') {
                // Use the completed group as one signed value.
                long[] saved = stack.remove(stack.size() - 1);
                total = saved[0] + saved[1] * total;
                sign = 1;
            }

            // Spaces require no action.
            index++;
        }

        return (int) total;
    }
}
```
