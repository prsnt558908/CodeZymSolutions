# Track Recent Temperature Peaks in Python

#### Problem Statement

[https://codezym.com/question/432-recent-temperature-peaks](https://codezym.com/question/432-recent-temperature-peaks)

The main idea is to keep only readings that could become the highest temperature. If a new reading is at least as warm as an older one, the older reading is no longer useful because the new reading will also stay in the recent group longer. A deque, a queue that supports adding and removing at both ends, lets us keep these useful readings in decreasing temperature order. The front always holds the answer.


## 1. Start with a simple scan

Let `k = recentReadingLimit`. Store the latest `k` temperatures in a deque called `readings`. After adding a temperature, remove the oldest one if the deque has more than `k` entries.

To answer a query, scan the deque using `max`.

```python
def getHighestRecentTemperature(self):
    return max(self.readings)
```

Using the recorded values also works when every temperature is negative.

Recording takes `O(1)` time, but each query takes `O(k)` time. Repeated queries scan the same readings again, so this approach misses the required query speed.


## 2. Keep only useful readings

Suppose an older reading is `18` and a newer reading is `24`. Every future recent group that still contains `18` will also contain `24`. Since `24` is warmer, we can discard `18` immediately.

The same reasoning works for equal temperatures. Keep the newer reading because it expires later.

Use a deque named `candidates`. Its readings stay in recording order, while their temperatures strictly decrease from front to back. This is often called a **monotonic deque**.

Each `Reading` stores its `index` and `temperature`. The index tells us when it becomes too old. Deque size alone cannot tell us this because we also remove readings that are no longer useful. A small dataclass keeps these two values together.

For a new reading at index `i`, the valid indices start at `i - k + 1`. Update the deque in three steps.

1. Remove readings from the front while their index is at most `i - k`.
2. Remove readings from the back while their temperature is less than or equal to the new temperature.
3. Add the new reading at the back.

Old readings are at the front because indices increase. Smaller temperatures are at the back because temperatures decrease. This lets us remove both kinds of unwanted readings without scanning the whole deque.

Use `collections.deque` because removal at either end takes `O(1)` time. Removing the first element of a regular list would shift the remaining elements.

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


## 5. Complete Python code

```python
from collections import deque
from dataclasses import dataclass


@dataclass
class Reading:
    index: int
    temperature: int


class ColdStorageMonitor:
    def __init__(self, recentReadingLimit: int):
        self.recentReadingLimit = recentReadingLimit
        self.nextIndex = 0
        self.candidates = deque()

    def recordTemperature(self, temperature: int) -> None:
        index = self.nextIndex
        self.nextIndex += 1

        # Remove readings that are outside the recent group.
        while (
            self.candidates
            and self.candidates[0].index <= index - self.recentReadingLimit
        ):
            self.candidates.popleft()

        # A newer, equally warm or warmer reading replaces these candidates.
        while self.candidates and self.candidates[-1].temperature <= temperature:
            self.candidates.pop()

        self.candidates.append(Reading(index, temperature))

    def getHighestRecentTemperature(self) -> int:
        # At least one reading is guaranteed before a query.
        return self.candidates[0].temperature
```
