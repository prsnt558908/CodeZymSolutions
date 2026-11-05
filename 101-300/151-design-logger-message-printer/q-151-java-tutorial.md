# Design Logger Message Printer in Java

#### Problem Statement

[https://codezym.com/question/151-design-logger-message-printer](https://codezym.com/question/151-design-logger-message-printer)

Store the next allowed time for each message in a map. For every call, compare the given timestamp with that saved time. If the message is allowed, move its next allowed time forward by ten seconds. This gives a short solution with an average constant-time check.


## 1. Start with a simple list scan

We could save every accepted message and its timestamp in two lists, `printedMessages` and `printedTimes`. Matching positions describe the same accepted call.

Inside `canPrintMessage`, scan backward to find the latest accepted occurrence of the current message.

```java
for (int i = printedMessages.size() - 1; i >= 0; i--) {
    if (printedMessages.get(i).equals(message)) {
        if (timestamp < printedTimes.get(i) + 10) {
            return false;
        }
        break;
    }
}

printedMessages.add(message);
printedTimes.add(timestamp);
return true;
```

This works, but a new message may require scanning the entire history. With `P` accepted calls so far, one check can take `O(P)` time. Over `N` calls, the total can reach `O(N²)`.


## 2. Replace the search with a map

Only the latest accepted occurrence of a message matters. Since timestamps never go backward, earlier occurrences cannot impose a longer wait.

Use a `HashMap<String, Integer>` named `nextAllowedTime`. Its key is the message text. Its value is the earliest timestamp when that message can be accepted again.

For each call:

1. Read the saved time. Use `0` for a new message because all valid timestamps are non-negative.
2. If `timestamp` is smaller, return `false` without changing the map.
3. Otherwise, save `timestamp + 10` and return `true`.

**A rejected call must not extend the wait.** Update the map only when returning `true`. The method records the decision, but does not print anything itself.

`MessageLogger` owns the map, so separate logger objects keep independent histories. No helper class is needed.


## 3. Walk through an example

`canPrintMessage(1, "foo")` returns `true` and saves `11` for `"foo"`.

`canPrintMessage(1, "bar")` also returns `true`. Different messages have separate waits.

`canPrintMessage(10, "foo")` returns `false` because `10 < 11`. The saved time stays `11`.

`canPrintMessage(11, "foo")` returns `true` and saves `21`. Exactly ten seconds is enough.

Another `canPrintMessage(11, "foo")` returns `false` because the newly saved time is `21`.


## 4. Why this works

After each call, the map stores the latest accepted timestamp plus ten for every message seen so far. A rejected call preserves this value. An accepted call replaces it with the new timestamp plus ten.

The comparison therefore accepts a message exactly when its required wait has ended. Unseen messages are accepted immediately.


## 5. Complexity

Each call takes **`O(1)` average time**, with message length limited to 30 characters. The map uses **`O(U)` space**, where `U` is the number of distinct messages seen.

Old entries remain in the map. With at most `10⁴` calls, keeping one entry per distinct message is sufficient. Also, `timestamp + 10` fits safely in a Java `int` under the given limit of `10⁹`.


## 6. Complete code

```java
import java.util.HashMap;
import java.util.Map;

public class MessageLogger {
    // Earliest allowed timestamp for each message.
    Map<String, Integer> nextAllowedTime;

    public MessageLogger() {
        nextAllowedTime = new HashMap<>();
    }

    public boolean canPrintMessage(int timestamp, String message) {
        int allowedAt = nextAllowedTime.getOrDefault(message, 0);

        if (timestamp < allowedAt) {
            // Rejected calls do not extend the wait.
            return false;
        }

        nextAllowedTime.put(message, timestamp + 10);
        return true;
    }
}
```
