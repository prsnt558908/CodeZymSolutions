# Design Unix "find" Command for File Search in Python

#### Problem Statement
[https://codezym.com/question/14-design-unix-find-command-file-search](https://codezym.com/question/14-design-unix-find-command-file-search)

A search here is really two separate questions glued together. First, which files sit under this directory. Second, out of those files, which ones do we keep.

Keeping these two questions apart is the whole design. The "which ones do we keep" part is the part that keeps changing, because the problem tells us new search criterias will be added later, so it goes behind the **Strategy** pattern: one tiny class per criteria, all of them exposing the same single method, picked out of a dictionary by id. The "which files sit under this directory" part never changes, so it just needs the right data structure, and that turns out to be a **directory tree** instead of a flat list of paths.

Strategy alone is enough here, no combination of patterns is needed. The only helper is a plain dictionary from criteria id to its object, which works as a very small registry so that `search()` never needs to know any criteria class by name.

We will build the flat, obvious version first, look at exactly where it hurts, and then fix both halves.


## Understanding the problem

Two operations, and both are short.

`putFile(path, sizeMb)` stores a file, and storing the same path twice replaces the old size.

`search(criteriaId, dirPath, args)` returns every file under `dirPath`, including files in nested subdirectories, that passes the criteria. Criteria 1 keeps files strictly larger than `args` MB. Criteria 2 keeps files whose name ends with the extension in `args`.

One rule is easy to skim past: the result must always come back in ascending lexicographical order with no duplicates. So for the first example in the problem statement, `/data/pics/movie.mp4` is returned before `/data/pics/photoA.jpg`, because `m` sorts before `p`.


## Solution 1: one flat dictionary and a linear scan

The simplest thing that can possibly work. Keep every file in a single dictionary from full path to size, and when a search comes in, walk the whole dictionary and keep whatever matches.

Why a dictionary and not a list of file objects. Two reasons, both free. The key is the full path, so assigning to an existing path overwrites the old size, which is exactly the "add or replace" rule we were asked for. And because keys are unique, duplicates can never appear in the output.

There is one trap worth calling out. It is tempting to test `path.startswith(dirPath)`, but that is wrong. The string `/data2/big.mp4` starts with `/data`, even though `/data2` is a completely different directory. The fix is to compare against the directory plus a trailing slash.

```python
class FileSearch:

    def __init__(self):
        # Absolute file path -> size in MB.
        # Assigning to an existing key overwrites it, which is the "add or replace" rule.
        self.files = {}

    def putFile(self, path, sizeMb):
        self.files[path] = sizeMb

    def search(self, ruleId, dirPath, args):
        # "/data" must match "/data/pics/a.jpg" but must NOT match "/data2/a.jpg",
        # so we always compare against the directory plus a trailing slash.
        prefix = dirPath if dirPath.endswith("/") else dirPath + "/"

        args = args.strip()
        minSizeMb = int(args) if ruleId == 1 else 0

        result = []
        for path, sizeMb in self.files.items():
            if not path.startswith(prefix):
                continue
            if ruleId == 1 and sizeMb > minSizeMb:
                result.append(path)
            elif ruleId == 2 and path.endswith(args):
                result.append(path)

        result.sort()   # strict ascending lexicographical order
        return result
```

### What is wrong with it

It passes, and with at most 2000 files it is fast enough. But it has two real problems.

**The if-elif chain grows forever.** Every new criteria means opening `search()` and adding another branch to a method that already works. Criteria 5 and criteria 6 end up living inside the same method as criteria 1, so a careless edit while adding one criteria can quietly break another. Testing one criteria on its own is also impossible, because there is nothing to test except the whole method.

**It ignores the directory structure completely.** Asking for files under `/media/images/thumbnails` still walks all 2000 files, even if that folder holds three of them. We flattened a tree into a bag of strings and then pay for it on every single search.


## Solution 2: Strategy for the criterias, a tree for the directories

### Fixing the criterias with the Strategy pattern

Strategy says: when one step of an algorithm has several interchangeable versions, pull that step out into its own interface and give each version its own class.

Here the interchangeable step is "does this file qualify". So every criteria class exposes the same single method.

```python
def matches(self, path, sizeMb, args):
    ...
```

Each criteria becomes a small class that answers only that question for itself, and knows nothing about directories, traversal or sorting. `MinSizeCriteria` compares the size. `ExtensionCriteria` checks the ending of the path. That is the whole class.

Then a dictionary holds id to object. `search()` looks up one object and calls `matches` on it, so `search()` contains no criteria specific code at all. Adding a third criteria is a new class plus one line in the constructor, with zero edits to the searching code.

### Why Strategy and not something else

**Chain of Responsibility** is the usual runner up, because it also replaces an if-elif chain with objects. But a chain is for when you do not know who should handle a request, so you pass it along until someone accepts it. Here the caller already hands us the exact criteria id, so a chain would walk past handlers that can never apply and would add an ordering between criterias that has no meaning in this problem. It buys nothing and costs a lookup that used to be free.

**Visitor** also gets suggested whenever a tree is involved. Visitor pays off when you have many different node types and want to add new operations that work across all of them. Here it is the opposite: there is exactly one kind of thing to inspect, a file with a path and a size, and many operations on it. When the types are fixed and the operations keep growing, a one method class per operation is lighter and far easier to read than a visitor with an accept method threaded through the tree.

### Fixing the traversal with a directory tree

Instead of storing `"/media/images/aa.jpg"` as one long string, store it the way a real file system does. Every directory becomes a node, and each node holds two dictionaries: its child directories by name, and its own files by name.

```python
class Directory:
    def __init__(self):
        self.subDirs = {}
        self.files = {}
```

Two dictionaries, nothing exotic. `subDirs` lets us jump from a directory to a child in one step, so walking down to `/media/images` costs two lookups no matter how big the store is. `files` is keyed by name, so writing the same file name twice overwrites it, keeping the "add or replace" behaviour we had before and still guaranteeing no duplicates.

`putFile` splits the path and walks down, creating any missing directory on the way, then drops the size into the last node.

`search` splits `dirPath`, walks down to that node, and does a depth first walk of everything below it. If any step of the walk down finds no such child, the directory does not exist and we return an empty list.

Two nice things fall out of this. The scan now visits only the files under the requested directory instead of every file in the system. And the `/data` versus `/data2` trap disappears on its own, because `data` and `data2` are two different keys under the root, so descending into one can never reach the other.

We still sort the matches at the end. A depth first walk does not come out in lexicographical order, so one `result.sort()` on the matches is the honest and simple way to meet that requirement.

```python
class MinSizeCriteria:
    """Criteria 1: keep files strictly larger than the given size in MB."""

    def matches(self, path, sizeMb, args):
        return sizeMb > int(args.strip())


class ExtensionCriteria:
    """Criteria 2: keep files whose name ends with the given extension."""

    def matches(self, path, sizeMb, args):
        return path.endswith(args.strip())


class Directory:
    """One node of the directory tree: child directories by name and files by name."""

    def __init__(self):
        self.subDirs = {}
        self.files = {}


class FileSearch:

    def __init__(self):
        self.root = Directory()
        # Criteria id -> the object that knows how to test a file for that criteria.
        # A new criteria later needs one new class and one line here.
        self.criterias = {
            1: MinSizeCriteria(),
            2: ExtensionCriteria(),
        }

    def putFile(self, path, sizeMb):
        parts = self._split(path)
        if not parts:
            return

        current = self.root
        for name in parts[:-1]:
            if name not in current.subDirs:
                current.subDirs[name] = Directory()
            current = current.subDirs[name]

        # Same name in the same directory overwrites the old size, so no duplicates appear.
        current.files[parts[-1]] = sizeMb

    def search(self, ruleId, dirPath, args):
        criteria = self.criterias.get(ruleId)
        if criteria is None:
            return []

        # Walk down to the starting directory. Everything below it is the search space.
        start = self.root
        prefix = ""
        for name in self._split(dirPath):
            if name not in start.subDirs:
                return []               # directory does not exist
            start = start.subDirs[name]
            prefix += "/" + name

        result = []
        self._collect(start, prefix, criteria, args, result)
        result.sort()                   # strict ascending lexicographical order
        return result

    def _collect(self, directory, prefix, criteria, args, out):
        """Depth first walk of one sub tree, keeping only files the criteria accepts."""
        for name, sizeMb in directory.files.items():
            fullPath = prefix + "/" + name
            if criteria.matches(fullPath, sizeMb, args):
                out.append(fullPath)

        for name, child in directory.subDirs.items():
            self._collect(child, prefix + "/" + name, criteria, args, out)

    def _split(self, path):
        """'/a/b/c.txt' -> ['a', 'b', 'c.txt']. Empty pieces from '/' or '//' are dropped."""
        return [piece for piece in path.split("/") if piece]
```


## Adding a third criteria

This is the payoff. Say we now want "files whose name contains some text". We write one class and register it, and we do not touch `search`, `_collect`, `putFile` or either of the existing criterias.

```python
class NameContainsCriteria:
    """Criteria 3: keep files whose name contains the given text."""

    def matches(self, path, sizeMb, args):
        name = path.rsplit("/", 1)[-1]
        return args.strip() in name
```

```python
self.criterias[3] = NameContainsCriteria()
```


## Complexity

Let `d` be the number of parts in a path, `k` the number of files under the requested directory and `m` the number of matches.

`putFile` is O(d), just a walk down the tree.

`search` is O(k + m log m): visit the sub tree once, then sort the matches. The flat version was O(n + m log m) where n is every file in the system, so the tree only helps more as the store grows and the searched directory stays small.