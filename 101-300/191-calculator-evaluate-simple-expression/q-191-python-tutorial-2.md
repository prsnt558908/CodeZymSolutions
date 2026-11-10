# Calculator To Evaluate Simple Expression String in Python

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

Notice that we divided `-6`, not `6`. That is fine because we truncate toward zero: `int(-7 / 2)` is `-3`, exactly the negative of `int(7 / 2)`.

Be careful here: Python's `//` rounds down, so `-7 // 2` is `-4`. That is why the code divides with `int(x / y)` instead.

**Why a stack?** `*` and `/` only ever need the last value we pushed. A Python list works as a stack: `append()` pushes and `pop()` removes the last value.

In the code, digits build up the current number (`12` is `1 * 10 + 2`), and spaces are simply ignored.

A number is complete only when we reach the next operator, or the end. So when a new operator shows up, we apply the previous operator to the number we just finished reading.

```python
def calculate(self, expression: str) -> int:
    numbers = []
    number = 0
    operator = '+'  # operator written just before 'number'

    for ch in expression:
        if ch.isdigit():
            number = number * 10 + int(ch)
        elif ch in '+-*/':
            self.applyOperator(numbers, operator, number)
            operator = ch
            number = 0

    self.applyOperator(numbers, operator, number)  # the last number
    return sum(numbers)

def applyOperator(self, numbers: list, operator: str, number: int) -> None:
    if operator == '+':
        numbers.append(number)
    elif operator == '-':
        numbers.append(-number)
    elif operator == '*':
        numbers.append(numbers.pop() * number)
    else:
        # int() truncates toward zero, while // would round down
        numbers.append(int(numbers.pop() / number))
```


## Step 2: Add Brackets With Recursion

A bracket is just a smaller expression. So when we see `(`, we can call the same logic to solve the inside, and then use its result like a normal number.

Each bracket gets its own call, with its own stack. We call this a **level**.

`self.index` is shared by all calls. When an inner call stops at `)`, the outer call continues right after it.

`finishLevel()` is the end of Step 1 moved into a helper. It applies the last number and returns the sum of the stack.

```python
def __init__(self):
    self.index = 0  # position in the string, shared by all calls

def calculate(self, expression: str) -> int:
    self.index = 0
    return self.evaluate(expression)

# Reads from 'index' until the matching ')' or the end of the string.
def evaluate(self, expression: str) -> int:
    numbers = []
    number = 0
    operator = '+'

    while self.index < len(expression):
        ch = expression[self.index]
        self.index += 1

        if ch.isdigit():
            number = number * 10 + int(ch)
        elif ch == '(':
            number = self.evaluate(expression)  # solve the bracket first
        elif ch == ')':
            break                               # this bracket is done
        elif ch in '+-*/':
            self.applyOperator(numbers, operator, number)
            operator = ch
            number = 0

    return self.finishLevel(numbers, operator, number)
```

### The Problem

Each `(` starts one more call. By default, Python allows only about 1,000 nested calls, so about 1,000 nested brackets are already enough to crash this code with a `RecursionError`.

The expression can be 300,000 characters long, so it can hold about 150,000 nested brackets, like `((((...1...))))`.


## Final Solution: Our Own Stack Instead of Recursion

A recursive call really does three things. It pauses the current work, solves the inside, and then resumes the paused work with that answer.

We can do the same thing by hand.

- **On `(`:** save the current stack and the current operator. Then start with an empty stack and operator `+`.
- **On `)`:** finish the inside, which gives one number. Then bring back the saved stack and operator, and continue as if we had just read that number.

Our own stacks are just normal lists, and they can grow as big as needed. There is no recursion limit for them, so 150,000 nested brackets are no problem.

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
- **`saved_numbers` and `saved_operators` (stacks):** one entry for each open bracket. The bracket opened last is always closed first, so a stack fits perfectly.

### Code

```python
class Calculator:
    def __init__(self):
        pass

    def calculate(self, expression: str) -> int:
        """
        Evaluates the expression in one left to right pass.
        Brackets are handled with our own stacks instead of recursion,
        so even very deep nesting is safe.
        """
        # Values of the current bracket level, already signed and already
        # multiplied or divided. The value of the level is their sum.
        numbers = []

        # When we enter a '(', the outer level is paused and saved here.
        saved_numbers = []
        saved_operators = []

        number = 0      # the number being read right now
        operator = '+'  # the operator written just before it

        for ch in expression:
            if ch.isdigit():
                number = number * 10 + int(ch)
            elif ch == '(':
                # Pause the current level and start a fresh one.
                saved_numbers.append(numbers)
                saved_operators.append(operator)
                numbers = []
                operator = '+'
            elif ch == ')':
                # The whole bracket becomes one number...
                number = self.finishLevel(numbers, operator, number)
                # ...and the outer level continues as if it just read it.
                numbers = saved_numbers.pop()
                operator = saved_operators.pop()
            elif ch in '+-*/':
                self.applyOperator(numbers, operator, number)
                operator = ch
                number = 0
            # spaces are simply skipped

        return self.finishLevel(numbers, operator, number)

    def applyOperator(self, numbers: list, operator: str, number: int) -> None:
        """
        Uses 'number' with the operator written just before it.
        + and - are kept for later, * and / are done right away.
        """
        if operator == '+':
            numbers.append(number)
        elif operator == '-':
            numbers.append(-number)
        elif operator == '*':
            numbers.append(numbers.pop() * number)
        else:
            # int() truncates toward zero, while // would round down
            numbers.append(int(numbers.pop() / number))

    def finishLevel(self, numbers: list, operator: str, number: int) -> int:
        """
        Uses the last number of a level, then adds up the whole level.
        """
        self.applyOperator(numbers, operator, number)
        return sum(numbers)
```

### Complexity

- **Time:** O(n). Each character is read once, and each value is pushed, popped and added up only a few times.
- **Space:** O(n) for the stacks, for example when brackets are nested very deeply.