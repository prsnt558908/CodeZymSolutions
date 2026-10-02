# Search Matching Lines in a Document in Java

#### Problem Statement
[https://codezym.com/question/327-search-matching-lines-in-document](https://codezym.com/question/327-search-matching-lines-in-document)

## Core Idea

The core idea is to do the hard work once and answer every search fast. When the service is created, we build a word index, just like the index at the back of a book: for every word, we store the ids of the lines that contain it, in increasing order. This is called an inverted index, and most search engines are built on it.

To answer a query, we start from the rarest query word, because every matching line must contain it. We walk through its short list of line ids and keep only the ones that also contain the other query words.

Deleting a line only marks it as deleted, and search simply skips marked lines.

We will first solve it the simple way by checking every line, see why that is slow, and then improve it step by step.

## Rules to Keep in Mind

- **Whole words only:** split a line into words and compare whole words. Do not use `line.contains(word)`, because `car` would wrongly match `scar` and `cards`.
- **Case-insensitive:** turn every word into lower case before comparing.
- **Order does not matter, repeated query words count once:** keep the query words in a `Set`.
- **One or more spaces between words:** splitting on a space can give empty strings, so skip them.
- **Line ids never change:** the id of a line is its position in the original list, even after other lines are deleted.
- **Output:** `"lineId,lineText"` with the original text (not the lower case one), sorted by `lineId`.

Both solutions use the same small helper `getWords(text)`, which returns the distinct lower case words of a text.

## Solution 1: Brute Force

### Idea

Keep all lines in a list, and a `deleted` flag for every line.

For a search, convert the query into a set of lower case words. Then go through the lines from id `0` upwards. Skip deleted lines, split every other line into its own set of words, and check if it contains all the query words.

We visit lines in increasing id order, so the result is already sorted.

`deleteLine` only sets the flag. If `lineId` is negative or too large, or the line is already deleted, it returns `false`.

### Code

```java
import java.util.*;

public class SearchService {

    // Original text of every line. The list index is the line id.
    List<String> lines = new ArrayList<>();

    // deleted[lineId] becomes true once that line is deleted.
    boolean[] deleted;

    public SearchService(List<String> documentLines) {
        lines.addAll(documentLines);
        deleted = new boolean[lines.size()];
    }

    public List<String> search(String query) {
        Set<String> queryWords = getWords(query);
        List<String> result = new ArrayList<>();

        // Lines are checked in increasing id order, so the result is already sorted.
        for (int lineId = 0; lineId < lines.size(); lineId++) {
            if (deleted[lineId]) continue;

            // Split the line again on every search: this is the slow part.
            Set<String> lineWords = getWords(lines.get(lineId));
            if (lineWords.containsAll(queryWords)) {
                result.add(lineId + "," + lines.get(lineId));
            }
        }
        return result;
    }

    public boolean deleteLine(int lineId) {
        if (lineId < 0 || lineId >= lines.size() || deleted[lineId]) return false;
        deleted[lineId] = true;
        return true;
    }

    // Returns the distinct lower case words of a text.
    Set<String> getWords(String text) {
        Set<String> words = new HashSet<>();
        for (String word : text.split(" ")) {
            // Many spaces in a row create empty strings, skip them.
            if (!word.isEmpty()) words.add(word.toLowerCase());
        }
        return words;
    }
}
```

### Why is it slow?

Every search splits every line of the document, even when only one line matches. With 100,000 lines of up to 1,000 characters, one search can read 100 million characters.

It also splits the same line again and again on every search, although a line never changes.

- `search`: O(total characters in the document) for every search
- `deleteLine`: O(1)

## Solution 2: Word Index (Optimal)

### Improving it step by step

**Fix 1: Split every line only once.** A line never changes, so we split it in the constructor and never again.

**Fix 2: Flip the question.** Brute force asks "which words does this line have?" for every line. We ask the opposite: "which lines have this word?". We keep a map from every word to the list of line ids that contain it. Now a search only looks at lines that contain the query words.

We add lines in the order `0, 1, 2, ...`, so every list is sorted for free.

**Fix 3: Start from the rarest word.** A matching line must contain every query word, so it must be in the shortest list. We walk only that list, and for each id we check the lists of the other words. Those lists are sorted, so each check is a quick binary search.

Since we walk a sorted list, the results come out in increasing id order. No extra sorting is needed.

**Fix 4: Delete by marking.** Removing an id from the middle of a list is slow, and one line can be in many lists. So we only set `deleted[lineId] = true`, and search skips deleted ids. Delete becomes O(1).

### Why these data structures?

- `Map<String, List<Integer>> wordToLineIds`: the word index. The map finds the lines of a word in O(1). A `List` is enough because ids arrive in sorted order, and a sorted list gives us binary search and sorted results for free. A `Set` per word would also work, but it has no order, so we would have to sort the results, and it uses more memory.
- `boolean[] deleted`: marks deleted lines. Marking and checking are both O(1), and it lets a second `deleteLine` on the same id return `false`.
- `List<String> lines`: the original text, used to build `"lineId,lineText"`.
- `Set<String>` returned by `getWords`: removes repeated words. So a line is added only once to a word's list, and a repeated query word is checked only once.

### Walkthrough

Example 3 has two lines:

```
0: "Java supports object oriented programming"
1: "Programming requires regular practice"
```

Part of the index after the constructor:

```
programming -> [0, 1]
practice    -> [1]
java        -> [0]
requires    -> [1]
```

`search("PRACTICE programming")`:

1. The query words are `practice` and `programming`.
2. Their lists are `[1]` and `[0, 1]`. The shortest one is `[1]`, so line `1` is the only line we check.
3. Line `1` is not deleted, and binary search finds it in `[0, 1]`, so it matches.
4. Result: `["1,Programming requires regular practice"]`.

Example 2 shows how delete works. There, `cities -> [0, 1]`. After `deleteLine(0)` the list is still `[0, 1]`, but `deleted[0]` is `true`. So `search("cities")` skips `0` and returns only `"1,Cities need reliable public transport"`.

### Code

```java
import java.util.*;

public class SearchService {

    // Original text of every line. The list index is the line id.
    List<String> lines = new ArrayList<>();

    // The word index: lower case word -> ids of the lines that contain it.
    // Ids are added in increasing order, so every list is already sorted.
    Map<String, List<Integer>> wordToLineIds = new HashMap<>();

    // deleted[lineId] becomes true once that line is deleted.
    boolean[] deleted;

    public SearchService(List<String> documentLines) {
        lines.addAll(documentLines);
        deleted = new boolean[lines.size()];

        // Split every line only once and add its id to the list of each of its words.
        for (int lineId = 0; lineId < lines.size(); lineId++) {
            for (String word : getWords(lines.get(lineId))) {
                // Create the list the first time we see this word.
                wordToLineIds.computeIfAbsent(word, k -> new ArrayList<>()).add(lineId);
            }
        }
    }

    public List<String> search(String query) {
        List<String> result = new ArrayList<>();

        // Find the list of line ids for every distinct query word.
        List<List<Integer>> idLists = new ArrayList<>();
        for (String word : getWords(query)) {
            List<Integer> ids = wordToLineIds.get(word);
            // No line has this word, so no line can have all the query words.
            if (ids == null) return result;
            idLists.add(ids);
        }
        if (idLists.isEmpty()) return result; // query had no words

        // Shortest list first: the rarest word gives the fewest lines to check.
        idLists.sort((a, b) -> a.size() - b.size());

        // Walk the shortest list in increasing id order, so results come out sorted.
        for (int lineId : idLists.get(0)) {
            if (!deleted[lineId] && isInAllLists(lineId, idLists)) {
                result.add(lineId + "," + lines.get(lineId));
            }
        }
        return result;
    }

    public boolean deleteLine(int lineId) {
        if (lineId < 0 || lineId >= lines.size() || deleted[lineId]) return false;

        // Only mark the line. Search skips deleted lines, so the index is not touched.
        deleted[lineId] = true;
        return true;
    }

    // True when every list contains the line id.
    boolean isInAllLists(int lineId, List<List<Integer>> idLists) {
        for (List<Integer> ids : idLists) {
            // Lists are sorted, so binary search works.
            // It returns a negative number when the id is not in the list.
            if (Collections.binarySearch(ids, lineId) < 0) return false;
        }
        return true;
    }

    // Returns the distinct lower case words of a text.
    // Using a Set means a line is added only once to a word's list.
    Set<String> getWords(String text) {
        Set<String> words = new HashSet<>();
        for (String word : text.split(" ")) {
            // Many spaces in a row create empty strings, skip them.
            if (!word.isEmpty()) words.add(word.toLowerCase());
        }
        return words;
    }
}
```

### Complexity

Let `N` be the number of lines, `W` the number of distinct query words and `R` the length of the rarest word's list.

- Constructor: O(total characters), because every line is split once.
- `search`: O(R × W × log N). We only look at the `R` lines of the rarest word, and check each of them with `W` binary searches.
- `deleteLine`: O(1).
- Space: O(total characters) for the lines and the index.

`R` is usually tiny compared to `N`, which is why this is so much faster than brute force.

| Approach | search | deleteLine |
|---|---|---|
| Brute force | O(total characters in the document) | O(1) |
| Word index | O(R × W × log N) | O(1) |