# Design Logger System With Message Timestamps in Python

#### Problem Statement

[https://codezym.com/question/187-design-logger-system-message-timestamps](https://codezym.com/question/187-design-logger-system-message-timestamps)

Store the last successful print time of each message in a dictionary. When a message arrives, compare its timestamp with that saved time. This gives an average `O(1)` decision per call and stores just one timestamp per distinct message.


## 1. Start with a list of printed messages

A simple approach is to keep every successful print in two lists. `messages` stores the text, and `printedTimes` stores the timestamp at the same index.

For each arrival, scan these lists. Reject the message if a matching print happened less than 10 seconds ago. Otherwise, record the new print.

This snippet belongs inside `shouldPrintMessage`. Both lists are initially empty and are kept between calls.

```python
for i, printedMessage in enumerate(self.messages):
    if (
        printedMessage == message
        and timestamp - self.printedTimes[i] < 10
    ):
        return False

self.messages.append(message)
self.printedTimes.append(timestamp)
return True
```

With `p` previous successful prints, one call can take `O(p)` time. The lists also use `O(p)` space, including repeated records of the same message.


## 2. Improve it with a dictionary

We only need the latest successful print of each message. Calls arrive in non-decreasing timestamp order, so every earlier print is at least as old as the latest one. Older records cannot change the decision.

Use a dictionary named `lastPrinted`. The key is the message, and the value is its latest successful print time. A dictionary lets us find that time directly without scanning other messages.

For each call:

1. Look up the message in `lastPrinted`.
2. If it exists and the time difference is less than `10`, return `False` without changing the dictionary.
3. Otherwise, save the current timestamp and return `True`.

An exact 10-second gap is allowed. Each message has its own timer, so different messages can be accepted at the same timestamp.

The `Logger` class keeps the dictionary between calls. Its method returns a decision. It does not write anything to the console.


## 3. Walk through an example

Start with an empty logger.

- At time `0`, `"hello"` returns `True`. Store `hello -> 0`.
- At time `0`, `"hello"` returns `False` because no time has passed.
- At time `9`, `"hello"` returns `False`. Its saved time remains `0`.
- At time `10`, `"hello"` returns `True` because `10 - 0 = 10`. Update its saved time to `10`.
- At time `10`, `"world"` returns `True` because it has never been printed.

The code checks `lastTime is not None` because `0` is a valid saved timestamp. A truthiness check such as `if lastTime` would miss it.


## 4. Complexity

Each call takes **`O(1)` average time** for the dictionary operations. With `u` distinct messages, the dictionary uses **`O(u)` space**.

These bounds treat message length as bounded, as it is at most 100 characters. If message length is counted separately, hashing or comparing a message of length `L` can take `O(L)` time, and stored text can use `O(uL)` space.

The dictionary keeps one entry for every distinct message seen. It does not remove old entries.


## 5. Complete code

```python
class Logger:
    def __init__(self):
        # The last successful print time for each message.
        self.lastPrinted = {}

    def shouldPrintMessage(self, timestamp: int, message: str) -> bool:
        """Return whether this message may be printed at the given timestamp."""
        lastTime = self.lastPrinted.get(message)

        if lastTime is not None and timestamp - lastTime < 10:
            return False

        # Save the timestamp only when the message is allowed.
        self.lastPrinted[message] = timestamp
        return True
```
