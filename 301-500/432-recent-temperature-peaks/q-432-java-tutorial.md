# Track Recent Temperature Peaks in Java

#### Problem Statement

[https://codezym.com/question/432-recent-temperature-peaks](https://codezym.com/question/432-recent-temperature-peaks)

The main idea is to keep only readings that could become the highest temperature. If a new reading is at least as warm as an older one, the older reading is no longer useful because the new reading will also stay in the recent group longer. A deque, a queue that supports adding and removing at both ends, lets us keep these useful readings in decreasing temperature order. The front always holds the answer.


## 1. Start with a simple scan

Let `k = recentReadingLimit`. Store the latest `k` temperatures in a queue called `readings`. After adding a temperature, remove the oldest one if the queue has more than `k` entries.

To answer a query, scan the queue.

```java
public int getHighestRecentTemperature() {
    int highest = readings.peekFirst();
    for (int temperature : readings) {
        highest = Math.max(highest, temperature);
    }
    return highest;
}
```

Starting with an actual reading also works when every temperature is negative.

Recording takes amortized `O(1)` time, but each query takes `O(k)` time. Repeated queries scan the same readings again, so this approach misses the required query speed.


## 2. Keep only useful readings

Suppose an older reading is `18` and a newer reading is `24`. Every future recent group that still contains `18` will also contain `24`. Since `24` is warmer, we can discard `18` immediately.

The same reasoning works for equal temperatures. Keep the newer reading because it expires later.

Use a deque named `candidates`. Its readings stay in recording order, while their temperatures strictly decrease from front to back. This is often called a **monotonic deque**.

Each `Reading` stores its `index` and `temperature`. The index tells us when it becomes too old. Deque size alone cannot tell us this because we also remove readings that are no longer useful.

For a new reading at index `i`, the valid indices start at `i - k + 1`. Update the deque in three steps.

1. Remove readings from the front while their index is at most `i - k`.
2. Remove readings from the back while their temperature is less than or equal to the new temperature.
3. Add the new reading at the back.

Old readings are at the front because indices increase. Smaller temperatures are at the back because temperatures decrease. This lets us remove both kinds of unwanted readings without scanning the whole deque.

`getHighestRecentTemperature()` simply reads the front. It does not change anything. The problem guarantees that a query follows at least one recorded reading.


## 3. Walk through an example

Take `k = 3`. The lists below show candidate temperatures from front to back. The code also stores their indices.

- Record `18`, `24`, and `21`. The candidates are `[24, 21]`. The newer `24` made `18` unnecessary.
- Record `16`. The candidates become `[24, 21, 16]`. The answer is `24`.
- Record `15`. The reading `24` expires. The candidates become `[21, 16, 15]`, and the answer is `21`.
- Record `29`. The reading `21` expires, and `29` removes the smaller candidates `15` and `16`. The candidates become `[29]`.


## 4. Why it works and how fast it is

We discard a reading only when it has expired or when a newer reading is at least as warm. Neither kind can affect a future answer. The remaining candidates are ordered from warmest to coolest, so the front is the highest recent temperature.

Each reading enters the deque once and leaves at most once. Recording `n` readings therefore takes `O(n)` total time, giving **amortized `O(1)` per recording**. One recording can still take `O(k)` if it removes many candidates.

Each query takes **`O(1)` time**. At most `k` candidates remain, so space is **`O(k)`**. The same code handles a partly filled recent group, duplicate temperatures, negative values, and `k = 1`.


## 5. Complete Java code

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class ColdStorageMonitor {
    int recentReadingLimit;
    int nextIndex;
    Deque<Reading> candidates;

    public ColdStorageMonitor(int recentReadingLimit) {
        this.recentReadingLimit = recentReadingLimit;
        this.nextIndex = 0;
        this.candidates = new ArrayDeque<>();
    }

    public void recordTemperature(int temperature) {
        int index = nextIndex;
        nextIndex++;

        // Remove readings that are outside the recent group.
        while (!candidates.isEmpty()
                && candidates.peekFirst().index <= index - recentReadingLimit) {
            candidates.removeFirst();
        }

        // A newer, equally warm or warmer reading replaces these candidates.
        while (!candidates.isEmpty()
                && candidates.peekLast().temperature <= temperature) {
            candidates.removeLast();
        }

        candidates.addLast(new Reading(index, temperature));
    }

    public int getHighestRecentTemperature() {
        // At least one reading is guaranteed before a query.
        return candidates.peekFirst().temperature;
    }
}

class Reading {
    int index;
    int temperature;

    Reading(int index, int temperature) {
        this.index = index;
        this.temperature = temperature;
    }
}
```
