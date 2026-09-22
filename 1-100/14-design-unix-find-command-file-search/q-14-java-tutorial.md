# Design Unix "find" Command for File Search in Java

#### Problem Statement
[https://codezym.com/question/14-design-unix-find-command-file-search](https://codezym.com/question/14-design-unix-find-command-file-search)

A search here is really two separate questions glued together. First, which files sit under this directory. Second, out of those files, which ones do we keep.

Keeping these two questions apart is the whole design. The "which ones do we keep" part is the part that keeps changing, because the problem tells us new search criterias will be added later, so it goes behind the **Strategy** pattern: one tiny class per criteria, all of them exposing the same single method, picked out of a map by id. The "which files sit under this directory" part never changes, so it just needs the right data structure, and that turns out to be a **directory tree** instead of a flat list of paths.

Strategy alone is enough here, no combination of patterns is needed. The only helper is a plain `Map<Integer, SearchCriteria>` that maps a criteria id to its object, which works as a very small registry so that `search()` never needs to know any criteria class by name.

We will build the flat, obvious version first, look at exactly where it hurts, and then fix both halves.


## Understanding the problem

Two operations, and both are short.

`putFile(path, sizeMb)` stores a file, and storing the same path twice replaces the old size.

`search(criteriaId, dirPath, args)` returns every file under `dirPath`, including files in nested subdirectories, that passes the criteria. Criteria 1 keeps files strictly larger than `args` MB. Criteria 2 keeps files whose name ends with the extension in `args`.

One rule is easy to skim past: the result must always come back in ascending lexicographical order with no duplicates. So for the first example in the problem statement, `/data/pics/movie.mp4` is returned before `/data/pics/photoA.jpg`, because `m` sorts before `p`.


## Solution 1: one flat map and a linear scan

The simplest thing that can possibly work. Keep every file in a single `HashMap` from full path to size, and when a search comes in, walk the whole map and keep whatever matches.

Why a `HashMap<String, Integer>` and not a list of file objects. Two reasons, both free. The key is the full path, so `put` on an existing path overwrites the old size, which is exactly the "add or replace" rule we were asked for. And because keys are unique, duplicates can never appear in the output.

There is one trap worth calling out. It is tempting to test `path.startsWith(dirPath)`, but that is wrong. The string `/data2/big.mp4` starts with `/data`, even though `/data2` is a completely different directory. The fix is to compare against the directory plus a trailing slash.

```java
import java.util.*;

public class FileSearch {

    // Absolute file path -> size in MB.
    // Map.put() overwrites an existing key, which is exactly the "add or replace" rule.
    private final Map<String, Integer> files = new HashMap<>();

    public FileSearch() {
    }

    public void putFile(String path, int sizeMb) {
        files.put(path, sizeMb);
    }

    public List<String> search(int ruleId, String dirPath, String args) {
        // "/data" must match "/data/pics/a.jpg" but must NOT match "/data2/a.jpg",
        // so we always compare against the directory plus a trailing slash.
        String prefix = dirPath.endsWith("/") ? dirPath : dirPath + "/";

        // Parse once instead of inside the loop.
        int minSizeMb = ruleId == 1 ? Integer.parseInt(args.trim()) : 0;
        String extension = args.trim();

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : files.entrySet()) {
            String path = entry.getKey();
            if (!path.startsWith(prefix)) continue;

            if (ruleId == 1 && entry.getValue() > minSizeMb) {
                result.add(path);
            } else if (ruleId == 2 && path.endsWith(extension)) {
                result.add(path);
            }
        }

        Collections.sort(result);   // strict ascending lexicographical order
        return result;
    }
}
```

### What is wrong with it

It passes, and with at most 2000 files it is fast enough. But it has two real problems.

**The if-else chain grows forever.** Every new criteria means opening `search()` and adding another branch to a method that already works. Criteria 5 and criteria 6 end up living inside the same method as criteria 1, so a careless edit while adding one criteria can quietly break another. Testing one criteria on its own is also impossible, because there is nothing to test except the whole method.

**It ignores the directory structure completely.** Asking for files under `/media/images/thumbnails` still walks all 2000 files, even if that folder holds three of them. We flattened a tree into a bag of strings and then pay for it on every single search.


## Solution 2: Strategy for the criterias, a tree for the directories

### Fixing the criterias with the Strategy pattern

Strategy says: when one step of an algorithm has several interchangeable versions, pull that step out into its own interface and give each version its own class.

Here the interchangeable step is "does this file qualify". So we define one interface with one method.

```java
private interface SearchCriteria {
    boolean matches(String path, int sizeMb, String args);
}
```

Each criteria becomes a small class that answers only that question for itself, and knows nothing about directories, traversal or sorting. `MinSizeCriteria` compares the size. `ExtensionCriteria` checks the ending of the path. That is the whole class.

Then a `Map<Integer, SearchCriteria>` holds id to object. `search()` looks up one object and calls `matches` on it, so `search()` contains no criteria specific code at all. Adding a third criteria is a new class plus one line in the constructor, with zero edits to the searching code.

### Why Strategy and not something else

**Chain of Responsibility** is the usual runner up, because it also replaces an if-else chain with objects. But a chain is for when you do not know who should handle a request, so you pass it along until someone accepts it. Here the caller already hands us the exact criteria id, so a chain would walk past handlers that can never apply and would add an ordering between criterias that has no meaning in this problem. It buys nothing and costs a lookup that used to be free.

**Visitor** also gets suggested whenever a tree is involved. Visitor pays off when you have many different node types and want to add new operations that work across all of them. Here it is the opposite: there is exactly one kind of thing to inspect, a file with a path and a size, and many operations on it. When the types are fixed and the operations keep growing, a one method interface per operation is lighter and far easier to read than a visitor with an accept method threaded through the tree.

### Fixing the traversal with a directory tree

Instead of storing `"/media/images/aa.jpg"` as one long string, store it the way a real file system does. Every directory becomes a node, and each node holds two maps: its child directories by name, and its own files by name.

```java
private static class Directory {
    final Map<String, Directory> subDirs = new HashMap<>();
    final Map<String, Integer> files = new HashMap<>();
}
```

Two maps, both plain `HashMap`, nothing exotic. `subDirs` lets us jump from a directory to a child in one step, so walking down to `/media/images` costs two lookups no matter how big the store is. `files` is keyed by name, so writing the same file name twice overwrites it, keeping the "add or replace" behaviour we had before and still guaranteeing no duplicates.

`putFile` splits the path and walks down, creating any missing directory on the way, then drops the size into the last node.

`search` splits `dirPath`, walks down to that node, and does a depth first walk of everything below it. If any step of the walk down finds no such child, the directory does not exist and we return an empty list.

Two nice things fall out of this. The scan now visits only the files under the requested directory instead of every file in the system. And the `/data` versus `/data2` trap disappears on its own, because `data` and `data2` are two different keys under the root, so descending into one can never reach the other.

We still sort the matches at the end. A depth first walk does not come out in lexicographical order, so one `Collections.sort` on the matches is the honest and simple way to meet that requirement.

```java
import java.util.*;

public class FileSearch {

    /** Every search criteria implements this one method. args is the raw string from search(). */
    private interface SearchCriteria {
        boolean matches(String path, int sizeMb, String args);
    }

    /** Criteria 1: keep files strictly larger than the given size in MB. */
    private static class MinSizeCriteria implements SearchCriteria {
        @Override
        public boolean matches(String path, int sizeMb, String args) {
            return sizeMb > Integer.parseInt(args.trim());
        }
    }

    /** Criteria 2: keep files whose name ends with the given extension. */
    private static class ExtensionCriteria implements SearchCriteria {
        @Override
        public boolean matches(String path, int sizeMb, String args) {
            return path.endsWith(args.trim());
        }
    }

    /** One node of the directory tree: child directories by name and files by name. */
    private static class Directory {
        final Map<String, Directory> subDirs = new HashMap<>();
        final Map<String, Integer> files = new HashMap<>();
    }

    private final Directory root = new Directory();

    /** Criteria id -> the object that knows how to test a file for that criteria. */
    private final Map<Integer, SearchCriteria> criterias = new HashMap<>();

    public FileSearch() {
        criterias.put(1, new MinSizeCriteria());
        criterias.put(2, new ExtensionCriteria());
        // A new criteria later needs one new class and one line here.
    }

    public void putFile(String path, int sizeMb) {
        List<String> parts = split(path);
        if (parts.isEmpty()) return;

        Directory current = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            current = current.subDirs.computeIfAbsent(parts.get(i), name -> new Directory());
        }
        // Same name in the same directory overwrites the old size, so no duplicates appear.
        current.files.put(parts.get(parts.size() - 1), sizeMb);
    }

    public List<String> search(int ruleId, String dirPath, String args) {
        List<String> result = new ArrayList<>();

        SearchCriteria criteria = criterias.get(ruleId);
        if (criteria == null) return result;

        // Walk down to the starting directory. Everything below it is the search space.
        Directory start = root;
        StringBuilder prefix = new StringBuilder();
        for (String part : split(dirPath)) {
            start = start.subDirs.get(part);
            if (start == null) return result;   // directory does not exist
            prefix.append('/').append(part);
        }

        collect(start, prefix.toString(), criteria, args, result);
        Collections.sort(result);               // strict ascending lexicographical order
        return result;
    }

    /** Depth first walk of one sub tree, keeping only files the criteria accepts. */
    private void collect(Directory dir, String prefix, SearchCriteria criteria,
                         String args, List<String> out) {
        for (Map.Entry<String, Integer> file : dir.files.entrySet()) {
            String fullPath = prefix + "/" + file.getKey();
            if (criteria.matches(fullPath, file.getValue(), args)) {
                out.add(fullPath);
            }
        }
        for (Map.Entry<String, Directory> child : dir.subDirs.entrySet()) {
            collect(child.getValue(), prefix + "/" + child.getKey(), criteria, args, out);
        }
    }

    /** "/a/b/c.txt" -> ["a", "b", "c.txt"]. Empty pieces from "/" or "//" are dropped. */
    private List<String> split(String path) {
        List<String> parts = new ArrayList<>();
        for (String piece : path.split("/")) {
            if (!piece.isEmpty()) parts.add(piece);
        }
        return parts;
    }
}
```


## Adding a third criteria

This is the payoff. Say we now want "files whose name contains some text". We write one class and register it, and we do not touch `search`, `collect`, `putFile` or either of the existing criterias.

```java
/** Criteria 3: keep files whose name contains the given text. */
private static class NameContainsCriteria implements SearchCriteria {
    @Override
    public boolean matches(String path, int sizeMb, String args) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.contains(args.trim());
    }
}
```

```java
criterias.put(3, new NameContainsCriteria());
```


## Complexity

Let `d` be the number of parts in a path, `k` the number of files under the requested directory and `m` the number of matches.

`putFile` is O(d), just a walk down the tree.

`search` is O(k + m log m): visit the sub tree once, then sort the matches. The flat version was O(n + m log m) where n is every file in the system, so the tree only helps more as the store grows and the searched directory stays small.