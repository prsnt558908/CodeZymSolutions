# Design Backup System for a File Storage Service in Java

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

```java
class BackupRecord {
    String type;
    Map<String, String> allFiles;                // full copy, saved for EVERY backup
    Map<String, String> data = new HashMap<>();  // fileName -> text for readBackupData
}

// inside createBackup, after the checks
BackupRecord backup = new BackupRecord();
backup.type = backupType;
backup.allFiles = new HashMap<>(files);

if (backupType.equals("FULL")) {
    for (String name : files.keySet()) {
        backup.data.put(name, "FILE," + name + "," + files.get(name));
    }
} else if (backupType.equals("DIFFERENTIAL")) {
    // compare files with the latest FULL backup, store "UPSERT,..." for changed files
} else if (backupType.equals("LOG")) {
    // group pending writes by file, store "WRITE,...|WRITE,..." for each file
}

// inside restoreBackup, after the checks
files = new HashMap<>(backup.allFiles);
```

### Problems With This Approach

**1. Too much memory.** Every backup keeps a copy of every file, even `DIFFERENTIAL` and `LOG` backups. With 1,000 files and 1,000 backups, that is a million file copies. Saving space is the whole reason `DIFFERENTIAL` and `LOG` backups exist.

**2. One class knows everything.** Every type specific rule lives in `if-else` branches inside `BackupSystem`. Adding a new backup type means editing that one big class in many places.

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

So we create one abstract parent class `Backup` with these two methods, and one child class per type. This is the **Strategy** pattern. `BackupSystem` keeps all backups in a `Map<Integer, Backup>` and just calls `backup.readData()` or `backup.applyTo()`. It never needs an `if-else` on the type to read or restore data.

For example, `readBackupData` is just a lookup, a type check, and one call to `backup.readData(fileName)`.

Only creation needs to turn a type string into an object. The factory method `newBackup()` does that in one spot. It returns `null` for an invalid type, so `createBackup` gets the invalid type check for free.

Now adding a new backup type means one new class and one new line in the factory.

### What About the Command Pattern?

A `LOG` backup stores write operations and replays them later. That sounds like the **Command** pattern, where each operation is an object with an `execute()` method.

But here there is only one kind of operation, a write, and we never need undo. A tiny `WriteOperation` class with `fileName` and `content` is enough. A full Command setup would add classes without making anything simpler.

### The Important Classes

**`WriteOperation`**: one `writeFile` call. A `LOG` backup must keep writes in their original order, and the same file can be written many times, so we store each write as its own small object in a list.

**`Backup`**: the abstract parent class. Besides `backupId` and `backupType`, it has:

- `creationOrder`: 0 for the first backup created, 1 for the next, and so on. Backup ids come in any order, so ids cannot tell us which backup is older. This number can.
- `dependency`: the backup to restore first, `null` for `FULL`.

**`FullBackup`**: keeps `snapshot`, a copy of all files. It must be a copy, because backup data never changes, while the real files keep changing.

**`DifferentialBackup`**: keeps `changes`, only the files whose content differs from its base `FULL` backup. A file missing from the base counts as changed, even if its content is an empty string:

```java
String baseContent = base.snapshot.get(fileName); // null if the file is new
if (baseContent == null || !baseContent.equals(content)) {
    changes.put(fileName, content);
}
```

**`LogBackup`**: keeps two things:

- `writes`: all writes in original order, replayed on restore.
- `contentsByFile`: file name to the list of contents written to it. `readBackupData` asks about one file at a time, so this map gives us that file's writes directly, without scanning the whole log.

**`BackupSystem`**: the main class.

- `files`: the current file system.
- `backups`: all backups by id. Ids can be any number in any order, so a map fits better than a list.
- `pendingWrites`: the writes the next `LOG` backup will store.
- `latestBackup` and `latestFullBackup`: the two pointers from the rules.

### How `createBackup` Works

1. Return `false` if the id is already used.
2. Return `false` if there is no `FULL` backup yet and the type is not `FULL`.
3. Ask the factory for a backup object. `null` means the type is invalid, so return `false`.
4. Save the backup, move `latestBackup` to it (and `latestFullBackup` too, if it is `FULL`), and start a fresh `pendingWrites` list.

All checks happen before anything changes, so a `false` result leaves the state untouched.

### How `restoreBackup` Works

1. Return `false` if the backup does not exist or its type does not match.
2. Return `false` if its `creationOrder` is smaller than the latest `FULL` backup's `creationOrder`.
3. Walk the `dependency` links to collect the chain, from this backup back to its `FULL` backup.
4. Start from an empty map and call `applyTo()` from the end of the chain (the `FULL` backup) to the start.
5. Replace `files` with the rebuilt map, move `latestBackup` here, and clear `pendingWrites`.

```java
List<Backup> chain = new ArrayList<>();
for (Backup current = backup; current != null; current = current.dependency) {
    chain.add(current);
}

Map<String, String> state = new HashMap<>();
for (int i = chain.size() - 1; i >= 0; i--) {
    chain.get(i).applyTo(state);
}
```

We use a loop here, not recursion. With recursion, a chain of thousands of `LOG` backups could overflow the call stack. A loop has no such limit.


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

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupSystem {
    Map<String, String> files = new HashMap<>();            // current file system: fileName -> content
    Map<Integer, Backup> backups = new HashMap<>();         // backupId -> backup
    List<WriteOperation> pendingWrites = new ArrayList<>(); // writes done after the latest backup pointer moved
    Backup latestBackup = null;                             // latest backup pointer
    FullBackup latestFullBackup = null;                     // latest successful FULL backup

    public BackupSystem() {
    }

    public void writeFile(String fileName, String content) {
        files.put(fileName, content);
        pendingWrites.add(new WriteOperation(fileName, content));
    }

    public String readFile(String fileName) {
        return files.getOrDefault(fileName, "");
    }

    public boolean createBackup(int backupId, String backupType) {
        if (backups.containsKey(backupId)) return false;
        // DIFFERENTIAL and LOG backups need a FULL backup to build on
        if (latestFullBackup == null && !backupType.equals("FULL")) return false;

        Backup backup = newBackup(backupId, backupType);
        if (backup == null) return false; // invalid backup type

        backups.put(backupId, backup);
        if (backupType.equals("FULL")) latestFullBackup = (FullBackup) backup;
        latestBackup = backup;
        pendingWrites = new ArrayList<>(); // the pointer moved, so start a fresh write log
        return true;
    }

    public String readBackupData(int backupId, String backupType, String fileName) {
        Backup backup = backups.get(backupId);
        if (backup == null || !backup.backupType.equals(backupType)) return "";
        return backup.readData(fileName);
    }

    public boolean restoreBackup(int backupId, String backupType) {
        Backup backup = backups.get(backupId);
        if (backup == null || !backup.backupType.equals(backupType)) return false;
        // only the latest FULL backup, or a backup created after it, can be restored
        if (backup.creationOrder < latestFullBackup.creationOrder) return false;

        // follow the dependency links: backup -> ... -> its FULL backup
        List<Backup> chain = new ArrayList<>();
        for (Backup current = backup; current != null; current = current.dependency) {
            chain.add(current);
        }

        // rebuild the files, starting from the FULL backup at the end of the chain
        Map<String, String> state = new HashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            chain.get(i).applyTo(state);
        }

        files = state;
        latestBackup = backup;
        pendingWrites = new ArrayList<>(); // restore is not logged, and older writes are dropped
        return true;
    }

    // Simple Factory: the only place that turns a type string into a backup object.
    // Returns null if the type is invalid.
    Backup newBackup(int backupId, String backupType) {
        int order = backups.size(); // backups are never removed, so this counts 0, 1, 2, ...
        if (backupType.equals("FULL")) {
            return new FullBackup(backupId, order, files);
        }
        if (backupType.equals("DIFFERENTIAL")) {
            return new DifferentialBackup(backupId, order, latestFullBackup, files);
        }
        if (backupType.equals("LOG")) {
            return new LogBackup(backupId, order, latestBackup, pendingWrites);
        }
        return null;
    }
}

// One writeFile call, kept in order so a LOG backup can replay it later.
class WriteOperation {
    String fileName;
    String content;

    WriteOperation(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
    }
}

// Parent of all backup types. BackupSystem only talks to backups through this class.
abstract class Backup {
    int backupId;
    String backupType;
    int creationOrder;  // 0 for the first backup created, 1 for the next, and so on
    Backup dependency;  // backup that must be restored before this one, null for FULL

    Backup(int backupId, String backupType, int creationOrder, Backup dependency) {
        this.backupId = backupId;
        this.backupType = backupType;
        this.creationOrder = creationOrder;
        this.dependency = dependency;
    }

    // Exact data this backup stores for the file, or "" if it stores nothing for it.
    abstract String readData(String fileName);

    // Puts this backup's own changes on top of the state rebuilt from its dependency.
    abstract void applyTo(Map<String, String> state);
}

class FullBackup extends Backup {
    Map<String, String> snapshot; // copy of every file at creation time

    FullBackup(int backupId, int creationOrder, Map<String, String> files) {
        super(backupId, "FULL", creationOrder, null);
        snapshot = new HashMap<>(files);
    }

    String readData(String fileName) {
        if (!snapshot.containsKey(fileName)) return "";
        return "FILE," + fileName + "," + snapshot.get(fileName);
    }

    void applyTo(Map<String, String> state) {
        state.putAll(snapshot);
    }
}

class DifferentialBackup extends Backup {
    Map<String, String> changes = new HashMap<>(); // files whose final content differs from the base

    DifferentialBackup(int backupId, int creationOrder, FullBackup base, Map<String, String> files) {
        super(backupId, "DIFFERENTIAL", creationOrder, base);
        for (String fileName : files.keySet()) {
            String content = files.get(fileName);
            String baseContent = base.snapshot.get(fileName); // null if the file is new
            if (baseContent == null || !baseContent.equals(content)) {
                changes.put(fileName, content);
            }
        }
    }

    String readData(String fileName) {
        if (!changes.containsKey(fileName)) return "";
        return "UPSERT," + fileName + "," + changes.get(fileName);
    }

    void applyTo(Map<String, String> state) {
        state.putAll(changes);
    }
}

class LogBackup extends Backup {
    List<WriteOperation> writes;                                // all writes in original order
    Map<String, List<String>> contentsByFile = new HashMap<>(); // fileName -> contents written, in order

    LogBackup(int backupId, int creationOrder, Backup previous, List<WriteOperation> pendingWrites) {
        super(backupId, "LOG", creationOrder, previous);
        writes = new ArrayList<>(pendingWrites);
        for (WriteOperation write : writes) {
            contentsByFile.putIfAbsent(write.fileName, new ArrayList<>());
            contentsByFile.get(write.fileName).add(write.content);
        }
    }

    String readData(String fileName) {
        List<String> contents = contentsByFile.get(fileName);
        if (contents == null) return "";
        List<String> entries = new ArrayList<>();
        for (String content : contents) {
            entries.add("WRITE," + fileName + "," + content);
        }
        return String.join("|", entries);
    }

    void applyTo(Map<String, String> state) {
        // replay the writes in the same order they happened
        for (WriteOperation write : writes) {
            state.put(write.fileName, write.content);
        }
    }
}
```