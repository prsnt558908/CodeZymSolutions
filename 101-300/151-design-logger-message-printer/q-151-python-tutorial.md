# Design Logger Message Printer in Python

#### Problem Statement

[https://codezym.com/question/151-design-logger-message-printer](https://codezym.com/question/151-design-logger-message-printer)

Store the next allowed time for each message in a dictionary. For every call, compare the given timestamp with that saved time. If the message is allowed, move its next allowed time forward by ten seconds. This gives a short solution with an average constant-time check.


## 1. Start with a simple list scan

We could save every accepted message and its timestamp in two lists, `printedMessages` and `printedTimes`. Matching positions describe the same accepted call.

Inside `canPrintMessage`, scan backward to find the latest accepted occurrence of the current message.

```python
for i in range(len(printedMessages) - 1, -1, -1):
    if printedMessages[i] == message:
        if timestamp < printedTimes[i] + 10:
            return False
        break

printedMessages.append(message)
printedTimes.append(timestamp)
return True
```

This works, but a new message may require scanning the entire history. With `P` accepted calls so far, one check can take `O(P)` time. Over `N` calls, the total can reach `O(N²)`.


## 2. Replace the search with a dictionary

Only the latest accepted occurrence of a message matters. Since timestamps never go backward, earlier occurrences cannot impose a longer wait.

Use a dictionary named `nextAllowedTime`. Its key is the message text. Its value is the earliest timestamp when that message can be accepted again.

For each call:

1. Read the saved time. Use `0` for a new message because all valid timestamps are non-negative.
2. If `timestamp` is smaller, return `False` without changing the dictionary.
3. Otherwise, save `timestamp + 10` and return `True`.

**A rejected call must not extend the wait.** Update the dictionary only when returning `True`. The method records the decision, but does not print anything itself.

`MessageLogger` owns the dictionary, so separate logger objects keep independent histories. The dataclass field uses `default_factory=dict` to give each new logger its own empty dictionary. `init=False` keeps the constructor argument-free.


## 3. Walk through an example

`canPrintMessage(1, "foo")` returns `True` and saves `11` for `"foo"`.

`canPrintMessage(1, "bar")` also returns `True`. Different messages have separate waits.

`canPrintMessage(10, "foo")` returns `False` because `10 < 11`. The saved time stays `11`.

`canPrintMessage(11, "foo")` returns `True` and saves `21`. Exactly ten seconds is enough.

Another `canPrintMessage(11, "foo")` returns `False` because the newly saved time is `21`.


## 4. Why this works

After each call, the dictionary stores the latest accepted timestamp plus ten for every message seen so far. A rejected call preserves this value. An accepted call replaces it with the new timestamp plus ten.

The comparison therefore accepts a message exactly when its required wait has ended. Unseen messages are accepted immediately.


## 5. Complexity

Each call takes **`O(1)` average time**, with message length limited to 30 characters. The dictionary uses **`O(U)` space**, where `U` is the number of distinct messages seen.

Old entries remain in the dictionary. With at most `10⁴` calls, keeping one entry per distinct message is sufficient.


## 6. Complete code

```python
from dataclasses import dataclass, field


@dataclass
class MessageLogger:
    # Earliest allowed timestamp for each message.
    nextAllowedTime: dict[str, int] = field(default_factory=dict, init=False)

    def canPrintMessage(self, timestamp: int, message: str) -> bool:
        allowedAt = self.nextAllowedTime.get(message, 0)

        if timestamp < allowedAt:
            # Rejected calls do not extend the wait.
            return False

        self.nextAllowedTime[message] = timestamp + 10
        return True
```
