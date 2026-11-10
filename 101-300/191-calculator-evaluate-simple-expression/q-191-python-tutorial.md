# Calculator To Evaluate Simple Expression String in Python

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

**Why a stack?** `*` and `/` always work on the value right before them, and that value is always the last term. A stack gives us the last item instantly. A plain Python list works as a stack: `append` pushes and `pop` removes the last item.

**Careful with division.** It must truncate toward zero, and terms can be negative, like `-7` in `8 - 7 / 2`. Python's `//` rounds down, so `-7 // 2` is `-4`, but we need `-3`. `int(-7 / 2)` gives `-3`, so we use that.

```python
def calculate(self, expression: str) -> int:
    terms = []
    op = '+'   # pretend there is a + before the first number
    num = 0    # the number we are reading right now

    for c in expression:
        if c.isdigit():
            num = num * 10 + int(c)              # a number can have many digits
        elif c != ' ':
            self.applyOperator(terms, op, num)   # finish the number we just read
            op = c
            num = 0

    self.applyOperator(terms, op, num)           # finish the last number
    return self.sumTerms(terms)                  # add up all terms

def applyOperator(self, terms: list, op: str, num: int) -> None:
    """Applies the operator that was waiting before num."""
    if op == '+':
        terms.append(num)
    elif op == '-':
        terms.append(-num)
    elif op == '*':
        terms.append(terms.pop() * num)
    else:
        terms.append(int(terms.pop() / num))     # truncate toward zero
```


## Step 2: Add Parentheses Using Recursion

A pair of parentheses is just a smaller expression. So when we see `(`, we call the same logic to solve the inside. The value it returns becomes our `num`, like any normal number.

When the inner call reaches `)`, its part is done, so it returns.

Each call has its own `terms`, `op` and `num`, so the outer work simply waits while the inner call runs. One shared `self.index` lets the inner call continue from where we are, and lets us continue right after its `)`.

```python
def evaluate(self, expression: str) -> int:
    terms = []
    op = '+'
    num = 0

    while self.index < len(expression):
        c = expression[self.index]
        self.index += 1

        if c.isdigit():
            num = num * 10 + int(c)
        elif c == '(':
            num = self.evaluate(expression)   # solve the inside, use it as a number
        elif c == ')':
            break                             # the inside is finished
        elif c != ' ':
            self.applyOperator(terms, op, num)
            op = c
            num = 0

    self.applyOperator(terms, op, num)
    return self.sumTerms(terms)
```

`calculate` just sets `self.index = 0` and returns `self.evaluate(expression)`.

**The problem: deep nesting.** Every `(` adds one more function call. Python allows only about 1,000 nested calls by default, so `((((...1...))))` with 1,000 pairs already raises `RecursionError`. And the expression can have 300,000 characters, which means about 150,000 levels.

The call stack was only remembering the paused work of the outer calls. We can remember that ourselves with a normal stack.


## Step 3: Final Solution Using Our Own Stack

Each level of parentheses has its own `terms` and its own waiting `op`, just like each recursive call had. We keep the current level in normal variables, and all paused outer levels in two stacks: `paused_terms` and `paused_ops`.

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


## Complexity

- **Time:** `O(n)`. Each character is read once. Each number causes at most one push and one pop, and each term is added up only once.
- **Space:** `O(n)` for the terms and the paused levels.


## Python Code

```python
class Calculator:

    def __init__(self):
        pass

    def calculate(self, expression: str) -> int:
        """
        Reads the expression once, from left to right.
        "(" pauses the current level and starts a fresh one.
        ")" finishes the inner level and resumes the paused one.
        """
        terms = []   # terms of the current level, added up at the end
        op = '+'     # operator waiting just before the current number
        num = 0      # the number we are reading right now

        # paused outer levels. Both lists always grow and shrink together
        paused_terms = []
        paused_ops = []

        for c in expression:
            if c.isdigit():
                num = num * 10 + int(c)
            elif c == '(':
                # pause the current level and start a fresh one
                paused_terms.append(terms)
                paused_ops.append(op)
                terms = []
                op = '+'
                num = 0
            elif c == ')':
                # finish the inner level, its value becomes a plain number
                self.applyOperator(terms, op, num)
                num = self.sumTerms(terms)
                # resume the paused outer level
                terms = paused_terms.pop()
                op = paused_ops.pop()
            elif c != ' ':
                # c is one of + - * /
                self.applyOperator(terms, op, num)
                op = c
                num = 0

        self.applyOperator(terms, op, num)
        return self.sumTerms(terms)

    def applyOperator(self, terms: list, op: str, num: int) -> None:
        """
        Applies the operator that was waiting before num.
        + and - start a new term. * and / change the last term right away.
        """
        if op == '+':
            terms.append(num)
        elif op == '-':
            terms.append(-num)
        elif op == '*':
            terms.append(terms.pop() * num)
        else:
            # int() truncates toward zero, // would round down instead
            terms.append(int(terms.pop() / num))

    def sumTerms(self, terms: list) -> int:
        """Adds up all terms of one level."""
        return sum(terms)
```