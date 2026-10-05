# Design Dictionary App to Store Words and Their Meanings in Python

#### Problem Statement
[https://codezym.com/question/26-design-dictionary-app-words-meanings](https://codezym.com/question/26-design-dictionary-app-words-meanings)

Our dictionary has to answer three kinds of questions: what does a word mean, which words start with some letters, and does any word fit a pattern like `c.t`. All three read a word from left to right, one character at a time.

A **Trie** (also called a prefix tree) is built for exactly this. Words that start the same way share the same path in the tree. So a prefix search jumps straight to the right branch, and a `.` wildcard simply means "try every branch at this step".

We will start with a simple solution using a Python `dict`, see where it gets slow, and then fix those parts with a Trie.

## Understanding the Problem

| Method | What it does |
|---|---|
| `storeWord(word, meaning)` | Adds a word, or replaces its meaning if the word already exists |
| `getMeaning(word)` | Returns the meaning, or `""` if the word is not stored |
| `searchWords(prefix, n)` | Returns up to `n` words starting with `prefix`, sorted in ascending order |
| `exists(word)` | Returns `True` if at least one stored word matches `word`. Here `word` is a pattern where each `.` matches exactly one character |

Two details matter:

- Words contain only `a` to `z`, space `' '` and hyphen `'-'`. They never contain `.`.
- In `exists`, the pattern and the stored word must have the **same length**. So `c.` does not match `cat`.

## Solution 1: Simple Dict (Brute Force)

### Idea

Keep a `dict` from word to meaning. It finds any word's meaning in a single lookup.

- `storeWord` is `meanings[word] = meaning`, which also replaces an old meaning for us.
- `getMeaning` is `meanings.get(word, "")`.
- `searchWords` checks every word with `startswith(prefix)`, sorts the matches and returns the first `n`.
- `exists` compares the pattern with every stored word. Lengths must be equal, a `.` matches any character, and every other character must be the same.

### Code

```python
class DictionaryApp:
    def __init__(self):
        # word -> meaning
        self.meanings = {}

    def storeWord(self, word, meaning):
        # assigning again replaces any older meaning of the same word
        self.meanings[word] = meaning

    def getMeaning(self, word):
        return self.meanings.get(word, "")

    def searchWords(self, prefix, n):
        # 1. collect every word that starts with prefix
        matches = [word for word in self.meanings if word.startswith(prefix)]
        # 2. sort them and keep only the first n
        matches.sort()
        return matches[:n]

    def exists(self, word):
        """'word' is a pattern. Each '.' in it matches exactly one character."""
        for stored in self.meanings:
            if self.is_match(stored, word):
                return True
        return False

    def is_match(self, stored, pattern):
        """Compares one stored word with the pattern, character by character."""
        if len(stored) != len(pattern):
            return False
        for s, p in zip(stored, pattern):
            if p != "." and p != s:
                return False
        return True
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

```python
class TrieNode:
    def __init__(self):
        self.children = [None] * 28
        self.word = None
        self.meaning = None
```

- **`children`**: a list with one slot for each allowed character, 26 letters plus space and hyphen. A slot is `None` when no word continues with that character.
- **`word`**: the full word that ends at this node, or `None` if no word ends here. It marks the end of a word, and it lets us return the word directly instead of rebuilding it letter by letter.
- **`meaning`**: the meaning of that word. The node already marks where the word ends, so it is the natural place to keep the meaning. No separate `dict` is needed.

### Why the slots are in sorted order

Each character gets its slot from its position in this string:

```python
self.alphabet = " -abcdefghijklmnopqrstuvwxyz"
```

Space is slot 0, hyphen is slot 1, `a` is slot 2 and `z` is slot 27.

Why do space and hyphen come first? Python compares strings using character codes. Space is 32, hyphen is 45 and `a` is 97. So `"ice cream"` < `"ice-cream"` < `"icebox"`.

Because the slots follow the same order, visiting children from slot 0 to slot 27 visits them in sorted order. We get sorted results without ever calling sort.

### storeWord and getMeaning

`storeWord` walks down one character at a time and creates any missing node. At the last node, it saves the word and its meaning. Storing the same word again just replaces the meaning.

`getMeaning` follows the same path. If the path breaks, the word is not stored.

There is one catch. A path can exist only as part of a longer word. After storing just `apple`, the path `a → p → p` exists, but no word ends there. That is why we also check `node.word is not None`.

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
- **`.`**: it can be any character, so try every child that exists. If any of them leads to a match, return `True`.
- **End of the pattern**: a word must end exactly at this node (`node.word is not None`). This is what makes the lengths match.

Let us try `..p` on the Trie above. The first `.` tries `c`, the second `.` tries `a`, and then `p` exists under `ca`. A word ends there (`cap`), so the answer is `True`. We never even look at the `m` branch.

Now try `c.`. We go to `c`, and the `.` tries its only child `a`. The pattern ends here, but no word ends at `ca`. No other child is left, so the answer is `False`.

### Code

```python
class TrieNode:
    """
    One node of the Trie.
    The path from the root to a node spells a prefix shared by one or more words.
    """

    def __init__(self):
        # one slot per allowed character, in sorted order: space, hyphen, 'a' to 'z'
        self.children = [None] * 28
        # the full word that ends at this node, None if no word ends here
        self.word = None
        # meaning of the word that ends at this node
        self.meaning = None


class DictionaryApp:
    def __init__(self):
        # allowed characters in sorted order. Index of a character here = its slot in children.
        # space (32) and hyphen (45) come before 'a' (97) when Python compares strings.
        self.alphabet = " -abcdefghijklmnopqrstuvwxyz"
        self.root = TrieNode()

    def storeWord(self, word, meaning):
        node = self.root
        for c in word:
            slot = self.alphabet.find(c)
            # create the path if it does not exist yet
            if node.children[slot] is None:
                node.children[slot] = TrieNode()
            node = node.children[slot]
        node.word = word
        node.meaning = meaning  # replaces any older meaning

    def getMeaning(self, word):
        node = self.find_node(word)
        # the path can exist only as a prefix of a longer word, so check node.word too
        if node is None or node.word is None:
            return ""
        return node.meaning

    def searchWords(self, prefix, n):
        result = []
        node = self.find_node(prefix)
        if node is not None:
            self.collect_words(node, n, result)
        return result

    def exists(self, word):
        """'word' is a pattern. Each '.' in it matches exactly one character."""
        return self.matches(self.root, word, 0)

    def find_node(self, text):
        """Follows text from the root. Returns the node where it ends, or None if the path breaks."""
        node = self.root
        for c in text:
            slot = self.alphabet.find(c)
            if slot == -1 or node.children[slot] is None:
                return None
            node = node.children[slot]
        return node

    def collect_words(self, node, n, result):
        """
        Depth first search. Takes the word at a node first, then visits children
        from the smallest character to the largest. This gives words in sorted order.
        Stops as soon as n words are collected.
        """
        if len(result) >= n:
            return
        if node.word is not None:
            result.append(node.word)
        for child in node.children:
            if child is not None:
                self.collect_words(child, n, result)

    def matches(self, node, pattern, pos):
        """True if some word below node matches the pattern from index pos onwards."""
        # whole pattern is used up: a word must end exactly here
        if pos == len(pattern):
            return node.word is not None
        c = pattern[pos]
        if c == ".":
            # '.' can be any character, so try every child
            for child in node.children:
                if child is not None and self.matches(child, pattern, pos + 1):
                    return True
            return False
        slot = self.alphabet.find(c)
        if slot == -1 or node.children[slot] is None:
            return False
        return self.matches(node.children[slot], pattern, pos + 1)
```

## Complexity

`L` = length of the word or pattern, `P` = length of the prefix, `W` = number of stored words.

| Method | Solution 1: Dict | Solution 2: Trie |
|---|---|---|
| `storeWord` | O(L) | O(L) |
| `getMeaning` | O(L) | O(L) |
| `searchWords` | Checks all `W` words, then sorts the matches | O(P + n × L) |
| `exists` | O(W × L) | O(L) without dots |

For `searchWords`, the Trie visits only the nodes on the paths to the `n` words it returns.

For `exists` with dots, the Trie may try several branches. But it only follows paths that really exist, while the dict solution always checks every word.

**Space:** O(total characters stored) for both. Shared prefixes are stored only once in the Trie, and each node keeps 28 child slots.