# Highest Average Student Score in Python

#### Problem Statement
[https://codezym.com/question/110-highest-average-student-score](https://codezym.com/question/110-highest-average-student-score)

Keep each student's total score and number of scores in two dictionaries. Then compare their averages using cross multiplication. This gives an exact answer without rounding, and a name comparison settles ties.

## Start with a simple approach

For each distinct student, scan the entire list to calculate their average. With `n` entries and `k` students, this takes `O(n * k)` time, which can become `O(n²)`.

We can avoid these repeated scans by collecting everyone's totals and counts together.

## Collect totals and counts

Use two dictionaries keyed by the student's name.

- `totals` stores the sum of that student's scores.
- `counts` stores how many scores that student has.

Split each entry at its comma, convert the score to an integer, and update both dictionaries. Keep the name unchanged, including any spaces.

There is no need to store individual scores or sort the students.

## Compare averages exactly

Suppose the current student has total `total` and count `count`. The current winner has `best_total` and `best_count`.

Instead of dividing, compare `total * best_count` with `best_total * count`. Both counts are positive, so this preserves the order even when totals are negative.

If the products are equal, choose the smaller name using `<`.

Python integers keep totals and products exact without floating-point calculations.

Begin with no winner. This lets the first student become the winner even if every average is negative.

## Small walkthrough

For `["Zoe,21", "Zoe,22", "Alice,21"]`, Zoe has total `43` and count `2`. Alice has total `21` and count `1`.

Compare `43 * 1` with `21 * 2`. Since `43 > 42`, return `"Zoe"`. Rounding both averages down would incorrectly make this a tie.

If Alice and Bob both average `-5`, return `"Alice"` because her name comes first.

## Why it works

The first pass adds every score to the correct total and increments the corresponding count. Each stored fraction therefore represents that student's exact average.

The second pass keeps the best student seen so far. Each comparison selects the higher average, or the smaller name when averages tie. After every student is checked, the stored winner is the required answer.

## Complexity

For `n` entries and `k` students, expected time is `O(n)` and extra space is `O(k)` when names and numbers have bounded length. Longer strings and larger integers also add parsing, comparison, and arithmetic costs.

## Python code

```python
class HighestAverageScore:
    def __init__(self):
        pass

    def highestAverageScore(self, marks):
        totals = {}
        counts = {}

        # Collect each student's total and number of scores.
        for mark in marks:
            name, score_text = mark.split(",", 1)
            score = int(score_text)

            totals[name] = totals.get(name, 0) + score
            counts[name] = counts.get(name, 0) + 1

        best_name = None
        best_total = 0
        best_count = 1

        for name, total in totals.items():
            count = counts[name]

            # Compare the two averages without division or rounding.
            candidate_product = total * best_count
            best_product = best_total * count

            if (
                best_name is None
                or candidate_product > best_product
                or (candidate_product == best_product and name < best_name)
            ):
                best_name = name
                best_total = total
                best_count = count

        return best_name
```
