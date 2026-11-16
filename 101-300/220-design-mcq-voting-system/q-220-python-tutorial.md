# Design MCQ Voting System in Python

#### Problem Statement
[https://codezym.com/question/220-design-mcq-voting-system](https://codezym.com/question/220-design-mcq-voting-system)


## Core Idea

This problem is all about counting. Each option keeps its own vote count, and the system keeps one running total of all votes. An option's percentage is `votes * 100 // totalVotes`, and that same number tells the UI how much of the option row to fill with color.


The design needs just two small classes. `Option` holds an option's text and its vote count. `VotingSystem` keeps the 4 options in a list, in their original order, along with the total.


No design pattern is needed here. There is one kind of option, one way to vote and one fixed formula, so plain classes are the optimal design. Later we will see why tempting patterns like Observer and Strategy do not fit.


## Solution 1: Brute Force (Save Every Vote)

The most direct idea is to save every vote in a list, as the index of the clicked option. Whenever results are needed, we scan this list to count the votes of each option.

```python
class VotingSystem:
    def __init__(self, options: List[str]):
        self.texts = list(options)
        # every vote ever cast, saved as the option index
        self.allVotes = []

    def vote(self, optionIndex: int) -> List[str]:
        self.allVotes.append(optionIndex)
        return self.getResults()

    def getResults(self) -> List[str]:
        # rebuild every option's count by scanning all votes
        counts = [0] * len(self.texts)
        for index in self.allVotes:
            counts[index] += 1
        total = len(self.allVotes)

        results = []
        for i in range(len(self.texts)):
            percentage = 0 if total == 0 else counts[i] * 100 // total
            results.append(f"{self.texts[i]},{counts[i]},{percentage}")
        return results
```

The answers are correct, but this is slow.


### What is wrong with it?

- **Every call scans every past vote.** After 50,000 votes, the next `vote()` reads all 50,000 again. With up to 100,000 calls, this adds up to billions of steps.
- **Memory keeps growing.** We store one entry per vote, even though we only ever need 4 counts.


## Solution 2: Option Class + Running Total

We never need the history of votes. We only need how many votes each option has right now.


So each option keeps its own count, and the system keeps a running total. A vote now changes just 2 numbers, and nothing is ever recounted.


### Why these pieces?

- **`Option` class:** an option's text and vote count always belong together, so they live in one object instead of being split across a text list and a count list. It also works out its own percentage, so that math lives in one place.
- **List of `Option` objects:** results must follow the original option order. A list keeps that order for free, and `self.options[optionIndex]` finds the voted option in one step.
- **`totalVotes` counter:** every percentage divides by the total. Updating it on each vote means we never add up the counts again.


### Getting the Percentage Right

A few small details matter in `getPercentage()`:

- **Use `//`, not `/`.** In Python, `200 / 3` gives a decimal number `66.66...`, while `200 // 3` is floor division and gives `66`.
- **Multiply first, then divide.** `1 // 3 * 100` is `0`, because `1 // 3` is already `0`. Writing `1 * 100 // 3` gives the correct `33`.
- **No votes yet.** If `totalVotes` is `0`, dividing would crash. Every option simply shows `0`.
- **The sum can be less than 100.** With votes `1` and `2` out of `3`, we get `33` and `66`, which add up to `99`. The problem allows this.


### Dry Run

Example 3 with options `["A", "B", "C", "D"]`. Each cell shows votes and percentage.

| Call | A | B | C | D | totalVotes |
|---|---|---|---|---|---|
| `vote(0)` | 1 (100%) | 0 (0%) | 0 (0%) | 0 (0%) | 1 |
| `vote(2)` | 1 (50%) | 0 (0%) | 1 (50%) | 0 (0%) | 2 |
| `vote(2)` | 1 (33%) | 0 (0%) | 2 (66%) | 0 (0%) | 3 |

On the last call, A gets `1 * 100 // 3 = 33` and C gets `2 * 100 // 3 = 66`.


## Why Not Observer or Strategy?

**Observer:** the color fill must refresh after every vote, so it is tempting to let the UI subscribe to vote changes. But `vote()` already returns the fresh results to the caller. There is nobody else to notify, so listener classes would be extra code with no job to do.


Observer would start to make sense if many screens had to show live results of one shared poll. This problem does not ask for that.


**Strategy:** we could make the percentage formula swappable, like floor, normal rounding, or a method that always adds up to 100. But the problem fixes one rule: floor division. A separate strategy class with only one version adds code without adding value. If the rule ever changes, we only edit `getPercentage()`.


## Complexity

| | Brute Force | Option Class + Running Total |
|---|---|---|
| `vote()` | O(V + n) | O(n) |
| `getResults()` | O(V + n) | O(n) |
| Space | O(V + n) | O(n) |

Here `V` is the number of votes cast so far and `n` is the number of options. Since `n` is always 4, every call in Solution 2 takes constant time.


## Final Python Code

```python
from typing import List


class Option:
    """One MCQ option: its text and how many votes it has received."""

    def __init__(self, text: str):
        self.text = text
        self.votes = 0

    def addVote(self) -> None:
        self.votes += 1

    def getPercentage(self, totalVotes: int) -> int:
        """
        Share of all votes for this option, using floor division.
        The same number is the color fill percentage for this option row.
        """
        if totalVotes == 0:
            return 0  # no votes yet, avoid dividing by zero
        return self.votes * 100 // totalVotes  # multiply first, then divide


class VotingSystem:
    def __init__(self, options: List[str]):
        # all options, kept in their original order
        self.options = [Option(text) for text in options]
        # running total of votes across all options
        self.totalVotes = 0

    def vote(self, optionIndex: int) -> List[str]:
        self.options[optionIndex].addVote()
        self.totalVotes += 1
        return self.getResults()

    def getResults(self) -> List[str]:
        """
        Returns "optionText,votes,percentage" for every option,
        in the original option order.
        """
        results = []
        for option in self.options:
            percentage = option.getPercentage(self.totalVotes)
            results.append(f"{option.text},{option.votes},{percentage}")
        return results
```