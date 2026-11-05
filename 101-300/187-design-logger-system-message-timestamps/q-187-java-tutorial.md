# Design Logger System With Message Timestamps in Java

#### Problem Statement

[https://codezym.com/question/187-design-logger-system-message-timestamps](https://codezym.com/question/187-design-logger-system-message-timestamps)

Store the last successful print time of each message in a map. When a message arrives, compare its timestamp with that saved time. This gives an average `O(1)` decision per call and stores just one timestamp per distinct message.


## 1. Start with a list of printed messages

A simple approach is to keep every successful print in two lists. `messages` stores the text, and `printedTimes` stores the timestamp at the same index.

For each arrival, scan these lists. Reject the message if a matching print happened less than 10 seconds ago. Otherwise, record the new print.

This snippet belongs inside `shouldPrintMessage`. Both lists are initially empty and are kept between calls.

```java
for (int i = 0; i < messages.size(); i++) {
    if (messages.get(i).equals(message)
            && timestamp - printedTimes.get(i) < 10) {
        return false;
    }
}

messages.add(message);
printedTimes.add(timestamp);
return true;
```

With `p` previous successful prints, one call can take `O(p)` time. The lists also use `O(p)` space, including repeated records of the same message.


## 2. Improve it with a map

We only need the latest successful print of each message. Calls arrive in non-decreasing timestamp order, so every earlier print is at least as old as the latest one. Older records cannot change the decision.

Use `Map<String, Integer> lastPrinted`, backed by a `HashMap`. The key is the message, and the value is its latest successful print time. A map lets us find that time directly without scanning other messages.

For each call:

1. Look up the message in `lastPrinted`.
2. If it exists and the time difference is less than `10`, return `false` without changing the map.
3. Otherwise, save the current timestamp and return `true`.

An exact 10-second gap is allowed. Each message has its own timer, so different messages can be accepted at the same timestamp.

The `Logger` class keeps the map between calls. Its method returns a decision. It does not write anything to the console.


## 3. Walk through an example

Start with an empty logger.

- At time `0`, `"hello"` returns `true`. Store `hello -> 0`.
- At time `0`, `"hello"` returns `false` because no time has passed.
- At time `9`, `"hello"` returns `false`. Its saved time remains `0`.
- At time `10`, `"hello"` returns `true` because `10 - 0 = 10`. Update its saved time to `10`.
- At time `10`, `"world"` returns `true` because it has never been printed.


## 4. Complexity

Each call takes **`O(1)` average time** for the hash map operations. With `u` distinct messages, the map uses **`O(u)` space**.

These bounds treat message length as bounded, as it is at most 100 characters. If message length is counted separately, hashing or comparing a message of length `L` can take `O(L)` time, and stored text can use `O(uL)` space.

The map keeps one entry for every distinct message seen. It does not remove old entries.


## 5. Complete code

```java
import java.util.HashMap;
import java.util.Map;

public class Logger {
    // The last successful print time for each message.
    Map<String, Integer> lastPrinted;

    public Logger() {
        lastPrinted = new HashMap<>();
    }

    /** Returns whether this message may be printed at the given timestamp. */
    public boolean shouldPrintMessage(int timestamp, String message) {
        Integer lastTime = lastPrinted.get(message);

        if (lastTime != null && timestamp - lastTime < 10) {
            return false;
        }

        // Save the timestamp only when the message is allowed.
        lastPrinted.put(message, timestamp);
        return true;
    }
}
```
