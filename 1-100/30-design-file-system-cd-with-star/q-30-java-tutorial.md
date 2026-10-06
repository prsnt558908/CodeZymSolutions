# Design a File System (cd with '*') in Java

#### Problem Statement
[https://codezym.com/question/30-design-file-system-cd-with-star](https://codezym.com/question/30-design-file-system-cd-with-star)

## Core Idea

A file system is just a **tree of folders** (directories). The root `/` sits at the top, and every folder knows its parent and its children.

Once we have this tree, every command becomes a short walk. `mkdir` walks down the path and creates missing folders. `cd` walks down the path and gives up if a folder is missing. `pwd` just reads the path of the folder we are standing in.

The design pattern that fits best is a simple form of **Composite**: a `Directory` object holds other `Directory` objects, exactly like a folder tree. There are no files in this problem, so we skip the full Composite setup with a shared interface.

Patterns like **Command** or **Strategy** look good on paper, but here they would only add extra classes.

For the wildcard `*`, every folder remembers its **smallest child**, so `*` is answered instantly without any searching.

We will start with a simple solution that stores every folder as a full path string, see where it gets slow, and then improve it into a real folder tree.

## Understanding the Rules

Both `mkdir` and `cd` read a path in the same way:

1. **Pick the start:** root `/` if the path starts with `/`, otherwise the current folder.
2. **Split by `/`** and handle one segment at a time.
3. **Empty segment or `.`:** stay in the same folder. Empty segments come from extra slashes like `//` and are simply skipped.
4. **`..`:** go to the parent. The parent of root is root.
5. **`*`:** go to the child with the smallest name (dictionary order). If there is no child, stay where you are.
6. **Any other name:** `mkdir` creates the folder if it is missing. `cd` fails if it is missing.

A failed `cd` must not move us. So `cd` first finds the target folder and changes the current folder only when the whole path was valid.

Two small points about `*`:

- The rule lists `..` as a last fallback, but `.` always works. So in practice `*` either picks the smallest child or stays.
- The choice is final. In `cd /*/x`, if the smallest child of `/` has no `x`, the command fails. It does not try other children.

## Solution 1: Simple Approach (Set of Full Paths)

### Idea

Save every folder as its full path string inside a `Set`, like `"/"`, `"/a"` and `"/a/b"`. The current folder is also just a string.

- `pwd` returns that string.
- `mkdir` builds the path one segment at a time and adds every piece to the set.
- `cd` builds the path the same way and checks the set at every step.
- `..` cuts off the last part, so `"/a/b"` becomes `"/a"`.
- `*` scans the whole set for direct children of the current folder and picks the smallest.

**Why a `Set`?** It quickly tells us whether a folder exists, and it ignores duplicates. So creating an existing folder does nothing, for free.

### Code

```java
import java.util.HashSet;
import java.util.Set;

/**
 * Simple approach: every folder is saved as its full path string, like "/a/b".
 */
public class FileSystemShell {
    Set<String> folders = new HashSet<>();   // full paths of all folders that exist
    String currentPath = "/";                // folder we are standing in

    public FileSystemShell() {
        folders.add("/");
    }

    public String pwd() {
        return currentPath;
    }

    /** Walks the path and adds every folder on the way (like mkdir -p). */
    public void mkdir(String path) {
        String folder = path.startsWith("/") ? "/" : currentPath;
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;   // "//" or "." : stay here

            if (part.equals("..")) {
                folder = parentOf(folder);
            } else {
                folder = childOf(folder, part);
                folders.add(folder);   // does nothing if it already exists
            }
        }
    }

    /** Walks the path. If any folder is missing, we stay where we were. */
    public void cd(String path) {
        String folder = path.startsWith("/") ? "/" : currentPath;
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;

            if (part.equals("..")) {
                folder = parentOf(folder);
            } else if (part.equals("*")) {
                folder = smallestChildOf(folder);
            } else {
                folder = childOf(folder, part);
                if (!folders.contains(folder)) return;   // cd fails
            }
        }
        currentPath = folder;
    }

    /** "/" + "a" gives "/a", and "/a" + "b" gives "/a/b". */
    String childOf(String folder, String name) {
        return folder.equals("/") ? "/" + name : folder + "/" + name;
    }

    /** "/a/b" gives "/a", "/a" gives "/", and "/" stays "/". */
    String parentOf(String folder) {
        int lastSlash = folder.lastIndexOf('/');
        return lastSlash == 0 ? "/" : folder.substring(0, lastSlash);
    }

    /**
     * Slow part: scans EVERY folder to find the direct children of this folder
     * and returns the smallest one. With no children, '*' acts like '.'.
     */
    String smallestChildOf(String folder) {
        String prefix = folder.equals("/") ? "/" : folder + "/";
        String smallest = null;
        for (String candidate : folders) {
            boolean isDirectChild = candidate.length() > prefix.length()
                    && candidate.startsWith(prefix)
                    && candidate.indexOf('/', prefix.length()) == -1;
            if (isDirectChild && (smallest == null || candidate.compareTo(smallest) < 0)) {
                smallest = candidate;
            }
        }
        return smallest == null ? folder : smallest;
    }
}
```

### Problems With This Approach

- **`*` is slow:** to find the children of one folder, we look at every folder in the whole file system. With N folders, every `*` checks all N of them.
- **Lots of string building:** every step creates and hashes a new full path string.
- **No real structure:** the tree is hidden inside strings. A folder has no direct link to its parent or its children.

All three problems go away if we store the folders as a real tree.

## Solution 2: Folder Tree (Optimized)

### Which Design Pattern Fits?

**Composite (used, in a simple form):** a folder contains other folders, the classic use case for Composite. Normally Composite also needs a shared interface for files and folders, but this problem has only folders. So one `Directory` class that holds child `Directory` objects is enough.

**Command (not used):** classes like `MkdirCommand` and `CdCommand` sound natural for a shell. But Command pays off only when we need undo, history or a queue of commands. None of that is asked here, and the methods are called directly.

**Strategy (not used):** we could write one handler class per segment type (`.`, `..`, `*` and names). But these four rules are tiny and never change. One `if / else` chain inside a single method is much easier to read.

### The `Directory` Class

This class replaces the path strings with real objects that point to each other.

| Field | Why we need it |
|---|---|
| `children` (Map) | Finds a child by name in O(1). |
| `parent` | Makes `..` O(1). Root's parent is root itself, so `..` at root needs no special check. |
| `path` | The full path, saved once when the folder is created. Folders never move or get renamed, so it never changes and `pwd` is O(1). |
| `smallestChild` | The answer for `*`, ready in O(1). |

**How `smallestChild` stays correct:** folders are only added, never deleted. So when a new child is added, we compare its name with the current smallest and keep the smaller one. That is one comparison per new folder, and `*` never needs to search.

If a delete command like `rmdir` is added in the future, the smallest child could disappear. A sorted set of child names (`TreeSet`) would then be the right tool. For this problem, one field is enough.

### One `walk` Method for Both Commands

`mkdir` and `cd` read paths in exactly the same way. They only differ when a folder is missing:

- `mkdir` creates it and keeps going.
- `cd` stops, and `walk` returns `null` to signal failure.

So both use one `walk(path, createMissing)` method. `cd` moves only when `walk` returns a folder, which keeps the current folder unchanged on failure.

### Quick Example

Folders after `mkdir /a/b/c` and `mkdir /a/d`:

```
/
+-- a
    +-- b
    |   +-- c
    +-- d
```

We are at `/a/b/c` and run `cd ../../*`:

1. `..` moves to `/a/b`.
2. `..` moves to `/a`.
3. `*` at `/a`: the children are `b` and `d`, the smallest is `b`, so we move to `/a/b`.

`pwd` now returns `/a/b`.

### Code

```java
import java.util.HashMap;
import java.util.Map;

/**
 * One folder in the file system.
 * Folders are only ever added (never deleted, renamed or moved),
 * so a folder's full path never changes after it is created.
 */
class Directory {
    String name;
    String path;                 // full path like "/a/b", saved once so pwd() is instant
    Directory parent;            // root's parent is root itself
    Map<String, Directory> children = new HashMap<>();   // child name -> child folder
    Directory smallestChild;     // child with the smallest name, used by '*'

    Directory(String name, Directory parent) {
        this.name = name;
        if (parent == null) {    // this is the root
            this.parent = this;
            this.path = "/";
        } else {
            this.parent = parent;
            this.path = parent.path.equals("/") ? "/" + name : parent.path + "/" + name;
        }
    }

    /** Returns the child with this name, creating it if it does not exist yet. */
    Directory getOrCreateChild(String childName) {
        Directory child = children.get(childName);
        if (child == null) {
            child = new Directory(childName, this);
            children.put(childName, child);
            // Folders are never deleted, so the smallest name can only get smaller.
            if (smallestChild == null || childName.compareTo(smallestChild.name) < 0) {
                smallestChild = child;
            }
        }
        return child;
    }
}

public class FileSystemShell {
    Directory root;
    Directory current;

    public FileSystemShell() {
        root = new Directory("", null);
        current = root;
    }

    public String pwd() {
        return current.path;
    }

    public void mkdir(String path) {
        walk(path, true);
    }

    public void cd(String path) {
        Directory target = walk(path, false);
        if (target != null) {
            current = target;    // move only if the whole path was valid
        }
    }

    /**
     * Follows the path one segment at a time and returns the folder where it ends.
     * createMissing = true  (mkdir): missing folders are created on the way.
     * createMissing = false (cd)   : a missing folder means failure, so it returns null.
     */
    Directory walk(String path, boolean createMissing) {
        Directory folder = path.startsWith("/") ? root : current;
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;   // "//" or "." : stay here

            if (part.equals("..")) {
                folder = folder.parent;
            } else if (part.equals("*")) {
                if (folder.smallestChild != null) {   // no children: '*' acts like '.'
                    folder = folder.smallestChild;
                }
            } else if (createMissing) {
                folder = folder.getOrCreateChild(part);
            } else {
                folder = folder.children.get(part);
                if (folder == null) return null;      // folder does not exist
            }
        }
        return folder;
    }
}
```

## Complexity

k = number of segments in a path, L = length of a path, N = total number of folders.

| Operation | Solution 1 (set of paths) | Solution 2 (folder tree) |
|---|---|---|
| `pwd` | O(1) | O(1) |
| `mkdir` or `cd` (without `*`) | O(k × L) | O(L) |
| Each `*` inside `cd` | O(N × L) | O(1) |

In Solution 2, a new folder also builds its saved `path` string once, at the moment it is created.