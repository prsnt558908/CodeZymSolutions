# DoorDash Restaurant Search Engine in Java

#### Problem Statement
[https://codezym.com/question/107-doordash-restaurant-search-engine](https://codezym.com/question/107-doordash-restaurant-search-engine)


## Core Idea

The tricky part of this problem is prefix search: does any name start with `pan`, and which names start with `pan`? A **trie** (prefix tree) is built for exactly this. Names that start with the same letters share the same path of nodes, so walking down the letters of a prefix takes us to the one node under which all matching names are stored.


If we then explore that part of the tree in sorted order (space first, then `a` to `z`), the names come out already sorted. So we can stop as soon as we have `k` names, without sorting anything.


We will start with a simple brute force solution, see why it is slow, and then improve it with a trie.


## Approach 1: Brute Force with a Set

Keep every restaurant name in a `HashSet<String>`.


**Why a set?** Each name must be stored only once. A set ignores duplicates on its own, and it can tell very quickly whether an exact name exists.


For `startsWith`, loop over every stored name and check `name.startsWith(prefix)`. For `getTopKRestaurants`, collect all matching names, sort them, and return the first `k`.

```java
import java.util.*;

public class RestaurantSearchEngine {

    // A set keeps each name only once and checks exact names quickly
    Set<String> names = new HashSet<>();

    public RestaurantSearchEngine(List<String> restaurantNames) {
        names.addAll(restaurantNames);
    }

    public void insertRestaurant(String restaurantName) {
        names.add(restaurantName); // the set ignores duplicates
    }

    public boolean containsRestaurant(String restaurantName) {
        return names.contains(restaurantName);
    }

    public boolean startsWith(String prefix) {
        // Check every stored name, one by one
        for (String name : names) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getTopKRestaurants(String prefix, int k) {
        // Collect every match, sort all of them, then keep only the first k
        List<String> matches = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(prefix)) {
                matches.add(name);
            }
        }
        Collections.sort(matches);
        return new ArrayList<>(matches.subList(0, Math.min(k, matches.size())));
    }
}
```

### Why is this slow?

- `startsWith` and `getTopKRestaurants` check **every** stored name, even when only a few of them match.
- `getTopKRestaurants` sorts **all** the matches on every call, even when we only need the first `k`.
- With 30,000 names and 30,000 calls, this can add up to 900 million name checks.


What we really want is to jump straight to the names that start with the prefix, and read them in sorted order without sorting. A trie gives us exactly that.


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


The children of `n` are in sorted order: `c`, `d`, `e`. Reading the branches from top to bottom gives `["pancake house", "panda express", "panera bread"]`, which is exactly the answer for prefix `"pan"` in Example 2.


### The `TrieNode` class

Each node keeps two things:

- `children`: an array of 27 slots, one for each possible next character. Slot `0` is for space, and slots `1` to `26` are for `a` to `z`.
- `name`: the full restaurant name if a name ends at this node, otherwise `null`.


**Why an array of 27 slots?** Names only contain lowercase letters and spaces, so 27 slots cover every possible character. Space has a smaller character code (32) than any letter (`a` is 97), so space comes first in sorted order. Keeping space in slot `0` means a simple loop from slot `0` to slot `26` visits the children in sorted order. No sorting is needed.


**Why store the full name?** When the search reaches a node where a name ends, it can add that name to the answer right away, instead of rebuilding it letter by letter. It also tells us whether a name ends here: `null` means no name ends at this node.


### How each method works

**`insertRestaurant(name)`**: walk down the trie one character at a time. If the next node is missing, create it. At the last node, save the name.


Inserting the same name again just reaches the same last node. Nothing new is created, so duplicates are handled for free.


**`containsRestaurant(name)`**: walk down using the name. Return `true` only if the walk does not break **and** a name ends at the last node. For `"pan"` the walk works, but no name ends at the `n` node, so the answer is `false`.


**`startsWith(prefix)`**: walk down using the prefix. If the walk does not break, return `true`. This is safe because names are never removed, so every node in the trie is part of at least one stored name.


**`getTopKRestaurants(prefix, k)`**: walk to the node where the prefix ends. If the walk breaks, return an empty list. Otherwise run a depth first search (DFS) from that node:

1. If a name ends at the current node, add it to the answer.
2. Visit the children from slot `0` (space) to slot `26` (`z`).
3. Stop as soon as the answer has `k` names.


A name is at most 2000 characters long, so the recursion never goes deeper than 2000 levels. Java handles that easily.


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


`V` is the number of nodes the DFS visits before it finds `k` names. Each visit loops over 27 slots, which is a small constant.


**Space:** in the worst case, one node for each character of each unique name. Names that share a prefix also share nodes, so the real count is usually much smaller.


### Code

```java
import java.util.*;

/**
 * Restaurant search engine built on a trie (prefix tree).
 * Names that start with the same letters share the same path of nodes.
 */
public class RestaurantSearchEngine {

    TrieNode root = new TrieNode();

    public RestaurantSearchEngine(List<String> restaurantNames) {
        for (String name : restaurantNames) {
            insertRestaurant(name);
        }
    }

    /** Walks down the trie, creates missing nodes, and saves the name at the last node. */
    public void insertRestaurant(String restaurantName) {
        TrieNode node = root;
        for (char c : restaurantName.toCharArray()) {
            int index = indexOf(c);
            if (node.children[index] == null) {
                node.children[index] = new TrieNode();
            }
            node = node.children[index];
        }
        // Inserting the same name again reaches this same node, so no duplicate is created
        node.name = restaurantName;
    }

    public boolean containsRestaurant(String restaurantName) {
        TrieNode node = findNode(restaurantName);
        // The path must exist AND a name must end exactly at this node
        return node != null && node.name != null;
    }

    public boolean startsWith(String prefix) {
        // Names are never removed, so every node is part of at least one stored name
        return findNode(prefix) != null;
    }

    public List<String> getTopKRestaurants(String prefix, int k) {
        List<String> result = new ArrayList<>();
        TrieNode node = findNode(prefix);
        if (node != null) {
            collectNames(node, k, result);
        }
        return result;
    }

    /** Follows the text one character at a time. Returns the last node, or null if the path breaks. */
    TrieNode findNode(String text) {
        TrieNode node = root;
        for (char c : text.toCharArray()) {
            node = node.children[indexOf(c)];
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /**
     * DFS that adds names in sorted order until k names are found.
     * A node's own name goes first ("pan" comes before "panda"),
     * then children are visited from slot 0 (space) to slot 26 ('z').
     */
    void collectNames(TrieNode node, int k, List<String> result) {
        if (result.size() >= k) {
            return; // already found k names
        }
        if (node.name != null) {
            result.add(node.name);
        }
        for (TrieNode child : node.children) {
            if (child != null) {
                collectNames(child, k, result);
            }
        }
    }

    /** Space goes to slot 0, and 'a' to 'z' go to slots 1 to 26. This matches sorted order. */
    int indexOf(char c) {
        return c == ' ' ? 0 : c - 'a' + 1;
    }
}

/** One node of the trie. */
class TrieNode {
    // children[0] is for space, children[1] to children[26] are for 'a' to 'z'
    TrieNode[] children = new TrieNode[27];

    // Full restaurant name if a name ends at this node, otherwise null
    String name;
}
```