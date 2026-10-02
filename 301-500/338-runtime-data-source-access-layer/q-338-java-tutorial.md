# Design Runtime Data Source Access Layer in Java

#### Problem Statement
[https://codezym.com/question/338-runtime-data-source-access-layer](https://codezym.com/question/338-runtime-data-source-access-layer)

We need a service that saves and reads key-value records, where the place those records live (`"DATABASE"`, `"API"` or `"FILE"`) can be switched while the program is running.

The best fit is a combination of two design patterns, because each one solves a different half of the problem. **Strategy** handles *how* data is read and written: every data source is its own small class behind one common interface, so the service only says "read this key" or "write this key" and never cares which source is active. **Factory** handles *which* object to build: it is the single place that turns a name like `"FILE"` into the right object, and it tells us when a name is not supported.

One detail shapes the whole design: every source keeps its own records, and those records must still be there when we come back to that source later. So the service remembers each source it has already created, instead of building a fresh one on every switch.

## Understanding the Problem

- There are 3 sources. Each one has its own separate records and starts empty.
- `retrieveData` reads from the active source and returns `""` when the key is missing.
- `updateData` adds or replaces a record in the active source and always returns `true`.
- `changeDataSource` switches the active source. For an unknown name it returns `false` and nothing changes. Names are case-sensitive, so `"file"` is unknown.

Example 2 shows the tricky part. We store `status = active` in `API`, switch to `DATABASE` and store `status = pending` there, then switch back to `API`. Reading `status` now gives `"active"`.

So switching away from a source must never wipe its records.

## Solution 1: Simple If-Else Approach

The most direct idea is to keep one `HashMap` per source, plus a string holding the name of the active source. Every method checks that string with an if-else chain.

```java
import java.util.HashMap;
import java.util.Map;

public class DataAccessService {

    // one map of records for each data source
    Map<String, String> databaseRecords = new HashMap<>();
    Map<String, String> apiRecords = new HashMap<>();
    Map<String, String> fileRecords = new HashMap<>();

    // name of the active source: "DATABASE", "API" or "FILE"
    String activeSource;

    public DataAccessService(String initialDataSource) {
        activeSource = initialDataSource;
    }

    public String retrieveData(String key) {
        if (activeSource.equals("DATABASE")) {
            return databaseRecords.getOrDefault(key, "");
        } else if (activeSource.equals("API")) {
            return apiRecords.getOrDefault(key, "");
        } else {
            return fileRecords.getOrDefault(key, "");
        }
    }

    public boolean updateData(String key, String value) {
        if (activeSource.equals("DATABASE")) {
            databaseRecords.put(key, value);
        } else if (activeSource.equals("API")) {
            apiRecords.put(key, value);
        } else {
            fileRecords.put(key, value);
        }
        return true;
    }

    public boolean changeDataSource(String dataSourceType) {
        if (dataSourceType.equals("DATABASE") || dataSourceType.equals("API")
                || dataSourceType.equals("FILE")) {
            activeSource = dataSourceType;
            return true;
        }
        return false; // unknown name, keep the current source
    }
}
```

This works, but it does not scale well:

- **The same if-else chain is repeated in every method.** Adding a 4th source like `"CACHE"` means editing every method and the name check. Forgetting one place gives a silent bug.
- **The service knows how every source works.** Here each branch is a single map call. In a real system the `DATABASE` branch would run SQL, the `API` branch would make HTTP calls and the `FILE` branch would read files, all inside this one class.
- **Business logic is mixed with data access.** The problem asks us to keep them independent, and this design does the opposite.

## Solution 2: Strategy + Factory

### Why Strategy?

Strategy fits when the same job can be done in different ways and we want to pick or swap the way at runtime. Here the job is "read and write a record", and the ways are database, API and file.

We create a `DataSource` interface with `read` and `write`, and one class per source that implements it. The service keeps a single `DataSource` reference called `activeSource` and simply calls `activeSource.read(key)`.

Switching sources now just means pointing `activeSource` at a different object. There is no if-else on source names anywhere in the service.

### Why Factory?

Something still has to turn the string `"DATABASE"` into a `DatabaseDataSource` object. If the service did that itself, it would again know about every concrete class.

`DataSourceFactory` keeps that knowledge in one place. It returns a new object for a supported name and `null` for anything else, which is exactly what `changeDataSource` needs to decide between `true` and `false`.

With both patterns together, adding a new source like `"CACHE"` means writing one new class and adding one `case` to the factory. `DataAccessService` does not change at all.

### Why Not State or Singleton?

- **State:** it also changes behavior at runtime, so it looks similar. But State is meant for an object whose behavior changes because its own situation changes, and the states usually decide what comes next, like a traffic light going green, then yellow, then red. Here nothing changes by itself. The caller picks the source by name, and the sources know nothing about each other. That is the job of Strategy.
- **Singleton for each source:** it looks like an easy way to keep records alive across switches, since the same object would always come back. But a Singleton is shared by the whole program, so a second `DataAccessService` would see the first one's records and would not start empty. A small map inside each service gives the same benefit without this leak.

### Main Classes

```text
DataAccessService
  |-- activeSource   : DataSource          (the strategy in use right now)
  |-- createdSources : name -> DataSource  (sources built so far)
  |-- factory        : DataSourceFactory   (builds a source from its name)

DataSource (interface): read(key), write(key, value)
  |-- DatabaseDataSource
  |-- ApiDataSource
  |-- FileDataSource
```

**`DataSource` (interface):** the common contract, `read(key)` and `write(key, value)`. The service depends only on this, and that is what keeps business logic independent of the source.

**`DatabaseDataSource`, `ApiDataSource`, `FileDataSource`:** one class per source, each with its own `HashMap<String, String>` called `records`. A `HashMap` is the natural choice here: `put` already means "add or replace", and `getOrDefault(key, "")` returns the empty string for a missing key. Both run in O(1) on average.

In this problem all three keep data in memory, so their code looks the same. In a real system each would have very different code inside, and the service would still not change.

**`DataSourceFactory`:** turns a name into a new, empty data source, or returns `null` if the name is not supported. It uses exact string matching, so case-sensitivity is handled for free.

**`DataAccessService`:** the business layer. It has two important fields:

- `activeSource`: the strategy that `retrieveData` and `updateData` call.
- `createdSources`: a `HashMap<String, DataSource>` from a source name to the object already built for this service. This is what keeps old records alive. If we asked the factory for a new object on every switch, Example 2 would return `""` instead of `"active"`.

### How `changeDataSource` Works

```text
changeDataSource(type)
  |
  |-- already in createdSources?  yes --> make it active, return true
  |
  |-- not there yet: ask the factory to build it
        |-- factory returns null   --> unsupported, return false (active source unchanged)
        |-- factory returns object --> save it in createdSources, make it active, return true
```

The constructor simply calls `changeDataSource(initialDataSource)`, so the first source is set up the same way. The constraints guarantee that the initial name is valid.

A source is created only when it is first used. That is fine, because a brand new source is empty, exactly as the problem expects.

### Walkthrough of Example 2

| Call | Active | API records | DATABASE records | Returns |
|---|---|---|---|---|
| `DataAccessService("API")` | API | empty | not created yet | |
| `updateData("status", "active")` | API | status = active | not created yet | `true` |
| `changeDataSource("DATABASE")` | DATABASE | status = active | empty | `true` |
| `updateData("status", "pending")` | DATABASE | status = active | status = pending | `true` |
| `changeDataSource("API")` | API | status = active | status = pending | `true` |
| `retrieveData("status")` | API | status = active | status = pending | `"active"` |

### Complexity

- **Time:** `retrieveData`, `updateData` and `changeDataSource` each do at most a couple of `HashMap` operations, so each runs in O(1) on average.
- **Space:** O(total number of records stored across all sources).

### Java Code

```java
import java.util.HashMap;
import java.util.Map;

/**
 * Strategy interface. Every data source must know how to read and write a record.
 * DataAccessService talks only to this interface, never to a concrete class.
 */
interface DataSource {

    /** Returns the stored value, or "" if the key does not exist. */
    String read(String key);

    /** Adds a new record, or replaces the value of an existing key. */
    void write(String key, String value);
}

/** Strategy for "DATABASE". A real version would run SQL queries here. */
class DatabaseDataSource implements DataSource {

    Map<String, String> records = new HashMap<>();

    public String read(String key) {
        return records.getOrDefault(key, "");
    }

    public void write(String key, String value) {
        records.put(key, value);
    }
}

/** Strategy for "API". A real version would make HTTP calls here. */
class ApiDataSource implements DataSource {

    Map<String, String> records = new HashMap<>();

    public String read(String key) {
        return records.getOrDefault(key, "");
    }

    public void write(String key, String value) {
        records.put(key, value);
    }
}

/** Strategy for "FILE". A real version would read and write a file here. */
class FileDataSource implements DataSource {

    Map<String, String> records = new HashMap<>();

    public String read(String key) {
        return records.getOrDefault(key, "");
    }

    public void write(String key, String value) {
        records.put(key, value);
    }
}

/**
 * Factory. The only place that knows the supported names
 * and which class to build for each of them.
 */
class DataSourceFactory {

    /** Returns a new, empty data source, or null if the type is not supported. */
    DataSource createDataSource(String type) {
        switch (type) {
            case "DATABASE":
                return new DatabaseDataSource();
            case "API":
                return new ApiDataSource();
            case "FILE":
                return new FileDataSource();
            default:
                return null; // matching is exact, so "file" or "CACHE" land here
        }
    }
}

/**
 * Business layer. It does not know how any data source works,
 * it just forwards each call to the currently active source.
 */
public class DataAccessService {

    DataSourceFactory factory = new DataSourceFactory();

    // source name -> object already built for this service.
    // Reusing these objects keeps their records alive when we switch back.
    Map<String, DataSource> createdSources = new HashMap<>();

    // the strategy used by retrieveData and updateData
    DataSource activeSource;

    public DataAccessService(String initialDataSource) {
        changeDataSource(initialDataSource);
    }

    public String retrieveData(String key) {
        return activeSource.read(key);
    }

    public boolean updateData(String key, String value) {
        activeSource.write(key, value);
        return true;
    }

    public boolean changeDataSource(String dataSourceType) {
        DataSource source = createdSources.get(dataSourceType);

        if (source == null) {
            // first time this type is used: let the factory build it
            source = factory.createDataSource(dataSourceType);
            if (source == null) {
                return false; // unsupported type, active source stays the same
            }
            createdSources.put(dataSourceType, source);
        }

        activeSource = source;
        return true;
    }
}
```