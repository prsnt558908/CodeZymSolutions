# In-Memory File System with Path Values in Java

#### Problem Statement
[https://codezym.com/question/404-file-system](https://codezym.com/question/404-file-system)

Every path in this problem is just a chain of names. `/data/archive/note` is the name `data`, then `archive`, then `note`. So the file system is a tree, and each name is one step down from its parent. Once you see it like that, all six methods turn into the same small action: start at the root, walk the names one by one, and then do something at the node you land on. `ls` reads the names of the children at that node, `get` reads a number stored on it, `addContentToFile` appends text to it, and `createPath` adds one new child under it.

Design patterns are worth one thought here and no more. A file system is the classic example used to teach the Composite pattern, so it is tempting to build a `Directory` class and a `File` class behind a common interface. For these six methods that extra structure does not pay for itself, and a short section near the end explains why. The real work is choosing the right containers: a map from child name to child node, and a growable text buffer for file content.

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

## Solution 1: one flat map from full path to entry

The simplest thing that can work is to forget the tree and store one map whose key is the entire path string, like `"/data/archive"`. Every entry remembers whether it is a file, its content, and its value.

Two tiny string helpers make this possible. `parent("/a/b")` cuts at the last slash and gives `"/a"`, and `name("/a/b")` gives `"b"`.

Now the methods are easy. `get` and `readContentFromFile` are single map lookups. `createPath` looks up `parent(path)` in the map, which answers "does the parent exist and is it a directory" in one step. `mkdir` adds the path and every prefix of it, so `/a/b/c` puts `/a`, `/a/b` and `/a/b/c` into the map.

The awkward one is `ls`. The map has no idea who is inside whom, so to list a directory we walk over every key in the map and keep the ones whose parent equals the given path.

```java
import java.util.*;

public class FileSystem {

    /** One entry stored at one full path. */
    private static class Entry {
        boolean isFile;
        String content = "";
        int value = -1;          // -1 means no value attached to this path
    }

    // full path string -> entry stored at that path
    private final Map<String, Entry> paths = new HashMap<>();

    public FileSystem() {
        paths.put("/", new Entry());   // root always exists
    }

    public List<String> ls(String path) {
        List<String> result = new ArrayList<>();
        Entry entry = paths.get(path);
        if (entry == null) {
            return result;
        }
        if (entry.isFile) {
            result.add(name(path));
            return result;
        }
        // scan every known path and keep the ones sitting directly inside path
        for (String other : paths.keySet()) {
            if (!other.equals("/") && parent(other).equals(path)) {
                result.add(name(other));
            }
        }
        Collections.sort(result);
        return result;
    }

    public void mkdir(String path) {
        StringBuilder current = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            current.append("/").append(part);
            // putIfAbsent keeps an existing directory and its value untouched
            paths.putIfAbsent(current.toString(), new Entry());
        }
    }

    public void addContentToFile(String filePath, String content) {
        Entry entry = paths.get(filePath);
        if (entry == null) {
            mkdir(parent(filePath));      // make sure the folders above exist
            entry = new Entry();
            entry.isFile = true;
            paths.put(filePath, entry);
        }
        entry.content = entry.content + content;   // copies everything on every call
    }

    public String readContentFromFile(String filePath) {
        Entry entry = paths.get(filePath);
        return entry == null ? "" : entry.content;
    }

    public boolean createPath(String path, int value) {
        if (path.equals("/") || paths.containsKey(path)) {
            return false;                 // root exists, and so does this path
        }
        Entry parentEntry = paths.get(parent(path));
        if (parentEntry == null || parentEntry.isFile) {
            return false;                 // missing parent, or parent is a file
        }
        Entry entry = new Entry();
        entry.value = value;
        paths.put(path, entry);
        return true;
    }

    public int get(String path) {
        Entry entry = paths.get(path);
        return entry == null ? -1 : entry.value;
    }

    /** "/a/b" -> "/a" and "/a" -> "/" */
    private String parent(String path) {
        int idx = path.lastIndexOf('/');
        return idx == 0 ? "/" : path.substring(0, idx);
    }

    /** "/a/b" -> "b" */
    private String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
```

This is correct, and for a handful of calls it is perfectly fine. It is also a good first answer in an interview because it shows you understood the rules before you started optimising.

### Where this solution hurts

**`ls` reads the whole map.** If the file system holds N paths, one `ls` does N parent cuts and N string comparisons, even when the directory has two children. With thousands of paths and thousands of `ls` calls that is tens of millions of string operations.

**Appending content copies the file every time.** `entry.content + content` builds a brand new string, so a file grown to 100,000 characters in 2,000 small appends copies about 100 million characters in total.

**Every key is a full path string.** `/a/b/c/d` stores the text `a`, `b` and `c` again inside every deeper key, so memory grows with path length, not with the number of names.

On a workload that stays inside the limits of the problem, 5,000 directories plus 4,000 `ls` calls plus 4,000 appends, this version took around ten times longer than the next one. The gap widens as the number of stored paths grows.

## Solution 2: a tree of nodes, one node per name

Instead of one flat map, give every name its own small object and let each object hold a map of its children. That is exactly the shape the paths already have.

```java
private static class Node {
    boolean isFile;
    Map<String, Node> children = new HashMap<>();
    StringBuilder content = new StringBuilder();
    int value = -1;
}
```

Why this `Node` and these four fields:

**`Map<String, Node> children`** is the heart of the fix. Looking up one child by name is O(1), so walking a path costs only as many lookups as there are names in it, no matter how big the file system gets. A plain `HashMap` is enough because nothing in the problem asks for ordering during lookup.

**`boolean isFile`** lets one class serve both roles. A file is simply a node that never gets children, so `ls` can answer "this is a file, return just its name" by reading one field.

**`StringBuilder content`** appends in place instead of building a new string each time. Two thousand appends stay two thousand small copies into the same buffer rather than two thousand full rewrites of the file.

**`int value = -1`** means `get` needs no extra map and no "was a value ever set" flag. The default already is the answer the problem asks for when a path was created by `mkdir`.

### Walking a path

One helper turns a path into its names, and it quietly handles the root as well. `split("/a/b")` gives `["a", "b"]`, and `split("/")` gives an empty list, which means "you are already standing on the root". That single behaviour removes every special case for `"/"` from the rest of the code, including `createPath("/", value)` which must return `false`.

A second helper, `find`, walks those names from the root and returns `null` the moment a name is missing. `get`, `ls` and `readContentFromFile` all lean on it.

### Why sort inside `ls` instead of keeping children sorted

Order matters in exactly one method. A sorted map would pay for ordering on every single insert, which is the common case, to make the rare case cheap. So we keep the fast `HashMap`, copy the children names into a list when `ls` is called, and sort that list. Sorting k names costs k log k, and k is only the number of entries inside one directory. Names here are lowercase letters only, so plain `Collections.sort` gives the lexicographic order the problem asks for.

### Why `mkdir` uses `computeIfAbsent`

`computeIfAbsent` creates a node only when the name is missing and returns the existing one otherwise. That is the rule "calling `mkdir` for an existing directory has no effect" written in one line, and it is what keeps a value set earlier by `createPath` from being wiped out.

### Code

```java
import java.util.*;

public class FileSystem {

    /**
     * One node stands for one name in the file system.
     * A directory uses children, a file uses content.
     */
    private static class Node {
        boolean isFile;
        Map<String, Node> children = new HashMap<>();   // child name -> child node
        StringBuilder content = new StringBuilder();    // file content
        int value = -1;                                 // -1 means no value attached
    }

    private final Node root = new Node();

    public FileSystem() {
    }

    public List<String> ls(String path) {
        List<String> result = new ArrayList<>();
        Node node = find(path);
        if (node == null) {
            return result;
        }
        if (node.isFile) {
            result.add(lastName(path));     // a file lists only its own name
            return result;
        }
        result.addAll(node.children.keySet());
        Collections.sort(result);           // order is needed only here
        return result;
    }

    public void mkdir(String path) {
        Node node = root;
        for (String name : split(path)) {
            // creates the node only when missing, so existing values survive
            node = node.children.computeIfAbsent(name, key -> new Node());
        }
    }

    public void addContentToFile(String filePath, String content) {
        List<String> parts = split(filePath);
        Node node = root;
        for (int i = 0; i + 1 < parts.size(); i++) {
            node = node.children.computeIfAbsent(parts.get(i), key -> new Node());
        }
        Node file = node.children.computeIfAbsent(parts.get(parts.size() - 1), key -> new Node());
        file.isFile = true;
        file.content.append(content);       // append in place, nothing is copied
    }

    public String readContentFromFile(String filePath) {
        Node file = find(filePath);
        return file == null ? "" : file.content.toString();
    }

    public boolean createPath(String path, int value) {
        List<String> parts = split(path);
        if (parts.isEmpty()) {
            return false;                   // the root already exists
        }
        Node parent = root;
        for (int i = 0; i + 1 < parts.size(); i++) {
            parent = parent.children.get(parts.get(i));
            if (parent == null || parent.isFile) {
                return false;               // a missing or file parent stops us
            }
        }
        String name = parts.get(parts.size() - 1);
        if (parent.children.containsKey(name)) {
            return false;                   // the path already exists
        }
        Node created = new Node();
        created.value = value;              // only createPath attaches a value
        parent.children.put(name, created);
        return true;
    }

    public int get(String path) {
        Node node = find(path);
        return node == null ? -1 : node.value;
    }

    /** Walks the path name by name, returns null when some name is missing. */
    private Node find(String path) {
        Node node = root;
        for (String name : split(path)) {
            node = node.children.get(name);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /** "/a/b" -> ["a", "b"] and "/" -> [] */
    private List<String> split(String path) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < path.length()) {
            if (path.charAt(i) == '/') {
                i++;
                continue;
            }
            int j = i;
            while (j < path.length() && path.charAt(j) != '/') {
                j++;
            }
            parts.add(path.substring(i, j));
            i = j;
        }
        return parts;
    }

    /** "/a/b" -> "b" */
    private String lastName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
```

### Walking through Example 2

`createPath("/data", 7)` finds one name, `data`. The parent is the root, the name is free, so a node is created with value 7 and it returns `true`.

`createPath("/data", 20)` sees `data` already inside the root children and returns `false` without touching the stored 7.

`mkdir("/data/archive/old")` creates `archive` under `data` and `old` under `archive`. Neither gets a value, so `get("/data/archive")` returns the default `-1`.

`addContentToFile("/data/archive/note", "saved")` walks to `archive`, creates the child `note`, marks it a file and appends the text.

`ls("/data")` lands on the `data` node, which is not a file, so it copies the children names `archive` and `logs` and sorts them. `ls("/data/archive/note")` lands on a file, so it returns just `["note"]`.

## Do we need a design pattern here

The Composite pattern is the textbook answer for tree structures, with `File` and `Directory` as two classes behind one `Entry` interface. Its benefit shows up when you ask the tree to do something recursive through a single call, for example "total size of this folder" or "print this folder and everything under it", where every class handles its own part and directories just forward the call to their children.

This problem never asks for that. `ls` looks exactly one level down, `get` and `read` act on one node, and nothing walks a whole subtree. Splitting into two classes would add an interface, two implementations and a cast or a type check in every method, and would buy nothing. One `Node` with an `isFile` flag reads better and runs faster here.

The Visitor pattern is the other one that sounds attractive for trees, and it is a worse fit for the same reason plus one more: it pays off when you keep adding new kinds of traversals over a stable structure, while here there are six fixed operations and none of them traverse.

If the problem later grew a `du`, a `find`, a `copy` or permissions per entry, Composite would earn its place. Reaching for it now is design for a future that the requirements do not describe.

## Time and space

Let L be the length of a path, k the number of entries inside one directory, and C the size of a file.

| Method | Solution 1 | Solution 2 |
| --- | --- | --- |
| `ls` on a directory | O(N x L + k log k) over all N stored paths | O(L + k log k) |
| `mkdir` | O(L^2) because every prefix builds a new string | O(L) |
| `addContentToFile` | O(C) per call, the whole file is copied | O(L + length of the added text) |
| `readContentFromFile` | O(1) | O(L + C) to walk there and build the string |
| `createPath` | O(L) | O(L) |
| `get` | O(L) | O(L) |

Space for solution 2 is proportional to the number of names stored plus the total content, while solution 1 also pays for repeating every ancestor name inside every deeper key.