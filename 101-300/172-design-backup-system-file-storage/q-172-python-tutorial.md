# Design Backup System for a File Storage Service in Python

#### Problem Statement
[https://codezym.com/question/172-design-backup-system-file-storage](https://codezym.com/question/172-design-backup-system-file-storage)


## Core Idea

Think of every backup as a small note that says: **"start from that other backup, then apply my changes."**

A `FULL` backup is the only one that needs nothing else, because it keeps a copy of every file. A `DIFFERENTIAL` backup keeps only the files that changed since its `FULL` backup. A `LOG` backup keeps only the writes done after the latest backup pointer.

So to restore any backup, we follow these links back to a `FULL` backup, then apply the changes forward, one backup at a time.

The best fit here is a combination of two common design patterns. The **Strategy** pattern gives each backup type its own class that knows how to read and restore its data. A **Simple Factory** turns the type string into the right backup object. Together they keep all the type specific logic out of the main class.


## Key Rules in Short

| Type | Stores | Depends on | Data format |
|---|---|---|---|
| `FULL` | A copy of every file | Nothing | `FILE,fileName,content` |
| `DIFFERENTIAL` | Files whose final content differs from the latest `FULL` backup | That `FULL` backup | `UPSERT,fileName,content` |
| `LOG` | Every write done after the latest backup pointer, in order | The latest backup pointer | `WRITE,fileName,content` |

If a `LOG` backup has many writes for the same file, they are joined with `|`.

The system also tracks three things:

- **Latest backup pointer**: the last backup that was created or restored.
- **Latest full backup**: the last `FULL` backup created. Backups created before it can still be read, but not restored.
- **Pending writes**: the `writeFile` calls made since the latest backup pointer last moved. This list starts fresh after every successful create or restore.


## Simple Approach: Save Every File in Every Backup

The easiest idea: for every backup, whatever its type, save a full copy of all files. Restore then becomes one line: copy the saved files back.

This works because every backup represents the files exactly as they were when it was created. It is the classic **Memento** pattern: save the whole state, bring it back later.

```python
class BackupRecord:
    def __init__(self, backupType, allFiles):
        self.backupType = backupType
        self.allFiles = allFiles  # full copy, saved for EVERY backup
        self.data = {}            # fileName -> text for readBackupData


# inside createBackup, after the checks
backup = BackupRecord(backupType, dict(self.files))

if backupType == "FULL":
    for name, content in self.files.items():
        backup.data[name] = "FILE," + name + "," + content
elif backupType == "DIFFERENTIAL":
    pass  # compare files with the latest FULL backup, store "UPSERT,..." for changed files
elif backupType == "LOG":
    pass  # group pending writes by file, store "WRITE,...|WRITE,..." for each file

# inside restoreBackup, after the checks
self.files = dict(backup.allFiles)
```

### Problems With This Approach

**1. Too much memory.** Every backup keeps a copy of every file, even `DIFFERENTIAL` and `LOG` backups. With 1,000 files and 1,000 backups, that is a million file copies. Saving space is the whole reason `DIFFERENTIAL` and `LOG` backups exist.

**2. One class knows everything.** Every type specific rule lives in `if-elif` branches inside `BackupSystem`. Adding a new backup type means editing that one big class in many places.

So Memento alone is not optimal. It fits `FULL` backups well, but `DIFFERENTIAL` and `LOG` backups are about storing **changes**, not full copies.


## Better Approach: Store Only Changes and Follow the Links

Each backup now stores **only its own data**, plus a link called `dependency` to the backup it builds on (the "Depends on" column above).

For example, suppose backups were created in this order: `FULL 1`, `LOG 2`, `DIFFERENTIAL 3`, `LOG 4`. The links for `LOG 4` look like this:

```
LOG 4  -->  DIFFERENTIAL 3  -->  FULL 1
```

To restore `LOG 4`, we copy the files of `FULL 1`, apply the changes of `DIFFERENTIAL 3`, then replay the writes of `LOG 4`.

`LOG 2` is not in the chain at all. `DIFFERENTIAL 3` already contains every change made since `FULL 1`.

### Design Patterns Used: Strategy and Simple Factory

All three backup types do the same two jobs, each in its own way:

- `readData(fileName)`: return the exact stored text for one file, or `""`.
- `applyTo(state)`: put this backup's changes on top of a state.

So we create one abstract parent class `Backup` with these two methods, and one child class per type. `Backup` extends `ABC`, and both methods are marked with `@abstractmethod`, so every child class must write its own version. This is the **Strategy** pattern.

`BackupSystem` keeps all backups in a dictionary from id to `Backup`, and just calls `backup.readData()` or `backup.applyTo()`. It never needs an `if-elif` on the type to read or restore data.

For example, `readBackupData` is just a lookup, a type check, and one call to `backup.readData(fileName)`.

Only creation needs to turn a type string into an object. The factory method `newBackup()` does that in one spot. It returns `None` for an invalid type, so `createBackup` gets the invalid type check for free.

Now adding a new backup type means one new class and one new line in the factory.

### What About the Command Pattern?

A `LOG` backup stores write operations and replays them later. That sounds like the **Command** pattern, where each operation is an object with an `execute()` method.

But here there is only one kind of operation, a write, and we never need undo. A tiny `WriteOperation` class with `fileName` and `content` is enough. A full Command setup would add classes without making anything simpler.

### The Important Classes

**`WriteOperation`**: one `writeFile` call. A `LOG` backup must keep writes in their original order, and the same file can be written many times, so we store each write as its own small object in a list.

**`Backup`**: the abstract parent class. Besides `backupId` and `backupType`, it has:

- `creationOrder`: 0 for the first backup created, 1 for the next, and so on. Backup ids come in any order, so ids cannot tell us which backup is older. This number can.
- `dependency`: the backup to restore first, `None` for `FULL`.

**`FullBackup`**: keeps `snapshot`, a copy of all files. It must be a copy, because backup data never changes, while the real files keep changing.

**`DifferentialBackup`**: keeps `changes`, only the files whose content differs from its base `FULL` backup. A file missing from the base counts as changed, even if its content is an empty string:

```python
baseContent = base.snapshot.get(fileName)  # None if the file is new
if baseContent != content:
    self.changes[fileName] = content
```

`None` never equals a string, so a new file is always stored.

**`LogBackup`**: keeps two things:

- `writes`: all writes in original order, replayed on restore.
- `contentsByFile`: file name to the list of contents written to it. `readBackupData` asks about one file at a time, so this dictionary gives us that file's writes directly, without scanning the whole log.

**`BackupSystem`**: the main class.

- `files`: the current file system.
- `backups`: all backups by id. Ids can be any number in any order, so a dictionary fits better than a list.
- `pendingWrites`: the writes the next `LOG` backup will store.
- `latestBackup` and `latestFullBackup`: the two pointers from the rules.

### How `createBackup` Works

1. Return `False` if the id is already used.
2. Return `False` if there is no `FULL` backup yet and the type is not `FULL`.
3. Ask the factory for a backup object. `None` means the type is invalid, so return `False`.
4. Save the backup, move `latestBackup` to it (and `latestFullBackup` too, if it is `FULL`), and start a fresh `pendingWrites` list.

All checks happen before anything changes, so a `False` result leaves the state untouched.

### How `restoreBackup` Works

1. Return `False` if the backup does not exist or its type does not match.
2. Return `False` if its `creationOrder` is smaller than the latest `FULL` backup's `creationOrder`.
3. Walk the `dependency` links to collect the chain, from this backup back to its `FULL` backup.
4. Start from an empty dictionary and call `applyTo()` from the end of the chain (the `FULL` backup) to the start.
5. Replace `files` with the rebuilt dictionary, move `latestBackup` here, and clear `pendingWrites`.

```python
chain = []
current = backup
while current is not None:
    chain.append(current)
    current = current.dependency

state = {}
for item in reversed(chain):
    item.applyTo(state)
```

We use a loop here, not recursion. Python allows only about 1,000 nested calls by default, so a recursive version would crash on a chain of more than 1,000 `LOG` backups. A loop has no such limit.


## Complexity

Let `F` be the number of files and `W` the number of pending writes.

| Method | Time |
|---|---|
| `writeFile`, `readFile` | O(1) |
| `createBackup` for `FULL` or `DIFFERENTIAL` | O(F) |
| `createBackup` for `LOG` | O(W) |
| `readBackupData` | O(1) for `FULL` and `DIFFERENTIAL`, O(k) for `LOG`, where k is the number of writes to that file |
| `restoreBackup` | O(backups in the chain + data stored in them) |

Each write lands in at most one `LOG` backup, so all `LOG` creations together cost at most the total number of writes.


## Final Code

```python
from abc import ABC, abstractmethod
from typing import Dict, List, Optional


class WriteOperation:
    """One writeFile call, kept in order so a LOG backup can replay it later."""

    def __init__(self, fileName: str, content: str):
        self.fileName = fileName
        self.content = content


class Backup(ABC):
    """Parent of all backup types. BackupSystem only talks to backups through this class."""

    def __init__(self, backupId: int, backupType: str, creationOrder: int, dependency: Optional["Backup"]):
        self.backupId = backupId
        self.backupType = backupType
        self.creationOrder = creationOrder  # 0 for the first backup created, 1 for the next, and so on
        self.dependency = dependency        # backup that must be restored before this one, None for FULL

    @abstractmethod
    def readData(self, fileName: str) -> str:
        """Exact data this backup stores for the file, or "" if it stores nothing for it."""

    @abstractmethod
    def applyTo(self, state: Dict[str, str]) -> None:
        """Puts this backup's own changes on top of the state rebuilt from its dependency."""


class FullBackup(Backup):
    def __init__(self, backupId: int, creationOrder: int, files: Dict[str, str]):
        super().__init__(backupId, "FULL", creationOrder, None)
        self.snapshot = dict(files)  # copy of every file at creation time

    def readData(self, fileName: str) -> str:
        if fileName not in self.snapshot:
            return ""
        return "FILE," + fileName + "," + self.snapshot[fileName]

    def applyTo(self, state: Dict[str, str]) -> None:
        state.update(self.snapshot)


class DifferentialBackup(Backup):
    def __init__(self, backupId: int, creationOrder: int, base: FullBackup, files: Dict[str, str]):
        super().__init__(backupId, "DIFFERENTIAL", creationOrder, base)
        self.changes = {}  # files whose final content differs from the base
        for fileName, content in files.items():
            baseContent = base.snapshot.get(fileName)  # None if the file is new
            if baseContent != content:
                self.changes[fileName] = content

    def readData(self, fileName: str) -> str:
        if fileName not in self.changes:
            return ""
        return "UPSERT," + fileName + "," + self.changes[fileName]

    def applyTo(self, state: Dict[str, str]) -> None:
        state.update(self.changes)


class LogBackup(Backup):
    def __init__(self, backupId: int, creationOrder: int, previous: Backup, pendingWrites: List[WriteOperation]):
        super().__init__(backupId, "LOG", creationOrder, previous)
        self.writes = list(pendingWrites)  # all writes in original order
        self.contentsByFile = {}           # fileName -> contents written, in order
        for write in self.writes:
            self.contentsByFile.setdefault(write.fileName, []).append(write.content)

    def readData(self, fileName: str) -> str:
        contents = self.contentsByFile.get(fileName)
        if contents is None:
            return ""
        entries = ["WRITE," + fileName + "," + content for content in contents]
        return "|".join(entries)

    def applyTo(self, state: Dict[str, str]) -> None:
        # replay the writes in the same order they happened
        for write in self.writes:
            state[write.fileName] = write.content


class BackupSystem:
    def __init__(self):
        self.files: Dict[str, str] = {}                  # current file system: fileName -> content
        self.backups: Dict[int, Backup] = {}             # backupId -> backup
        self.pendingWrites: List[WriteOperation] = []    # writes done after the latest backup pointer moved
        self.latestBackup: Optional[Backup] = None       # latest backup pointer
        self.latestFullBackup: Optional[FullBackup] = None  # latest successful FULL backup

    def writeFile(self, fileName: str, content: str) -> None:
        self.files[fileName] = content
        self.pendingWrites.append(WriteOperation(fileName, content))

    def readFile(self, fileName: str) -> str:
        return self.files.get(fileName, "")

    def createBackup(self, backupId: int, backupType: str) -> bool:
        if backupId in self.backups:
            return False
        # DIFFERENTIAL and LOG backups need a FULL backup to build on
        if self.latestFullBackup is None and backupType != "FULL":
            return False

        backup = self.newBackup(backupId, backupType)
        if backup is None:
            return False  # invalid backup type

        self.backups[backupId] = backup
        if backupType == "FULL":
            self.latestFullBackup = backup
        self.latestBackup = backup
        self.pendingWrites = []  # the pointer moved, so start a fresh write log
        return True

    def readBackupData(self, backupId: int, backupType: str, fileName: str) -> str:
        backup = self.backups.get(backupId)
        if backup is None or backup.backupType != backupType:
            return ""
        return backup.readData(fileName)

    def restoreBackup(self, backupId: int, backupType: str) -> bool:
        backup = self.backups.get(backupId)
        if backup is None or backup.backupType != backupType:
            return False
        # only the latest FULL backup, or a backup created after it, can be restored
        if backup.creationOrder < self.latestFullBackup.creationOrder:
            return False

        # follow the dependency links: backup -> ... -> its FULL backup
        chain = []
        current = backup
        while current is not None:
            chain.append(current)
            current = current.dependency

        # rebuild the files, starting from the FULL backup at the end of the chain
        state = {}
        for item in reversed(chain):
            item.applyTo(state)

        self.files = state
        self.latestBackup = backup
        self.pendingWrites = []  # restore is not logged, and older writes are dropped
        return True

    def newBackup(self, backupId: int, backupType: str) -> Optional[Backup]:
        """Simple Factory: the only place that turns a type string into a backup object.
        Returns None if the type is invalid."""
        order = len(self.backups)  # backups are never removed, so this counts 0, 1, 2, ...
        if backupType == "FULL":
            return FullBackup(backupId, order, self.files)
        if backupType == "DIFFERENTIAL":
            return DifferentialBackup(backupId, order, self.latestFullBackup, self.files)
        if backupType == "LOG":
            return LogBackup(backupId, order, self.latestBackup, self.pendingWrites)
        return None
```