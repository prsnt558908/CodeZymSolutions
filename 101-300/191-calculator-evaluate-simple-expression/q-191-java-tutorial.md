# Calculator To Evaluate Simple Expression String in Java

#### Problem Statement
[https://codezym.com/question/191-calculator-evaluate-simple-expression](https://codezym.com/question/191-calculator-evaluate-simple-expression)


## Core Idea

We read the expression only once, from left to right. The main trick is to see the expression as a list of **terms** that we simply add up at the end. For example, `2 + 3 * 4 - 5` is the same as `2 + 12 + (-5)`.

A `+` or `-` starts a new term. A `*` or `/` changes the last term right away. That is how `*` and `/` get done before `+` and `-`.

Parentheses are just a smaller expression inside the bigger one. When we see `(`, we pause our current work, solve the inside, and then use its value like a normal number.

We first build this pausing with recursion. Then we replace recursion with our own stack, so even very deep nesting is safe. The final solution runs in `O(n)` time.


## Step 1: Simple Version Without Parentheses

Let's forget parentheses for a moment.

Every number has an operator just before it. The very first number has none, so we pretend it is `+`.

We keep a stack of terms and handle each number like this:

| Operator before the number | What we do |
| --- | --- |
| `+` | push `num` |
| `-` | push `-num` |
| `*` | pop the last term, push `last * num` |
| `/` | pop the last term, push `last / num` |

At the end, we add up all the terms.

Here is `2 + 3 * 4 - 5`:

```
read 2, then +    terms = [2]
read 3, then *    terms = [2, 3]
read 4, then -    terms = [2, 12]        3 * 4 replaced the last term
read 5, end       terms = [2, 12, -5]

answer = 2 + 12 + (-5) = 9
```

An operator is applied only when the number after it is complete. A number is complete when we reach the next operator or the end of the string. Spaces are simply skipped.

**Why a stack?** `*` and `/` always work on the value right before them, and that value is always the last term. A stack gives us the last item instantly.

Java's integer division already truncates toward zero, so `-7 / 2` is `-3`, exactly what the problem wants. This matters because terms can be negative, like `-7` in `8 - 7 / 2`.

```java
public int calculate(String expression) {
    Stack<Integer> terms = new Stack<>();
    char op = '+';   // pretend there is a + before the first number
    int num = 0;     // the number we are reading right now

    for (char c : expression.toCharArray()) {
        if (Character.isDigit(c)) {
            num = num * 10 + (c - '0');      // a number can have many digits
        } else if (c != ' ') {
            applyOperator(terms, op, num);   // finish the number we just read
            op = c;
            num = 0;
        }
    }
    applyOperator(terms, op, num);           // finish the last number
    return sumTerms(terms);                  // add up all terms
}

// Applies the operator that was waiting before num
void applyOperator(Stack<Integer> terms, char op, int num) {
    if (op == '+') {
        terms.push(num);
    } else if (op == '-') {
        terms.push(-num);
    } else if (op == '*') {
        terms.push(terms.pop() * num);
    } else {
        terms.push(terms.pop() / num);
    }
}
```


## Step 2: Add Parentheses Using Recursion

A pair of parentheses is just a smaller expression. So when we see `(`, we call the same logic to solve the inside. The value it returns becomes our `num`, like any normal number.

When the inner call reaches `)`, its part is done, so it returns.

Each call has its own `terms`, `op` and `num`, so the outer work simply waits while the inner call runs. One shared `index` lets the inner call continue from where we are, and lets us continue right after its `)`.

```java
int index = 0;   // shared position in the expression

int evaluate(String expression) {
    Stack<Integer> terms = new Stack<>();
    char op = '+';
    int num = 0;

    while (index < expression.length()) {
        char c = expression.charAt(index);
        index++;

        if (Character.isDigit(c)) {
            num = num * 10 + (c - '0');
        } else if (c == '(') {
            num = evaluate(expression);   // solve the inside, use it as a number
        } else if (c == ')') {
            break;                        // the inside is finished
        } else if (c != ' ') {
            applyOperator(terms, op, num);
            op = c;
            num = 0;
        }
    }
    applyOperator(terms, op, num);
    return sumTerms(terms);
}
```

`calculate` just sets `index = 0` and returns `evaluate(expression)`.

**The problem: deep nesting.** Every `(` adds one more function call. The expression can have 300,000 characters, so an input like `((((...1...))))` can be about 150,000 calls deep. That is too deep for the call stack, and Java throws `StackOverflowError`.

The call stack was only remembering the paused work of the outer calls. We can remember that ourselves with a normal stack.


## Step 3: Final Solution Using Our Own Stack

Each level of parentheses has its own `terms` and its own waiting `op`, just like each recursive call had. We keep the current level in normal variables, and all paused outer levels in two stacks: `pausedTerms` and `pausedOps`.

**When we see `(`**

- Push the current `terms` and `op` onto the paused stacks.
- Start a fresh level: empty `terms`, `op = '+'` and `num = 0`.

We don't need to save `num`, because it is always `0` right before a `(`.

**When we see `)`**

- Finish the inner level: apply the waiting `op` to `num`, then add up its terms. This sum is the value of the parentheses.
- Pop the outer `terms` and `op` back.
- Set `num` to the inner value. Now the outer level sees the whole `( ... )` as one plain number.

Digits, spaces and `+ - * /` work exactly like Step 1.

**Why a stack for paused levels?** Parentheses close in reverse order. The most recent `(` is always the first one to close. That is last in, first out, which is exactly how a stack works.

Here is `4*(2+3*3)/2+(7-5)` step by step:

| Read | What happens | terms | op |
| --- | --- | --- | --- |
| `4*` | 4 becomes a term | `[4]` | `*` |
| `(` | pause `[4]` and `*`, start fresh | `[]` | `+` |
| `2+3*3` | 2 and 3 become terms, the last 3 waits for `*` | `[2, 3]` | `*` |
| `)` | inside: `3*3 = 9`, value `2+9 = 11`. Resume, `num = 11` | `[4]` | `*` |
| `/` | `4*11 = 44` | `[44]` | `/` |
| `2+` | `44/2 = 22` | `[22]` | `+` |
| `(` | pause `[22]` and `+`, start fresh | `[]` | `+` |
| `7-5` | 7 becomes a term, 5 waits for `-` | `[7]` | `-` |
| `)` | inside: value `7-5 = 2`. Resume, `num = 2` | `[22]` | `+` |
| end | push 2, answer `22 + 2 = 24` | `[22, 2]` | |

**One small change: `long` instead of `int`.** A term carries its sign. In `0-(0-2147483647-1)/2`, the part in parentheses is `-2147483648`, which fits in `int`. But the `-` before it makes us push `2147483648`, which does not fit, so the answer would come out wrong.

With `long` every step stays exact. The final answer always fits in `int`, so we cast it back at the end.


## Complexity

- **Time:** `O(n)`. Each character is read once. Each number causes at most one push and one pop, and each term is added up only once.
- **Space:** `O(n)` for the terms and the paused levels.


## Java Code

```java
import java.util.*;

public class Calculator {

    public Calculator() {

    }

    /**
     * Reads the expression once, from left to right.
     * "(" pauses the current level and starts a fresh one.
     * ")" finishes the inner level and resumes the paused one.
     */
    public int calculate(String expression) {
        // terms of the current level, added up at the end
        Stack<Long> terms = new Stack<>();
        // operator waiting just before the current number
        char op = '+';
        // the number we are reading right now
        long num = 0;

        // paused outer levels. Both stacks always grow and shrink together
        Stack<Stack<Long>> pausedTerms = new Stack<>();
        Stack<Character> pausedOps = new Stack<>();

        for (char c : expression.toCharArray()) {
            if (Character.isDigit(c)) {
                num = num * 10 + (c - '0');
            } else if (c == '(') {
                // pause the current level and start a fresh one
                pausedTerms.push(terms);
                pausedOps.push(op);
                terms = new Stack<>();
                op = '+';
                num = 0;
            } else if (c == ')') {
                // finish the inner level, its value becomes a plain number
                applyOperator(terms, op, num);
                num = sumTerms(terms);
                // resume the paused outer level
                terms = pausedTerms.pop();
                op = pausedOps.pop();
            } else if (c != ' ') {
                // c is one of + - * /
                applyOperator(terms, op, num);
                op = c;
                num = 0;
            }
        }

        applyOperator(terms, op, num);
        return (int) sumTerms(terms);
    }

    /**
     * Applies the operator that was waiting before num.
     * + and - start a new term. * and / change the last term right away.
     */
    void applyOperator(Stack<Long> terms, char op, long num) {
        if (op == '+') {
            terms.push(num);
        } else if (op == '-') {
            terms.push(-num);
        } else if (op == '*') {
            terms.push(terms.pop() * num);
        } else {
            // Java integer division truncates toward zero, as required
            terms.push(terms.pop() / num);
        }
    }

    /** Adds up all terms of one level. */
    long sumTerms(Stack<Long> terms) {
        long total = 0;
        for (long term : terms) {
            total += term;
        }
        return total;
    }
}
```