# Count and Print Changed Nodes in DoorDash Menu Tree in Python

#### Problem Statement
[https://codezym.com/question/105-count-changed-nodes-doordash-menu-tree](https://codezym.com/question/105-count-changed-nodes-doordash-menu-tree)

The menu is a tree, but we do not need to walk it. To decide what happened to a node, the rules only look at three things: its **key**, its **value** and its **immediate parent**.

So we flatten each tree into a lookup table: `key -> (value, parent)`. Comparing two trees then becomes comparing two dictionaries, one key at a time.

We will start with a simple brute force version to understand the rules. Then we will make it fast with dictionaries.

---

## Understanding the Change Rules

For every key, we check two things: is it in both trees, and is its immediate parent the same?

| Situation | Records |
|---|---|
| Key only in the old tree | `DELETED` |
| Key only in the new tree | `ADDED` |
| Key in both, parent changed | `DELETED` + `ADDED` (no `UPDATED_VALUE`, even if the value changed) |
| Key in both, same parent, value changed | `UPDATED_VALUE` |
| Key in both, same parent, same value | nothing |

A different children list is not a change by itself. It only shows up through children that were added, deleted or moved.

The root has no parent, so its parent key is `ROOT`.

---

## Why Not Walk Both Trees Together?

It is tempting to run DFS on both trees side by side. When a child exists only in the old tree, we mark its whole branch as deleted. When it exists only in the new tree, we mark its whole branch as added.

This breaks one rule: **only the immediate parent matters**. Look at this case:

```
Old:  a(1) -> b(2) -> c(3)
New:  a(1) -> x(9) -> b(2) -> c(3)
```

Only `b` changed its parent, from `a` to `x`. Node `c` is still under `b` with the same value, so `c` did **not** change.

The correct answer is 3 records:

```
DELETED:b,2,a
ADDED:b,2,x
ADDED:x,9,a
```

A side by side DFS sees `b` missing under `a`. It deletes the whole branch `b, c` and then adds the whole branch `x, b, c`. That gives 5 records, and `c` is wrongly deleted and added.

So we must compare every node on its own, not the shape of the trees.

---

## Brute Force: Search the Lists Every Time

For each key, we need its value and its parent in both trees. The simplest way is to scan the list every time we need one of them.

```python
# Value of a key, or None if the key is not in this tree. O(n)
def findValue(self, tree, key):
    for line in tree:
        nodeKey, value, children = line.split(",")
        if nodeKey == key:
            return value
    return None

# Parent of a key: the node that lists it as a child. O(n)
def findParent(self, tree, key):
    for line in tree:
        nodeKey, value, children = line.split(",")
        if key in children.split("|"):
            return nodeKey
    return "ROOT"  # nobody lists it as a child
```

For every key in the old tree, we call these helpers on both trees and apply the rules table. We do the same for every key in the new tree to find `ADDED` records.

**The problem:** each helper scans the whole list, which is `O(n)`. We call them for every key, so the total is `O(n^2)`.

With 100,000 nodes that is about 10 billion steps. Far too slow.

---

## Optimized Solution: Build Lookup Dictionaries Once

The brute force asks the same two questions again and again: "what is the value of this key?" and "who is the parent of this key?".

So we answer both questions once for every key, up front, and store the answers in a dictionary.

### Step 1: Flatten each tree into a dictionary

We read the node strings in two passes:

1. **Create nodes.** For every line, create a node with its value. Set its parent to `ROOT` for now.
2. **Link parents.** For every line, go through its children and set each child's parent to this line's key.

Why two passes? The lines can come in any order, so a child's line may appear before its parent's line. After pass 2, the only node still pointing to `ROOT` is the real root.

One small parsing trap: `"".split("|")` returns `[""]`, not an empty list. So we skip leaf nodes before splitting children.

### Step 2: Compare the two dictionaries

- Walk the **old** keys. If a key is missing from the new dictionary, or its parent changed, add a `DELETED` record. Else, if only its value changed, add an `UPDATED_VALUE` record.
- Walk the **new** keys. If a key is missing from the old dictionary, or its parent changed, add an `ADDED` record.

A moved node fails the parent check in both loops. So it naturally becomes one `DELETED` and one `ADDED`, and never an `UPDATED_VALUE`.

### Step 3: Put the output in order

We collect records in three lists: `deleted`, `added` and `updated`.

Since we walk the keys in sorted order, each list is already sorted by key. The final answer is `deleted + added + updated`.

`countChangedNodes` is just the length of this list.

### Dry Run on Example 2

| Key | Old (value, parent) | New (value, parent) | Result |
|---|---|---|---|
| a | 1, ROOT | 1, ROOT | no change |
| b | 2, a | 2, a | no change |
| c | 3, a | missing | `DELETED:c,3,a` |
| d | 4, b | 4, b | no change |
| e | 5, b | 5, b | no change |
| f | missing | 6, b | `ADDED:f,6,b` |
| g | 7, c | 7, h | `DELETED:g,7,c` and `ADDED:g,7,h` |
| h | missing | 8, a | `ADDED:h,8,a` |

Output: `["DELETED:c,3,a", "DELETED:g,7,c", "ADDED:f,6,b", "ADDED:g,7,h", "ADDED:h,8,a"]`, so the count is 5.

Notice that `b` got a new child `f`, but `b` itself is not counted. Only its children list changed.

### Why These Data Structures?

- **`MenuNode`**: holds the only two facts the rules need, `value` and `parent`. One lookup gives us both.
- **`dict` of key -> `MenuNode`**: one per tree. It finds any key in the other tree in `O(1)`, which removes the `O(n^2)` scans.
- **Sorted list of keys**: walking keys in sorted order gives sorted output for free. Calling `sorted()` on the dictionary keys is enough.

### Python Code

```python
from typing import Dict, List


class MenuNode:
    """The only two facts the change rules need about a node:
    its value and the key of its immediate parent."""

    def __init__(self, value: str, parent: str):
        self.value = value
        self.parent = parent


class DoorDashMenuDiff:

    def __init__(self):
        pass

    def countChangedNodes(self, existingTree: List[str], newTree: List[str]) -> int:
        """Total number of change records."""
        return len(self.printChanges(existingTree, newTree))

    def printChanges(self, existingTree: List[str], newTree: List[str]) -> List[str]:
        """DELETED records first, then ADDED, then UPDATED_VALUE. Each group is sorted by key."""
        oldNodes = self.buildNodeMap(existingTree)
        newNodes = self.buildNodeMap(newTree)

        deleted = []
        added = []
        updated = []

        # Old keys in sorted order: find DELETED and UPDATED_VALUE records.
        for key in self.getSortedKeys(oldNodes):
            oldNode = oldNodes[key]
            newNode = newNodes.get(key)

            if newNode is None or newNode.parent != oldNode.parent:
                # gone from the new tree, or moved to a different parent
                deleted.append(f"DELETED:{key},{oldNode.value},{oldNode.parent}")
            elif newNode.value != oldNode.value:
                # same parent, different value
                updated.append(f"UPDATED_VALUE:{key},{oldNode.value},"
                               f"{newNode.value},{oldNode.parent}")

        # New keys in sorted order: find ADDED records.
        for key in self.getSortedKeys(newNodes):
            newNode = newNodes[key]
            oldNode = oldNodes.get(key)

            if oldNode is None or oldNode.parent != newNode.parent:
                # brand new, or moved here from a different parent
                added.append(f"ADDED:{key},{newNode.value},{newNode.parent}")

        return deleted + added + updated

    def buildNodeMap(self, tree: List[str]) -> Dict[str, MenuNode]:
        """Turns "key,value,children" strings into a dict of key -> MenuNode.
        Two passes are needed because a child's line can come before its parent's line."""
        nodes = {}

        # Pass 1: create every node. Its parent is "ROOT" until some parent claims it.
        for line in tree:
            key, value, _ = line.split(",")
            nodes[key] = MenuNode(value, "ROOT")

        # Pass 2: every parent tells its children who their parent is.
        for line in tree:
            key, _, children = line.split(",")
            if not children:
                continue  # leaf node. Note: "".split("|") gives [""], not []
            for child in children.split("|"):
                nodes[child].parent = key

        return nodes

    def getSortedKeys(self, nodes: Dict[str, MenuNode]) -> List[str]:
        """Keys of the dict in ascending lexicographic order."""
        return sorted(nodes.keys())
```

---

## Complexity

- **Time:** `O(n log n)`, where `n` is the total number of nodes in both trees. Building and comparing the dictionaries takes `O(n)`. Sorting the keys takes `O(n log n)`.
- **Space:** `O(n)` for the two dictionaries and the output.