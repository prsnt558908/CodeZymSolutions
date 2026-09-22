# Design File System With Create, Get and Delete Path in Java

#### Problem Statement
[https://codezym.com/question/279-design-file-system-create-get-delete-path](https://codezym.com/question/279-design-file-system-create-get-delete-path)

## Core Idea

This question looks like a big systems problem, but underneath it is only about one decision: how do we store paths so that every path can quickly find its parent and its children.

Once we store the data as a **tree**, where one node stands for one path component, all three methods turn into a short walk down that tree. `createPath` walks to the parent and adds one child. `get` walks to the node and reads its value. `deletePath` walks to the parent and removes one entry from the parent's map, and the whole subtree below it disappears along with it, because nothing points to it anymore.

The problem statement gives us a strong hint about the design. It says the file system does not distinguish between files and folders, and any path may store a value and may also have child paths. That is exactly the **Composite pattern**: a single node class that carries both a value and a map of children, so a leaf path and a path with children are the same kind of object. No method ever has to ask "is this a file or a folder", which removes an entire class of special cases from the code.

We will not jump to the tree straight away. We will first write the simplest thing that works, a single map from the full path string to its value, see exactly where it becomes slow, and then fix that one weakness with the tree.

## What each method has to check

Before writing any code it helps to list the failure cases, because most of this problem is rule checking and not algorithms.

| Method | Returns false / "-1" when |
| --- | --- |
| `createPath(path, value)` | path is invalid, value is exactly `"-1"`, path already exists, immediate parent does not exist |
| `get(path)` | path is invalid, path does not exist |
| `deletePath(path)` | path is invalid, path does not exist |

Two rules are easy to miss.

Creating a path never creates missing parents, so `/music/rock` fails on an empty file system even though it looks harmless.

Creating a path that already exists fails and leaves the old value untouched, so create is never an update.

## Step 0: validating a path

Both solutions need the same check, so we write it once.

A valid path is a `/` followed by one or more components, each component holding at least one character and only lowercase letters `a` to `z`.

So `/documents` and `/documents/images` are fine, while `""`, `/`, `documents`, `/documents/`, `/documents//images` and `/Documents` are all rejected.

```java
/** A valid path is "/" plus one or more lowercase components joined by "/". */
private boolean isValidPath(String path) {
    if (path == null || path.length() < 2 || path.charAt(0) != '/') return false;

    int i = 1;
    while (i < path.length()) {
        int start = i;
        while (i < path.length() && path.charAt(i) != '/') {
            char c = path.charAt(i);
            if (c < 'a' || c > 'z') return false;   // only a-z allowed
            i++;
        }
        if (i == start) return false;               // empty component, such as "//"
        if (i == path.length()) return true;        // ended on a component, so valid
        i++;                                        // step over the '/' and read the next one
    }
    return false;                                   // ended with '/', such as "/docs/"
}
```

The single scan covers every bad case at once. A length below 2 kills `""` and `/`. An empty component kills `//` and a leading `documents` without a slash never gets past the first check. Ending the loop right after a `/` kills a trailing slash.

## Solution 1: one flat map from path to value

The simplest storage is a `HashMap<String, String>` where the key is the complete path text.

Creating a path needs the immediate parent, and with full paths as keys the parent is just a substring. The parent of `/store/orders` is everything before the last slash, which is `/store`. For a top level path such as `/store` that substring is the empty string, and the empty string stands for the root, which always exists.

Deleting is the awkward part. The map has no idea which keys are children of which, so we look at every stored key and drop the ones that start with `path + "/"`.

```java
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FileSystem {

    /** Reserved answer for an invalid or missing path. */
    private static final String MISSING = "-1";

    /** Full path string to the value stored at that path. */
    private final Map<String, String> pathToValue = new HashMap<>();

    public FileSystem() {
    }

    public boolean createPath(String path, String value) {
        if (!isValidPath(path) || value == null || MISSING.equals(value)) return false;
        if (pathToValue.containsKey(path)) return false;

        // parent of "/a/b" is "/a", parent of "/a" is "" which means the root
        String parent = path.substring(0, path.lastIndexOf('/'));
        if (!parent.isEmpty() && !pathToValue.containsKey(parent)) return false;

        pathToValue.put(path, value);
        return true;
    }

    public String get(String path) {
        if (!isValidPath(path)) return MISSING;
        return pathToValue.getOrDefault(path, MISSING);
    }

    public boolean deletePath(String path) {
        if (!isValidPath(path) || !pathToValue.containsKey(path)) return false;

        // every descendant key starts with path + "/", so scan and drop them
        String prefix = path + "/";
        Iterator<String> keys = pathToValue.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.equals(path) || key.startsWith(prefix)) keys.remove();
        }
        return true;
    }

    /** A valid path is "/" plus one or more lowercase components joined by "/". */
    private boolean isValidPath(String path) {
        if (path == null || path.length() < 2 || path.charAt(0) != '/') return false;

        int i = 1;
        while (i < path.length()) {
            int start = i;
            while (i < path.length() && path.charAt(i) != '/') {
                char c = path.charAt(i);
                if (c < 'a' || c > 'z') return false;   // only a-z allowed
                i++;
            }
            if (i == start) return false;               // empty component, such as "//"
            if (i == path.length()) return true;        // ended on a component, so valid
            i++;                                        // step over the '/' and read the next one
        }
        return false;                                   // ended with '/', such as "/docs/"
    }
}
```

Why `Map<String, String>` here: a hash map gives us constant time create and get, and that part of the design is already as good as it gets. Nothing else is needed for two of the three methods.

**Note on the prefix check.** We compare against `path + "/"` and not against `path` alone. If we used `path` alone, deleting `/a/b` would also wipe out `/a/bx`, which is a completely unrelated sibling. The extra slash is what separates a real child from a name that merely starts with the same letters.

### Where this solution hurts

Create and get are fine. Delete is the problem.

Every delete touches every key in the map, so with 10,000 stored paths a single delete does 10,000 string comparisons even when the path being removed has no children at all. A run of many deletes ends up doing tens of millions of comparisons for work that should be nearly instant.

The deeper issue is that the structure of the data is hidden inside the strings. The map stores `/store` and `/store/orders` as two unrelated keys, and we have to re-derive the parent and child relationship with text operations on every delete. Storing the relationship directly is what fixes it.

## Solution 2: store the file system as a tree of nodes

Instead of one long key per path, we keep one node per component.

`/store/orders/recent` becomes root, then a child named `store`, then a child named `orders` under it, then a child named `recent` under that. Each node holds its own value and a map from child name to child node.

```
root
 └── "store"   value = "open"
      └── "orders"   value = "pending"
           └── "recent"   value = "five"
```

Now the three operations line up with the structure.

To create, walk through every component except the last. If any step is missing, the immediate parent does not exist and we return false. Otherwise check the last name inside the parent's children map and add it if it is free.

To get, do the same walk and read the value of the final node.

To delete, do the same walk and remove the last name from the parent's children map. That one removal detaches the entire subtree, because the only way into those nodes was through the link we just cut.

```java
import java.util.HashMap;
import java.util.Map;

public class FileSystem {

    /** Reserved answer for an invalid or missing path. */
    private static final String MISSING = "-1";

    /**
     * One node per path component.
     * The same class holds a value and child nodes, so a path that stores data
     * and a path that has children under it are the same kind of object.
     */
    private static class Node {
        String value;
        Map<String, Node> children = new HashMap<>();

        Node(String value) {
            this.value = value;
        }
    }

    /** The root only holds top level paths. It is never a path itself. */
    private final Node root = new Node(null);

    public FileSystem() {
    }

    public boolean createPath(String path, String value) {
        if (!isValidPath(path) || value == null || MISSING.equals(value)) return false;

        String[] parts = path.substring(1).split("/");
        Node parent = walkToParent(parts);
        if (parent == null) return false;                       // immediate parent missing

        String name = parts[parts.length - 1];
        if (parent.children.containsKey(name)) return false;    // path already exists

        parent.children.put(name, new Node(value));
        return true;
    }

    public String get(String path) {
        if (!isValidPath(path)) return MISSING;

        String[] parts = path.substring(1).split("/");
        Node parent = walkToParent(parts);
        if (parent == null) return MISSING;

        Node node = parent.children.get(parts[parts.length - 1]);
        return node == null ? MISSING : node.value;
    }

    public boolean deletePath(String path) {
        if (!isValidPath(path)) return false;

        String[] parts = path.substring(1).split("/");
        Node parent = walkToParent(parts);
        if (parent == null) return false;

        // unlinking the child drops its whole subtree in one step
        return parent.children.remove(parts[parts.length - 1]) != null;
    }

    /** Walks from the root through every component except the last one. */
    private Node walkToParent(String[] parts) {
        Node current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            current = current.children.get(parts[i]);
            if (current == null) return null;
        }
        return current;
    }

    /** A valid path is "/" plus one or more lowercase components joined by "/". */
    private boolean isValidPath(String path) {
        if (path == null || path.length() < 2 || path.charAt(0) != '/') return false;

        int i = 1;
        while (i < path.length()) {
            int start = i;
            while (i < path.length() && path.charAt(i) != '/') {
                char c = path.charAt(i);
                if (c < 'a' || c > 'z') return false;   // only a-z allowed
                i++;
            }
            if (i == start) return false;               // empty component, such as "//"
            if (i == path.length()) return true;        // ended on a component, so valid
            i++;                                        // step over the '/' and read the next one
        }
        return false;                                   // ended with '/', such as "/docs/"
    }
}
```

### Why these pieces

**The `Node` class.** This is the composite. One class holds a value and a map of children, so a path with data and a path with children under it need no separate types and no type checks anywhere in the code.

**`Map<String, Node> children`.** A plain hash map gives constant time lookup of one child by name, which is what every walk does at each step. A list of children would force a linear scan at every level, and a sorted structure would cost more without buying anything, because the problem never asks for children in order.

**The `root` node with a `null` value.** The rules say the root exists as a parent but can never be created, read or deleted. Keeping it as a normal node with no value gives top level paths a parent to attach to, and since `isValidPath` already rejects `"/"`, no method can ever reach the root as a target.

**`walkToParent`.** All three methods need the same walk, stopping one component short of the end. Writing it once keeps create, get and delete down to a few lines each and guarantees they agree on what "parent exists" means.

## Why Composite, and what we deliberately skipped

The Composite pattern is the right fit because the problem itself refuses to separate files from folders. Any path may hold a value and may hold children, which is exactly what a composite node is. The payoff shows up in delete: since a node owns its children, cutting one link removes the whole subtree in one operation, with no recursion and no bookkeeping.

Two other patterns sound reasonable here but are not worth it for this problem.

**Visitor** is the usual answer for "do something to a whole subtree", and deleting descendants sounds like exactly that. But we never actually need to visit the descendants. Removing the parent's link already makes them unreachable, so a visitor would add an interface and a traversal to do work that a single map removal does for free.

**Command**, wrapping each create, get and delete into an object, pays off when you need undo, replay or a history log. This problem has none of those requirements. It would only add one object per call and an extra layer of indirection for the same result.

## Dry run of example 4

Start with an empty tree that holds only the root.

`createPath("/store", "open")` has one component, so the parent is the root. The name `store` is free, so we attach a node and return true.

`createPath("/store/orders", "pending")` walks to `store`, finds `orders` free, attaches it and returns true.

`createPath("/store/orders/recent", "five")` walks `store` then `orders`, attaches `recent` and returns true.

`deletePath("/store/orders")` walks to `store` and removes the key `orders` from its children. The node for `recent` is still in memory for a moment, but nothing points to it anymore, so it is gone from the file system. The call returns true.

`get("/store/orders")` walks to `store` and finds no child named `orders`, so it returns `"-1"`.

`get("/store/orders/recent")` cannot even reach the parent, since `orders` is missing, so it returns `"-1"`.

`get("/store")` reads the node under the root and returns `"open"`, which confirms the parent was untouched.

## Complexity

Let `L` be the length of the path, `C` the number of components in it, and `N` the number of paths currently stored.

| Operation | Solution 1, flat map | Solution 2, tree |
| --- | --- | --- |
| `createPath` | O(L) | O(L) |
| `get` | O(L) | O(L) |
| `deletePath` | O(N x L) | O(L) |
| Memory | one full path string per entry | one short name per component, shared across siblings |

In the tree, each of the `C` steps hashes one short component, and the total work across those steps is proportional to `L`, so a single walk covers the whole path.

Delete is where the two approaches separate. The flat map pays for the number of stored paths, while the tree pays only for the length of the path being deleted, no matter how large the subtree under it is.