# Design Dictionary App to Store Words and Their Meanings in Java

#### Problem Statement
[https://codezym.com/question/26-design-dictionary-app-words-meanings](https://codezym.com/question/26-design-dictionary-app-words-meanings)

Our dictionary has to answer three kinds of questions: what does a word mean, which words start with some letters, and does any word fit a pattern like `c.t`. All three read a word from left to right, one character at a time.

A **Trie** (also called a prefix tree) is built for exactly this. Words that start the same way share the same path in the tree. So a prefix search jumps straight to the right branch, and a `.` wildcard simply means "try every branch at this step".

We will start with a simple `HashMap` solution, see where it gets slow, and then fix those parts with a Trie.

## Understanding the Problem

| Method | What it does |
|---|---|
| `storeWord(word, meaning)` | Adds a word, or replaces its meaning if the word already exists |
| `getMeaning(word)` | Returns the meaning, or `""` if the word is not stored |
| `searchWords(prefix, n)` | Returns up to `n` words starting with `prefix`, sorted in ascending order |
| `exists(word)` | Returns `true` if at least one stored word matches `word`. Here `word` is a pattern where each `.` matches exactly one character |

Two details matter:

- Words contain only `a` to `z`, space `' '` and hyphen `'-'`. They never contain `.`.
- In `exists`, the pattern and the stored word must have the **same length**. So `c.` does not match `cat`.

## Solution 1: Simple HashMap (Brute Force)

### Idea

Keep a `HashMap` from word to meaning. It finds any word's meaning in a single lookup.

- `storeWord` is a `put()`, which also replaces an old meaning for us.
- `getMeaning` is a `getOrDefault(word, "")`.
- `searchWords` checks every word with `startsWith(prefix)`, sorts the matches and returns the first `n`.
- `exists` compares the pattern with every stored word. Lengths must be equal, a `.` matches any character, and every other character must be the same.

### Code

```java
import java.util.*;

public class DictionaryApp {

    // word -> meaning
    Map<String, String> meanings;

    public DictionaryApp() {
        meanings = new HashMap<>();
    }

    public void storeWord(String word, String meaning) {
        // put() replaces any older meaning of the same word
        meanings.put(word, meaning);
    }

    public String getMeaning(String word) {
        return meanings.getOrDefault(word, "");
    }

    public List<String> searchWords(String prefix, int n) {
        // 1. collect every word that starts with prefix
        List<String> matches = new ArrayList<>();
        for (String word : meanings.keySet()) {
            if (word.startsWith(prefix)) {
                matches.add(word);
            }
        }
        // 2. sort them and keep only the first n
        Collections.sort(matches);
        return new ArrayList<>(matches.subList(0, Math.min(n, matches.size())));
    }

    /** 'word' is a pattern. Each '.' in it matches exactly one character. */
    public boolean exists(String word) {
        for (String stored : meanings.keySet()) {
            if (isMatch(stored, word)) {
                return true;
            }
        }
        return false;
    }

    /** Compares one stored word with the pattern, character by character. */
    boolean isMatch(String stored, String pattern) {
        if (stored.length() != pattern.length()) {
            return false;
        }
        for (int i = 0; i < pattern.length(); i++) {
            char p = pattern.charAt(i);
            if (p != '.' && p != stored.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
```

### Problems with this approach

`storeWord` and `getMeaning` are already fast. The other two methods are the problem.

- **`searchWords` looks at every word on every call.** With 100,000 words, it checks all of them and sorts the matches, even when we need only 3 words.
- **`exists` also looks at every word.** A pattern like `c.t` gets compared even with words that start with `z`.

A sorted list of words could speed up prefix search, but a pattern like `..p` would still need to check every word. We need one structure that groups words by how they start. That structure is a Trie.

## Solution 2: Trie (Optimal)

### What is a Trie?

A Trie is a tree where each step down is one character. The path from the root to a node spells a prefix. If a stored word ends at a node, we mark that node.

Here is the Trie for `cat, cap, caps, map, man, many`:

```
              root
            /      \
          c          m
          |          |
          a          a
        /   \      /   \
      p*     t*  n*     p*
      |          |
      s*         y*

   * = a word ends here
```

`cat`, `cap` and `caps` share the path `c → a`. So when we search for words starting with `ca`, we go straight to that node and never look at `map` or `many`.

### TrieNode: one node of the tree

```java
class TrieNode {
    TrieNode[] children = new TrieNode[28];
    String word;
    String meaning;
}
```

- **`children`**: one slot for each allowed character, 26 letters plus space and hyphen. A slot is `null` when no word continues with that character.
- **`word`**: the full word that ends at this node, or `null` if no word ends here. It marks the end of a word, and it lets us return the word directly instead of rebuilding it letter by letter.
- **`meaning`**: the meaning of that word. The node already marks where the word ends, so it is the natural place to keep the meaning. No separate `HashMap` is needed.

### Why the slots are in sorted order

Each character gets its slot from its position in this string:

```java
String alphabet = " -abcdefghijklmnopqrstuvwxyz";
```

Space is slot 0, hyphen is slot 1, `a` is slot 2 and `z` is slot 27.

Why do space and hyphen come first? Java compares strings using character codes. Space is 32, hyphen is 45 and `a` is 97. So `"ice cream"` < `"ice-cream"` < `"icebox"`.

Because the slots follow the same order, visiting children from slot 0 to slot 27 visits them in sorted order. We get sorted results without ever calling sort.

### storeWord and getMeaning

`storeWord` walks down one character at a time and creates any missing node. At the last node, it saves the word and its meaning. Storing the same word again just replaces the meaning.

`getMeaning` follows the same path. If the path breaks, the word is not stored.

There is one catch. A path can exist only as part of a longer word. After storing just `apple`, the path `a → p → p` exists, but no word ends there. That is why we also check `node.word != null`.

### searchWords: DFS in slot order

1. Walk down to the node where the prefix ends. If the path breaks, return an empty list.
2. Run a depth first search (DFS) from that node. Take the node's own word first, then visit its children from slot 0 to slot 27.
3. Stop as soon as `n` words are collected.

Why does this give sorted order?

- A word comes before any longer word that starts with it, like `cap` before `caps`. Taking a node's word before its children's words handles this.
- When two words differ at some position, the one with the smaller character there comes first. Visiting children in slot order handles this.

In the Trie above, a DFS from the root collects `cap, caps, cat, man, many, map`. That is exactly sorted order.

Since we stop at `n` words, we visit only the part of the Trie we actually need.

### exists: matching `.` wildcards

We walk the pattern and the Trie together, one character at a time.

- **Normal character**: move to that child. If the child does not exist, this path fails.
- **`.`**: it can be any character, so try every child that exists. If any of them leads to a match, return `true`.
- **End of the pattern**: a word must end exactly at this node (`node.word != null`). This is what makes the lengths match.

Let us try `..p` on the Trie above. The first `.` tries `c`, the second `.` tries `a`, and then `p` exists under `ca`. A word ends there (`cap`), so the answer is `true`. We never even look at the `m` branch.

Now try `c.`. We go to `c`, and the `.` tries its only child `a`. The pattern ends here, but no word ends at `ca`. No other child is left, so the answer is `false`.

### Code

```java
import java.util.*;

/**
 * One node of the Trie.
 * The path from the root to a node spells a prefix shared by one or more words.
 */
class TrieNode {
    // One slot per allowed character, in sorted order: space, hyphen, 'a' to 'z'
    TrieNode[] children = new TrieNode[28];

    // The full word that ends at this node, null if no word ends here
    String word;

    // Meaning of the word that ends at this node
    String meaning;
}

public class DictionaryApp {

    // Allowed characters in sorted order. Index of a character here = its slot in children.
    // Space (32) and hyphen (45) come before 'a' (97) when Java compares strings.
    String alphabet = " -abcdefghijklmnopqrstuvwxyz";

    TrieNode root;

    public DictionaryApp() {
        root = new TrieNode();
    }

    public void storeWord(String word, String meaning) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int slot = alphabet.indexOf(c);
            // create the path if it does not exist yet
            if (node.children[slot] == null) {
                node.children[slot] = new TrieNode();
            }
            node = node.children[slot];
        }
        node.word = word;
        node.meaning = meaning; // replaces any older meaning
    }

    public String getMeaning(String word) {
        TrieNode node = findNode(word);
        // the path can exist only as a prefix of a longer word, so check node.word too
        if (node == null || node.word == null) {
            return "";
        }
        return node.meaning;
    }

    public List<String> searchWords(String prefix, int n) {
        List<String> result = new ArrayList<>();
        TrieNode node = findNode(prefix);
        if (node != null) {
            collectWords(node, n, result);
        }
        return result;
    }

    /** 'word' is a pattern. Each '.' in it matches exactly one character. */
    public boolean exists(String word) {
        return matches(root, word, 0);
    }

    /** Follows text from the root. Returns the node where it ends, or null if the path breaks. */
    TrieNode findNode(String text) {
        TrieNode node = root;
        for (char c : text.toCharArray()) {
            int slot = alphabet.indexOf(c);
            if (slot == -1 || node.children[slot] == null) {
                return null;
            }
            node = node.children[slot];
        }
        return node;
    }

    /**
     * Depth first search. Takes the word at a node first, then visits children
     * from the smallest character to the largest. This gives words in sorted order.
     * Stops as soon as n words are collected.
     */
    void collectWords(TrieNode node, int n, List<String> result) {
        if (result.size() >= n) {
            return;
        }
        if (node.word != null) {
            result.add(node.word);
        }
        for (TrieNode child : node.children) {
            if (child != null) {
                collectWords(child, n, result);
            }
        }
    }

    /** True if some word below node matches the pattern from index pos onwards. */
    boolean matches(TrieNode node, String pattern, int pos) {
        // whole pattern is used up: a word must end exactly here
        if (pos == pattern.length()) {
            return node.word != null;
        }
        char c = pattern.charAt(pos);
        if (c == '.') {
            // '.' can be any character, so try every child
            for (TrieNode child : node.children) {
                if (child != null && matches(child, pattern, pos + 1)) {
                    return true;
                }
            }
            return false;
        }
        int slot = alphabet.indexOf(c);
        if (slot == -1 || node.children[slot] == null) {
            return false;
        }
        return matches(node.children[slot], pattern, pos + 1);
    }
}
```

## Complexity

`L` = length of the word or pattern, `P` = length of the prefix, `W` = number of stored words.

| Method | Solution 1: HashMap | Solution 2: Trie |
|---|---|---|
| `storeWord` | O(L) | O(L) |
| `getMeaning` | O(L) | O(L) |
| `searchWords` | Checks all `W` words, then sorts the matches | O(P + n × L) |
| `exists` | O(W × L) | O(L) without dots |

For `searchWords`, the Trie visits only the nodes on the paths to the `n` words it returns.

For `exists` with dots, the Trie may try several branches. But it only follows paths that really exist, while the HashMap solution always checks every word.

**Space:** O(total characters stored) for both. Shared prefixes are stored only once in the Trie, and each node keeps 28 child slots.