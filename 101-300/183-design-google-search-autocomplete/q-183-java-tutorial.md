# Design Search Autocomplete System in Java

#### Problem Statement
[https://codezym.com/question/183-design-google-search-autocomplete](https://codezym.com/question/183-design-google-search-autocomplete)


Every time the user types a letter or a space, we have to answer one question: **which 3 saved sentences start with the text typed so far?**


The simple way is to check every saved sentence on every key press. It works, but it repeats a lot of work.


The better way is a **Trie** (prefix tree), where every node stands for one prefix. If every node also keeps its **top 3 sentences** ready, then a key press is just one step down the tree and the answer is already waiting there. When a sentence is finished with `#`, we walk down its path once and refresh those top 3 lists.


We will build the simple version first, see where it is slow, and then improve it step by step.


## Quick Recap of the Rules

- Each typed character is added to the current prefix.
- For a letter or a space, return up to 3 saved sentences that start with the prefix.
- Higher count comes first. For the same count, smaller ASCII order comes first. So `"a b"` comes before `"ab"` (space is smaller than letters), and `"car"` comes before `"cart"` (when one is a prefix of the other, the shorter one comes first).
- For `#`: save the typed sentence with its count increased by 1 (a new sentence gets count 1), clear the prefix and return an empty list. If nothing was typed, nothing is saved.


## Solution 1: Brute Force (Check Every Sentence)

### Idea

Keep every sentence and its count in a map. On every key press:

1. Add the character to the typed text.
2. Collect all sentences that start with the typed text.
3. Sort them: higher count first, then ASCII order.
4. Return the first 3.


On `#`, add 1 to the count of the typed sentence (starting from 0 if it is new) and clear the typed text.

### Data Structures

- **`Map<String, Integer> countMap`**: sentence to count. It gives the count of any sentence instantly, which we need for sorting and for the `+1` on `#`.
- **`StringBuilder typed`**: the characters typed so far. On `#` we need the whole sentence to save it.


The helper `compareSentences(a, b)` keeps the sorting rule in one place. It returns a negative number when `a` should come before `b`. Java's `String.compareTo` compares character codes one by one, which is exactly the ASCII order we need.

### Code

```java
import java.util.*;

public class SearchAutocomplete {
    // sentence -> popularity (how many times it was completed)
    Map<String, Integer> countMap = new HashMap<>();

    // characters typed so far for the current sentence
    StringBuilder typed = new StringBuilder();

    public SearchAutocomplete(List<String> phrases, List<Integer> counts) {
        for (int i = 0; i < phrases.size(); i++) {
            countMap.put(phrases.get(i), counts.get(i));
        }
    }

    public List<String> getSuggestions(char ch) {
        if (ch == '#') {
            // save the finished sentence (a new sentence starts from 0), then start fresh
            if (typed.length() > 0) {
                String sentence = typed.toString();
                countMap.put(sentence, countMap.getOrDefault(sentence, 0) + 1);
            }
            typed = new StringBuilder();
            return new ArrayList<>();
        }

        typed.append(ch);
        String prefix = typed.toString();

        // 1. collect every sentence that starts with the prefix
        List<String> matches = new ArrayList<>();
        for (String sentence : countMap.keySet()) {
            if (sentence.startsWith(prefix)) {
                matches.add(sentence);
            }
        }

        // 2. most popular first, ties broken by ASCII order
        matches.sort((a, b) -> compareSentences(a, b));

        // 3. return only the best three
        List<String> result = new ArrayList<>();
        for (int i = 0; i < matches.size() && i < 3; i++) {
            result.add(matches.get(i));
        }
        return result;
    }

    // negative value means sentence a should be shown before sentence b
    int compareSentences(String a, String b) {
        int countA = countMap.get(a);
        int countB = countMap.get(b);
        if (countA != countB) {
            return countB - countA;   // higher popularity first
        }
        return a.compareTo(b);        // same popularity: smaller ASCII order first
    }
}
```

### Why Is This Slow?

- **Every key press scans all sentences.** A real search box has millions of them.
- **Every key press sorts all the matches,** even though we only need 3. A single letter like `s` can match a huge part of the history.
- **Work is repeated.** Sentences matching `se` are always a subset of the ones matching `s`, yet we start from zero on every key press.


## Solution 2: Trie with Top 3 Stored at Every Node (Optimal)

We fix these problems in three small steps.

### Step 1: Use a Trie to stop scanning everything

A Trie stores sentences character by character. Every node stands for one prefix and every child is the next character. Sentences that share a prefix share the same nodes.


Here is the Trie for Example 4 (`cat = 3`, `car = 2`, `cart = 2`). The lists on the right are added in Step 2.

```text
root
`-- c                 top 3: cat, car, cart
    `-- a             top 3: cat, car, cart
        |-- t         top 3: cat
        `-- r         top 3: car, cart
            `-- t     top 3: cart
```

Typing a character now just means "move to that child". We keep the node of the typed prefix in a variable `current`, so each key press continues from where the last one stopped.


If the child does not exist, no saved sentence starts with this prefix, and typing more characters can never create a match. So we set `current` to `null` and return empty lists until `#`.

### Step 2: Store the top 3 inside every node

A Trie alone is not enough. To answer the prefix `s`, we would still have to visit every sentence below node `s` and sort them.


So every node keeps its answer ready in a small list, `topSentences`, which holds the best 3 sentences for its prefix. A key press now just returns a copy of that list. No scanning and no sorting.


This is a good trade because key presses (reads) happen far more often than finished sentences (writes).

### Step 3: Keep the top 3 lists correct

When a sentence is added or its count grows, only the nodes on **its own path** can change. So we walk down its path and at every node:

1. Remove the sentence from the list if it is already there.
2. Add it.
3. Sort the list (it has at most 4 items).
4. If there are 4 items, drop the last one.


The constructor uses the same method to add each initial phrase with its starting count.


For example, the user types `c`, `a`, `r`, `#`. Now `car` has count 3 and we refresh the nodes on its path:

| Node (prefix) | Top 3 before | Top 3 after |
|---|---|---|
| `c` | cat, car, cart | **car**, cat, cart |
| `ca` | cat, car, cart | **car**, cat, cart |
| `car` | car, cart | car, cart |
| `cat` | cat | not on the path, unchanged |
| `cart` | cart | not on the path, unchanged |

`car` and `cat` both have count 3 now, so ASCII order puts `car` first.


**Why is it safe to remember only 3 sentences per node?** Counts only go up and sentences are never removed. When one sentence becomes more popular, all others keep their counts. So any sentence outside a node's top 3 still has 3 better sentences ahead of it and can never sneak in. The new top 3 always comes from the old top 3 plus the updated sentence, and that is exactly the list we sort.

### Important Classes and Data Structures

- **`TrieNode`**: one prefix. `children` maps the next character to the child node. A `HashMap` keeps it simple and treats letters and the space the same way. `topSentences` is the ready made answer for that prefix.
- **`countMap`**: sentence to count. Nodes only store sentence strings, so sorting looks up the counts here. It also gives the old count when a sentence is typed again.
- **`current`**: the node of the typed prefix. Each key press moves it one step down. `null` means nothing matches anymore.
- **`typed`**: the full typed text. We still need it after falling off the Trie, because `#` must save the sentence even if it had no suggestions.

### Code

```java
import java.util.*;

/**
 * One node of the Trie. Every node stands for one prefix.
 * Example: the path c -> a -> r ends at the node for "car".
 */
class TrieNode {
    // next character -> child node
    Map<Character, TrieNode> children = new HashMap<>();

    // up to 3 best sentences that start with this node's prefix, best first
    List<String> topSentences = new ArrayList<>();
}

public class SearchAutocomplete {
    // sentence -> popularity (how many times it was completed)
    Map<String, Integer> countMap = new HashMap<>();
    TrieNode root = new TrieNode();

    // characters typed so far for the current sentence
    StringBuilder typed = new StringBuilder();

    // trie node of the typed prefix, null once no sentence matches it
    TrieNode current = root;

    public SearchAutocomplete(List<String> phrases, List<Integer> counts) {
        for (int i = 0; i < phrases.size(); i++) {
            addSentence(phrases.get(i), counts.get(i));
        }
    }

    public List<String> getSuggestions(char ch) {
        if (ch == '#') {
            // save the finished sentence, then start fresh
            if (typed.length() > 0) {
                addSentence(typed.toString(), 1);
            }
            typed = new StringBuilder();
            current = root;
            return new ArrayList<>();
        }

        typed.append(ch);

        // move one step down. Once we fall off the trie, we stay off until '#'
        if (current != null) {
            current = current.children.get(ch);
        }
        if (current == null) {
            return new ArrayList<>();
        }

        // the answer is already waiting at this node, return a copy of it
        return new ArrayList<>(current.topSentences);
    }

    /**
     * Adds 'times' to the popularity of a sentence (a new sentence starts from 0)
     * and refreshes the top 3 list of every node on its path.
     */
    void addSentence(String sentence, int times) {
        countMap.put(sentence, countMap.getOrDefault(sentence, 0) + times);

        TrieNode node = root;
        for (char c : sentence.toCharArray()) {
            if (!node.children.containsKey(c)) {
                node.children.put(c, new TrieNode());
            }
            node = node.children.get(c);
            updateTopSentences(node, sentence);
        }
    }

    /**
     * 'sentence' just became more popular, so it is the only one that can
     * enter this node's top 3. Remove it if present, add it back, sort, keep 3.
     */
    void updateTopSentences(TrieNode node, String sentence) {
        List<String> top = node.topSentences;
        top.remove(sentence);
        top.add(sentence);
        top.sort((a, b) -> compareSentences(a, b));
        if (top.size() > 3) {
            top.remove(3);   // drop the 4th one
        }
    }

    // negative value means sentence a should be shown before sentence b
    int compareSentences(String a, String b) {
        int countA = countMap.get(a);
        int countB = countMap.get(b);
        if (countA != countB) {
            return countB - countA;   // higher popularity first
        }
        return a.compareTo(b);        // same popularity: smaller ASCII order first
    }
}
```

## Complexity

`N` = number of saved sentences, `L` = maximum sentence length.

| Operation | Brute Force | Trie with Top 3 |
|---|---|---|
| Type a letter or space | O(N × L × log N) | O(1) |
| Type `#` | O(L) | O(L) node updates, each one sorts at most 4 sentences |
| Constructor | O(N × L) | O(N × L) node updates |
| Extra space | O(N × L) | O(N × L) |


With at most 120 sentences, both solutions are fast enough here. But only the Trie version stays fast when the history grows to millions of sentences, because the cost of a key press does not depend on how many sentences are saved.