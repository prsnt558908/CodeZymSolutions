# Repeated Characters in Dictionary Words Due To Faulty Keyboard in Java

#### Problem Statement
[https://codezym.com/question/142-repeated-characters-dictionary-words-faulty-keyboard](https://codezym.com/question/142-repeated-characters-dictionary-words-faulty-keyboard)


## Core Idea

A stuck key only repeats the character you pressed. It can turn `e` into `eee`, but it can never remove a character, add a different character, or change the order.


So it helps to look at a string as **groups** of the same character. `heeellooo` has 4 groups: `h`, `eee`, `ll` and `ooo`. A stuck key can only make a group longer. It never adds, removes or reorders groups.


We split `typed` into groups only once. Then we split each dictionary word into groups and match them with the typed groups, one by one. So the whole solution is just one pass over `typed` and one pass over each word.


## When is a Word Valid?

A dictionary word is valid only when all 3 rules hold:

1. It has the same number of groups as `typed`.
2. Matching groups have the same character.
3. Each typed group is at least as long as the matching word group.


For `typed = "heeellooo"`, the groups are `h:1 e:3 l:2 o:3`.

| Word | Groups | Valid? |
|---|---|---|
| `hello` | `h:1 e:1 l:2 o:1` | Yes, all 3 rules hold |
| `hell` | `h:1 e:1 l:2` | No, the `o` group is missing (rule 1) |
| `hallo` | `h:1 a:1 l:2 o:1` | No, 2nd group is `a`, not `e` (rule 2) |
| `helllo` | `h:1 e:1 l:3 o:1` | No, `l:3` is longer than typed `l:2` (rule 3) |


## Simple Solution: Two Pointers for Every Word

The first idea is to check each word directly against `typed` using two pointers. Pointer `i` walks on `typed` and pointer `j` walks on the word.


At each step, both pointers must be on the same character. We count the length of the current group in both strings and check rule 3. After counting, both pointers are already at the start of their next group.

```java
boolean canProduce(String word, String typed) {
    int i = 0; // walks on typed
    int j = 0; // walks on word
    while (i < typed.length() && j < word.length()) {
        char ch = typed.charAt(i);
        // rule 2: both groups must have the same character
        if (word.charAt(j) != ch) {
            return false;
        }

        // count the current group in both strings
        int typedCount = 0;
        while (i < typed.length() && typed.charAt(i) == ch) {
            typedCount++;
            i++;
        }
        int wordCount = 0;
        while (j < word.length() && word.charAt(j) == ch) {
            wordCount++;
            j++;
        }

        // rule 3: the typed group must be at least as long
        if (typedCount < wordCount) {
            return false;
        }
    }
    // rule 1: both strings must finish together
    return i == typed.length() && j == word.length();
}
```

We call `canProduce(word, typed)` for every dictionary word and keep the ones that return `true`.


### Problem With This Approach

It repeats the same work. For every word, we walk over `typed` and count its groups again, even though `typed` never changes.


For example, let `typed` be `a` repeated 10,000 times, and let the dictionary have 10,000 words like `ab`, `ac`, `abc`. For each word, we count all 10,000 `a` characters again. That adds up to 100 million steps.


Time complexity is `O(D * T + S)`, where `D` is the number of words, `T` is the length of `typed` and `S` is the total length of all words.


## Better Solution: Split Typed into Groups Only Once

The groups of `typed` are the same for every word. So we compute them once and store them in two lists:

- `typedChars`: the character of each group
- `typedCounts`: the length of each group


For `heeellooo`, we get `typedChars = [h, e, l, o]` and `typedCounts = [1, 3, 2, 3]`.


**Why two lists?** We need the groups in their original order, and we need to reach any group by its index. A list gives us both. Keeping the characters in a list of their own also lets us check rules 1 and 2 with a single `equals()` call.


Checking a word now takes 3 small steps:

1. Split the word into groups using the same helper, `buildGroups()`.
2. `wordChars.equals(typedChars)` checks rules 1 and 2 together. Two lists are equal only when they have the same size and the same items in the same order.
3. A simple loop over the counts checks rule 3.


We never walk over `typed` again. A long group like 10,000 `a` characters is now just one number in `typedCounts`. So the work for each word depends only on the length of that word.


### Code

```java
import java.util.*;

public class FaultyKeyboard {

    public FaultyKeyboard() {
    }

    /**
     * Returns all dictionary words that could have produced the typed string,
     * in the same order as they appear in the dictionary.
     */
    public List<String> possibleIntendedWords(String typed, List<String> dictionary) {
        // split typed into groups only once, then reuse them for every word
        List<Character> typedChars = new ArrayList<>();
        List<Integer> typedCounts = new ArrayList<>();
        buildGroups(typed, typedChars, typedCounts);

        List<String> result = new ArrayList<>();
        for (String word : dictionary) {
            if (canProduce(word, typedChars, typedCounts)) {
                result.add(word);
            }
        }
        return result;
    }

    /**
     * Splits text into groups of the same character.
     * Example: "heeellooo" gives chars = [h, e, l, o] and counts = [1, 3, 2, 3]
     */
    void buildGroups(String text, List<Character> chars, List<Integer> counts) {
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            int count = 0;
            // move i to the end of the current group
            while (i < text.length() && text.charAt(i) == ch) {
                count++;
                i++;
            }
            chars.add(ch);
            counts.add(count);
        }
    }

    /**
     * Returns true if the typed string (given as its groups)
     * could have been produced from this word.
     */
    boolean canProduce(String word, List<Character> typedChars, List<Integer> typedCounts) {
        List<Character> wordChars = new ArrayList<>();
        List<Integer> wordCounts = new ArrayList<>();
        buildGroups(word, wordChars, wordCounts);

        // rules 1 and 2: same number of groups, same characters, same order
        if (!wordChars.equals(typedChars)) {
            return false;
        }

        // rule 3: a stuck key can only add characters, never remove them
        for (int i = 0; i < wordCounts.size(); i++) {
            if (typedCounts.get(i) < wordCounts.get(i)) {
                return false;
            }
        }
        return true;
    }
}
```


## Complexity

Let `T` be the length of `typed` and `S` be the total length of all dictionary words.

- **Time:** `O(T + S)`. Splitting `typed` takes `O(T)`. Each word takes time in proportion to its own length, so all words together take `O(S)`.
- **Space:** `O(T + L)`, where `L` is the length of the longest word. This holds the groups of `typed` and the groups of the current word.