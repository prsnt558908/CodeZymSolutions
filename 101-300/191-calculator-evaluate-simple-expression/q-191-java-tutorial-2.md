# Calculator To Evaluate Simple Expression String in Java

#### Problem Statement
[https://codezym.com/question/191-calculator-evaluate-simple-expression](https://codezym.com/question/191-calculator-evaluate-simple-expression)


We read the expression once, from left to right, and keep a stack of numbers.

The trick for priority is simple. `*` and `/` are done right away.

`+` and `-` are kept for later by pushing the number on the stack, with a minus sign for `-`. At the end, adding up the stack gives the answer.

Brackets are just smaller expressions inside the big one. When we see `(`, we pause our current work and start fresh. When we see `)`, the inside becomes one number, and the paused work continues with it.

We will build this in three steps: first without brackets, then brackets with recursion, and finally brackets with our own stack, so that even very deep brackets are safe.


## Step 1: Expressions Without Brackets

Let's first solve something like `8 - 6 / 3`.

Look at each number together with the operator written just before it. For the first number, we pretend that operator is `+`.

- `+`: push the number on the stack.
- `-`: push the negative of the number, because `a - b` is the same as `a + (-b)`.
- `*`: pop the last value, multiply it by the number, push the result.
- `/`: pop the last value, divide it by the number, push the result.

`*` and `/` are done at once, so they win over `+` and `-`, which simply wait on the stack. At the end, we add up everything on the stack.

Here is `8 - 6 / 3`:

| Number | Operator before it | Stack |
|---|---|---|
| 8 | `+` (start) | [8] |
| 6 | `-` | [8, -6] |
| 3 | `/` | [8, -2] |

The answer is 8 + (-2) = 6.

Notice that we divided `-6`, not `6`. That is fine: Java's integer division truncates toward zero, so `-7 / 2` is `-3`, exactly the same as `-(7 / 2)`.

**Why a stack?** `*` and `/` only ever need the last value we pushed, and a stack gives us exactly that.

In the code, digits build up the current number (`12` is `1 * 10 + 2`), and spaces are simply ignored.

A number is complete only when we reach the next operator, or the end. So when a new operator shows up, we apply the previous operator to the number we just finished reading.

```java
public int calculate(String expression) {
    Stack<Long> numbers = new Stack<>();
    long number = 0;
    char operator = '+';  // operator written just before 'number'

    for (int i = 0; i < expression.length(); i++) {
        char ch = expression.charAt(i);

        if (Character.isDigit(ch)) {
            number = number * 10 + (ch - '0');
        } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
            applyOperator(numbers, operator, number);
            operator = ch;
            number = 0;
        }
    }
    applyOperator(numbers, operator, number);  // the last number

    long total = 0;
    for (long value : numbers) {
        total += value;
    }
    return (int) total;
}

void applyOperator(Stack<Long> numbers, char operator, long number) {
    if (operator == '+') {
        numbers.push(number);
    } else if (operator == '-') {
        numbers.push(-number);
    } else if (operator == '*') {
        numbers.push(numbers.pop() * number);
    } else {
        numbers.push(numbers.pop() / number);
    }
}
```


## Step 2: Add Brackets With Recursion

A bracket is just a smaller expression. So when we see `(`, we can call the same logic to solve the inside, and then use its result like a normal number.

Each bracket gets its own call, with its own stack. We call this a **level**.

`index` is shared by all calls. When an inner call stops at `)`, the outer call continues right after it.

`finishLevel()` is the end of Step 1 moved into a helper. It applies the last number and returns the sum of the stack.

```java
int index = 0;  // position in the string, shared by all calls

public int calculate(String expression) {
    index = 0;
    return (int) evaluate(expression);
}

// Reads from 'index' until the matching ')' or the end of the string.
long evaluate(String expression) {
    Stack<Long> numbers = new Stack<>();
    long number = 0;
    char operator = '+';

    while (index < expression.length()) {
        char ch = expression.charAt(index);
        index++;

        if (Character.isDigit(ch)) {
            number = number * 10 + (ch - '0');
        } else if (ch == '(') {
            number = evaluate(expression);  // solve the bracket first
        } else if (ch == ')') {
            break;                          // this bracket is done
        } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
            applyOperator(numbers, operator, number);
            operator = ch;
            number = 0;
        }
    }
    return finishLevel(numbers, operator, number);
}
```

### The Problem

Each `(` starts one more call, and unfinished calls pile up on Java's call stack.

The expression can be 300,000 characters long, so it can hold about 150,000 nested brackets, like `((((...1...))))`.

The call stack is far too small for that. A few thousand nested brackets can already be enough to crash this code with a `StackOverflowError`.


## Final Solution: Our Own Stack Instead of Recursion

A recursive call really does three things. It pauses the current work, solves the inside, and then resumes the paused work with that answer.

We can do the same thing by hand.

- **On `(`:** save the current stack and the current operator. Then start with an empty stack and operator `+`.
- **On `)`:** finish the inside, which gives one number. Then bring back the saved stack and operator, and continue as if we had just read that number.

Our own stacks are stored in normal memory (the heap), which has much more room than the call stack. So 150,000 nested brackets are no problem.

Here is `10 - (2 + 3) * 2`:

| Read | What we do | numbers | Saved |
|---|---|---|---|
| `10 -` | push 10 | [10] | |
| `(` | save [10] and `-`, start empty | [] | [10] and `-` |
| `2 + 3 )` | the inside is [2, 3], so the bracket is 5. Bring back [10] and `-` | [10] | |
| `*` | 5 has `-` before it, push -5 | [10, -5] | |
| `2` | `*` is done at once: -5 * 2 | [10, -10] | |

The answer is 10 + (-10) = 0.

### Why These Data Structures

- **`numbers` (stack):** the values of the current level. `*` and `/` only need its last value, and the value of the level is its sum.
- **`savedNumbers` and `savedOperators` (stacks):** one entry for each open bracket. The bracket opened last is always closed first, so a stack fits perfectly.
- **`long` instead of `int`:** a bracket can be `-2147483648`. If a `-` comes before it, we push `2147483648`, which does not fit in an `int`. A `long` holds it, and the final answer is turned back into an `int`.

### Code

```java
import java.util.*;

public class Calculator {

    public Calculator() {

    }

    /**
     * Evaluates the expression in one left to right pass.
     * Brackets are handled with our own stacks instead of recursion,
     * so even very deep nesting is safe.
     */
    public int calculate(String expression) {
        // Values of the current bracket level, already signed and already
        // multiplied or divided. The value of the level is their sum.
        Stack<Long> numbers = new Stack<>();

        // When we enter a '(', the outer level is paused and saved here.
        Stack<Stack<Long>> savedNumbers = new Stack<>();
        Stack<Character> savedOperators = new Stack<>();

        long number = 0;      // the number being read right now
        char operator = '+';  // the operator written just before it

        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);

            if (Character.isDigit(ch)) {
                number = number * 10 + (ch - '0');
            } else if (ch == '(') {
                // Pause the current level and start a fresh one.
                savedNumbers.push(numbers);
                savedOperators.push(operator);
                numbers = new Stack<>();
                operator = '+';
            } else if (ch == ')') {
                // The whole bracket becomes one number...
                number = finishLevel(numbers, operator, number);
                // ...and the outer level continues as if it just read it.
                numbers = savedNumbers.pop();
                operator = savedOperators.pop();
            } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                applyOperator(numbers, operator, number);
                operator = ch;
                number = 0;
            }
            // spaces are simply skipped
        }

        return (int) finishLevel(numbers, operator, number);
    }

    /**
     * Uses 'number' with the operator written just before it.
     * + and - are kept for later, * and / are done right away.
     */
    void applyOperator(Stack<Long> numbers, char operator, long number) {
        if (operator == '+') {
            numbers.push(number);
        } else if (operator == '-') {
            numbers.push(-number);
        } else if (operator == '*') {
            numbers.push(numbers.pop() * number);
        } else {
            // Java's integer division already truncates toward zero
            numbers.push(numbers.pop() / number);
        }
    }

    /**
     * Uses the last number of a level, then adds up the whole level.
     */
    long finishLevel(Stack<Long> numbers, char operator, long number) {
        applyOperator(numbers, operator, number);

        long total = 0;
        for (long value : numbers) {
            total += value;
        }
        return total;
    }
}
```

### Complexity

- **Time:** O(n). Each character is read once, and each value is pushed, popped and added up only a few times.
- **Space:** O(n) for the stacks, for example when brackets are nested very deeply.