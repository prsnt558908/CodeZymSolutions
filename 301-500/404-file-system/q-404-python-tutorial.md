# In-Memory File System with Path Values in Python

#### Problem Statement
[https://codezym.com/question/404-file-system](https://codezym.com/question/404-file-system)

Every path in this problem is just a chain of names. `/data/archive/note` is the name `data`, then `archive`, then `note`. So the file system is a tree, and each name is one step down from its parent. Once you see it like that, all six methods turn into the same small action: start at the root, walk the names one by one, and then do something at the node you land on. `ls` reads the names of the children at that node, `get` reads a number stored on it, `addContentToFile` appends text to it, and `createPath` adds one new child under it.

Design patterns are worth one thought here and no more. A file system is the classic example used to teach the Composite pattern, so it is tempting to build a `Directory` class and a `File` class behind a common base class. For these six methods that extra structure does not pay for itself, and a short section near the end explains why. The real work is choosing the right containers: a dict from child name to child node, and a cheap way to keep appending to file content.

## What each method actually needs

| Method | What it needs from the structure |
| --- | --- |
| `ls` | the immediate children of a path, in sorted order |
| `mkdir` | create every missing name along a path, without touching what already exists |
| `addContentToFile` | find or create one file, then append to it cheaply |
| `readContentFromFile` | the whole content of one file |
| `createPath` | check that the parent exists and is a directory, and that the name is free |
| `get` | a number stored on one path, or `-1` |

Two of these are the interesting ones. `mkdir` must not disturb a value that `createPath` put there earlier, and `ls` is the only place where sorted order matters.

## Solution 1: one flat dict from full path to entry

The simplest thing that can work is to forget the tree and store one dict whose key is the entire path string, like `"/data/archive"`. Every entry remembers whether it is a file, its content, and its value.

Two tiny string helpers make this possible. `_parent("/a/b")` cuts at the last slash and gives `"/a"`, and `_name("/a/b")` gives `"b"`.

Now the methods are easy. `get` and `readContentFromFile` are single dict lookups. `createPath` looks up `_parent(path)` in the dict, which answers "does the parent exist and is it a directory" in one step. `mkdir` adds the path and every prefix of it, so `/a/b/c` puts `/a`, `/a/b` and `/a/b/c` into the dict.

The awkward one is `ls`. The dict has no idea who is inside whom, so to list a directory we walk over every key in it and keep the ones whose parent equals the given path.

```python
class Entry:
    """One entry stored at one full path."""

    def __init__(self):
        self.is_file = False
        self.content = ""
        self.value = -1          # -1 means no value attached to this path


class FileSystem:
    def __init__(self):
        # full path string -> entry stored at that path
        self.paths = {"/": Entry()}     # root always exists

    def ls(self, path):
        entry = self.paths.get(path)
        if entry is None:
            return []
        if entry.is_file:
            return [self._name(path)]
        # scan every known path and keep the ones sitting directly inside path
        result = []
        for other in self.paths:
            if other != "/" and self._parent(other) == path:
                result.append(self._name(other))
        result.sort()
        return result

    def mkdir(self, path):
        current = ""
        for part in path.split("/"):
            if not part:
                continue
            current += "/" + part
            if current not in self.paths:        # keeps an existing directory as it is
                self.paths[current] = Entry()

    def addContentToFile(self, filePath, content):
        entry = self.paths.get(filePath)
        if entry is None:
            self.mkdir(self._parent(filePath))  # make sure the folders above exist
            entry = Entry()
            entry.is_file = True
            self.paths[filePath] = entry
        entry.content = entry.content + content  # copies everything on every call

    def readContentFromFile(self, filePath):
        entry = self.paths.get(filePath)
        return "" if entry is None else entry.content

    def createPath(self, path, value):
        if path == "/" or path in self.paths:
            return False                        # root exists, and so does this path
        parent = self.paths.get(self._parent(path))
        if parent is None or parent.is_file:
            return False                        # missing parent, or parent is a file
        entry = Entry()
        entry.value = value
        self.paths[path] = entry
        return True

    def get(self, path):
        entry = self.paths.get(path)
        return -1 if entry is None else entry.value

    def _parent(self, path):
        """ "/a/b" -> "/a" and "/a" -> "/" """
        idx = path.rfind("/")
        return "/" if idx == 0 else path[:idx]

    def _name(self, path):
        """ "/a/b" -> "b" """
        return path[path.rfind("/") + 1:]
```

This is correct, and for a handful of calls it is perfectly fine. It is also a good first answer in an interview because it shows you understood the rules before you started optimising.

### Where this solution hurts

**`ls` reads the whole dict.** If the file system holds N paths, one `ls` does N parent cuts and N string comparisons, even when the directory has two children. With thousands of paths and thousands of `ls` calls that is tens of millions of string operations.

**Appending content copies the file every time.** Strings cannot be changed in place, so `entry.content + content` builds a brand new string. A file grown to 100,000 characters in 2,000 small appends copies about 100 million characters in total.

**Every key is a full path string.** `/a/b/c/d` stores the text `a`, `b` and `c` again inside every deeper key, so memory grows with path length, not with the number of names.

On a workload that stays inside the limits of the problem, 5,000 directories plus 4,000 `ls` calls plus 4,000 appends, this version took roughly a hundred times longer than the next one. The gap widens as the number of stored paths grows.

## Solution 2: a tree of nodes, one node per name

Instead of one flat dict, give every name its own small object and let each object hold a dict of its children. That is exactly the shape the paths already have.

```python
class Node:
    def __init__(self):
        self.is_file = False
        self.children = {}
        self.parts = []
        self.value = -1
```

Why this `Node` and these four fields:

**`children` dict** is the heart of the fix. Looking up one child by name is O(1), so walking a path costs only as many lookups as there are names in it, no matter how big the file system gets. A plain dict is enough because nothing in the problem asks for ordering during lookup.

**`is_file` flag** lets one class serve both roles. A file is simply a node that never gets children, so `ls` can answer "this is a file, return just its name" by reading one field.

**`parts` list** replaces string concatenation. Appending text pushes one small piece onto a list, which is O(1), instead of rebuilding the whole file. `readContentFromFile` joins the pieces and stores the joined text back as a single piece, so reading the same file again costs nothing until the next append.

**`value = -1`** means `get` needs no extra dict and no "was a value ever set" flag. The default already is the answer the problem asks for when a path was created by `mkdir`.

### Walking a path

One helper turns a path into its names, and it quietly handles the root as well. `_split("/a/b")` gives `["a", "b"]`, and `_split("/")` gives an empty list, which means "you are already standing on the root". That single behaviour removes every special case for `"/"` from the rest of the code, including `createPath("/", value)` which must return `False`.

A second helper, `_find`, walks those names from the root and returns `None` the moment a name is missing. `get`, `ls` and `readContentFromFile` all lean on it.

### Why sort inside `ls` instead of keeping children sorted

Order matters in exactly one method. A structure that stays sorted would pay for ordering on every single insert, which is the common case, to make the rare case cheap. So we keep the fast dict and call `sorted` on the children names when `ls` is asked. Sorting k names costs k log k, and k is only the number of entries inside one directory. Names here are lowercase letters only, so plain `sorted` gives the lexicographic order the problem asks for.

### Why the creation check is written as "only when missing"

Creating a node only when the name is absent, and reusing it otherwise, is the rule "calling `mkdir` for an existing directory has no effect" written in one line. It is also what keeps a value set earlier by `createPath` from being wiped out.

### Code

```python
class Node:
    """
    One node stands for one name in the file system.
    A directory uses children, a file uses parts.
    """

    def __init__(self):
        self.is_file = False
        self.children = {}      # child name -> child node
        self.parts = []         # file content kept as a list of appended pieces
        self.value = -1         # -1 means no value attached


class FileSystem:
    def __init__(self):
        self.root = Node()

    def ls(self, path):
        node = self._find(path)
        if node is None:
            return []
        if node.is_file:
            return [path[path.rfind("/") + 1:]]      # a file lists only its own name
        return sorted(node.children.keys())          # order is needed only here

    def mkdir(self, path):
        node = self.root
        for name in self._split(path):
            if name not in node.children:            # existing directories survive
                node.children[name] = Node()
            node = node.children[name]

    def addContentToFile(self, filePath, content):
        parts = self._split(filePath)
        node = self.root
        for name in parts[:-1]:
            if name not in node.children:
                node.children[name] = Node()
            node = node.children[name]
        name = parts[-1]
        if name not in node.children:
            node.children[name] = Node()
        file_node = node.children[name]
        file_node.is_file = True
        file_node.parts.append(content)               # O(1), nothing is copied

    def readContentFromFile(self, filePath):
        node = self._find(filePath)
        if node is None:
            return ""
        if len(node.parts) > 1:
            node.parts = ["".join(node.parts)]        # join once, later reads are free
        return node.parts[0] if node.parts else ""

    def createPath(self, path, value):
        parts = self._split(path)
        if not parts:
            return False                              # the root already exists
        parent = self.root
        for name in parts[:-1]:
            parent = parent.children.get(name)
            if parent is None or parent.is_file:
                return False                          # a missing or file parent stops us
        name = parts[-1]
        if name in parent.children:
            return False                              # the path already exists
        created = Node()
        created.value = value                         # only createPath attaches a value
        parent.children[name] = created
        return True

    def get(self, path):
        node = self._find(path)
        return -1 if node is None else node.value

    def _find(self, path):
        """Walks the path name by name, returns None when some name is missing."""
        node = self.root
        for name in self._split(path):
            node = node.children.get(name)
            if node is None:
                return None
        return node

    def _split(self, path):
        """ "/a/b" -> ["a", "b"] and "/" -> [] """
        return [part for part in path.split("/") if part]
```

### Walking through Example 2

`createPath("/data", 7)` finds one name, `data`. The parent is the root, the name is free, so a node is created with value 7 and it returns `True`.

`createPath("/data", 20)` sees `data` already inside the root children and returns `False` without touching the stored 7.

`mkdir("/data/archive/old")` creates `archive` under `data` and `old` under `archive`. Neither gets a value, so `get("/data/archive")` returns the default `-1`.

`addContentToFile("/data/archive/note", "saved")` walks to `archive`, creates the child `note`, marks it a file and appends the text.

`ls("/data")` lands on the `data` node, which is not a file, so it sorts the children names `archive` and `logs`. `ls("/data/archive/note")` lands on a file, so it returns just `["note"]`.

## Do we need a design pattern here

The Composite pattern is the textbook answer for tree structures, with `File` and `Directory` as two classes sharing one base class. Its benefit shows up when you ask the tree to do something recursive through a single call, for example "total size of this folder" or "print this folder and everything under it", where every class handles its own part and directories just forward the call to their children.

This problem never asks for that. `ls` looks exactly one level down, `get` and `read` act on one node, and nothing walks a whole subtree. Splitting into two classes would add a base class, two subclasses and a type check in every method, and would buy nothing. One `Node` with an `is_file` flag reads better and runs faster here.

The Visitor pattern is the other one that sounds attractive for trees, and it is a worse fit for the same reason plus one more: it pays off when you keep adding new kinds of traversals over a stable structure, while here there are six fixed operations and none of them traverse.

If the problem later grew a `du`, a `find`, a `copy` or permissions per entry, Composite would earn its place. Reaching for it now is design for a future that the requirements do not describe.

## Time and space

Let L be the length of a path, k the number of entries inside one directory, and C the size of a file.

| Method | Solution 1 | Solution 2 |
| --- | --- | --- |
| `ls` on a directory | O(N x L + k log k) over all N stored paths | O(L + k log k) |
| `mkdir` | O(L^2) because every prefix builds a new string | O(L) |
| `addContentToFile` | O(C) per call, the whole file is copied | O(L + length of the added text) |
| `readContentFromFile` | O(1) | O(L + C) on the first read after an append, O(L) after that |
| `createPath` | O(L) | O(L) |
| `get` | O(L) | O(L) |

Space for solution 2 is proportional to the number of names stored plus the total content, while solution 1 also pays for repeating every ancestor name inside every deeper key.