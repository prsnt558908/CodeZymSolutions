# Design Multi-Module Logging Framework

#### Problem Statement

[https://codezym.com/question/340-multi-module-logging-framework](https://codezym.com/question/340-multi-module-logging-framework)

The core idea:  A dictionary stores every accepted log by sequence number, a set keeps the accepted sequence numbers unique, and a dictionary of lists represents the FILE and TOOL destination streams. A frozen `dataclass` keeps the fields of one log together. Python does not have a built-in sorted-set collection, so we call `sorted` on the sequence-number set when retrieving logs. This keeps the implementation easy to read while still preserving the required increasing order.

## What Do We Need to Support?

The framework receives logs from `MODULE1`, `MODULE2`, and `MODULE3`. Every submitted log contains:

- A globally unique sequence number.
- A destination: `FILE` or `TOOL`.
- A level: `INFO`, `WARNING`, or `ERROR`.
- The activity data.

A valid log must be written exactly once. Only then do we return `"ACK,sequenceNumber"`. Invalid data or a sequence number that was already accepted must return `"REJECTED"`.

For retrieval, all three filters are combined. A log is returned only if it matches the destination, module, and level filters. `ALL` works as a wildcard. The sequence-number range is inclusive, and the final result must always be sorted by sequence number.

## A Simple List-Based Approach

We could keep every accepted log in one list.

For each new log, we would scan the list to check whether its sequence number was already used. For each retrieval, we would scan the complete list, keep the matching logs, and sort them by sequence number.

This works, but it becomes wasteful as the number of logs grows:

- Duplicate checking takes `O(n)` time.
- Every query scans all `n` logs.
- Every query may need another sorting step.

We can improve duplicate checks and lookups with a dictionary while still using familiar Python collections.

## Better Approach with a Dictionary and Set

We use these main data structures:

```python
self.logs_by_sequence = {}
self.sequence_numbers = set()
```

The dictionary stores a sequence number and its complete `LogEntry`. It gives average `O(1)` duplicate checking and direct access to a log.

The set stores every accepted sequence number. At retrieval time, `sorted(self.sequence_numbers)` gives the required increasing order. The set itself prevents a sequence number from being inserted twice.

We also keep the destination streams in a dictionary of lists:

```python
self.destination_streams = {
    "FILE": [],
    "TOOL": [],
}
```

Adding a log to the selected list represents writing it to that destination before returning the acknowledgement.

## Important Classes and Data Structures

### `LogEntry`

`LogEntry` is a small frozen dataclass containing the five fields of one accepted log. Keeping the values together makes validation and formatting easier. The fields are joined only when creating the output string, so activity data such as spaces, Unicode characters, and commas is preserved exactly.

### `logs_by_sequence`

This dictionary is the main lookup structure. An accepted sequence number becomes a key, so a later submission with the same number can be rejected without scanning all logs.

### `sequence_numbers`

This set stores the sequence numbers that were successfully accepted. Sorting it during retrieval gives the correct output order without introducing a custom sorted-set class.

### `destination_streams`

This dictionary maps each destination to a list. Appending to the selected list simulates writing to that destination stream. Rejected logs are never appended.

### Validation Sets

`VALID_MODULES` and `VALID_LEVELS` are sets. Membership checks are simple and take average `O(1)` time.

## How `logActivity` Works

For each submission:

1. Validate the sequence number, module, destination, level, and activity-data length.
2. Use the dictionary to reject a sequence number that was already accepted.
3. Create a frozen `LogEntry`.
4. Append the entry to the selected FILE or TOOL list.
5. Add the entry to the lookup dictionary and its sequence number to the set.
6. Return `"ACK,sequenceNumber"`.

An invalid submission is not added to any collection. Therefore, its sequence number remains available for a later corrected submission.

## How `getLogs` Works

First, we sort the accepted sequence numbers:

```python
for sequence_number in sorted(self.sequence_numbers):
```

For each number, we skip values below the minimum and stop once we pass the maximum. This makes the range inclusive.

For every log inside the range, we check all three filters:

```text
destination matches AND module matches AND level matches
```

A filter matches when it equals the stored value or when it is `"ALL"`. Matching logs are formatted and appended directly to the result. Activity data is not split on commas, so commas inside it remain unchanged.

## Complexity Analysis

Let `n` be the number of accepted logs and `k` be the number of logs returned.

- `logActivity`: average `O(1)` time for dictionary, set, and list operations.
- `getLogs`: `O(n log n)` time to sort the sequence-number set, followed by an `O(n)` scan in the worst case. Building the `k` output strings takes additional `O(k)` time.
- Space: `O(n)` for the logs, sequence-number set, destination lists, and result list.

The approach favors simple built-in collections. If an application needed many large range queries, it could add a dedicated sorted-index structure later.

## Python Solution

```python
from dataclasses import dataclass


@dataclass(frozen=True)
class LogEntry:
    sequence_number: int
    module_name: str
    destination_type: str
    log_level: str
    log_data: str


class MultiModuleLoggingFramework:
    MIN_SEQUENCE_NUMBER = 1
    MAX_SEQUENCE_NUMBER = 1_000_000_000
    MAX_LOG_DATA_LENGTH = 10_000

    VALID_MODULES = {"MODULE1", "MODULE2", "MODULE3"}
    VALID_LEVELS = {"INFO", "WARNING", "ERROR"}

    def __init__(self):
        # The dictionary gives average O(1) duplicate checks and lookups.
        self.logs_by_sequence = {}

        # A set keeps the accepted sequence numbers unique.
        self.sequence_numbers = set()

        # Each destination has its own list representing its written stream.
        self.destination_streams = {
            "FILE": [],
            "TOOL": [],
        }

    def logActivity(self, sequenceNumber, moduleName, destinationType, logLevel, logData):
        if not self._is_valid_log(
            sequenceNumber, moduleName, destinationType, logLevel, logData
        ):
            return "REJECTED"

        if sequenceNumber in self.logs_by_sequence:
            return "REJECTED"

        entry = LogEntry(
            sequence_number=sequenceNumber,
            module_name=moduleName,
            destination_type=destinationType,
            log_level=logLevel,
            log_data=logData,
        )

        # Append first, then acknowledge only after the destination write.
        self.destination_streams[destinationType].append(entry)
        self.logs_by_sequence[sequenceNumber] = entry
        self.sequence_numbers.add(sequenceNumber)
        return f"ACK,{sequenceNumber}"

    def getLogs(
        self,
        destinationType,
        moduleName,
        logLevel,
        minimumSequenceNumber,
        maximumSequenceNumber,
    ):
        if not self._is_valid_query(
            destinationType,
            moduleName,
            logLevel,
            minimumSequenceNumber,
            maximumSequenceNumber,
        ):
            return []

        result = []

        # Python has no built-in sorted-set collection. Sorting the set here
        # gives the required increasing sequence order without a custom type.
        for sequence_number in sorted(self.sequence_numbers):
            if sequence_number < minimumSequenceNumber:
                continue
            if sequence_number > maximumSequenceNumber:
                break

            entry = self.logs_by_sequence[sequence_number]
            if (
                self._matches(destinationType, entry.destination_type)
                and self._matches(moduleName, entry.module_name)
                and self._matches(logLevel, entry.log_level)
            ):
                result.append(
                    f"{entry.sequence_number},{entry.destination_type},"
                    f"{entry.module_name},{entry.log_level},{entry.log_data}"
                )

        return result

    def _is_valid_log(
        self, sequence_number, module_name, destination_type, log_level, log_data
    ):
        return (
            self.MIN_SEQUENCE_NUMBER <= sequence_number <= self.MAX_SEQUENCE_NUMBER
            and module_name in self.VALID_MODULES
            and destination_type in self.destination_streams
            and log_level in self.VALID_LEVELS
            and log_data is not None
            and 1 <= len(log_data) <= self.MAX_LOG_DATA_LENGTH
        )

    def _is_valid_query(
        self,
        destination_type,
        module_name,
        log_level,
        minimum_sequence_number,
        maximum_sequence_number,
    ):
        return (
            (destination_type == "ALL" or destination_type in self.destination_streams)
            and (module_name == "ALL" or module_name in self.VALID_MODULES)
            and (log_level == "ALL" or log_level in self.VALID_LEVELS)
            and self.MIN_SEQUENCE_NUMBER <= minimum_sequence_number
            and maximum_sequence_number <= self.MAX_SEQUENCE_NUMBER
            and minimum_sequence_number <= maximum_sequence_number
        )

    @staticmethod
    def _matches(filter_value, actual_value):
        return filter_value == "ALL" or filter_value == actual_value
```
