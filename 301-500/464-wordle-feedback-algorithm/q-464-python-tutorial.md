# Wordle Feedback Algorithm in Python


#### Problem Statement

[https://codezym.com/question/464-wordle-feedback-algorithm](https://codezym.com/question/464-wordle-feedback-algorithm)


Match letters in the right position first. Then count the unused target letters and use those counts to assign yellow matches from left to right. This prevents repeated guess letters from using the same target occurrence twice. For multiple attempts, repeat this process until the word is found or the attempt limit is reached.


## Understand the Feedback

- `G` means the letter matches at the same position.
- `Y` means an unused matching letter exists elsewhere in the target.
- `X` means no unused matching letter remains.

Every word has five lowercase letters. Repeated letters are allowed. No dictionary check is needed.


## Start with a Simple Idea

Checking whether a letter appears anywhere in the target sounds enough for yellow feedback, but it ignores repeated letters.

For target `abbey` and guess `bobby`, one `b` is green. Only one other target `b` remains, so the remaining two guessed `b` letters cannot both be yellow. We need counts, not just a presence check.


## Two Passes with a Count Map

The first pass marks every exact match as `G`. For each target position that is not an exact match, add its letter to a count map. These counts describe the target letters left after all greens have been reserved.

The second pass visits the guess from left to right and skips green positions. If the current letter has a positive remaining count, mark it `Y` and decrease that count by one. Otherwise, leave it `X`.

Processing greens first matters. With target `cigar` and guess `aaaaa`, the fourth letter must be green. That uses the target's only `a`, so all other positions are `X`. The result is `XXXGX`.


## Walk Through Repeated Letters

For target `abbey` and guess `bobby`:

1. The third letter, `b`, and the fifth letter, `y`, are exact matches. Feedback starts as `XXGXG`.
2. The unused target letters are `a`, `b`, and `e`, each with count one.
3. The first guessed `b` becomes `Y` and uses the remaining `b`.
4. The letter `o` stays `X`. The fourth guessed letter, another `b`, also stays `X` because no unused `b` remains.

The final feedback is `YXGXG`.


## Follow-Up: Multiple Attempts

For each guess, call `generateFeedback` and append its result. Stop after `GGGGG`, after `maxAttempts` guesses, or when the list ends. Appending before the success check includes the successful attempt.

Each call creates a fresh count map, so every attempt starts with all target occurrences available.

For target `plant`, guesses `["plate", "slant", "plant", "prone"]`, and a limit of five, the result is `["GGGYX", "XGGGG", "GGGGG"]`. With a limit of two, the result is `["GGGYX", "XGGGG"]`. An empty guess list returns an empty list.


## Why These Data Structures

- A list stores the feedback in guess order. It starts with `X` at every position, and `join` converts it into the returned string.
- A dictionary counts unused target occurrences. A set would lose the counts needed for repeated letters.
- A result list stores feedback strings in attempt order.

All working data stays inside method calls, so `WordleFeedback` needs no stored fields.


## Why This Works

The first pass reserves every green occurrence. The count map then contains only unused target letters.

In the second pass, a positive count allows one yellow match. Decreasing the count prevents reuse. A missing or zero count produces `X`. Visiting positions from left to right gives earlier unmatched guess letters priority, as required.


## Complexity

For a word of length `n`, one guess takes `O(n)` average time and `O(n)` space. The count map has at most 26 entries.

The word length is fixed at five. Therefore, one guess takes constant time and space. Processing `a` attempts takes `O(a)` time and `O(a)` output space, with constant extra working space.


## Python Code

```python
class WordleFeedback:
    def __init__(self):
        pass

    def generateFeedback(self, targetWord: str, guess: str) -> str:
        feedback = ["X"] * len(targetWord)
        remaining = {}

        # Reserve exact matches before using any letters for yellow matches.
        for i, targetLetter in enumerate(targetWord):
            if targetLetter == guess[i]:
                feedback[i] = "G"
            else:
                count = remaining.get(targetLetter, 0)
                remaining[targetLetter] = count + 1

        # Process unmatched guess positions from left to right.
        for i, letter in enumerate(guess):
            if feedback[i] == "G":
                continue

            count = remaining.get(letter, 0)

            if count > 0:
                feedback[i] = "Y"
                remaining[letter] = count - 1

        return "".join(feedback)

    def generateFeedbackForAttempts(
        self, targetWord: str, guesses: list[str], maxAttempts: int
    ) -> list[str]:
        results = []

        for guess in guesses:
            if len(results) == maxAttempts:
                break

            feedback = self.generateFeedback(targetWord, guess)
            results.append(feedback)

            # Include the successful attempt, then stop.
            if feedback == "GGGGG":
                break

        return results
```
