# Design File System With Create, Get and Delete Path

#### Problem Statement
[https://codezym.com/question/279-design-file-system-create-get-delete-path](https://codezym.com/question/279-design-file-system-create-get-delete-path)

## Core Idea

This question looks like a big systems problem, but underneath it is only about one decision: how do we store paths so that every path can quickly find its parent and its children.

Once we store the data as a **tree**, where one node stands for one path component, all three methods turn into a short walk down that tree. `createPath` walks to the parent and adds one child. `get` walks to the node and reads its value. `deletePath` walks to the parent and removes one entry from the parent's dictionary, and the whole subtree below it disappears along with it, because nothing points to it anymore.

The problem statement gives us a strong hint about the design. It says the file system does not distinguish between files and folders, and any path may store a value and may also have child paths. That is exactly the **Composite pattern**: a single node class that carries both a value and a dictionary of children, so a leaf path and a path with children are the same kind of object. No method ever has to ask "is this a file or a folder", which removes an entire class of special cases from the code.

We will not jump to the tree straight away. We will first write the simplest thing that works, a single dictionary from the full path string to its value, see exactly where it becomes slow, and then fix that one weakness with the tree.

## What each method has to check

Before writing any code it helps to list the failure cases, because most of this problem is rule checking and not algorithms.

| Method | Returns False / "-1" when |
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

```python
def _is_valid_path(self, path):
    """A valid path is '/' plus one or more lowercase components joined by '/'."""
    if not isinstance(path, str) or len(path) < 2 or path[0] != '/':
        return False

    i, n = 1, len(path)
    while i < n:
        start = i
        while i < n and path[i] != '/':
            if not ('a' <= path[i] <= 'z'):   # only a-z allowed
                return False
            i += 1
        if i == start:                        # empty component, such as "//"
            return False
        if i == n:                            # ended on a component, so valid
            return True
        i += 1                                # step over the '/' and read the next one
    return False                              # ended with '/', such as "/docs/"
```

The single scan covers every bad case at once. A length below 2 kills `""` and `/`. An empty component kills `//` and a leading `documents` without a slash never gets past the first check. Ending the loop right after a `/` kills a trailing slash.

## Solution 1: one flat dictionary from path to value

The simplest storage is a `dict` where the key is the complete path text and the value is the stored string.

Creating a path needs the immediate parent, and with full paths as keys the parent is just a slice. The parent of `/store/orders` is everything before the last slash, which is `/store`. For a top level path such as `/store` that slice is the empty string, and the empty string stands for the root, which always exists.

Deleting is the awkward part. The dictionary has no idea which keys are children of which, so we look at every stored key and drop the ones that start with `path + "/"`.

```python
class FileSystem:
    # Reserved answer for an invalid or missing path.
    MISSING = "-1"

    def __init__(self):
        # full path string to the value stored at that path
        self.path_to_value = {}

    def createPath(self, path, value):
        if not self._is_valid_path(path) or value is None or value == self.MISSING:
            return False
        if path in self.path_to_value:
            return False

        # parent of "/a/b" is "/a", parent of "/a" is "" which means the root
        parent = path[:path.rfind('/')]
        if parent and parent not in self.path_to_value:
            return False

        self.path_to_value[path] = value
        return True

    def get(self, path):
        if not self._is_valid_path(path):
            return self.MISSING
        return self.path_to_value.get(path, self.MISSING)

    def deletePath(self, path):
        if not self._is_valid_path(path) or path not in self.path_to_value:
            return False

        # every descendant key starts with path + "/", so collect them first
        prefix = path + "/"
        doomed = [k for k in self.path_to_value if k == path or k.startswith(prefix)]
        for key in doomed:
            del self.path_to_value[key]
        return True

    def _is_valid_path(self, path):
        """A valid path is '/' plus one or more lowercase components joined by '/'."""
        if not isinstance(path, str) or len(path) < 2 or path[0] != '/':
            return False

        i, n = 1, len(path)
        while i < n:
            start = i
            while i < n and path[i] != '/':
                if not ('a' <= path[i] <= 'z'):   # only a-z allowed
                    return False
                i += 1
            if i == start:                        # empty component, such as "//"
                return False
            if i == n:                            # ended on a component, so valid
                return True
            i += 1                                # step over the '/' and read the next one
        return False                              # ended with '/', such as "/docs/"
```

Why a `dict` here: it gives us constant time create and get, and that part of the design is already as good as it gets. Nothing else is needed for two of the three methods.

**Note on the prefix check.** We compare against `path + "/"` and not against `path` alone. If we used `path` alone, deleting `/a/b` would also wipe out `/a/bx`, which is a completely unrelated sibling. The extra slash is what separates a real child from a name that merely starts with the same letters.

**Note on the doomed list.** We collect the keys into a list before deleting them, because removing entries from a dictionary while looping over it raises an error in Python.

### Where this solution hurts

Create and get are fine. Delete is the problem.

Every delete touches every key in the dictionary, so with 10,000 stored paths a single delete does 10,000 string comparisons even when the path being removed has no children at all.

The deeper issue is that the structure of the data is hidden inside the strings. The dictionary stores `/store` and `/store/orders` as two unrelated keys, and we have to re-derive the parent and child relationship with text operations on every delete. Storing the relationship directly is what fixes it.

## Solution 2: store the file system as a tree of nodes

Instead of one long key per path, we keep one node per component.

`/store/orders/recent` becomes root, then a child named `store`, then a child named `orders` under it, then a child named `recent` under that. Each node holds its own value and a dictionary from child name to child node.

```
root
 └── "store"   value = "open"
      └── "orders"   value = "pending"
           └── "recent"   value = "five"
```

Now the three operations line up with the structure.

To create, walk through every component except the last. If any step is missing, the immediate parent does not exist and we return False. Otherwise check the last name inside the parent's children and add it if it is free.

To get, do the same walk and read the value of the final node.

To delete, do the same walk and remove the last name from the parent's children. That one removal detaches the entire subtree, because the only way into those nodes was through the link we just cut.

```python
class Node:
    """
    One node per path component.
    The same class holds a value and child nodes, so a path that stores data
    and a path that has children under it are the same kind of object.
    """

    def __init__(self, value=None):
        self.value = value
        self.children = {}


class FileSystem:
    # Reserved answer for an invalid or missing path.
    MISSING = "-1"

    def __init__(self):
        # the root only holds top level paths, it is never a path itself
        self.root = Node()

    def createPath(self, path, value):
        if not self._is_valid_path(path) or value is None or value == self.MISSING:
            return False

        parts = path[1:].split('/')
        parent = self._walk_to_parent(parts)
        if parent is None:                       # immediate parent missing
            return False
        if parts[-1] in parent.children:         # path already exists
            return False

        parent.children[parts[-1]] = Node(value)
        return True

    def get(self, path):
        if not self._is_valid_path(path):
            return self.MISSING

        parts = path[1:].split('/')
        parent = self._walk_to_parent(parts)
        if parent is None:
            return self.MISSING

        node = parent.children.get(parts[-1])
        return self.MISSING if node is None else node.value

    def deletePath(self, path):
        if not self._is_valid_path(path):
            return False

        parts = path[1:].split('/')
        parent = self._walk_to_parent(parts)
        if parent is None:
            return False

        # unlinking the child drops its whole subtree in one step
        return parent.children.pop(parts[-1], None) is not None

    def _walk_to_parent(self, parts):
        """Walks from the root through every component except the last one."""
        current = self.root
        for part in parts[:-1]:
            current = current.children.get(part)
            if current is None:
                return None
        return current

    def _is_valid_path(self, path):
        """A valid path is '/' plus one or more lowercase components joined by '/'."""
        if not isinstance(path, str) or len(path) < 2 or path[0] != '/':
            return False

        i, n = 1, len(path)
        while i < n:
            start = i
            while i < n and path[i] != '/':
                if not ('a' <= path[i] <= 'z'):   # only a-z allowed
                    return False
                i += 1
            if i == start:                        # empty component, such as "//"
                return False
            if i == n:                            # ended on a component, so valid
                return True
            i += 1                                # step over the '/' and read the next one
        return False                              # ended with '/', such as "/docs/"
```

### Why these pieces

**The `Node` class.** This is the composite. One class holds a value and a dictionary of children, so a path with data and a path with children under it need no separate types and no type checks anywhere in the code.

**The `children` dictionary.** It gives constant time lookup of one child by name, which is what every walk does at each step. A list of children would force a linear scan at every level, and a sorted structure would cost more without buying anything, because the problem never asks for children in order.

**The `root` node with a `None` value.** The rules say the root exists as a parent but can never be created, read or deleted. Keeping it as a normal node with no value gives top level paths a parent to attach to, and since `_is_valid_path` already rejects `"/"`, no method can ever reach the root as a target.

**`_walk_to_parent`.** All three methods need the same walk, stopping one component short of the end. Writing it once keeps create, get and delete down to a few lines each and guarantees they agree on what "parent exists" means.

## Why Composite, and what we deliberately skipped

The Composite pattern is the right fit because the problem itself refuses to separate files from folders. Any path may hold a value and may hold children, which is exactly what a composite node is. The payoff shows up in delete: since a node owns its children, cutting one link removes the whole subtree in one operation, with no recursion and no bookkeeping.

Two other patterns sound reasonable here but are not worth it for this problem.

**Visitor** is the usual answer for "do something to a whole subtree", and deleting descendants sounds like exactly that. But we never actually need to visit the descendants. Removing the parent's link already makes them unreachable, so a visitor would add an interface and a traversal to do work that a single dictionary removal does for free.

**Command**, wrapping each create, get and delete into an object, pays off when you need undo, replay or a history log. This problem has none of those requirements. It would only add one object per call and an extra layer of indirection for the same result.

## Dry run of example 4

Start with an empty tree that holds only the root.

`createPath("/store", "open")` has one component, so the parent is the root. The name `store` is free, so we attach a node and return True.

`createPath("/store/orders", "pending")` walks to `store`, finds `orders` free, attaches it and returns True.

`createPath("/store/orders/recent", "five")` walks `store` then `orders`, attaches `recent` and returns True.

`deletePath("/store/orders")` walks to `store` and removes the key `orders` from its children. The node for `recent` is still in memory for a moment, but nothing points to it anymore, so it is gone from the file system. The call returns True.

`get("/store/orders")` walks to `store` and finds no child named `orders`, so it returns `"-1"`.

`get("/store/orders/recent")` cannot even reach the parent, since `orders` is missing, so it returns `"-1"`.

`get("/store")` reads the node under the root and returns `"open"`, which confirms the parent was untouched.

## Complexity

Let `L` be the length of the path, `C` the number of components in it, and `N` the number of paths currently stored.

| Operation | Solution 1, flat dictionary | Solution 2, tree |
| --- | --- | --- |
| `createPath` | O(L) | O(L) |
| `get` | O(L) | O(L) |
| `deletePath` | O(N x L) | O(L) |
| Memory | one full path string per entry | one short name per component, shared across siblings |

In the tree, each of the `C` steps hashes one short component, and the total work across those steps is proportional to `L`, so a single walk covers the whole path.

Delete is where the two approaches separate. The flat dictionary pays for the number of stored paths, while the tree pays only for the length of the path being deleted, no matter how large the subtree under it is.