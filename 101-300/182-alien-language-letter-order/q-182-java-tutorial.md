# Alien Language Letter Order in Java

#### Problem Statement
[https://codezym.com/question/182-alien-language-letter-order](https://codezym.com/question/182-alien-language-letter-order)


## Core Idea

The word list is sorted by the alien alphabet. So neighboring words quietly give us rules, like "`b` comes before `a`".

Think of each rule as an arrow from one letter to another. A valid alphabet is any order of letters where every arrow points forward. Finding such an order is called a **topological sort**.

We want the smallest valid answer. So we build the order one letter at a time, and at every step we pick the smallest letter that is allowed to go next. If at some point no letter is allowed, the rules go in a circle and the answer is an empty string.


## Step 1: Find the Rules

Compare each word with the word right after it. Only the **first position where they differ** gives a rule.

For `"abca"` and `"cab"`, the first letters differ, so `a` comes before `c`. The letters after that point tell us nothing. In English, `"cat"` before `"dog"` tells us `c < d`, but nothing about `a` and `o`.

Comparing only neighbors is enough. If every word comes before the next word, the whole list is in order. So words that are far apart give no new rules.

**The prefix trap:** if no letter differs, one word is a prefix of the other. Then the shorter word must come first. `"abc"` before `"abcd"` is fine. `"abc"` before `"ab"` can never happen, so we return `""` right away.

```java
List<char[]> rules = new ArrayList<>();

for (int i = 0; i + 1 < words.size(); i++) {
    String first = words.get(i);
    String second = words.get(i + 1);
    int len = Math.min(first.length(), second.length());
    int j = 0;
    // skip the common start of both words
    while (j < len && first.charAt(j) == second.charAt(j)) {
        j++;
    }
    if (j < len) {
        // first different letters: first.charAt(j) comes before second.charAt(j)
        rules.add(new char[]{first.charAt(j), second.charAt(j)});
    } else if (first.length() > second.length()) {
        return ""; // longer word before its own prefix, like "abc" before "ab"
    }
}
```

Some letters never show up in any rule, like `a` and `b` in `["abc", "abx"]`. They must still be in the answer, so we also collect every unique letter from all the words.


## Simple Solution: Scan From a to z Every Time

Now we turn the rules into an order, one letter at a time.

A letter is **free** when every letter that must come before it is already placed. At each step, we check letters from `a` to `z` and place the first free one.

**Why does this give the smallest answer?** The first letter of any valid order must be a free letter. Picking the smallest free letter makes the first position as small as possible. Placing a free letter never breaks a rule and never blocks the remaining letters. So we can make the same choice again for the next position, and the next.

If no letter is free but some letters are still left, the rules must contain a circle, like `a` before `b` and `b` before `a`. So we return `""`.

```java
// letters: set of all unique letters, rules: list from Step 1
StringBuilder order = new StringBuilder();
Set<Character> placed = new HashSet<>();

while (placed.size() < letters.size()) {
    boolean found = false;
    for (char c = 'a'; c <= 'z'; c++) {
        if (letters.contains(c) && !placed.contains(c) && isFree(c, rules, placed)) {
            placed.add(c);
            order.append(c);
            found = true;
            break;
        }
    }
    if (!found) {
        return ""; // no letter is free: the rules form a loop
    }
}
return order.toString();
```

```java
// c is free when every letter that must come before c is already placed
boolean isFree(char c, List<char[]> rules, Set<Character> placed) {
    for (char[] rule : rules) {
        if (rule[1] == c && !placed.contains(rule[0])) {
            return false;
        }
    }
    return true;
}
```

**The problem:** `isFree()` scans the whole rule list for every letter, in every round. With `k` letters and `r` rules, that is about `k × k × r` checks, and almost all of them repeat work we already did.


## Optimized Solution: Kahn's Algorithm With a Min-Heap

Two small changes remove the repeated work.

**1. Keep a count instead of rechecking rules.**
For every letter, store `inDegree`: how many letters must still come before it. A letter is free when its count is `0`. When we place a letter, we lower the count only for the letters that come right after it. Now each rule is handled just once.

**2. Keep free letters in a min-heap.**
A min-heap (`PriorityQueue`) always hands back its smallest letter. So we no longer scan from `a` to `z`.

This is the well known **Kahn's algorithm** for topological sort. The only twist is the min-heap in place of a normal queue, and that is what makes the answer the smallest.

**Loop check:** if the heap runs empty before every letter is placed, some letters never became free. That only happens when the rules contain a circle, so we return `""`.


### Important Data Structures

- **`graph`** (`Map<Character, Set<Character>>`): for each letter, the letters that must come after it. When we place a letter, this tells us whose counts to lower.
- **`inDegree`** (`Map<Character, Integer>`): how many letters must still come before each letter. It tells us in one step if a letter is free.
- **`freeLetters`** (`PriorityQueue<Character>`): every letter that is free right now, with the smallest one always on top.

**Watch out for repeated rules.** In `["za", "zb", "ca", "cb"]`, two pairs give the same rule `a` before `b`. The set inside `graph` stores it once, and we raise `inDegree` only when the rule is new. If we counted it twice, `b` would wait for two arrows when only one exists, and it would never become free.


### Dry Run

Words: `["za", "zb", "ca", "cb"]`

Rules: `a → b` (found twice, kept once) and `z → c`.

Starting counts: `a = 0`, `b = 1`, `c = 1`, `z = 0`.

| Step | Free letters | Pick | Order so far | Counts updated |
|------|--------------|------|--------------|----------------|
| 1 | a, z | a | a | b: 1 → 0, so b is free |
| 2 | b, z | b | ab | none |
| 3 | z | z | abz | c: 1 → 0, so c is free |
| 4 | c | c | abzc | none |

Answer: `"abzc"`.

Look at step 2. `b` just became free and it is smaller than `z`, so it goes first. A normal first in, first out queue would pick `z` there and return `"azbc"`. That order is valid, but it is not the smallest.


### Complexity

Let `L` be the total number of letters in all words, `k` the number of unique letters (at most 26), and `r` the number of unique rules.

- **Time:** `O(L + (k + r) log k)`. Collecting letters and finding rules takes `O(L)`. Each letter enters the heap once and each rule is used once. Since `k` is at most 26, the heap part is tiny, so in practice this is `O(L)`.
- **Space:** `O(k + r)` for the graph, the counts and the heap.


### Java Code

```java
import java.util.*;

public class AlienLanguageOrder {

    public AlienLanguageOrder() {
    }

    public String alienOrder(List<String> words) {
        // graph: letter -> letters that must come after it
        Map<Character, Set<Character>> graph = new HashMap<>();
        // inDegree: letter -> how many letters must still come before it
        Map<Character, Integer> inDegree = new HashMap<>();

        // Every unique letter must be in the answer, even if no rule mentions it
        for (String word : words) {
            for (char c : word.toCharArray()) {
                graph.putIfAbsent(c, new HashSet<>());
                inDegree.putIfAbsent(c, 0);
            }
        }

        // Step 1: find one rule from each pair of neighboring words
        for (int i = 0; i + 1 < words.size(); i++) {
            if (!addRule(words.get(i), words.get(i + 1), graph, inDegree)) {
                return "";
            }
        }

        // Step 2: Kahn's algorithm, always taking the smallest free letter
        PriorityQueue<Character> freeLetters = new PriorityQueue<>();
        for (char c : inDegree.keySet()) {
            if (inDegree.get(c) == 0) {
                freeLetters.add(c);
            }
        }

        StringBuilder order = new StringBuilder();
        while (!freeLetters.isEmpty()) {
            char letter = freeLetters.poll();
            order.append(letter);

            // letter is placed, so every letter after it waits for one less letter
            for (char after : graph.get(letter)) {
                inDegree.put(after, inDegree.get(after) - 1);
                if (inDegree.get(after) == 0) {
                    freeLetters.add(after);
                }
            }
        }

        // Some letters never became free: the rules form a loop
        if (order.length() < inDegree.size()) {
            return "";
        }
        return order.toString();
    }

    // Adds the rule given by two neighboring words.
    // Returns false when the pair is impossible, like "abc" before "ab".
    boolean addRule(String first, String second,
                    Map<Character, Set<Character>> graph, Map<Character, Integer> inDegree) {
        int len = Math.min(first.length(), second.length());
        for (int j = 0; j < len; j++) {
            char before = first.charAt(j);
            char after = second.charAt(j);
            if (before != after) {
                // count a rule only the first time we see it
                if (graph.get(before).add(after)) {
                    inDegree.put(after, inDegree.get(after) + 1);
                }
                return true;
            }
        }
        // No different letter found, so the shorter word must come first
        return first.length() <= second.length();
    }
}
```