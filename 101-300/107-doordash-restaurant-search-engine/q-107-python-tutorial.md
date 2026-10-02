# DoorDash Restaurant Search Engine in Python

#### Problem Statement
[https://codezym.com/question/107-doordash-restaurant-search-engine](https://codezym.com/question/107-doordash-restaurant-search-engine)


## Core Idea

The tricky part of this problem is prefix search: does any name start with `pan`, and which names start with `pan`? A **trie** (prefix tree) is built for exactly this. Names that start with the same letters share the same path of nodes, so walking down the letters of a prefix takes us to the one node under which all matching names are stored.


If we then explore that part of the tree in sorted order (space first, then `a` to `z`), the names come out already sorted. So we can stop as soon as we have `k` names, and we never sort the full list of matches.


We will start with a simple brute force solution, see why it is slow, and then improve it with a trie.


## Approach 1: Brute Force with a Set

Keep every restaurant name in a Python `set`.


**Why a set?** Each name must be stored only once. A set ignores duplicates on its own, and it can tell very quickly whether an exact name exists.


For `startsWith`, loop over every stored name and check `name.startswith(prefix)`. For `getTopKRestaurants`, collect all matching names, sort them, and return the first `k`.

```python
class RestaurantSearchEngine:
    def __init__(self, restaurantNames):
        # A set keeps each name only once and checks exact names quickly
        self.names = set(restaurantNames)

    def insertRestaurant(self, restaurantName):
        self.names.add(restaurantName)  # the set ignores duplicates

    def containsRestaurant(self, restaurantName):
        return restaurantName in self.names

    def startsWith(self, prefix):
        # Check every stored name, one by one
        for name in self.names:
            if name.startswith(prefix):
                return True
        return False

    def getTopKRestaurants(self, prefix, k):
        # Collect every match, sort all of them, then keep only the first k
        matches = [name for name in self.names if name.startswith(prefix)]
        matches.sort()
        return matches[:k]
```

### Why is this slow?

- `startsWith` and `getTopKRestaurants` check **every** stored name, even when only a few of them match.
- `getTopKRestaurants` sorts **all** the matches on every call, even when we only need the first `k`.
- With 30,000 names and 30,000 calls, this can add up to 900 million name checks.


What we really want is to jump straight to the names that start with the prefix, and read them in sorted order. A trie gives us exactly that.


## Approach 2: Trie

### What the trie looks like

Here is the trie after storing `"pancake house"`, `"panda express"` and `"panera bread"`:

```text
root
 |
 p
 |
 a
 |
 n
 |-- c -- a -- k -- e -- ' ' -- h -- o -- u -- s -- e   => "pancake house"
 |-- d -- a -- ' ' -- e -- x -- p -- r -- e -- s -- s   => "panda express"
 |-- e -- r -- a -- ' ' -- b -- r -- e -- a -- d        => "panera bread"
```

The shared part `pan` is stored only once. Each name ends at its own node, and that node remembers the full name.


The children of `n` are `c`, `d` and `e`. Visiting them in sorted order and reading each branch gives `["pancake house", "panda express", "panera bread"]`, which is exactly the answer for prefix `"pan"` in Example 2.


### The `TrieNode` class

Each node keeps two things:

- `children`: a dictionary that maps a character to its child node. Only characters that actually appear get an entry.
- `name`: the full restaurant name if a name ends at this node, otherwise `None`.


**Why a dictionary?** Most nodes have one child or none, like the nodes along `"cake house"` in `"pancake house"`. A dictionary stores only the children that exist. A fixed list of 27 slots (space plus 26 letters) would also work, but every node would carry all 27 slots, and the search would check all of them at every node. So the dictionary does less work and usually uses less memory.


**How do we keep children in sorted order?** A dictionary does not keep its keys sorted, so we sort the child characters while exploring. Space has a smaller character code (32) than any letter (`a` is 97), so `sorted()` puts space first, then `a` to `z`. A node has at most 27 children, so this sort is tiny.


**Why store the full name?** When the search reaches a node where a name ends, it can add that name to the answer right away, instead of rebuilding it letter by letter. It also tells us whether a name ends here: `None` means no name ends at this node.


### How each method works

**`insertRestaurant(name)`**: walk down the trie one character at a time. If the next node is missing, create it. At the last node, save the name.


Inserting the same name again just reaches the same last node. Nothing new is created, so duplicates are handled for free.


**`containsRestaurant(name)`**: walk down using the name. Return `True` only if the walk does not break **and** a name ends at the last node. For `"pan"` the walk works, but no name ends at the `n` node, so the answer is `False`.


**`startsWith(prefix)`**: walk down using the prefix. If the walk does not break, return `True`. This is safe because names are never removed, so every node in the trie is part of at least one stored name.


**`getTopKRestaurants(prefix, k)`**: walk to the node where the prefix ends. If the walk breaks, return an empty list. Otherwise run a depth first search (DFS) from that node:

1. If a name ends at the current node, add it to the answer.
2. Visit the children in sorted order: space first, then `a` to `z`.
3. Stop as soon as the answer has `k` names.


A name can be 2000 characters long, so the search can go 2000 levels deep. Python's default recursion limit is 1000, so we use our own stack instead of recursion.


A stack gives back the last item we put in. So we push the children from `z` down to space, which makes the smallest child come out first. Its whole branch is explored before the next smallest child comes out.


### Why does the DFS give sorted names?

Sorted (lexicographic) order follows two rules:

1. A name comes before any longer name that starts with it, so `"pan"` comes before `"panda"`. Our DFS adds a node's own name **before** going deeper.
2. Otherwise, the first different character decides: space comes before `a`, `a` comes before `b`, and so on. Our DFS visits the children in exactly this order.


So the first `k` names the DFS finds are the `k` smallest ones. Once we have them the search stops, and the rest of the trie is never explored.


### Complexity

Let `L` be the length of the name or prefix passed to a method.

| Method | Time |
|---|---|
| `insertRestaurant` | O(L) |
| `containsRestaurant` | O(L) |
| `startsWith` | O(L) |
| `getTopKRestaurants` | O(L + V) |


`V` is the number of nodes the DFS visits before it finds `k` names. At each visited node we sort at most 27 child characters, which is a small constant.


**Space:** in the worst case, one node for each character of each unique name. Names that share a prefix also share nodes, so the real count is usually much smaller.


### Code

```python
class TrieNode:
    """One node of the trie."""

    def __init__(self):
        # Maps a character to its child node. Only characters that appear get an entry.
        self.children = {}
        # Full restaurant name if a name ends at this node, otherwise None
        self.name = None


class RestaurantSearchEngine:
    """Restaurant search engine built on a trie (prefix tree).

    Names that start with the same letters share the same path of nodes.
    """

    def __init__(self, restaurantNames):
        self.root = TrieNode()
        for name in restaurantNames:
            self.insertRestaurant(name)

    def insertRestaurant(self, restaurantName):
        """Walks down the trie, creates missing nodes, and saves the name at the last node."""
        node = self.root
        for c in restaurantName:
            if c not in node.children:
                node.children[c] = TrieNode()
            node = node.children[c]
        # Inserting the same name again reaches this same node, so no duplicate is created
        node.name = restaurantName

    def containsRestaurant(self, restaurantName):
        node = self.findNode(restaurantName)
        # The path must exist AND a name must end exactly at this node
        return node is not None and node.name is not None

    def startsWith(self, prefix):
        # Names are never removed, so every node is part of at least one stored name
        return self.findNode(prefix) is not None

    def getTopKRestaurants(self, prefix, k):
        result = []
        start = self.findNode(prefix)
        if start is None:
            return result

        # DFS with our own stack: names can be 2000 characters deep,
        # but Python's default recursion limit is only 1000
        stack = [start]
        while stack and len(result) < k:
            node = stack.pop()
            # A node's own name comes before longer names below it ("pan" before "panda")
            if node.name is not None:
                result.append(node.name)
            # Push children from 'z' down to space, so the smallest character is popped first
            for c in sorted(node.children, reverse=True):
                stack.append(node.children[c])
        return result

    def findNode(self, text):
        """Follows the text one character at a time. Returns the last node, or None if the path breaks."""
        node = self.root
        for c in text:
            node = node.children.get(c)
            if node is None:
                return None
        return node
```