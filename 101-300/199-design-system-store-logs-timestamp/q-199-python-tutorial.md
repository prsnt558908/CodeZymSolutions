# Design System to Store Logs with Timestamp in Python

#### Problem Statement

[https://codezym.com/question/199-design-system-store-logs-timestamp](https://codezym.com/question/199-design-system-store-logs-timestamp)

Store each log in a list. For a query, compare only the beginning of each timestamp up to the requested granularity, collect matching IDs, and sort them. Fixed-width, zero-padded timestamps let us use string comparisons without converting dates to seconds.


## 1. Start with field-by-field comparison

A straightforward approach splits each timestamp at `:`, converts the required fields to numbers, and compares them from year onward.

For example, parsing the first two fields looks like this:

```python
fields = timestamp.split(":")
year = int(fields[0])
month = int(fields[1])
```

This works, but repeats splitting, number conversion, and field-by-field comparisons for every log. We can simplify that work by using the timestamp format itself.


## 2. Compare timestamp prefixes

The largest time unit comes first. Every field has a fixed width, so string order matches time order. For example, `"2016:07"` comes before `"2016:10"`.

The year occupies four characters. Every additional field adds three characters, including its preceding colon. The final code records where to stop for each granularity.

With `"Day"`, keep the first ten characters. Both `"2016:07:08:00:00:00"` and `"2016:07:08:23:59:59"` become `"2016:07:08"`.

Apply the same cut to `start`, `end`, and every stored timestamp. Comparing full timestamps would incorrectly exclude some logs from the first or last requested time unit.

Strings support direct comparison. The condition `startPrefix <= timestampPrefix <= endPrefix` includes both boundaries.

This simplifies comparison but still scans all logs. With at most 300 total operations, a list scan is sufficient. Maintaining a separate time index would add bookkeeping that this limit does not require.


## 3. Store, filter, and sort

`LogEntry` is a dataclass that groups an ID with its timestamp. It creates the small record's constructor for us. `logs` is a list of these entries. Appending is simple, and separate entries preserve different IDs that share the same timestamp.

`prefixLengths` is a small dictionary containing the six valid granularities and their prefix lengths. `LogStorageSystem` owns these collections and provides `put` and `retrieve`.

`put` appends one entry. `retrieve` checks each entry, collects the matching IDs, and sorts the result numerically. Timestamp order and insertion order do not determine the output order.

An empty store or a query with no matches naturally returns an empty list. Inputs are guaranteed valid, so no date validation is needed.


## 4. Walk through a query

Suppose we store these logs in this order:

- ID `12` at `"2016:07:08:23:59:59"`.
- ID `3` at `"2016:07:08:00:00:00"`.
- ID `20` at `"2016:07:09:00:00:00"`.

Let both `start` and `end` be `"2016:07:08:12:00:00"`, with granularity `"Day"`.

Both query boundaries become `"2016:07:08"`. IDs `12` and `3` match even though neither timestamp is noon. ID `20` belongs to the next day. Sorting the matching IDs gives `[3, 12]`.


## 5. Complexity

Let `n` be the number of stored logs and `k` the number of matches.

- `put`: amortized `O(1)` time.
- `retrieve`: `O(n + k log k)` time, from scanning and sorting.
- Storage: `O(n)`. A retrieval uses `O(k)` extra space, including its result and sorting workspace.

Timestamps have a fixed length of 19 characters, so prefix creation and comparison take constant time here.


## 6. Complete Code

```python
from dataclasses import dataclass
from typing import List


@dataclass
class LogEntry:
    id: int
    timestamp: str


class LogStorageSystem:
    def __init__(self):
        self.logs: List[LogEntry] = []
        self.prefixLengths = {
            "Year": 4,
            "Month": 7,
            "Day": 10,
            "Hour": 13,
            "Minute": 16,
            "Second": 19,
        }

    def put(self, id: int, timestamp: str) -> None:
        # Different logs may have the same timestamp.
        self.logs.append(LogEntry(id, timestamp))

    def retrieve(self, start: str, end: str, granularity: str) -> List[int]:
        """Returns matching ids in ascending order."""
        # Compare only the fields requested by the query.
        length = self.prefixLengths[granularity]
        startPrefix = start[:length]
        endPrefix = end[:length]
        result = []

        for log in self.logs:
            timestampPrefix = log.timestamp[:length]
            if startPrefix <= timestampPrefix <= endPrefix:
                result.append(log.id)

        # The required order is by id, regardless of timestamp or insertion order.
        result.sort()
        return result
```
