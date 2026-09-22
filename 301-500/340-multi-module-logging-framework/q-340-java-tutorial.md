# Design Multi-Module Logging Framework

#### Problem Statement

[https://codezym.com/question/340-multi-module-logging-framework](https://codezym.com/question/340-multi-module-logging-framework)

Here is the core idea: A `HashMap` stores every accepted log by sequence number, a `TreeSet` keeps the sequence numbers sorted, and a `Map<String, List<LogEntry>>` represents the FILE and TOOL destination streams. Ordinary `HashSet`s hold the valid module names and log levels. We do not need a complicated design pattern here. The destination map works as a simple registry, while `MultiModuleLoggingFramework` coordinates validation, writing, and retrieval.

## What Do We Need to Support?

The framework receives logs from `MODULE1`, `MODULE2`, and `MODULE3`. Every submitted log contains:

- A globally unique sequence number.
- A destination: `FILE` or `TOOL`.
- A level: `INFO`, `WARNING`, or `ERROR`.
- The activity data.

A valid log must be written exactly once. Only then do we return `"ACK,sequenceNumber"`. Invalid data or a sequence number that was already accepted must return `"REJECTED"`.

For retrieval, all three filters are combined. A log is returned only if it matches the destination, module, and level filters. `ALL` works as a wildcard. The sequence-number range is inclusive, and the final result must always be sorted by sequence number.

## A Simple List-Based Approach

We could keep every accepted log in one `ArrayList`.

For each new log, we would scan the list to check whether its sequence number was already used. For each retrieval, we would scan the complete list, keep the matching logs, and sort them by sequence number.

This works, but it becomes wasteful as the number of logs grows:

- Duplicate checking takes `O(n)` time.
- Every query scans all `n` logs.
- Every query may need another sorting step.

We can improve this while still using only familiar collections.

## Better Approach with a Map and Sorted Set

We use two main data structures:

```java
Map<Long, LogEntry> logsBySequence = new HashMap<>();
TreeSet<Long> sortedSequenceNumbers = new TreeSet<>();
```

The `HashMap` stores a sequence number and its complete log. It gives us fast duplicate checking and fast access to an existing log.

`TreeSet` is Java's sorted-set implementation. It stores every accepted sequence number in increasing order. Its inclusive `subSet` operation gives us only the sequence numbers inside the requested range.

Keeping these two structures together gives us simple responsibilities:

1. The map answers: “Has this sequence number already been accepted?”
2. The map also answers: “Which log belongs to this sequence number?”
3. The sorted set answers: “Which sequence numbers belong to this range, in order?”

## Important Classes and Data Structures

### `LogEntry`

`LogEntry` keeps the five fields of one accepted log together. Its fields do not change after construction. Keeping the values separate is important because activity data must be preserved exactly, including spaces, Unicode characters, or commas. The fields are joined only when creating the required output string.

### `Map<Long, LogEntry>`

`logsBySequence` is the main lookup map. An accepted sequence number becomes a key in this map. This makes duplicate detection simple and ensures that a later duplicate cannot replace the original log.

### `TreeSet<Long>`

`sortedSequenceNumbers` keeps accepted sequence numbers in increasing order. Logs may arrive in any order, but retrieval does not need to sort them again.

### `Map<String, List<LogEntry>>`

`destinationStreams` maps `FILE` and `TOOL` to separate lists. Adding an entry to the selected list represents writing that log to its destination stream. Acknowledgement is returned only after this list write succeeds.

### Validation Sets

Two `Set<String>` objects hold the supported module names and log levels. The destination map itself tells us whether a submission destination is supported.

### Synchronization

The public methods are `synchronized`. This keeps duplicate checking, destination writing, and insertion into the shared collections together when several modules use the same framework instance concurrently.

## How `logActivity` Works

For each submission:

1. Validate the sequence number, module, destination, level, and activity-data length.
2. Use the map to reject a sequence number that was already accepted.
3. Create a `LogEntry`.
4. Add the entry to the selected FILE or TOOL list.
5. After the write succeeds, add the entry to the lookup map and its sequence number to the sorted set.
6. Return `"ACK,sequenceNumber"`.

An invalid submission is not added to any collection. Therefore, its sequence number remains available for a later corrected submission.

## How `getLogs` Works

The `TreeSet` provides the sequence numbers in the inclusive range:

```java
sortedSequenceNumbers.subSet(minimum, true, maximum, true)
```

We visit those numbers in their already sorted order and use `logsBySequence` to get each complete log.

For every log, we check all three filters:

```java
destination matches AND module matches AND level matches
```

A filter matches when it equals the stored value or when it is `ALL`. Matching logs are formatted and appended directly to the result, so no extra sorting is required.


## Complexity Analysis

Let `n` be the number of accepted logs, `r` be the number of logs inside the requested sequence range, and `k` be the number of logs returned.

- `logActivity`: `O(log n)` time because adding the sequence number to the `TreeSet` takes `O(log n)`. Map lookup, map insertion, and list insertion take `O(1)` average time.
- `getLogs`: `O(log n + r)` time to locate and scan the requested portion of the sorted set, plus the time required to build the `k` output strings.
- Space: `O(n)` for the logs, sorted sequence numbers, and destination-list references.

## Java Solution

```java
import java.util.*;

public class MultiModuleLoggingFramework {
    private static final long MIN_SEQUENCE_NUMBER = 1L;
    private static final long MAX_SEQUENCE_NUMBER = 1_000_000_000L;
    private static final int MAX_LOG_DATA_LENGTH = 10_000;

    private static final Set<String> VALID_MODULES = new HashSet<>(
            Arrays.asList("MODULE1", "MODULE2", "MODULE3"));
    private static final Set<String> VALID_LEVELS = new HashSet<>(
            Arrays.asList("INFO", "WARNING", "ERROR"));

    // A map gives fast duplicate checks and direct access to a log.
    private final Map<Long, LogEntry> logsBySequence;

    // A TreeSet keeps all accepted sequence numbers sorted.
    private final TreeSet<Long> sortedSequenceNumbers;

    // Each destination has its own in-memory stream of written logs.
    private final Map<String, List<LogEntry>> destinationStreams;

    public MultiModuleLoggingFramework() {
        logsBySequence = new HashMap<>();
        sortedSequenceNumbers = new TreeSet<>();

        destinationStreams = new HashMap<>();
        destinationStreams.put("FILE", new ArrayList<>());
        destinationStreams.put("TOOL", new ArrayList<>());
    }

    public synchronized String logActivity(long sequenceNumber,
                                           String moduleName,
                                           String destinationType,
                                           String logLevel,
                                           String logData) {
        if (!isValidLog(sequenceNumber, moduleName, destinationType, logLevel, logData)
                || logsBySequence.containsKey(sequenceNumber)) {
            return "REJECTED";
        }

        LogEntry entry = new LogEntry(
                sequenceNumber, moduleName, destinationType, logLevel, logData);

        // Write to the selected destination stream before acknowledging the log.
        boolean written = destinationStreams.get(destinationType).add(entry);
        if (!written) {
            return "REJECTED";
        }

        logsBySequence.put(sequenceNumber, entry);
        sortedSequenceNumbers.add(sequenceNumber);
        return "ACK," + sequenceNumber;
    }

    public synchronized List<String> getLogs(String destinationType,
                                             String moduleName,
                                             String logLevel,
                                             long minimumSequenceNumber,
                                             long maximumSequenceNumber) {
        List<String> result = new ArrayList<>();

        if (!isValidQuery(destinationType, moduleName, logLevel,
                minimumSequenceNumber, maximumSequenceNumber)) {
            return result;
        }

        for (Long sequenceNumber : sortedSequenceNumbers.subSet(
                minimumSequenceNumber, true, maximumSequenceNumber, true)) {
            LogEntry entry = logsBySequence.get(sequenceNumber);

            if (matches(destinationType, entry.destinationType)
                    && matches(moduleName, entry.moduleName)
                    && matches(logLevel, entry.logLevel)) {
                result.add(entry.toOutputString());
            }
        }

        return result;
    }

    private boolean isValidLog(long sequenceNumber,
                               String moduleName,
                               String destinationType,
                               String logLevel,
                               String logData) {
        return sequenceNumber >= MIN_SEQUENCE_NUMBER
                && sequenceNumber <= MAX_SEQUENCE_NUMBER
                && VALID_MODULES.contains(moduleName)
                && destinationStreams.containsKey(destinationType)
                && VALID_LEVELS.contains(logLevel)
                && logData != null
                && !logData.isEmpty()
                && logData.length() <= MAX_LOG_DATA_LENGTH;
    }

    private boolean isValidQuery(String destinationType,
                                 String moduleName,
                                 String logLevel,
                                 long minimumSequenceNumber,
                                 long maximumSequenceNumber) {
        return ("ALL".equals(destinationType)
                    || destinationStreams.containsKey(destinationType))
                && ("ALL".equals(moduleName) || VALID_MODULES.contains(moduleName))
                && ("ALL".equals(logLevel) || VALID_LEVELS.contains(logLevel))
                && minimumSequenceNumber >= MIN_SEQUENCE_NUMBER
                && maximumSequenceNumber <= MAX_SEQUENCE_NUMBER
                && minimumSequenceNumber <= maximumSequenceNumber;
    }

    private boolean matches(String filter, String value) {
        return "ALL".equals(filter) || filter.equals(value);
    }

    private static class LogEntry {
        private final long sequenceNumber;
        private final String moduleName;
        private final String destinationType;
        private final String logLevel;
        private final String logData;

        private LogEntry(long sequenceNumber,
                         String moduleName,
                         String destinationType,
                         String logLevel,
                         String logData) {
            this.sequenceNumber = sequenceNumber;
            this.moduleName = moduleName;
            this.destinationType = destinationType;
            this.logLevel = logLevel;
            this.logData = logData;
        }

        private String toOutputString() {
            return sequenceNumber + "," + destinationType + ","
                    + moduleName + "," + logLevel + "," + logData;
        }
    }
}
```
