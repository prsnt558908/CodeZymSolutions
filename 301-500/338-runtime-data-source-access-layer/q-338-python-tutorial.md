# Design Runtime Data Source Access Layer in Python

#### Problem Statement
[https://codezym.com/question/338-runtime-data-source-access-layer](https://codezym.com/question/338-runtime-data-source-access-layer)

We need a service that saves and reads key-value records, where the place those records live (`"DATABASE"`, `"API"` or `"FILE"`) can be switched while the program is running.

The best fit is a combination of two design patterns, because each one solves a different half of the problem. **Strategy** handles *how* data is read and written: every data source is its own small class behind one common interface, so the service only says "read this key" or "write this key" and never cares which source is active. **Factory** handles *which* object to build: it is the single place that turns a name like `"FILE"` into the right object, and it tells us when a name is not supported.

One detail shapes the whole design: every source keeps its own records, and those records must still be there when we come back to that source later. So the service remembers each source it has already created, instead of building a fresh one on every switch.

## Understanding the Problem

- There are 3 sources. Each one has its own separate records and starts empty.
- `retrieveData` reads from the active source and returns `""` when the key is missing.
- `updateData` adds or replaces a record in the active source and always returns `True`.
- `changeDataSource` switches the active source. For an unknown name it returns `False` and nothing changes. Names are case-sensitive, so `"file"` is unknown.

Example 2 shows the tricky part. We store `status = active` in `API`, switch to `DATABASE` and store `status = pending` there, then switch back to `API`. Reading `status` now gives `"active"`.

So switching away from a source must never wipe its records.

## Solution 1: Simple If-Elif Approach

The most direct idea is to keep one dictionary per source, plus a string holding the name of the active source. Every method checks that string with an if-elif chain.

```python
class DataAccessService:
    def __init__(self, initialDataSource: str):
        # one dictionary of records for each data source
        self.database_records = {}
        self.api_records = {}
        self.file_records = {}
        # name of the active source: "DATABASE", "API" or "FILE"
        self.active_source = initialDataSource

    def retrieveData(self, key: str) -> str:
        if self.active_source == "DATABASE":
            return self.database_records.get(key, "")
        elif self.active_source == "API":
            return self.api_records.get(key, "")
        else:
            return self.file_records.get(key, "")

    def updateData(self, key: str, value: str) -> bool:
        if self.active_source == "DATABASE":
            self.database_records[key] = value
        elif self.active_source == "API":
            self.api_records[key] = value
        else:
            self.file_records[key] = value
        return True

    def changeDataSource(self, dataSourceType: str) -> bool:
        if dataSourceType in ("DATABASE", "API", "FILE"):
            self.active_source = dataSourceType
            return True
        return False  # unknown name, keep the current source
```

This works, but it does not scale well:

- **The same if-elif chain is repeated in every method.** Adding a 4th source like `"CACHE"` means editing every method and the name check. Forgetting one place gives a silent bug.
- **The service knows how every source works.** Here each branch is a single dictionary call. In a real system the `DATABASE` branch would run SQL, the `API` branch would make HTTP calls and the `FILE` branch would read files, all inside this one class.
- **Business logic is mixed with data access.** The problem asks us to keep them independent, and this design does the opposite.

## Solution 2: Strategy + Factory

### Why Strategy?

Strategy fits when the same job can be done in different ways and we want to pick or swap the way at runtime. Here the job is "read and write a record", and the ways are database, API and file.

We create a `DataSource` base class with `read` and `write`, and one class per source that inherits from it. The service keeps a single `DataSource` object called `active_source` and simply calls `self.active_source.read(key)`.

Switching sources now just means pointing `active_source` at a different object. There is no if-elif on source names anywhere in the service.

### Why Factory?

Something still has to turn the string `"DATABASE"` into a `DatabaseDataSource` object. If the service did that itself, it would again know about every concrete class.

`DataSourceFactory` keeps that knowledge in one place. It returns a new object for a supported name and `None` for anything else, which is exactly what `changeDataSource` needs to decide between `True` and `False`.

With both patterns together, adding a new source like `"CACHE"` means writing one new class and adding one `if` to the factory. `DataAccessService` does not change at all.

### Why Not State or Singleton?

- **State:** it also changes behavior at runtime, so it looks similar. But State is meant for an object whose behavior changes because its own situation changes, and the states usually decide what comes next, like a traffic light going green, then yellow, then red. Here nothing changes by itself. The caller picks the source by name, and the sources know nothing about each other. That is the job of Strategy.
- **Singleton for each source:** it looks like an easy way to keep records alive across switches, since the same object would always come back. But a Singleton is shared by the whole program, so a second `DataAccessService` would see the first one's records and would not start empty. A small dictionary inside each service gives the same benefit without this leak.

### Main Classes

```text
DataAccessService
  |-- active_source   : DataSource          (the strategy in use right now)
  |-- created_sources : name -> DataSource  (sources built so far)
  |-- factory         : DataSourceFactory   (builds a source from its name)

DataSource (abstract base class): read(key), write(key, value)
  |-- DatabaseDataSource
  |-- ApiDataSource
  |-- FileDataSource
```

**`DataSource` (abstract base class):** the common contract, `read(key)` and `write(key, value)`. Python has no `interface` keyword, so we use `ABC` with `@abstractmethod`. A subclass that forgets one of these methods cannot even be created. The service depends only on this class, and that is what keeps business logic independent of the source.

**`DatabaseDataSource`, `ApiDataSource`, `FileDataSource`:** one class per source, each with its own dictionary called `records`. A dictionary is the natural choice here: `records[key] = value` already means "add or replace", and `records.get(key, "")` returns the empty string for a missing key. Both run in O(1) on average.

In this problem all three keep data in memory, so their code looks the same. In a real system each would have very different code inside, and the service would still not change.

**`DataSourceFactory`:** turns a name into a new, empty data source, or returns `None` if the name is not supported. It uses exact `==` comparison, so case-sensitivity is handled for free.

**`DataAccessService`:** the business layer. It has two important fields:

- `active_source`: the strategy that `retrieveData` and `updateData` call.
- `created_sources`: a dictionary from a source name to the object already built for this service. This is what keeps old records alive. If we asked the factory for a new object on every switch, Example 2 would return `""` instead of `"active"`.

### How `changeDataSource` Works

```text
changeDataSource(type)
  |
  |-- already in created_sources?  yes --> make it active, return True
  |
  |-- not there yet: ask the factory to build it
        |-- factory returns None   --> unsupported, return False (active source unchanged)
        |-- factory returns object --> save it in created_sources, make it active, return True
```

The constructor simply calls `self.changeDataSource(initialDataSource)`, so the first source is set up the same way. The constraints guarantee that the initial name is valid.

A source is created only when it is first used. That is fine, because a brand new source is empty, exactly as the problem expects.

### Walkthrough of Example 2

| Call | Active | API records | DATABASE records | Returns |
|---|---|---|---|---|
| `DataAccessService("API")` | API | empty | not created yet | |
| `updateData("status", "active")` | API | status = active | not created yet | `True` |
| `changeDataSource("DATABASE")` | DATABASE | status = active | empty | `True` |
| `updateData("status", "pending")` | DATABASE | status = active | status = pending | `True` |
| `changeDataSource("API")` | API | status = active | status = pending | `True` |
| `retrieveData("status")` | API | status = active | status = pending | `"active"` |

### Complexity

- **Time:** `retrieveData`, `updateData` and `changeDataSource` each do at most a couple of dictionary operations, so each runs in O(1) on average.
- **Space:** O(total number of records stored across all sources).

### Python Code

The public method names stay in camelCase to match the problem, while internal names use the usual Python snake_case.

```python
from abc import ABC, abstractmethod
from typing import Optional


class DataSource(ABC):
    """Strategy interface. Every data source must know how to read and write a record.
    DataAccessService talks only to this interface, never to a concrete class."""

    @abstractmethod
    def read(self, key: str) -> str:
        """Returns the stored value, or "" if the key does not exist."""
        pass

    @abstractmethod
    def write(self, key: str, value: str) -> None:
        """Adds a new record, or replaces the value of an existing key."""
        pass


class DatabaseDataSource(DataSource):
    """Strategy for "DATABASE". A real version would run SQL queries here."""

    def __init__(self):
        self.records = {}

    def read(self, key: str) -> str:
        return self.records.get(key, "")

    def write(self, key: str, value: str) -> None:
        self.records[key] = value


class ApiDataSource(DataSource):
    """Strategy for "API". A real version would make HTTP calls here."""

    def __init__(self):
        self.records = {}

    def read(self, key: str) -> str:
        return self.records.get(key, "")

    def write(self, key: str, value: str) -> None:
        self.records[key] = value


class FileDataSource(DataSource):
    """Strategy for "FILE". A real version would read and write a file here."""

    def __init__(self):
        self.records = {}

    def read(self, key: str) -> str:
        return self.records.get(key, "")

    def write(self, key: str, value: str) -> None:
        self.records[key] = value


class DataSourceFactory:
    """Factory. The only place that knows the supported names
    and which class to build for each of them."""

    def create_data_source(self, source_type: str) -> Optional[DataSource]:
        """Returns a new, empty data source, or None if the type is not supported."""
        if source_type == "DATABASE":
            return DatabaseDataSource()
        if source_type == "API":
            return ApiDataSource()
        if source_type == "FILE":
            return FileDataSource()
        return None  # matching is exact, so "file" or "CACHE" land here


class DataAccessService:
    """Business layer. It does not know how any data source works,
    it just forwards each call to the currently active source."""

    def __init__(self, initialDataSource: str):
        self.factory = DataSourceFactory()
        # source name -> object already built for this service.
        # Reusing these objects keeps their records alive when we switch back.
        self.created_sources = {}
        # the strategy used by retrieveData and updateData
        self.active_source = None
        self.changeDataSource(initialDataSource)

    def retrieveData(self, key: str) -> str:
        return self.active_source.read(key)

    def updateData(self, key: str, value: str) -> bool:
        self.active_source.write(key, value)
        return True

    def changeDataSource(self, dataSourceType: str) -> bool:
        source = self.created_sources.get(dataSourceType)

        if source is None:
            # first time this type is used: let the factory build it
            source = self.factory.create_data_source(dataSourceType)
            if source is None:
                return False  # unsupported type, active source stays the same
            self.created_sources[dataSourceType] = source

        self.active_source = source
        return True
```