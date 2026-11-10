# Find Celebrity At Party in Python

#### Problem Statement
[https://codezym.com/question/205-find-celebrity-at-party](https://codezym.com/question/205-find-celebrity-at-party)

A celebrity is a person whom everyone knows, but who knows nobody. The core trick is that a single check, "does person `a` know person `b`?", always rules out one of the two people.

So we walk through the party once and rule out one person per check, until only one possible celebrity is left. Then we walk once more to confirm that this last person really follows both rules.

We will start with a simple brute force that tests every person, see where it wastes work, and then improve it to this two pass solution.

## The Rules

Person `c` is the celebrity if, for every other person `i`:

1. `i` knows `c`.
2. `c` does not know `i`.

The value at row `c`, column `c` (a person knowing themselves) is ignored.

## Reading the Input

Row `a` of `graph` is a string like `"1,0,1"`. A `1` at column `b` means person `a` knows person `b`.

Splitting strings again and again would be slow and messy. So we split every row once and store the answers in a 2D list of booleans called `knows_matrix`.

A boolean matrix is the simplest fit here: `True` means "knows", `False` means "does not know", and any lookup takes O(1) time.

Then a tiny helper answers any "does `a` know `b`?" question:

```python
# Returns True if person a knows person b
def knows(self, a: int, b: int) -> bool:
    return self.knows_matrix[a][b]
```

From here on, the code only talks to `knows(a, b)`, so it reads just like the rules above.

## Approach 1: Brute Force

The most direct idea is to try every person as the celebrity and check both rules against everyone else.

```python
for c in range(n):
    is_celebrity = True
    for i in range(n):
        if i == c:
            continue
        # c must not know i, and i must know c
        if self.knows(c, i) or not self.knows(i, c):
            is_celebrity = False
            break
    if is_celebrity:
        return c
return -1
```

### Problem with this approach

It needs about `n²` calls to `knows` in the worst case, because it can end up checking almost every pair of people.

Each check also tells us more than brute force uses, as we will see next.

In the classic version of this puzzle, you can only learn about the party by asking "does A know B?", and the goal is to ask as few questions as possible. So we want far fewer than `n²` checks.

## Approach 2: Eliminate, Then Verify (Optimal)

### Every check rules out one person

Pick any two people `a` and `b` and call `knows(a, b)`:

- **`a` knows `b`**: `a` knows someone, so `a` is not the celebrity.
- **`a` does not know `b`**: not everyone knows `b`, so `b` is not the celebrity.

Either way, one of them is out, and we never need to look at that person again.

### Pass 1: Find the only possible celebrity

Keep a single `candidate`, starting with person `0`. Then compare the candidate with each next person `i`:

- If the candidate knows `i`, the candidate is out, and `i` becomes the new candidate.
- Otherwise, `i` is out, and the candidate stays.

After `n - 1` checks, everyone except the candidate has been ruled out.

At any point, only one of the people we have looked at so far is still in the game. So a single integer variable is enough, and we don't need a list or a stack.

### Pass 2: Verify the candidate

Pass 1 only proves that nobody else can be the celebrity. It does not prove that the candidate is one.

For example, in `["1,1,0", "0,1,1", "1,0,1"]` the last candidate is person `2`, but person `2` knows person `0`.

So we check both rules for the candidate against every other person. If any check fails, we return `-1`.

### Dry Run

Example 1: `graph = ["1,0,1,0", "1,1,1,0", "0,0,1,0", "1,0,1,1"]`

**Pass 1**

| i | Check | Answer | Who is out | Candidate |
|---|---|---|---|---|
| 1 | `knows(0, 1)` | No | 1 | 0 |
| 2 | `knows(0, 2)` | Yes | 0 | 2 |
| 3 | `knows(2, 3)` | No | 3 | 2 |

**Pass 2**

Person `2` knows none of `0`, `1` and `3`, and all three of them know person `2`. So the answer is `2`.

### Complexity

- **Calls to `knows`:** at most `3(n - 1)`. That is `n - 1` in pass 1 and up to `2(n - 1)` in pass 2, compared to about `n²` for brute force.
- **Time:** O(n²) overall, but only because reading the input touches all `n²` values. Finding the celebrity itself is O(n).
- **Space:** O(n²) for `knows_matrix`. Finding the celebrity itself needs only O(1) extra space.

### Python Code

```python
from typing import List


class CelebrityFinder:

    def __init__(self):
        # knows_matrix[a][b] is True when person a knows person b
        self.knows_matrix = []

    def findCelebrityFromGraph(self, graph: List[str]) -> int:
        self.knows_matrix = self.buildMatrix(graph)
        n = len(graph)

        # Pass 1: each check rules out one person.
        # At the end, only the candidate can still be the celebrity.
        candidate = 0
        for i in range(1, n):
            if self.knows(candidate, i):
                # candidate knows someone, so candidate is out
                candidate = i
            # otherwise candidate does not know i, so i is out

        # Pass 2: make sure the candidate follows both rules
        for i in range(n):
            if i == candidate:
                continue
            if self.knows(candidate, i) or not self.knows(i, candidate):
                return -1
        return candidate

    # Returns True if person a knows person b
    def knows(self, a: int, b: int) -> bool:
        return self.knows_matrix[a][b]

    # Converts rows like "1,0,1" into a 2D list of True / False values
    def buildMatrix(self, graph: List[str]) -> List[List[bool]]:
        matrix = []
        for row in graph:
            values = row.split(",")
            matrix.append([value.strip() == "1" for value in values])
        return matrix
```